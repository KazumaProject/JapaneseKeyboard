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

    @Test
    fun selectMatchesEveryBitAcrossEmptyAndFullBlocks() {
        val bits = BitSet(704).apply {
            set(0, 8)
            set(12)
            set(64, 256)
            set(512, 576)
            set(621)
        }
        val vector = SuccinctBitVector(bits)
        var ones = 0
        var zeros = 0
        for (position in 0 until bits.size()) {
            if (bits[position]) {
                assertEquals(position, vector.select1(++ones))
            } else {
                assertEquals(position, vector.select0(++zeros))
            }
        }
        assertEquals(-1, vector.select1(ones + 1))
        assertEquals(-1, vector.select0(zeros + 1))
    }
}
