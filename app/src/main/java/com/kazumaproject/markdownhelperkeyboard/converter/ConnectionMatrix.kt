package com.kazumaproject.markdownhelperkeyboard.converter

import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    fun fromByteBuffer(byteBuffer: ByteBuffer): CostTable {
        val buffer = byteBuffer.slice().asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN)
        require(buffer.remaining() % Short.SIZE_BYTES == 0) {
            "connectionId.dat byte size ${buffer.remaining()} is not divisible by ${Short.SIZE_BYTES}"
        }
        val entryCount = buffer.remaining() / Short.SIZE_BYTES
        val matrixSize = inferMatrixSize(entryCount)
            ?: error("connectionId.dat entry count $entryCount is not a valid square matrix")
        return ByteBufferCostTable(buffer, matrixSize, entryCount)
    }

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

    private class ByteBufferCostTable(
        private val buffer: ByteBuffer,
        override val matrixSize: Int,
        override val entryCount: Int,
    ) : CostTable {
        override fun cost(rid: Int, lid: Int): Int {
            val index = indexOf(rid, lid, matrixSize, entryCount)
            return buffer.getShort(index * Short.SIZE_BYTES).toInt()
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
