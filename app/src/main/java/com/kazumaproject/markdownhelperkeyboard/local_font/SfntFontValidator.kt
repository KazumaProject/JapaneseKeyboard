package com.kazumaproject.markdownhelperkeyboard.local_font

import java.io.File
import java.io.RandomAccessFile

internal const val MAX_LOCAL_FONT_BYTES = 32L * 1024L * 1024L

internal enum class FontFormatIssue {
    EMPTY,
    TOO_LARGE,
    COLLECTION,
    WEB_FONT,
    UNSUPPORTED_SIGNATURE,
    MALFORMED,
    VARIABLE,
}

internal class FontValidationException(
    val issue: FontFormatIssue,
    cause: Throwable? = null,
) : IllegalArgumentException(issue.name, cause)

/** Validates the sfnt envelope and table directory without allocating based on file contents. */
internal object SfntFontValidator {
    fun validate(file: File) {
        if (!file.isFile) throw FontValidationException(FontFormatIssue.MALFORMED)
        RandomAccessFile(file, "r").use { raf ->
            validate(raf.length()) { offset, length ->
                if (offset < 0L || length < 0 || offset > raf.length() - length) {
                    throw FontValidationException(FontFormatIssue.MALFORMED)
                }
                val result = ByteArray(length)
                raf.seek(offset)
                raf.readFully(result)
                result
            }
        }
    }

    fun validate(bytes: ByteArray) {
        validate(bytes.size.toLong()) { offset, length ->
            if (offset < 0L || length < 0 || offset > bytes.size.toLong() - length) {
                throw FontValidationException(FontFormatIssue.MALFORMED)
            }
            bytes.copyOfRange(offset.toInt(), offset.toInt() + length)
        }
    }

    private fun validate(size: Long, read: (Long, Int) -> ByteArray) {
        if (size == 0L) throw FontValidationException(FontFormatIssue.EMPTY)
        if (size > MAX_LOCAL_FONT_BYTES) throw FontValidationException(FontFormatIssue.TOO_LARGE)
        if (size < 12L) throw FontValidationException(FontFormatIssue.MALFORMED)

        val header = read(0L, 12)
        val signature = header.copyOfRange(0, 4).toString(Charsets.ISO_8859_1)
        when (signature) {
            "ttcf" -> throw FontValidationException(FontFormatIssue.COLLECTION)
            "wOFF", "wOF2" -> throw FontValidationException(FontFormatIssue.WEB_FONT)
            "OTTO", "\u0000\u0001\u0000\u0000", "true" -> Unit
            else -> throw FontValidationException(FontFormatIssue.UNSUPPORTED_SIGNATURE)
        }

        val tableCount = u16(header, 4)
        if (tableCount == 0 || tableCount > 4096) {
            throw FontValidationException(FontFormatIssue.MALFORMED)
        }
        val directoryLength = 12L + tableCount.toLong() * 16L
        if (directoryLength > size) throw FontValidationException(FontFormatIssue.MALFORMED)

        val directory = read(12L, tableCount * 16)
        val tags = HashSet<String>(tableCount)
        repeat(tableCount) { index ->
            val record = index * 16
            val tag = directory.copyOfRange(record, record + 4).toString(Charsets.ISO_8859_1)
            if (!tags.add(tag)) throw FontValidationException(FontFormatIssue.MALFORMED)
            val offset = u32(directory, record + 8)
            val length = u32(directory, record + 12)
            if (offset > size || length > size - offset) {
                throw FontValidationException(FontFormatIssue.MALFORMED)
            }
            if (tag == "fvar" && length > 0L) {
                throw FontValidationException(FontFormatIssue.VARIABLE)
            }
        }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)
}
