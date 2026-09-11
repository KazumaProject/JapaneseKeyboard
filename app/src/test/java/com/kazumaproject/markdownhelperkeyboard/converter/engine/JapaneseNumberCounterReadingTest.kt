package com.kazumaproject.markdownhelperkeyboard.converter.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class JapaneseNumberCounterReadingTest {
    @Test
    fun canonicalCountsZeroThrough9999ProduceTheExpectedValues() {
        var accepted = 0
        var rejected = 0
        for (number in 0..9999) {
            val reading = cardinal(number)
            for (suffix in listOf("えん", "にん")) {
                assertValue(reading, suffix, number.toLong())
                accepted++
            }
            val (minutes, suffix) = minuteReading(number)
            assertValue(minutes, suffix, number.toLong())
            accepted++
            if (number <= 29) {
                assertValue(reading, "じ", number.toLong())
                accepted++
            } else {
                assertNull("$reading must not produce a clock hour", JapaneseNumberCounterReading.parse(reading, "じ"))
                rejected++
            }
        }
        report("generated-values.txt", "validCases=$accepted\nrejectedClockHours=$rejected")
    }

    @Test
    fun ordinaryAffixesMalformedPlacesAndWrongMinuteSoundChangesNeverProduceNumbers() {
        val inputs = linkedSetOf<Pair<String, String>>()
        for (number in 0..9999) {
            val reading = cardinal(number)
            inputs += "ぜん$reading" to "にん"
            inputs += "びゃく$reading" to "えん"
            inputs += "ひゃくひゃく$reading" to "えん"
            inputs += "${reading}おくまん" to "えん"
            inputs += "ごご$reading" to "じ"
            inputs += "${reading}の" to "にん"
            inputs += "${reading}にんで" to "じ"
            val (minutes, suffix) = minuteReading(number)
            if (number % 10 !in listOf(3, 4)) {
                inputs += minutes to if (suffix == "ぷん") "ふん" else "ぷん"
            }
        }
        for ((reading, suffix) in inputs) {
            assertNull("Unexpected numeric candidate: $reading$suffix", JapaneseNumberCounterReading.parse(reading, suffix))
        }
        report("generated-rejections.txt", "uniqueMalformedInputs=${inputs.size}\nresult=PASS")
    }

    @Test
    fun validReadingVariantsAndLargeCountsStayAvailable() {
        val cases = listOf(
            Triple("れい", "じ", 0L), Triple("よ", "じ", 4L), Triple("く", "じ", 9L),
            Triple("にじゅうよ", "じ", 24L), Triple("にじゅうく", "じ", 29L),
            Triple("よ", "にん", 4L), Triple("く", "にん", 9L),
            Triple("よん", "ふん", 4L), Triple("はち", "ふん", 8L),
            Triple("じっ", "ぷん", 10L), Triple("にじっ", "ぷん", 20L),
            Triple("ひゃっ", "ぷん", 100L), Triple("さんびゃっ", "ぷん", 300L),
            Triple("いっせん", "ぷん", 1000L), Triple("いちまん", "ぷん", 10000L),
            Triple("いちまん", "えん", 10000L), Triple("じゅうまん", "にん", 100000L),
            Triple("いちおく", "えん", 100000000L), Triple("いっちょう", "えん", 1000000000000L),
            Triple("はっちょう", "にん", 8000000000000L),
            Triple(cardinal(9999) + "ちょう" + cardinal(9999) + "おく" + cardinal(9999) + "まん" + cardinal(9999), "えん", 9999999999999999L),
        )
        cases.forEach { (reading, suffix, value) -> assertValue(reading, suffix, value) }
        for (input in listOf(
            "いちまんにおく", "いちまんにまん", "いちまんちょう", "きゅうひゃくまんちょう",
            "まいなすいち", "いちてんご", "いちいち", "さんご", "じっし", "よしよし",
        )) {
            for (suffix in listOf("じ", "にん", "えん", "ふん", "ぷん")) {
                assertNull("$input$suffix", JapaneseNumberCounterReading.parse(input, suffix))
            }
        }
    }

    private fun assertValue(reading: String, suffix: String, value: Long) {
        val result = JapaneseNumberCounterReading.parse(reading, suffix)
        val halfWidth = value.toString()
        val fullWidth = halfWidth.map { it + 0xFEE0 }.joinToString("")
        assertEquals("$reading$suffix", fullWidth to halfWidth, result)
    }

    // Independent arithmetic oracle: assemble spoken place values instead of
    // parsing text or reusing the production regex/decoder.
    private fun cardinal(value: Int): String {
        if (value == 0) return "ぜろ"
        val ones = listOf("", "いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう")
        val thousands = listOf("", "せん", "にせん", "さんぜん", "よんせん", "ごせん", "ろくせん", "ななせん", "はっせん", "きゅうせん")
        val hundreds = listOf("", "ひゃく", "にひゃく", "さんびゃく", "よんひゃく", "ごひゃく", "ろっぴゃく", "ななひゃく", "はっぴゃく", "きゅうひゃく")
        val tens = value / 10 % 10
        return thousands[value / 1000] + hundreds[value / 100 % 10] +
            (if (tens == 0) "" else (if (tens == 1) "" else ones[tens]) + "じゅう") + ones[value % 10]
    }

    private fun minuteReading(value: Int): Pair<String, String> {
        val reading = cardinal(value)
        return when (value % 10) {
            1 -> reading.dropLast(2) + "いっ" to "ぷん"
            2, 5, 7, 9 -> reading to "ふん"
            3, 4 -> reading to "ぷん"
            6 -> reading.dropLast(2) + "ろっ" to "ぷん"
            8 -> reading.dropLast(2) + "はっ" to "ぷん"
            else -> when {
                value == 0 -> reading to "ふん"
                value % 100 != 0 -> reading.dropLast(3) + "じゅっ" to "ぷん"
                value % 1000 != 0 -> reading.dropLast(1) + "っ" to "ぷん"
                else -> reading to "ぷん"
            }
        }
    }

    private fun report(name: String, value: String) {
        val dir = File("build/reports/number-candidate-audit").apply { mkdirs() }
        File(dir, name).writeText(value + "\n")
    }
}
