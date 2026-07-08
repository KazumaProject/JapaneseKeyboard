package com.kazumaproject.markdownhelperkeyboard.zeroquery

import android.content.res.AssetManager
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

private const val TOKEN_ENTRY_SIZE = 16
private const val NORMAL_TOKEN_PATH = "mozc/zero_query/zero_query_token.data"
private const val NORMAL_STRING_PATH = "mozc/zero_query/zero_query_string.data"
private const val NUMBER_TOKEN_PATH = "mozc/zero_query/zero_query_number_token.data"
private const val NUMBER_STRING_PATH = "mozc/zero_query/zero_query_number_string.data"
private const val DEFAULT_NUMBER_SUFFIX_KEY = "default"

const val ZERO_QUERY_CANDIDATE_TYPE: Byte = 9

enum class ZeroQueryType(val code: Int) {
    None(0),
    NumberSuffix(1),
    Emoticon(2),
    Emoji(3),
    ;

    companion object {
        fun fromCode(code: Int): ZeroQueryType =
            values().firstOrNull { it.code == code }
                ?: error("Invalid zero query type: type=$code")
    }
}

data class ZeroQuerySuggestion(
    val value: String,
    val type: ZeroQueryType,
)

interface ZeroQueryAssetReader {
    fun readBytes(path: String): ByteArray
}

class AndroidZeroQueryAssetReader(
    private val assetManager: AssetManager,
) : ZeroQueryAssetReader {
    override fun readBytes(path: String): ByteArray =
        assetManager.open(path).use { it.readBytes() }
}

class LazyZeroQueryProvider(
    private val factory: () -> ZeroQueryProvider,
) {
    private var provider: ZeroQueryProvider? = null

    val isInitialized: Boolean
        get() = provider != null

    fun getIfEnabled(enabled: Boolean): ZeroQueryProvider? {
        if (!enabled) {
            return null
        }
        return provider ?: factory().also { provider = it }
    }
}

class ZeroQueryProvider(
    private val reader: ZeroQueryAssetReader,
) {
    private val normalDict: ZeroQueryDict by lazy {
        ZeroQueryDict(
            tokenBytes = reader.readBytes(NORMAL_TOKEN_PATH),
            stringBytes = reader.readBytes(NORMAL_STRING_PATH),
        )
    }

    private val numberDict: ZeroQueryDict by lazy {
        ZeroQueryDict(
            tokenBytes = reader.readBytes(NUMBER_TOKEN_PATH),
            stringBytes = reader.readBytes(NUMBER_STRING_PATH),
        )
    }

    fun lookup(key: String): List<ZeroQuerySuggestion> {
        if (key.isBlank()) {
            return emptyList()
        }

        val result = mutableListOf<ZeroQuerySuggestion>()
        result += normalDict.lookup(key)
        normalizeNumberKeyOrNull(key)?.let { numberKey ->
            result += numberDict.lookup(numberKey)
            result += numberDict.lookup(DEFAULT_NUMBER_SUFFIX_KEY)
        }

        val seen = LinkedHashSet<String>()
        return result.mapNotNull { suggestion ->
            val value = suggestion.value
            if (value.isBlank() || !seen.add(value)) {
                null
            } else {
                suggestion
            }
        }
    }
}

private fun normalizeNumberKeyOrNull(key: String): String? {
    if (key.isEmpty()) {
        return null
    }

    val normalized = StringBuilder()
    var index = 0
    while (index < key.length) {
        val codePoint = key.codePointAt(index)
        val digit = Character.digit(codePoint, 10)
        if (digit < 0) {
            return null
        }
        normalized.append(digit)
        index += Character.charCount(codePoint)
    }
    return normalized.toString()
}

fun ZeroQuerySuggestion.toCandidate(): Candidate =
    Candidate(
        string = value,
        type = ZERO_QUERY_CANDIDATE_TYPE,
        length = value.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
        score = 0,
        yomi = value,
    )

