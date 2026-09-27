package com.kazumaproject.markdownhelperkeyboard.converter.number

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberPresentationConfigTest {
    @Test
    fun additionsDefaultToEnabledAndStyleOrderDefaultsToHalfFullKanji() {
        val config = NumberPresentationConfig()

        assertTrue(config.additionsEnabled)
        assertEquals(
            listOf(NumberStyle.HALF_WIDTH, NumberStyle.FULL_WIDTH, NumberStyle.KANJI),
            config.styleOrder,
        )
        assertEquals(config.styleOrder, NumberPresentationConfig.parseStyleOrder(null))
        assertEquals(config.styleOrder, NumberPresentationConfig.parseStyleOrder("invalid"))
    }

    @Test
    fun sixStyleOrderKeysAreUniqueCompletePermutations() {
        val keys = listOf(
            "half_full_kanji",
            "half_kanji_full",
            "full_half_kanji",
            "full_kanji_half",
            "kanji_half_full",
            "kanji_full_half",
        )
        val expectedStyles = NumberStyle.values().toSet()
        val parsed = keys.map { NumberPresentationConfig.parseStyleOrder(it) }

        assertEquals(6, parsed.toSet().size)
        parsed.forEach { order ->
            assertEquals(expectedStyles, order.toSet())
            assertEquals(3, order.size)
        }
    }

    @Test
    fun malformedOrdersNormalizeToAllThreeStylesWithoutDuplicates() {
        val config = NumberPresentationConfig(
            styleOrder = listOf(NumberStyle.KANJI, NumberStyle.KANJI),
        ).normalized()

        assertEquals(
            listOf(NumberStyle.KANJI, NumberStyle.HALF_WIDTH, NumberStyle.FULL_WIDTH),
            config.styleOrder,
        )
    }
}
