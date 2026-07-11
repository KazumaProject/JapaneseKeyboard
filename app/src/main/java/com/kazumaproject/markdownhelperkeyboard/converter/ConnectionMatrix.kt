package com.kazumaproject.markdownhelperkeyboard.converter

import kotlin.math.sqrt

object ConnectionMatrix {
    interface CostTable {
        val matrixSize: Int
        val entryCount: Int
        fun cost(rid: Int, lid: Int): Int
    }

    fun inferMatrixSize(connectionIdsSize: Int): Int? {
        if (connectionIdsSize <= 0) return null
        val matrixSize = sqrt(connectionIdsSize.toDouble()).toInt()
        return if (matrixSize.toLong() * matrixSize.toLong() == connectionIdsSize.toLong()) {
            matrixSize
        } else {
            null
        }
    }

    fun inferMatrixSize(connectionIds: ShortArray): Int? =
        inferMatrixSize(connectionIds.size)

    fun fromShortArray(
        connectionIds: ShortArray,
        matrixSize: Int = inferMatrixSize(connectionIds)
            ?: error("connectionId.dat size ${connectionIds.size} is not a valid square matrix"),
    ): CostTable = ShortArrayCostTable(connectionIds, matrixSize)

    private class ShortArrayCostTable(
        private val connectionIds: ShortArray,
        override val matrixSize: Int,
    ) : CostTable {
        override val entryCount: Int = connectionIds.size

        override fun cost(rid: Int, lid: Int): Int {
            val index = indexOf(rid, lid, matrixSize, entryCount)
            return connectionIds[index].toInt()
        }
    }

    private fun indexOf(
        rid: Int,
        lid: Int,
        matrixSize: Int,
        entryCount: Int,
    ): Int {
        require(matrixSize > 0) { "connectionMatrixSize must be positive: $matrixSize" }
        require(rid in 0 until matrixSize && lid in 0 until matrixSize) {
            "connection id out of range: rid=$rid, lid=$lid, matrixSize=$matrixSize"
        }
        val index = rid * matrixSize + lid
        require(index in 0 until entryCount) {
            "connection index out of range: index=$index, size=$entryCount, matrixSize=$matrixSize"
        }
        return index
    }
}