private data class DecodedZeroQueryToken(
    val keyIndex: Int,
    val valueIndex: Int,
    val type: ZeroQueryType,
)

private class ZeroQueryDict(
    tokenBytes: ByteArray,
    stringBytes: ByteArray,
) {
    private val strings: List<String> = SerializedStringArrayReader.read(stringBytes)
    private val tokens: List<DecodedZeroQueryToken> = decodeTokens(tokenBytes)

    fun lookup(key: String): List<ZeroQuerySuggestion> {
        val lower = lowerBound(key)
        val upper = upperBound(key)
        if (lower == upper) {
            return emptyList()
        }
        return tokens.subList(lower, upper).map { token ->
            ZeroQuerySuggestion(
                value = strings[token.valueIndex],
                type = token.type,
            )
        }
    }

    private fun lowerBound(key: String): Int {
        var low = 0
        var high = tokens.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (UnicodeCodePointStringComparator.compare(keyAt(mid), key) < 0) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    private fun upperBound(key: String): Int {
        var low = 0
        var high = tokens.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (UnicodeCodePointStringComparator.compare(key, keyAt(mid)) < 0) {
                high = mid
            } else {
                low = mid + 1
            }
        }
        return low
    }

    private fun keyAt(tokenIndex: Int): String = strings[tokens[tokenIndex].keyIndex]

    private fun decodeTokens(tokenBytes: ByteArray): List<DecodedZeroQueryToken> {
        if (tokenBytes.size % TOKEN_ENTRY_SIZE != 0) {
            error(
                "Invalid zero query token array: byte size=${tokenBytes.size}, " +
                    "reason=token file size is not a multiple of $TOKEN_ENTRY_SIZE"
            )
        }

        val result = ArrayList<DecodedZeroQueryToken>(tokenBytes.size / TOKEN_ENTRY_SIZE)
        var previousKey: String? = null
        for (offset in tokenBytes.indices step TOKEN_ENTRY_SIZE) {
            val tokenIndex = offset / TOKEN_ENTRY_SIZE
            val keyIndex = tokenBytes.readUInt32LE(offset).toIntInRange("key_index", tokenIndex)
            val valueIndex = tokenBytes.readUInt32LE(offset + 4).toIntInRange("value_index", tokenIndex)
            val typeCode = tokenBytes.readUInt16LE(offset + 8)
            val unused16 = tokenBytes.readUInt16LE(offset + 10)
            val unused32 = tokenBytes.readUInt32LE(offset + 12)

            if (keyIndex !in strings.indices) {
                failToken(tokenIndex, "key_index out of range: key_index=$keyIndex, string array size=${strings.size}")
            }
            if (valueIndex !in strings.indices) {
                failToken(tokenIndex, "value_index out of range: value_index=$valueIndex, string array size=${strings.size}")
            }
            if (unused16 != 0 || unused32 != 0L) {
                failToken(tokenIndex, "unused fields must be zero: unused16=$unused16, unused32=$unused32")
            }

            val key = strings[keyIndex]
            val previous = previousKey
            if (previous != null && UnicodeCodePointStringComparator.compare(previous, key) > 0) {
                failToken(tokenIndex, "token array is not sorted by key string: previous='$previous', current='$key'")
            }
            previousKey = key

            result += DecodedZeroQueryToken(
                keyIndex = keyIndex,
                valueIndex = valueIndex,
                type = ZeroQueryType.fromCode(typeCode),
            )
        }
        return result
    }

    private fun Long.toIntInRange(fieldName: String, tokenIndex: Int): Int {
        if (this > Int.MAX_VALUE) {
            failToken(tokenIndex, "$fieldName is too large: $fieldName=$this")
        }
        return toInt()
    }

    private fun failToken(tokenIndex: Int, reason: String): Nothing =
        error("Invalid zero query token array: token index=$tokenIndex, reason=$reason")
}
