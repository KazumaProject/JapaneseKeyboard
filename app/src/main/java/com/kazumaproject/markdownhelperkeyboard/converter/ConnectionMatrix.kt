package com.kazumaproject.markdownhelperkeyboard.converter

import kotlin.math.sqrt

object ConnectionMatrix {
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
}
