package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcConnector(
    private val connectionIds: ShortArray,
    private val matrixSize: Int,
) {
    fun getTransitionCost(prevRightId: Short, currentLeftId: Short): Int {
        val rightId = prevRightId.toInt() and 0xFFFF
        val leftId = currentLeftId.toInt() and 0xFFFF
        if (rightId !in 0 until matrixSize || leftId !in 0 until matrixSize) {
            return INVALID_CONNECTION_COST
        }
        val index = rightId * matrixSize + leftId
        if (index !in connectionIds.indices) return INVALID_CONNECTION_COST
        return connectionIds[index].toInt()
    }

    companion object {
        const val INVALID_CONNECTION_COST = 100000
    }
}
