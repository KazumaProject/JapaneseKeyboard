package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactDictionaryKind
import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactDictionaryTriple
import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactEnglishDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactEnglishDictionaryReader
import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactSystemDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactSystemDictionaryReader
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryBinaryReader @Inject constructor(
    private val resolver: DictionarySourceResolver,
    private val store: DictionaryOverrideStore,
) {
    @Volatile
    private var posTableCache: PosTableCache? = null

    @Volatile
    private var connectionMatrixCache: ConnectionMatrixCache? = null

    @Volatile
    private var compactSystemDictionaryCache: CompactSystemDictionary? = null

    @Volatile
    private var compactSystemDictionaryLoadAttempted = false

    @Volatile
    private var compactEnglishDictionaryCache: CompactEnglishDictionary? = null

    @Volatile
    private var compactEnglishDictionaryLoadAttempted = false

    fun openZipAwareObjectInputStream(input: InputStream, debugName: String): ObjectInputStream =
        openZipAwareObject(input, debugName)

    fun openZipAwareRawInputStream(input: InputStream, debugName: String): InputStream {
        return openZipAwareRaw(input, debugName)
    }

    fun openZipAwareTextReader(input: InputStream, debugName: String): BufferedReader =
        openZipAwareText(input, debugName)

    fun resolveCategoryLoadState(category: DictionaryCategory): DictionaryCategoryLoadState =
        resolver.resolveCategoryLoadState(category)

    fun loadLouds(key: DictionaryFileKey): LOUDS {
        compactBundledDictionary(key)?.tangoTrie?.let { return it }
        return loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use {
                LOUDS().readExternalNotCompress(it)
            }
        }
    }

    fun loadLoudsWithTermId(key: DictionaryFileKey): LOUDSWithTermId {
        compactBundledDictionary(key)?.yomiTrie?.let { return it }
        return loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use {
                LOUDSWithTermId().readExternalNotCompress(it)
            }
        }
    }

    fun loadTokenArray(key: DictionaryFileKey): TokenArray {
        compactBundledDictionary(key)?.tokenArray?.let { return it }
        return TokenArray().also { tokenArray ->
            loadTokenArrayInto(key, tokenArray)
            readPosTableInto(tokenArray)
        }
    }

    fun loadSystemYomiLbsIndex(yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        loadYomiLbsIndex(DictionaryFileKey.SYSTEM_YOMI, yomiTrie)

    fun loadSystemYomiLeafIndex(yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        loadYomiLeafIndex(DictionaryFileKey.SYSTEM_YOMI, yomiTrie)

    fun loadSystemTokenIndex(tokenArray: TokenArray): SuccinctBitVector =
        loadTokenIndex(DictionaryFileKey.SYSTEM_TOKEN, tokenArray)

    fun loadSystemTangoLbsIndex(tangoTrie: LOUDS): SuccinctBitVector =
        loadTangoLbsIndex(DictionaryFileKey.SYSTEM_TANGO, tangoTrie)

    fun loadYomiLbsIndex(key: DictionaryFileKey, yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        compactBundledDictionary(key)?.yomiLbsIndex ?: SuccinctBitVector(yomiTrie.LBS)

    fun loadYomiLeafIndex(key: DictionaryFileKey, yomiTrie: LOUDSWithTermId): SuccinctBitVector =
        compactBundledDictionary(key)?.yomiLeafIndex ?: SuccinctBitVector(yomiTrie.isLeaf)

    fun loadTokenIndex(key: DictionaryFileKey, tokenArray: TokenArray): SuccinctBitVector =
        compactBundledDictionary(key)?.tokenIndex ?: SuccinctBitVector(tokenArray.bitvector)

    fun loadTangoLbsIndex(key: DictionaryFileKey, tangoTrie: LOUDS): SuccinctBitVector =
        compactBundledDictionary(key)?.tangoLbsIndex ?: SuccinctBitVector(tangoTrie.LBS)

    fun loadTokenArrayInto(key: DictionaryFileKey, tokenArray: TokenArray) {
        loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use { objectInput ->
                tokenArray.readExternal(objectInput)
            }
        }
    }

    fun readPosTableInto(tokenArray: TokenArray) {
        val posTable = loadPosTable()
        tokenArray.setPOSTable(posTable.leftIds, posTable.rightIds)
    }

    fun loadConnectionIds(key: DictionaryFileKey = DictionaryFileKey.CONNECTION_ID): ShortArray =
        loadWithBundledFallback(key) { input ->
            openZipAwareRaw(input, key.name) { raw, byteSize ->
                raw.use {
                    ConnectionIdBuilder().readShortArrayFromBytes(
                        inputStream = it,
                        expectedByteSize = byteSize,
                    )
                }
            }
        }

    fun loadConnectionMatrix(key: DictionaryFileKey = DictionaryFileKey.CONNECTION_ID): ConnectionMatrix.CostTable {
        val revision = store.currentRevision
        connectionMatrixCache
            ?.takeIf { it.revision == revision && it.key == key }
            ?.let { return it.costTable }

        return synchronized(this) {
            connectionMatrixCache
                ?.takeIf { it.revision == revision && it.key == key }
                ?.costTable
                ?: createConnectionMatrix(key)
                    .also { connectionMatrixCache = ConnectionMatrixCache(revision, key, it) }
        }
    }

    fun openIdDefReader(key: DictionaryFileKey = DictionaryFileKey.ID_DEF): BufferedReader {
        val bytes = loadWithBundledFallback(key) { input ->
            openZipAwareRawInputStream(input, key.name).use { it.readBytes() }
        }
        return BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8))
    }

    fun loadEnglishReading(key: DictionaryFileKey): com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId =
        compactBundledEnglishDictionaryIfActive()?.readingTrie ?: loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use {
                com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId()
                    .readExternalNotCompress(it)
            }
        }

    fun loadEnglishWord(key: DictionaryFileKey): com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS =
        compactBundledEnglishDictionaryIfActive()?.wordTrie ?: loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use {
                com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS()
                    .readExternalNotCompress(it)
            }
        }

    fun loadEnglishToken(key: DictionaryFileKey): com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray =
        compactBundledEnglishDictionaryIfActive()?.tokenArray ?: loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use {
                com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray()
                    .readExternal(it)
            }
        }

    fun loadEnglishReadingLbsIndex(
        trie: com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId,
    ): SuccinctBitVector =
        compactBundledEnglishDictionaryIfActive()?.readingLbsIndex ?: SuccinctBitVector(trie.LBS)

    fun loadEnglishReadingLeafIndex(
        trie: com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId,
    ): SuccinctBitVector =
        compactBundledEnglishDictionaryIfActive()?.readingLeafIndex ?: SuccinctBitVector(trie.isLeaf)

    fun loadEnglishWordLbsIndex(
        trie: com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS,
    ): SuccinctBitVector =
        compactBundledEnglishDictionaryIfActive()?.wordLbsIndex ?: SuccinctBitVector(trie.LBS)

    fun loadEnglishTokenIndex(
        tokenArray: com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray,
    ): SuccinctBitVector =
        compactBundledEnglishDictionaryIfActive()?.tokenIndex ?: SuccinctBitVector(tokenArray.bitvector)

    private fun <T> loadWithBundledFallback(
        key: DictionaryFileKey,
        loader: (InputStream) -> T,
    ): T {
        val shouldUseOverride = resolver.shouldUseOverride(key)
        if (shouldUseOverride) {
            runCatching {
                resolver.openOverrideForKey(key).use(loader)
            }.onSuccess { return it }
                .onFailure { error ->
                    Timber.w(error, "External dictionary override failed for %s. Falling back to bundled asset.", key)
                    store.markInvalid(key, error.message ?: error::class.java.simpleName)
                }
        }
        return resolver.openBundledForKey(key).use(loader)
    }

    private fun loadPosTable(): PosTableCache {
        val revision = store.currentRevision
        posTableCache
            ?.takeIf { it.revision == revision }
            ?.let { return it }

        return synchronized(this) {
            posTableCache
                ?.takeIf { it.revision == revision }
                ?: loadWithBundledFallback(DictionaryFileKey.POS_TABLE) { input ->
                    openZipAwareObjectInputStream(input, DictionaryFileKey.POS_TABLE.name).use { objectInput ->
                        PosTableCache(
                            revision = revision,
                            leftIds = objectInput.readObject() as ShortArray,
                            rightIds = objectInput.readObject() as ShortArray,
                        )
                    }
                }.also { posTableCache = it }
        }
    }

    private data class PosTableCache(
        val revision: Long,
        val leftIds: ShortArray,
        val rightIds: ShortArray,
    )

    private fun createConnectionMatrix(key: DictionaryFileKey): ConnectionMatrix.CostTable {
        if (key == DictionaryFileKey.CONNECTION_ID && !resolver.shouldUseOverride(key)) {
            runCatching {
                resolver.openBundledAsset(COMPACT_CONNECTION_MATRIX_ASSET).use { data ->
                    resolver.openBundledAsset(COMPACT_CONNECTION_MATRIX_INDEX_ASSET).use { index ->
                        ConnectionMatrix.fromCompactInputStreams(data, index)
                    }
                }
            }.onSuccess { return it }
                .onFailure { error ->
                    Timber.w(
                        error,
                        "Exact compact connection matrix is unavailable. Falling back to dense matrix.",
                    )
                }
        }
        return ConnectionMatrix.fromShortArray(loadConnectionIds(key))
    }

    private fun compactBundledSystemDictionary(): CompactSystemDictionary? {
        compactSystemDictionaryCache?.let { return it }
        if (compactSystemDictionaryLoadAttempted) return null
        return synchronized(this) {
            compactSystemDictionaryCache?.let { return@synchronized it }
            if (compactSystemDictionaryLoadAttempted) return@synchronized null
            compactSystemDictionaryLoadAttempted = true
            runCatching {
                resolver.openBundledAssetFileDescriptor(COMPACT_SYSTEM_DICTIONARY_ASSET).use(
                    CompactSystemDictionaryReader::read,
                )
            }.onFailure { error ->
                Timber.w(
                    error,
                    "Compact system dictionary is unavailable. Falling back to serialized assets.",
                )
            }.getOrNull()?.also { compactSystemDictionaryCache = it }
        }
    }

    private fun compactBundledEnglishDictionaryIfActive(): CompactEnglishDictionary? {
        if (resolver.shouldUseOverrideCategory(DictionaryCategory.ENGLISH)) return null
        compactEnglishDictionaryCache?.let { return it }
        if (compactEnglishDictionaryLoadAttempted) return null
        return synchronized(this) {
            compactEnglishDictionaryCache?.let { return@synchronized it }
            if (compactEnglishDictionaryLoadAttempted) return@synchronized null
            compactEnglishDictionaryLoadAttempted = true
            runCatching {
                resolver.openBundledAssetFileDescriptor(COMPACT_ENGLISH_DICTIONARY_ASSET).use(
                    CompactEnglishDictionaryReader::read,
                )
            }.onFailure { error ->
                Timber.w(
                    error,
                    "Compact English dictionary is unavailable. Falling back to serialized assets.",
                )
            }.getOrNull()?.also { compactEnglishDictionaryCache = it }
        }
    }

    private fun compactBundledDictionary(key: DictionaryFileKey): CompactDictionaryTriple? {
        val category = DictionaryFileSpecs.get(key).category
        val kind = when (category) {
            DictionaryCategory.SYSTEM -> CompactDictionaryKind.SYSTEM
            DictionaryCategory.SINGLE_KANJI -> CompactDictionaryKind.SINGLE_KANJI
            DictionaryCategory.EMOJI -> CompactDictionaryKind.EMOJI
            DictionaryCategory.EMOTICON -> CompactDictionaryKind.EMOTICON
            DictionaryCategory.SYMBOL -> CompactDictionaryKind.SYMBOL
            DictionaryCategory.READING_CORRECTION -> CompactDictionaryKind.READING_CORRECTION
            DictionaryCategory.KOTOWAZA -> CompactDictionaryKind.KOTOWAZA
            else -> return null
        }
        if (resolver.shouldUseOverrideCategory(category)) return null
        return compactBundledSystemDictionary()?.dictionaries?.get(kind)
    }

    private data class ConnectionMatrixCache(
        val revision: Long,
        val key: DictionaryFileKey,
        val costTable: ConnectionMatrix.CostTable,
    )

    companion object {
        private const val COMPACT_CONNECTION_MATRIX_ASSET = "connection/connection.compact"
        private const val COMPACT_CONNECTION_MATRIX_INDEX_ASSET =
            "connection/connection.compact.index"
        private const val COMPACT_SYSTEM_DICTIONARY_ASSET = "system/system.compact.kdict"
        private const val COMPACT_ENGLISH_DICTIONARY_ASSET = "english/english.compact.kdict"
        private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        fun openZipAwareObject(input: InputStream, debugName: String): ObjectInputStream =
            ObjectInputStream(BufferedInputStream(openZipAwareRaw(input, debugName)))

        fun openZipAwareText(input: InputStream, debugName: String): BufferedReader =
            BufferedReader(InputStreamReader(openZipAwareRaw(input, debugName), Charsets.UTF_8))

        fun openZipAwareRaw(input: InputStream, debugName: String): InputStream {
            return openZipAwareRaw(input, debugName) { raw, _ -> raw }
        }

        fun <T> openZipAwareRaw(
            input: InputStream,
            debugName: String,
            block: (InputStream, Long?) -> T,
        ): T {
            val rawByteSize = input.rawByteSizeOrNull()
            val buffered = if (input.markSupported()) input else BufferedInputStream(input)
            buffered.mark(ZIP_SIGNATURE.size)
            val header = ByteArray(ZIP_SIGNATURE.size)
            val read = buffered.read(header)
            buffered.reset()
            if (read == ZIP_SIGNATURE.size && header.contentEquals(ZIP_SIGNATURE)) {
                val zipInputStream = ZipInputStream(buffered)
                var entry = zipInputStream.nextEntry
                while (entry != null && entry.isDirectory) {
                    entry = zipInputStream.nextEntry
                }
                require(entry != null) { "No readable entry found in $debugName" }
                return block(zipInputStream, entry.size.takeIf { it >= 0L })
            }
            return block(buffered, rawByteSize)
        }

        private fun InputStream.rawByteSizeOrNull(): Long? {
            val fileInputStream = this as? FileInputStream ?: return null
            return runCatching {
                fileInputStream.channel.size() - fileInputStream.channel.position()
            }.getOrNull()?.takeIf { it >= 0L }
        }
    }
}
