package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryOverrideValidator @Inject constructor() {
    fun validate(file: File, spec: DictionaryFileSpec): ValidationResult {
        return runCatching {
            FileInputStream(file).use { input ->
                when (spec.contentType) {
                    DictionaryContentType.LOUDS -> DictionaryBinaryReaderForValidation.openObject(input, spec.key.name).use {
                        LOUDS().readExternalNotCompress(it)
                    }
                    DictionaryContentType.LOUDS_WITH_TERM_ID -> DictionaryBinaryReaderForValidation.openObject(input, spec.key.name).use {
                        LOUDSWithTermId().readExternalNotCompress(it)
                    }
                    DictionaryContentType.TOKEN_ARRAY -> DictionaryBinaryReaderForValidation.openObject(input, spec.key.name).use {
                        TokenArray().readExternal(it)
                    }
                    DictionaryContentType.POS_TABLE -> DictionaryBinaryReaderForValidation.openObject(input, spec.key.name).use {
                        TokenArray().readPOSTable(it)
                    }
                    DictionaryContentType.CONNECTION_IDS -> DictionaryBinaryReaderForValidation.openRaw(input, spec.key.name).use {
                        val values = ConnectionIdBuilder().readShortArrayFromBytes(it)
                        require(values.isNotEmpty()) { "connectionId is empty" }
                    }
                    DictionaryContentType.ID_DEF_TEXT -> DictionaryBinaryReaderForValidation.openText(input, spec.key.name).use { reader ->
                        val parsed = reader.lineSequence().count { line ->
                            val trimmed = line.trim()
                            trimmed.isNotEmpty() && trimmed.substringBefore(' ').toIntOrNull() != null
                        }
                        require(parsed > 0) { "id.def has no valid entries" }
                    }
                    DictionaryContentType.ENGLISH_READING -> DictionaryBinaryReaderForValidation.openObject(input, spec.key.name).use {
                        com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId()
                            .readExternalNotCompress(it)
                    }
                    DictionaryContentType.ENGLISH_WORD -> DictionaryBinaryReaderForValidation.openObject(input, spec.key.name).use {
                        com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS()
                            .readExternalNotCompress(it)
                    }
                    DictionaryContentType.ENGLISH_TOKEN -> DictionaryBinaryReaderForValidation.openObject(input, spec.key.name).use {
                        com.kazumaproject.markdownhelperkeyboard.converter.english.tokenArray.TokenArray()
                            .readExternal(it)
                    }
                    DictionaryContentType.ENGLISH_QWERTY_GLIDE_INDEX ->
                        error("QWERTY glide index is a bundled runtime artifact")
                }
            }
            ValidationResult.valid()
        }.getOrElse { error ->
            ValidationResult.invalid(error.message ?: error::class.java.simpleName)
        }
    }
}

private object DictionaryBinaryReaderForValidation {
    fun openObject(input: java.io.InputStream, debugName: String) =
        java.io.ObjectInputStream(BufferedInputStream(openRaw(input, debugName)))

    fun openText(input: java.io.InputStream, debugName: String) =
        openRaw(input, debugName).bufferedReader(Charsets.UTF_8)

    fun openRaw(input: java.io.InputStream, debugName: String): java.io.InputStream {
        val buffered = if (input.markSupported()) input else BufferedInputStream(input)
        buffered.mark(4)
        val header = ByteArray(4)
        val read = buffered.read(header)
        buffered.reset()
        if (read == 4 && header.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))) {
            val zipInputStream = java.util.zip.ZipInputStream(buffered)
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
