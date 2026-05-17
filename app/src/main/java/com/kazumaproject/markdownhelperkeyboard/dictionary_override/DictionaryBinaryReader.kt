package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
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
    fun openZipAwareObjectInputStream(input: InputStream, debugName: String): ObjectInputStream =
        openZipAwareObject(input, debugName)

    fun openZipAwareRawInputStream(input: InputStream, debugName: String): InputStream {
        return openZipAwareRaw(input, debugName)
    }

    fun openZipAwareTextReader(input: InputStream, debugName: String): BufferedReader =
        openZipAwareText(input, debugName)

    fun loadLouds(key: DictionaryFileKey): LOUDS =
        loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use {
                LOUDS().readExternalNotCompress(it)
            }
        }

    fun loadLoudsWithTermId(key: DictionaryFileKey): LOUDSWithTermId =
        loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use {
                LOUDSWithTermId().readExternalNotCompress(it)
            }
        }

    fun loadTokenArray(key: DictionaryFileKey): TokenArray =
        TokenArray().also { tokenArray ->
            loadTokenArrayInto(key, tokenArray)
            readPosTableInto(tokenArray)
        }

    fun loadTokenArrayInto(key: DictionaryFileKey, tokenArray: TokenArray) {
        loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use { objectInput ->
                tokenArray.readExternal(objectInput)
            }
        }
    }

    fun readPosTableInto(tokenArray: TokenArray) {
        loadWithBundledFallback(DictionaryFileKey.POS_TABLE) { input ->
            openZipAwareObjectInputStream(input, DictionaryFileKey.POS_TABLE.name).use { objectInput ->
                tokenArray.readPOSTable(objectInput)
            }
        }
    }

    fun loadConnectionIds(key: DictionaryFileKey = DictionaryFileKey.CONNECTION_ID): ShortArray =
        loadWithBundledFallback(key) { input ->
            openZipAwareRawInputStream(input, key.name).use { raw ->
                ConnectionIdBuilder().readShortArrayFromBytes(raw)
            }
        }

    fun openIdDefReader(key: DictionaryFileKey = DictionaryFileKey.ID_DEF): BufferedReader {
        val bytes = loadWithBundledFallback(key) { input ->
            openZipAwareRawInputStream(input, key.name).use { it.readBytes() }
        }
        return BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8))
    }

    fun loadEnglishReading(key: DictionaryFileKey): com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId =
        loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use {
                com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId()
                    .readExternalNotCompress(it)
            }
        }

    fun loadEnglishWord(key: DictionaryFileKey): com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS =
        loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use {
                com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS()
                    .readExternalNotCompress(it)
            }
        }

    fun loadEnglishToken(key: DictionaryFileKey): com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray =
        loadWithBundledFallback(key) { input ->
            openZipAwareObjectInputStream(input, key.name).use {
                com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray()
                    .readExternal(it)
            }
        }

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

    companion object {
        private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        fun openZipAwareObject(input: InputStream, debugName: String): ObjectInputStream =
            ObjectInputStream(BufferedInputStream(openZipAwareRaw(input, debugName)))

        fun openZipAwareText(input: InputStream, debugName: String): BufferedReader =
            BufferedReader(InputStreamReader(openZipAwareRaw(input, debugName), Charsets.UTF_8))

        fun openZipAwareRaw(input: InputStream, debugName: String): InputStream {
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
                return zipInputStream
            }
            return buffered
        }
    }
}
