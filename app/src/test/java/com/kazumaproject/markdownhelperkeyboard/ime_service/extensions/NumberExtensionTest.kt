package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NumberExtensionTest {

    @Test
    fun toNumber_converts_basic_hiragana_numbers_to_half_width_digits() {
        val cases = mapOf(
            "いち" to "1",
            "じゅう" to "10",
            "にじゅう" to "20",
            "ひゃく" to "100",
            "せん" to "1000",
            "いちまん" to "10000",
            "にせんごひゃくじゅう" to "2510",
            "いちまんにせんさんびゃくよんじゅうご" to "12345"
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, input.toNumber()?.second)
        }
    }

    @Test
    fun toNumber_handles_zero_and_digit_by_digit_readings() {
        assertEquals("0", "ぜろ".toNumber()?.second)
        assertEquals("0", "れい".toNumber()?.second)
        assertEquals("301", "さんぜろいち".toNumber()?.second)
    }

    @Test
    fun toNumber_supports_sound_change_and_large_unit_patterns() {
        val cases = mapOf(
            "さんびゃく" to "300",
            "ろっぴゃく" to "600",
            "はっぴゃく" to "800",
            "さんぜん" to "3000",
            "はっせん" to "8000",
            "じゅうまん" to "100000"
        )

        cases.forEach { (input, expected) ->
            val number = input.toNumber()
            assertNotNull(input, number)
            assertEquals(expected, number?.second)
        }
    }
}
