package com.kazumaproject.markdownhelperkeyboard.converter.engine

import org.junit.Assert.*
import org.junit.Test

class NumberGraphMatcherTest {
    @Test fun allCountersMatchAtSentencePositionsWithoutConsumingSurroundingWords() {
        for ((counter, readings) in CounterReadingCases.rows) {
            for ((index, reading) in readings.split(" ").withIndex()) {
                for ((prefix, suffix) in listOf("" to "", "" to "だけ", "あと" to "ください", "あと" to "とにまい")) {
                    val input = prefix + reading + suffix
                    val match = NumberGraphMatcher(input, PredictionConfig()).matches(prefix.length)
                        .firstOrNull { it.end == prefix.length + reading.length }
                    assertNotNull("$input / $counter", match)
                    assertTrue("$input / $counter", match!!.forms.any { it.text == "${index + 1}$counter" })
                    assertEquals(reading, match.reading)
                }
            }
        }
    }

    @Test fun settingsAndSpecialReadingsAreSharedWithStandaloneGeneration() {
        val readings = listOf("さんぞく", "にじゅうさんぞく", "よにん", "ひとり", "ふたり", "さんじごふん", "ひゃくえん", "ろっぷん")
        for (reading in readings) for (order in NumberCandidateOrder.entries) {
            val config = PredictionConfig(numberCandidateOrder = order)
            val expected = ValidatedNumber.parseAll(reading, config.numberCandidateConfig).flatMap { proof -> order.indices.map { proof.basicForms[it] } }.distinct()
            val matcher = NumberGraphMatcher("あと${reading}だけ", config)
            val match = matcher.matches(2).single { it.end == 2 + reading.length }
            assertEquals(reading, expected, match.forms.map { it.text })
            assertEquals(reading, match.forms.map { it.cost }.sorted(), match.forms.map { it.cost })
            assertTrue(matcher.matches(2, 3 + reading.length).none { it.end == 2 + reading.length })
            assertTrue(NumberGraphMatcher(reading + "だけ", config.copy(japaneseNumberCandidatesEnabled = false)).matches(0).isEmpty())
        }
        for (counter in BuiltInCounter.entries) {
            val reading = counter.example.substringBefore(" →")
            val config = PredictionConfig(numberCandidateConfig = NumberCandidateConfig(disabledCounters = setOf(counter.storageId)))
            assertTrue(NumberGraphMatcher(reading + "だけ", config).matches(0).flatMap { it.forms }
                .none { it.text.endsWith(counter.output) })
        }
        for ((kind, reading) in listOf(NumberCandidateKind.PEOPLE to "ふたり", NumberCandidateKind.YEN to "ひゃくえん", NumberCandidateKind.TIME to "さんじごふん")) {
            val config = PredictionConfig(numberCandidateConfig = NumberCandidateConfig(disabledKinds = setOf(kind)))
            assertTrue(NumberGraphMatcher(reading + "だけ", config).matches(0).isEmpty())
        }
    }

    @Test fun customExactAndComposedReadingsUseTheirOwnSpansAndSettings() {
        val unit = CustomNumberUnit("box", "箱", "はこ", specialReadings = listOf(
            SpecialNumberReading(1, "いっぱこ", SpecialNumberReadingMode.COMPOSE, "いち"),
            SpecialNumberReading(42, "じゅういっぱこ"), SpecialNumberReading(20, "はたはこ")))
        val config = PredictionConfig(numberCandidateConfig = NumberCandidateConfig(units = listOf(unit)))
        for ((reading, expected) in mapOf("にはこ" to "2箱", "にじゅういっぱこ" to "21箱", "じゅういっぱこ" to "42箱", "はたはこ" to "20箱")) {
            val matches = NumberGraphMatcher("あと${reading}だけ", config).matches(2)
            assertEquals(expected, matches.single { it.end == reading.length + 2 }.forms.first().text)
        }
        assertTrue(NumberGraphMatcher("にはこだけ", config.copy(numberCandidateConfig = config.numberCandidateConfig.copy(
            units = listOf(unit.copy(enabled = false))))).matches(0).isEmpty())
    }

    @Test fun wholeInputParserRemainsStrictAndLongInputsStayBounded() {
        for (input in listOf("よんそくぶん", "さんぼんだけ", "ふたりぶんください", "これはいっこ", "よんぶん", "しんぶん", "さんご")) {
            assertTrue(input, NumberCandidateGenerator.generate(input, PredictionConfig()).isEmpty())
        }
        for (length in listOf(255, 256, 257)) {
            val input = "いっこ" + "あ".repeat(length - 3)
            assertEquals(length.toString(), length == 255, NumberGraphMatcher(input, PredictionConfig()).matches(0).any { it.reading == "いっこ" })
        }
    }
    @Test fun manyRegisteredRulesAndLongInputsDoNotRevalidateSettings() {
        fun kana(n: Int): String = buildString {
            var value = n
            repeat(4) { append(('あ'.code + value % 20).toChar()); value /= 20 }
        }
        val units = (0 until 256).map { unit -> CustomNumberUnit("unit-$unit", "単位$unit", "こ" + kana(unit),
            specialReadings = (0 until 32).map { rule ->
                SpecialNumberReading(1, "ぱ" + kana(unit * 32 + rule), SpecialNumberReadingMode.COMPOSE, "いち")
            }) }
        val config = PredictionConfig(numberCandidateConfig = NumberCandidateConfig(units = units))
        val reading = "じゅう" + units.last().specialReadings.last().reading
        val inputs = listOf("えんぴつをさんぼんください", "あ".repeat(240) + reading + "だけ", "に".repeat(255), "にん".repeat(127) + "に", "に".repeat(257))
        for (input in inputs) {
            var matches = 0
            repeat(3) {
                val matcher = NumberGraphMatcher(input, config)
                input.indices.forEach { matcher.matches(it) }
            }
            val started = System.nanoTime()
            repeat(10) {
                val matcher = NumberGraphMatcher(input, config)
                matches = input.indices.sumOf { matcher.matches(it).size }
            }
            val averageMs = (System.nanoTime() - started) / 10_000_000.0
            println("NUMBER_MATCHER length=${input.length} units=256 rules=8192 averageMs=$averageMs matches=$matches")
            if (input.length > 255) assertEquals(0, matches)
        }
        val match = NumberGraphMatcher("あと${reading}だけ", config).matches(2).single { it.end == reading.length + 2 }
        assertEquals("11単位255", match.forms.first().text)
        assertEquals(config.numberCandidateConfig.hashCode(), NumberCandidateConfig.decode(config.numberCandidateConfig.encode()).hashCode())
    }

}
