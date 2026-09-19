package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class NumberCandidateSafetyTest {
    @Test fun digitWidthAndLeadingZeroBoundaries() {
        for (length in listOf(255, 256, 257)) {
            for (digit in listOf("9", "９", "0", "０")) {
                val input = digit.repeat(length)
                val candidates = NumberCandidateGenerator.generate(input, PredictionConfig())
                assertTrue(candidates.isNotEmpty())
                assertTrue(candidates.all { it.length == length })
            }
            // Exercise the proof overload independently as well.
            val proof = requireNotNull(ValidatedNumber.parseDigits("0".repeat(length)))
            assertTrue(NumberCandidateGenerator.generate(proof, PredictionConfig()).isNotEmpty())
        }
    }

    @Test fun customUnitReadingBoundaries() {
        for ((prefix, suffixLength) in listOf("に" to 254, "に" to 255, "じゅう" to 254)) {
            val unit = CustomNumberUnit("long", "箱", "あ".repeat(suffixLength))
            assertTrue(unit.isValid())
            val input = prefix + unit.reading
            val config = PredictionConfig(numberCandidateConfig = NumberCandidateConfig(units = listOf(unit)))
            val candidates = NumberCandidateGenerator.generate(input, config)
            val expected = if (prefix == "に") listOf("2箱", "２箱", "二箱")
                else listOf("10箱", "１０箱", "十箱")
            assertEquals(expected, candidates.map { it.string })
            assertTrue(candidates.all { it.length == input.length })
        }
    }

    @Test fun settingsDefensivelyCopyNestedListsAndInvalidateCachedParses() {
        val rules = mutableListOf(SpecialNumberReading(1, "いっぱこ", SpecialNumberReadingMode.COMPOSE, "いち"))
        val units = mutableListOf(CustomNumberUnit("box", "箱", "はこ", specialReadings = rules))
        val disabled = mutableSetOf(NumberCandidateKind.DATE)
        val config = NumberCandidateConfig(disabledKinds = disabled, units = units)
        val first = ValidatedNumber.parseAll("じゅういっぱこ", config)
        assertEquals(11L, first.single().value)
        assertSame(first, ValidatedNumber.parseAll("じゅういっぱこ", config))
        rules.clear(); units.clear(); disabled.clear()
        assertEquals(1, config.units.single().specialReadings.size)
        assertTrue(NumberCandidateKind.DATE in config.disabledKinds)
        assertSame(first, ValidatedNumber.parseAll("じゅういっぱこ", config))
        val changed = config.copy(units = config.units.map { it.copy(enabled = false) })
        assertTrue(ValidatedNumber.parseAll("じゅういっぱこ", changed).isEmpty())
        assertFalse(changed.permits(first.single(), "11箱"))
        assertEquals(config, NumberCandidateConfig.decode(config.encode()))
    }

    @Test fun indexedCustomRulesMatchThePreviousParser() {
        val compose = SpecialNumberReadingMode.COMPOSE
        val units = listOf(
            CustomNumberUnit("a", "箱", "はこ", specialReadings = listOf(
                SpecialNumberReading(1, "いっぱこ", compose, "いち"),
                SpecialNumberReading(10, "じゅういっぱこ", compose, "じゅう"),
                SpecialNumberReading(42, "にじゅういっぱこ"),
                SpecialNumberReading(20, "はたはこ"))),
            CustomNumberUnit("b", "束", "はこ", specialReadings = listOf(SpecialNumberReading(1, "いっぱこ", compose, "いち"))),
            CustomNumberUnit("off", "無効", "はこ", enabled = false),
            CustomNumberUnit("bad", "不正", "invalid")
        )
        val config = NumberCandidateConfig(units = units)
        val inputs = listOf("", "に", "じゅう", "にじゅう", "ひゃく", "これは") .flatMap { prefix ->
            listOf("いっぱこ", "じゅういっぱこ", "はたはこ", "いちはこ", "にはこ", "じゅうはこ", "です").map { prefix + it }
        }
        for (input in inputs) {
            val expected = units.filter { it.enabled && it.isValid() }.flatMap { unit ->
                val exact = unit.specialReadings.filter { it.reading == input }.map { it.value }
                val composed = if (exact.isEmpty()) unit.specialReadings.mapNotNull { it.compose(input) } else emptyList()
                val ordinary = if (input.endsWith(unit.reading)) ValidatedNumber.parseReading(input.dropLast(unit.reading.length)) else null
                (exact + composed).ifEmpty {
                    listOfNotNull(ordinary?.value?.takeIf { n -> unit.specialReadings.none { it.value == n } &&
                        unit.specialReadings.none { it.mode == compose && input.dropLast(unit.reading.length).endsWith(it.baseReading) } })
                }.distinct().map { unit.id to it }
            }
            val actual = ValidatedNumber.parseAll(input, config).mapNotNull { proof -> proof.customUnit?.let { it.id to proof.value } }
            assertEquals(input, expected, actual)
        }
    }

    @Test fun cardinalFastRejectionPreservesAllFourDigitReadingsAndLargeUnits() {
        for (value in 0L..9999L) {
            val reading = SpecialNumberReading.suggestBase(value)
            assertEquals(reading, value, ValidatedNumber.parseReading(reading)?.value)
        }
        for (value in listOf(10_000L, 100_000_000L, 1_000_000_000_000L, 9_999_999_999_999_999L)) {
            val reading = SpecialNumberReading.suggestBase(value)
            assertEquals(reading, value, ValidatedNumber.parseReading(reading)?.value)
        }
    }

    @Test fun sharedSettingsCacheNeverReturnsAnotherInputsProofs() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val config = NumberCandidateConfig()
            val jobs = (1..200).map { n -> Callable {
                val input = n.toString()
                repeat(5) { assertEquals(input, ValidatedNumber.parseAll(input, config).single().reading) }
            } }
            executor.invokeAll(jobs).forEach { it.get() }
        } finally { executor.shutdownNow() }
    }
}
