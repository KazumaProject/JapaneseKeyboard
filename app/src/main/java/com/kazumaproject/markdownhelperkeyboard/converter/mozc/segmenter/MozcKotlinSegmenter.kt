package com.kazumaproject.markdownhelperkeyboard.converter.mozc.segmenter

class MozcKotlinSegmenter(
    private val compressedLSize: Int,
    private val compressedRSize: Int,
    private val lTable: ShortArray,
    private val rTable: ShortArray,
    private val bitarray: ByteArray,
    private val boundaryData: ShortArray,
) {
    fun isBoundary(rid: Short, lid: Short): Boolean {
        val r = rid.toInt() and 0xffff
        val l = lid.toInt() and 0xffff
        if (r == 0 || l == 0) return true
        require(r in lTable.indices)
        require(l in rTable.indices)
        val compressedRid = lTable[r].toInt() and 0xffff
        val compressedLid = rTable[l].toInt() and 0xffff
        require(compressedRid in 0 until compressedLSize)
        require(compressedLid in 0 until compressedRSize)
        val bitIndex = compressedRid + compressedLSize * compressedLid
        return getBit(bitarray, bitIndex)
    }

    fun getPrefixPenalty(lid: Short): Int {
        val id = lid.toInt() and 0xffff
        require(2 * id < boundaryData.size)
        return boundaryData[2 * id].toInt() and 0xffff
    }

    fun getSuffixPenalty(rid: Short): Int {
        val id = rid.toInt() and 0xffff
        require(2 * id + 1 < boundaryData.size)
        return boundaryData[2 * id + 1].toInt() and 0xffff
    }

    private fun getBit(data: ByteArray, index: Int): Boolean {
        val byteIndex = index ushr 3
        val bitOffset = index and 7
        require(byteIndex in data.indices)
        return ((data[byteIndex].toInt() ushr bitOffset) and 1) != 0
    }
}
