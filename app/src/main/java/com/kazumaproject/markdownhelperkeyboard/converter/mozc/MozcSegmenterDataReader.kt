package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import java.io.EOFException
import java.io.InputStream

class MozcSegmenterDataReader {
    fun read(inputStream: InputStream): MozcSegmenterData {
        inputStream.use { input ->
            val magic = input.readExact(4).decodeToString()
            require(magic == MAGIC) { "Invalid Mozc segmenter magic: $magic" }

            val version = input.readUInt32LittleEndian()
            require(version == VERSION) { "Unsupported Mozc segmenter version: $version" }

            val lNumElements = input.readUInt32LittleEndian()
            val rNumElements = input.readUInt32LittleEndian()
            val lTableSize = input.readUInt32LittleEndian()
            val rTableSize = input.readUInt32LittleEndian()
            val bitArraySize = input.readUInt32LittleEndian()
            val boundaryDataSize = input.readUInt32LittleEndian()

            require(lNumElements > 0) { "lNumElements must be positive" }
            require(rNumElements > 0) { "rNumElements must be positive" }
            require(lTableSize > 0) { "lTableSize must be positive" }
            require(rTableSize > 0) { "rTableSize must be positive" }
            require(bitArraySize > 0) { "bitArraySize must be positive" }
            require(boundaryDataSize > 0) { "boundaryDataSize must be positive" }

            val lTable = IntArray(lTableSize) { input.readUInt16LittleEndian() }
            val rTable = IntArray(rTableSize) { input.readUInt16LittleEndian() }
            val bitArrayData = input.readExact(bitArraySize)
            val boundaryData = IntArray(boundaryDataSize) { input.readUInt16LittleEndian() }

            require(lTable.all { it in 0 until lNumElements }) { "lTable contains an out-of-range compressed id" }
            require(rTable.all { it in 0 until rNumElements }) { "rTable contains an out-of-range compressed id" }
            require(lNumElements.toLong() * rNumElements.toLong() <= bitArrayData.size.toLong() * 8L) {
                "bitArrayData is too small for compressed matrix"
            }

            if (input.read() != -1) {
                throw IllegalArgumentException("Unexpected trailing bytes in Mozc segmenter data")
            }

            return MozcSegmenterData(
                lNumElements = lNumElements,
                rNumElements = rNumElements,
                lTable = lTable,
                rTable = rTable,
                bitArrayData = bitArrayData,
                boundaryData = boundaryData,
            )
        }
    }

    private fun InputStream.readUInt16LittleEndian(): Int {
        val b0 = read()
        val b1 = read()
        if (b0 < 0 || b1 < 0) throw EOFException("Unexpected EOF while reading UInt16")
        return b0 or (b1 shl 8)
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
        const val MAGIC = "MSGM"
        const val VERSION = 1
    }
}
