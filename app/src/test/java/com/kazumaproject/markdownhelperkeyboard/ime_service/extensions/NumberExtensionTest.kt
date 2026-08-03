package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NumberExtensionTest {

    @Test
    fun toNumber_converts_basic_hiragana_numbers_to_half_width_digits() {
        val cases = mapOf(
            "いち" to "1",
            "し" to "4",
            "よ" to "4",
            "く" to "9",
            "じゅう" to "10",
            "いちじゅう" to "10",
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
    fun toNumber_handles_zero_but_rejects_unstructured_digit_sequences() {
        assertEquals("0", "ぜろ".toNumber()?.second)
        assertEquals("0", "れい".toNumber()?.second)
        assertNull("さんぜろいち".toNumber())
        assertNull("いちいち".toNumber())
        assertNull("さんさん".toNumber())
    }

    @Test
    fun toNumber_supports_sound_change_and_large_unit_patterns() {
        val cases = mapOf(
            "さんびゃく" to "300",
            "ろっぴゃく" to "600",
            "はっぴゃく" to "800",
            "さんぜん" to "3000",
            "はっせん" to "8000",
            "じゅうまん" to "100000",
            "いっちょう" to "1000000000000",
            "しじゅう" to "40",
            "じゅうし" to "14",
            "じゅうよ" to "14",
            "じゅうく" to "19",
            "さんひゃく" to "300",
            "はちせん" to "8000",
        )

        cases.forEach { (input, expected) ->
            val number = input.toNumber()
            assertNotNull(input, number)
            assertEquals(expected, number?.second)
        }
    }

    @Test
    fun toNumber_rejects_ordinary_words_that_only_look_numeric_by_character() {
        val ordinaryReadings = listOf(
            "よしよし",
            "しせん",
            "くちょう",
            "ちょうせん",
            "ごご",
            "さんご",
            "しえん",
            "しじ",
        )

        ordinaryReadings.forEach { input ->
            assertNull(input, input.toNumber())
        }
    }

    @Test
    fun toNumber_rejects_invalid_unit_order_repetition_and_contextual_readings() {
        val invalidReadings = listOf(
            "よせん",
            "しせん",
            "くせん",
            "しけい",
            "しがいせん",
            "じゅうひゃく",
            "せんせん",
            "いちまんにおく",
            "いちまんにまん",
        )

        invalidReadings.forEach { input ->
            assertNull(input, input.toNumber())
        }
    }

    @Test
    fun toNumber_returns_null_instead_of_treating_long_invalid_input_as_digits() {
        assertNull("きゅう".repeat(20).toNumber())
    }
}
