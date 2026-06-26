package com.kazumaproject.markdownhelperkeyboard.converter.mozc.segmenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MozcKotlinSegmenterTest {

    @Test
    fun isBoundary_readsConfiguredBit() {
        val segmenter = MozcKotlinSegmenter(
            compressedLSize = 2,
            compressedRSize = 2,
            lTable = shortArrayOf(0, 1, 0),
            rTable = shortArrayOf(0, 0, 1),
            bitarray = byteArrayOf(0b0000_1000),
            boundaryData = ShortArray(6),
        )

        assertTrue(segmenter.isBoundary(1, 2))
        assertFalse(segmenter.isBoundary(2, 1))
    }

    @Test
    fun isBoundary_treatsBosOrEosAsBoundary() {
        val segmenter = MozcKotlinSegmenter(
            compressedLSize = 1,
            compressedRSize = 1,
            lTable = shortArrayOf(0),
            rTable = shortArrayOf(0),
            bitarray = byteArrayOf(0),
            boundaryData = ShortArray(2),
        )

        assertTrue(segmenter.isBoundary(0, 10))
        assertTrue(segmenter.isBoundary(10, 0))
    }

    @Test
    fun penaltiesReadBoundaryDataByPosId() {
        val segmenter = MozcKotlinSegmenter(
            compressedLSize = 1,
            compressedRSize = 1,
            lTable = shortArrayOf(0, 0),
            rTable = shortArrayOf(0, 0),
            bitarray = byteArrayOf(1),
            boundaryData = shortArrayOf(1, 2, 300, 400),
        )

        assertEquals(300, segmenter.getPrefixPenalty(1))
        assertEquals(400, segmenter.getSuffixPenalty(1))
    }

    @Test
    fun outOfRangeFails() {
        val segmenter = MozcKotlinSegmenter(
            compressedLSize = 1,
            compressedRSize = 1,
            lTable = shortArrayOf(0),
            rTable = shortArrayOf(0),
            bitarray = byteArrayOf(1),
            boundaryData = ShortArray(2),
        )

        assertThrows(IllegalArgumentException::class.java) {
            segmenter.isBoundary(1, 1)
        }
    }
}
