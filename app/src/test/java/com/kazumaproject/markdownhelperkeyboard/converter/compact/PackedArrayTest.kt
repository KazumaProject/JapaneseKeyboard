package com.kazumaproject.markdownhelperkeyboard.converter.compact

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.random.Random

class PackedArrayTest {
    @Test
    fun intArrayRoundTripsAcrossWordBoundariesAndNegativeValues() {
        val random = Random(867)
        val values = IntArray(10_003) { random.nextInt(-2, 2_500_000) }
        assertArrayEquals(values, PackedIntArray.from(values).toIntArray())
    }

    @Test
    fun constantIntArrayRoundTrips() {
        val values = IntArray(257) { 42 }
        assertArrayEquals(values, PackedIntArray.from(values).toIntArray())
    }

    @Test
    fun charArrayRoundTripsWithWideUnicodeAlphabet() {
        val alphabet = charArrayOf(' ', 'a', 'あ', '漢', '\uFFF5')
        val values = CharArray(10_003) { alphabet[it % alphabet.size] }
        assertArrayEquals(values, PackedCharArray.from(values).toCharArray())
    }
}
