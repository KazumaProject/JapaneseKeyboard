package com.kazumaproject.markdownhelperkeyboard.converter.mozc

data class MozcSegmenterData(
    val lNumElements: Int,
    val rNumElements: Int,
    val lTable: IntArray,
    val rTable: IntArray,
    val bitArrayData: ByteArray,
    val boundaryData: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MozcSegmenterData) return false
        return lNumElements == other.lNumElements &&
            rNumElements == other.rNumElements &&
            lTable.contentEquals(other.lTable) &&
            rTable.contentEquals(other.rTable) &&
            bitArrayData.contentEquals(other.bitArrayData) &&
            boundaryData.contentEquals(other.boundaryData)
    }

    override fun hashCode(): Int {
        var result = lNumElements
        result = 31 * result + rNumElements
        result = 31 * result + lTable.contentHashCode()
        result = 31 * result + rTable.contentHashCode()
        result = 31 * result + bitArrayData.contentHashCode()
        result = 31 * result + boundaryData.contentHashCode()
        return result
    }
}
