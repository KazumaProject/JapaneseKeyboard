package com.kazumaproject.markdownhelperkeyboard.zeroquery

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal object SerializedStringArrayReader {
    fun read(bytes: ByteArray, filePath: String = "<bytes>"): List<String> {
        if (bytes.size < 4) {
            error("Invalid SerializedStringArray: file path=$filePath, file size=${bytes.size}")
        }

        val arraySize = bytes.readUInt32LE(0)
        val tableEnd = 4L + 8L * arraySize
        if (arraySize > Int.MAX_VALUE || tableEnd > bytes.size.toLong()) {
            error(
                "Invalid SerializedStringArray: file path=$filePath, array_size=$arraySize, " +
                    "file size=${bytes.size}"
            )
        }

        val dataStart = tableEnd
        val strings = ArrayList<String>(arraySize.toInt())
        repeat(arraySize.toInt()) { index ->
            val tableOffset = 4 + index * 8
            val offset = bytes.readUInt32LE(tableOffset)
            val length = bytes.readUInt32LE(tableOffset + 4)
            val end = offset + length
            if (offset < dataStart || end > bytes.size.toLong()) {
                failInvalidRange(filePath, index, offset, length, bytes.size)
            }
            if (end >= bytes.size.toLong() || bytes[end.toInt()] != 0.toByte()) {
                failInvalidRange(filePath, index, offset, length, bytes.size)
            }
            if (offset > Int.MAX_VALUE || length > Int.MAX_VALUE) {
                failInvalidRange(filePath, index, offset, length, bytes.size)
            }

            strings += decodeUtf8(bytes, offset.toInt(), length.toInt(), filePath, index)
        }

        for (index in 1 until strings.size) {
            val previous = strings[index - 1]
            val current = strings[index]
            if (UnicodeCodePointStringComparator.compare(previous, current) >= 0) {
                error(
                    "Invalid SerializedStringArray: file path=$filePath, index=$index, " +
                        "previous='$previous', current='$current'"
                )
            }
        }
        return strings
    }

    private fun decodeUtf8(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        filePath: String,
        index: Int,
    ): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes, offset, length)).toString()
        } catch (exception: Exception) {
            error(
                "Invalid SerializedStringArray: file path=$filePath, index=$index, " +
                    "reason=UTF-8 decode failed: ${exception.message}"
            )
        }
    }

    private fun failInvalidRange(
        filePath: String,
        index: Int,
        offset: Long,
        length: Long,
        fileSize: Int,
    ): Nothing {
        error(
            "Invalid SerializedStringArray: file path=$filePath, index=$index, " +
                "offset=$offset, length=$length, file size=$fileSize"
        )
    }
}

internal object UnicodeCodePointStringComparator : Comparator<String> {
    override fun compare(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            val leftCodePoint = left.codePointAt(leftIndex)
            val rightCodePoint = right.codePointAt(rightIndex)
            if (leftCodePoint != rightCodePoint) {
                return leftCodePoint.compareTo(rightCodePoint)
            }
            leftIndex += Character.charCount(leftCodePoint)
            rightIndex += Character.charCount(rightCodePoint)
        }
        return (left.length - leftIndex).compareTo(right.length - rightIndex)
    }
}

internal fun ByteArray.readUInt32LE(offset: Int): Long {
    require(offset >= 0 && offset + 4 <= size) {
        "uint32 read out of range: offset=$offset, file size=$size"
    }
    return (this[offset].toLong() and 0xffL) or
        ((this[offset + 1].toLong() and 0xffL) shl 8) or
        ((this[offset + 2].toLong() and 0xffL) shl 16) or
        ((this[offset + 3].toLong() and 0xffL) shl 24)
}

internal fun ByteArray.readUInt16LE(offset: Int): Int {
    require(offset >= 0 && offset + 2 <= size) {
        "uint16 read out of range: offset=$offset, file size=$size"
    }
    return (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8)
}
