package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import java.io.EOFException
import java.io.InputStream

class MozcNodeAttributeTableReader {
    fun read(inputStream: InputStream): MozcNodeAttributeTable {
        inputStream.use { input ->
            val magic = input.readExact(4).decodeToString()
            require(magic == MAGIC) { "Invalid Mozc node attribute magic: $magic" }
            val version = input.readUInt32LittleEndian()
            require(version == VERSION) { "Unsupported Mozc node attribute version: $version" }
            val size = input.readUInt32LittleEndian()
            require(size > 0) { "Mozc node attribute table must not be empty" }
            val attributes = IntArray(size) { input.readUInt32LittleEndian() }
            if (input.read() != -1) {
                throw IllegalArgumentException("Unexpected trailing bytes in Mozc node attribute table")
            }
            return MozcNodeAttributeTable(attributes)
        }
    }

    private fun InputStream.readUInt32LittleEndian(): Int {
        val b0 = read()
        val b1 = read()
        val b2 = read()
        val b3 = read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
            throw EOFException("Unexpected EOF while reading UInt32")
        }
        val value = (b0.toLong() and 0xFFL) or
            ((b1.toLong() and 0xFFL) shl 8) or
            ((b2.toLong() and 0xFFL) shl 16) or
            ((b3.toLong() and 0xFFL) shl 24)
        require(value <= Int.MAX_VALUE) { "UInt32 value is too large for runtime arrays: $value" }
        return value.toInt()
    }

    private fun InputStream.readExact(size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(buffer, offset, size - offset)
            if (read < 0) throw EOFException("Unexpected EOF while reading $size bytes")
            offset += read
        }
        return buffer
    }

    private companion object {
        const val MAGIC = "MNAT"
        const val VERSION = 1
    }
}
