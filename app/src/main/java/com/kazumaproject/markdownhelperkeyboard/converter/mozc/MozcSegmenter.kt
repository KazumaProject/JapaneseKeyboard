package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.graph.MozcNodeAttributes
import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node

class MozcSegmenter(
    private val data: MozcSegmenterData,
) {
    fun isBoundary(
        left: Node,
        right: Node,
        isSingleSegment: Boolean,
    ): Boolean {
        if (left.mozcNodeType == MozcNodeType.BOS ||
            right.mozcNodeType == MozcNodeType.EOS
        ) {
            return true
        }
        if (isSingleSegment) {
            return false
        }
        if ((left.mozcAttributes and MozcNodeAttributes.STARTS_WITH_PARTICLE) != 0) {
            return false
        }
        return isBoundary(
            rid = left.r.toInt(),
            lid = right.l.toInt(),
        )
    }

    fun isBoundary(rid: Int, lid: Int): Boolean {
        require(rid in data.lTable.indices) { "rid out of range: $rid" }
        require(lid in data.rTable.indices) { "lid out of range: $lid" }
        val bitArrayIndex = data.lTable[rid] + data.lNumElements * data.rTable[lid]
        return getBit(data.bitArrayData, bitArrayIndex)
    }

    fun getPrefixPenalty(lid: Int): Int {
        val index = 2 * lid
        require(index in data.boundaryData.indices) { "prefix lid out of range: $lid" }
        return data.boundaryData[index]
    }

    fun getSuffixPenalty(rid: Int): Int {
        val index = 2 * rid + 1
        require(index in data.boundaryData.indices) { "suffix rid out of range: $rid" }
        return data.boundaryData[index]
    }

    private fun getBit(data: ByteArray, index: Int): Boolean {
        val byteIndex = index ushr 3
        require(byteIndex in data.indices) { "bit index out of range: $index" }
        return ((data[byteIndex].toInt() ushr (index and 0x07)) and 0x01) != 0
    }
}
