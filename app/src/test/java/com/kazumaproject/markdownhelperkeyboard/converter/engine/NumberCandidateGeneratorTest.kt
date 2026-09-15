package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import org.junit.Assert.*
import org.junit.Test

class NumberCandidateGeneratorTest {
    private fun generated(input: String, config: PredictionConfig = PredictionConfig()) =
        NumberCandidateGenerator.generate(input, config).map { it.string }

    @Test fun completeReadingsAndBoundaries() {
        val cases = mapOf(
            "よじ" to listOf("4時", "４時", "四時"),
            "いちじごふん" to listOf("1時5分", "１時５分", "一時五分", "1:05"),
            "さんじごふん" to listOf("3時5分", "３時５分", "三時五分", "3:05"),
            "にじゅうくじごじゅうきゅうふん" to listOf("29時59分", "２９時５９分", "二十九時五十九分", "29:59"),
            "ぜろじぜろふん" to listOf("0時0分", "０時０分", "〇時〇分", "0:00"),
            "ひゃっぷん" to listOf("100分", "１００分", "百分"),
        )
        cases.forEach { (reading, expected) -> assertEquals(reading, expected, generated(reading)) }
        listOf("これはよじ", "ごぜん", "しじ", "さんじゅうじ", "さんじろくじゅっぷん", "いちじいちふん",
            "いちじごふんご", "いちじご", "ぜん", "さんひゃく", "いっ", "びゃく").forEach {
            assertTrue("$it: ${generated(it)}", generated(it).isEmpty())
        }
        assertTrue(generated("229").contains("2月29日"))
        assertFalse(generated("230").contains("2月30日"))
        assertFalse(generated("431").contains("4月31日"))
        assertFalse(generated("3000").contains("30:00"))
        assertEquals(listOf("００３", "003"), generated("003", PredictionConfig(
            numberCandidateOrder = NumberCandidateOrder.FULL_HALF_KANJI)).take(2))
        assertTrue(generated("9223372036854775808").contains("９２２３３７２０３６８５４７７５８０８"))
    }

    @Test fun eachKindControlsOnlyGeneratedAdditions() {
        val cases = listOf(
            Triple(NumberCandidateKind.TIME, "いちじごふん", "1時5分"),
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
        cases.forEach { (kind, reading, surface) ->
            assertTrue("$kind", surface in generated(reading))
            assertFalse("$kind", surface in generated(reading, PredictionConfig(
                numberCandidateConfig = NumberCandidateConfig(disabledKinds = setOf(kind)))))
        }
        assertTrue(generated("さん", PredictionConfig(japaneseNumberCandidatesEnabled = false)).isEmpty())
        assertTrue(generated("3", PredictionConfig(japaneseNumberCandidatesEnabled = false)).contains("3"))
        assertFalse(generated("さん", PredictionConfig(showSymbolCandidates = false)).contains("③"))
    }

    @Test fun customUnitsRoundTripAndUpdateWithoutChangingDictionaryCandidates() {
        val unit = CustomNumberUnit("pieces", "個", "こ", specialReadings = listOf(
            SpecialNumberReading(1, "いっこ"), SpecialNumberReading(8, "はっこ"), SpecialNumberReading(8, "はちこ")))
        val settings = NumberCandidateConfig(units = listOf(unit))
        fun own(reading: String, units: List<CustomNumberUnit> = listOf(unit)) = generated(reading,
            PredictionConfig(numberCandidateConfig = settings.copy(units = units)))
        assertEquals(settings, NumberCandidateConfig.decode(settings.encode()))
        assertEquals(listOf("2個", "２個", "二個"), own("にこ"))
        assertEquals(listOf("1個", "１個", "一個"), own("いっこ"))
        assertTrue(own("いちこ").isEmpty())
        assertTrue(own("じゅういっこ").isEmpty())
        assertEquals(own("はっこ"), own("はちこ"))
        assertTrue(own("いっこ", emptyList()).isEmpty())
        assertTrue(own("いっこ", listOf(unit.copy(enabled = false))).isEmpty())
        assertEquals(listOf("1組", "１組", "一組"), own("いっこ", listOf(unit.copy(output = "組"))))
        assertFalse(unit.copy(specialReadings = listOf(SpecialNumberReading(1, "にこ"))).isValid())
        assertFalse(unit.copy(specialReadings = listOf(SpecialNumberReading(1, "いっこ"), SpecialNumberReading(2, "いっこ"))).isValid())
        assertEquals(NumberCandidateConfig(), NumberCandidateConfig.decode("broken"))
        assertEquals(NumberCandidateConfig(), NumberCandidateConfig.decode("{\"version\":2}"))
    }

    @Test fun notationOrderPreservesOtherSlotsAndDictionaryObjects() {
        val reading = "いちじごふん"
        val forms = listOf("1時5分", "１時５分", "一時五分")
        val candidates = listOf("1時5分", "1:05", "通常語", "１時５分", "一時五分").map {
            Candidate(it, 1, reading.length.toUByte(), 1000)
        }
        for (order in NumberCandidateOrder.entries) {
            val sorted = NumberCandidateGenerator.order(reading, candidates, PredictionConfig(numberCandidateOrder = order))
            assertSame(candidates[2], sorted[2])
            assertEquals(order.indices.map(forms::get) + "1:05", sorted.filterNot { it.string == "通常語" }.map { it.string })
            assertEquals(candidates.toSet(), sorted.toSet())
        }
        val explicit = candidates[0].copy(type = CANDIDATE_TYPE_USER_DICTIONARY)
        val source = listOf(explicit) + candidates.drop(1)
        assertSame(explicit, NumberCandidateGenerator.order(reading, source,
            PredictionConfig(numberCandidateOrder = NumberCandidateOrder.KANJI_FULL_HALF))[0])
        assertEquals(candidates, NumberCandidateGenerator.order(reading, candidates,
            PredictionConfig(japaneseNumberCandidatesEnabled = false)))
        assertEquals(candidates, NumberCandidateGenerator.order("これはよじ", candidates, PredictionConfig()))
    }
}
