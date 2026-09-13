package com.kazumaproject.markdownhelperkeyboard.converter.engine

import org.junit.Assert.*
import org.junit.Test

class NumberCandidateSettingsTest {
    private val engine = KanaKanjiEngine()
    private fun generated(reading: String, settings: NumberCandidateConfig = NumberCandidateConfig()) =
        engine.getCandidatesEnglishKana(reading, PredictionConfig(numberCandidateConfig = settings)).filter { it.generatedNumber }

    @Test fun eachKindCanBeDisabledWithoutDisablingBasicDigits() {
        val cases = listOf(
            Triple(NumberCandidateKind.TIME, "いちじごふん", "1時5分"),
            Triple(NumberCandidateKind.TIME, "ひゃっぷん", "100分"),
            Triple(NumberCandidateKind.PEOPLE, "さんにん", "3人"),
            Triple(NumberCandidateKind.YEN, "ひゃくえん", "100円"),
            Triple(NumberCandidateKind.DATE, "ひゃくいち", "1月1日"),
            Triple(NumberCandidateKind.COMMA, "せん", "1,000"),
            Triple(NumberCandidateKind.LARGE_UNIT, "いちまん", "1万"),
            Triple(NumberCandidateKind.EXPONENT, "いちおく", "10⁸"),
            Triple(NumberCandidateKind.SUPERSCRIPT, "さん", "³"),
            Triple(NumberCandidateKind.SUBSCRIPT, "さん", "₃"),
            Triple(NumberCandidateKind.CIRCLED, "さん", "③"),
            Triple(NumberCandidateKind.BLACK_CIRCLED, "さん", "❸"),
            Triple(NumberCandidateKind.ROMAN, "さん", "Ⅲ"),
            Triple(NumberCandidateKind.PARENTHESIZED, "さん", "⑶"),
            Triple(NumberCandidateKind.PERIOD, "さん", "⒊"),
        )
        for ((kind, reading, surface) in cases) {
            val before = generated(reading)
            assertTrue("$kind/$reading missing $surface", before.any { it.string == surface })
            val disabled = NumberCandidateConfig(disabledKinds = setOf(kind))
            assertFalse("$kind", generated(reading, disabled).any { it.string == surface })
            assertFalse(NumberCandidatePolicy.eligible(reading, before.first { it.string == surface }, PredictionConfig(numberCandidateConfig = disabled)))
        }
        val allOff = NumberCandidateConfig(disabledKinds = NumberCandidateKind.entries.toSet())
        assertEquals(setOf("3", "３", "三"), generated("さん", allOff).map { it.string }.toSet())
        assertTrue(engine.getCandidatesEnglishKana("003", PredictionConfig(japaneseNumberCandidatesEnabled = false, numberCandidateConfig = allOff)).any { it.string == "003" })
        assertFalse(generated("1234", allOff).any { it.string in setOf("12:34", "12時34分") })
    }

