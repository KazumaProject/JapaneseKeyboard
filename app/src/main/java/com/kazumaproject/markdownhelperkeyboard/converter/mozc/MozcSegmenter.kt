package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcSegmenter(
    private val data: MozcSegmenterData,
    private val trace: MozcConverterTrace? = null,
) {
    val posMatcher: MozcPosMatcherData
        get() = data.posMatcher

    fun getPrefixPenalty(leftId: Short): Int =
        data.prefixPenalty.getOrElse(leftId.toInt() and 0xFFFF) { 0 }

    fun getSuffixPenalty(rightId: Short): Int =
        data.suffixPenalty.getOrElse(rightId.toInt() and 0xFFFF) { 0 }

    fun isBoundary(leftNode: MozcNode, rightNode: MozcNode): Boolean {
        trace?.boundaryCheckCount = (trace?.boundaryCheckCount ?: 0) + 1
        if (leftNode.nodeType == MozcNodeType.BOS || rightNode.nodeType == MozcNodeType.EOS) {
            return true
        }
        if (leftNode.attributes and MozcNodeAttribute.STARTS_WITH_PARTICLE != 0) {
            return false
        }
        return isBoundary(leftNode.rightId, rightNode.leftId)
    }

    fun isBoundary(rightId: Short, leftId: Short): Boolean {
        val rid = rightId.toInt() and 0xFFFF
        val lid = leftId.toInt() and 0xFFFF
        val boundary = data.boundary
        if (rid !in boundary.lTable.indices || lid !in boundary.rTable.indices) return true
        val leftState = boundary.lTable[rid].toInt() and 0xFFFF
        val rightState = boundary.rTable[lid].toInt() and 0xFFFF
        val bitIndex = leftState + boundary.compressedLSize * rightState
        val byteIndex = bitIndex / 8
        if (byteIndex !in boundary.bitArray.indices) return true
        return boundary.bitArray[byteIndex].toInt() and (1 shl (bitIndex % 8)) != 0
    }
}
