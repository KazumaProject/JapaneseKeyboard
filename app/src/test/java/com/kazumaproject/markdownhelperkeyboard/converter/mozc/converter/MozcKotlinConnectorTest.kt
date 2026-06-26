package com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MozcKotlinConnectorTest {

    @Test
    fun getTransitionCostUsesRidTimesMatrixSizePlusLid() {
        val connector = MozcKotlinConnector(
            connectionIds = shortArrayOf(
                0, 1, 2,
                3, 4, 5,
                6, 7, 8,
            ),
            matrixSize = 3,
        )

        assertEquals(7, connector.getTransitionCost(2, 1))
    }

    @Test
    fun outOfRangeFails() {
        val connector = MozcKotlinConnector(ShortArray(4), matrixSize = 2)

        assertThrows(IllegalArgumentException::class.java) {
            connector.getTransitionCost(2, 0)
        }
    }
}
