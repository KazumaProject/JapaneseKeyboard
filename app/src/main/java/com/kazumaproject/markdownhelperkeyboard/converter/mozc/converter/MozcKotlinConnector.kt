package com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter

class MozcKotlinConnector(
    private val connectionIds: ShortArray,
    private val matrixSize: Int,
) {
    fun getTransitionCost(rid: Short, lid: Short): Int {
        val r = rid.toInt()
        val l = lid.toInt()
        require(r in 0 until matrixSize)
        require(l in 0 until matrixSize)
        return connectionIds[r * matrixSize + l].toInt()
    }
}
