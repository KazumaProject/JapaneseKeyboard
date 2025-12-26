package com.kazumaproject.markdownhelperkeyboard.converter.bitset

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.BitSet

class SuccinctBitVectorTest {
    @Test
    fun select1() {
        val bitSet = BitSet()
        bitSet.set(120)
        val sbv = SuccinctBitVector(bitSet)
        assertEquals(120, sbv.select1(1))
    }
}