    @Test fun customUnitsAndSpecialReadingsHaveExactAndEditableScope() {
        val unit = CustomNumberUnit("pieces", "個", "こ", specialReadings = listOf(
            SpecialNumberReading(1, "いっこ"), SpecialNumberReading(8, "はっこ"), SpecialNumberReading(8, "はちこ")))
        val settings = NumberCandidateConfig(units = listOf(unit))
        fun own(reading: String, config: NumberCandidateConfig = settings) = generated(reading, config).filter { it.number?.customUnit != null }
        assertEquals(listOf("2個", "２個", "二個"), own("にこ").map { it.string })
        assertEquals(listOf("1個", "１個", "一個"), own("いっこ").map { it.string })
        assertTrue(own("いちこ").isEmpty())
        assertTrue(own("じゅういっこ").isEmpty())
        assertEquals(own("はっこ").map { it.string }, own("はちこ").map { it.string })
        val candidate = own("いっこ").first()
        assertFalse(NumberCandidatePolicy.eligible("いっこ", candidate, PredictionConfig()))
        assertFalse(NumberCandidatePolicy.eligible("いっこ", candidate, PredictionConfig(numberCandidateConfig = settings.copy(units = listOf(unit.copy(enabled = false))))))
        assertFalse(NumberCandidatePolicy.eligible("いっこ", candidate, PredictionConfig(numberCandidateConfig = settings.copy(units = listOf(unit.copy(output = "セット"))))))
        assertFalse(NumberCandidatePolicy.eligible("いっこ", candidate.copy(string = "2個", commitText = "2個"), PredictionConfig(numberCandidateConfig = settings)))
        assertEquals(listOf("1個", "１個", "一個"), own("いちこ", settings.copy(units = listOf(unit.copy(specialReadings = emptyList())))).map { it.string })
        assertEquals(settings, NumberCandidateConfig.decode(settings.encode()))
        assertFalse(unit.copy(specialReadings = listOf(SpecialNumberReading(1, "いっこ"), SpecialNumberReading(2, "いっこ"))).isValid())
        assertFalse(unit.copy(specialReadings = listOf(SpecialNumberReading(1, "にこ"))).isValid())
        assertEquals(NumberCandidateConfig(), NumberCandidateConfig.decode("broken"))
    }

    @Test fun aCustomUnitCannotMergeItsNotationSlotsWithAClockProof() {
        val unit = CustomNumberUnit("custom", "時分", "じふん", specialReadings = listOf(SpecialNumberReading(65, "いちじごふん")))
        val config = NumberCandidateConfig(units = listOf(unit))
        for (order in NumberCandidateOrder.entries) {
            val proofs = ValidatedNumber.parseAll("いちじごふん", config)
            val grouped = proofs.flatMap { proof -> proof.basicForms.map { surface ->
                com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(surface, 18, 6u, 8000, number = proof)
            } }
            assertEquals(order.indices.map { listOf("1時5分", "１時５分", "一時五分")[it] },
                NumberCandidatePolicy.order("いちじごふん", grouped, order).take(3).map { it.string })
            val candidates = engine.getCandidatesEnglishKana("いちじごふん", PredictionConfig(numberCandidateConfig = config, numberCandidateOrder = order))
            for (forms in listOf(listOf("1時5分", "１時５分", "一時五分"), listOf("65時分", "６５時分", "六十五時分"))) {
                assertEquals(order.indices.map(forms::get), candidates.filter { it.string in forms }.map { it.string })
            }
        }
    }

    @Test fun colonFollowsAllThreeClockFormsAfterCandidateMerging() {
        val proof = ValidatedNumber.parse("いちじごふん")!!
        val surfaces = listOf("1時5分", "1:05", "通常語", "１時５分", "一時五分")
        val candidates = surfaces.map { surface ->
            com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(surface, 18, 6u, 0,
                number = if (surface == "通常語") null else proof)
        }
        for (order in NumberCandidateOrder.entries) {
            val sorted = NumberCandidatePolicy.order(proof.reading, candidates, order).map { it.string }
            assertEquals("通常語", sorted[2])
            assertEquals(order.indices.map { listOf("1時5分", "１時５分", "一時五分")[it] } + "1:05",
                sorted.filterNot { it == "通常語" })
        }
    }

    @Test fun invalidClockInputsDoNotGenerateClockCandidatesAndTimeTypesAreRetained() {
        for (reading in listOf("さんじろくじゅっぷん", "さんじゅうじごふん", "しじごふん", "いちじいちふん", "いちじごふんご", "いちじご")) {
            assertTrue(reading, generated(reading).none { it.number?.clock != null })
        }
        for (reading in listOf("いちじごふん", "さんじごふん", "にじゅうくじごじゅうきゅうふん")) {
            assertTrue(generated(reading).any { it.type == com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME && it.number?.clock != null })
        }
    }
}
