package com.kazumaproject.markdownhelperkeyboard.converter.utility

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_CALCULATION
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_UNIT_CONVERSION
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_UTILITY_LITERAL
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test

class UtilityCandidateComposerTest {
    @Test
    fun explicitResultsLeadAndDuplicateStringsAreRemoved() {
        val existing = listOf(candidate("3"), candidate("三"))
        val result = UtilityCandidateResult(
            candidates = listOf(UtilityCandidate("3", UtilityCandidateKind.CALCULATION)),
            trigger = UtilityTrigger.EXPLICIT_CALCULATION,
        )
        val composed = UtilityCandidateComposer.compose("1+2=", existing, result)
        assertEquals(listOf("3", "三"), composed.map { it.string })
        assertEquals(CANDIDATE_TYPE_CALCULATION, composed.first().type)
    }

    @Test
    fun automaticConversionKeepsLiteralFirstAndConversionsImmediatelyAfterIt() {
        val result = UtilityCandidateResult(
            candidates = listOf(UtilityCandidate("1m", UtilityCandidateKind.UNIT_CONVERSION)),
            trigger = UtilityTrigger.AUTOMATIC_UNIT_CONVERSION,
        )
        val composed = UtilityCandidateComposer.compose("100cm", listOf(candidate("百")), result)
        assertEquals(listOf("100cm", "1m", "百"), composed.map { it.string })
        assertEquals(CANDIDATE_TYPE_UTILITY_LITERAL, composed[0].type)
        assertEquals(CANDIDATE_TYPE_UNIT_CONVERSION, composed[1].type)
    }

    @Test
    fun existingLiteralIsReusedWithoutUtilityMarker() {
        val literal = candidate("100cm", type = 3)
        val result = UtilityCandidateResult(
            candidates = listOf(UtilityCandidate("1m", UtilityCandidateKind.UNIT_CONVERSION)),
            trigger = UtilityTrigger.AUTOMATIC_UNIT_CONVERSION,
        )
        val composed = UtilityCandidateComposer.compose("100cm", listOf(candidate("百"), literal), result)
        assertEquals(3, composed.first().type.toInt())
        assertEquals(1, composed.count { it.string == "100cm" })
    }

    @Test
    fun canonicalSourceFromUnitReadingLeadsAutomaticConversions() {
        val canonical = candidate("10尺", type = 3)
        val input = candidate("10しゃく", type = 4)
        val result = UtilityCandidateResult(
            candidates = listOf(UtilityCandidate("3.0303m", UtilityCandidateKind.UNIT_CONVERSION)),
            trigger = UtilityTrigger.AUTOMATIC_UNIT_CONVERSION,
            preferredSourceText = "10尺",
        )

        val composed = UtilityCandidateComposer.compose(
            input = "10しゃく",
            existingCandidates = listOf(input, candidate("十尺"), canonical),
            result = result,
        )

        assertEquals(listOf("10尺", "3.0303m", "10しゃく", "十尺"), composed.map { it.string })
        assertEquals(3, composed.first().type.toInt())
        assertEquals(CANDIDATE_TYPE_UNIT_CONVERSION, composed[1].type)
    }

    @Test
    fun canonicalSourceIsSynthesizedWhenDictionaryDoesNotProvideIt() {
        val result = UtilityCandidateResult(
            candidates = listOf(UtilityCandidate("33.0579m²", UtilityCandidateKind.UNIT_CONVERSION)),
            trigger = UtilityTrigger.AUTOMATIC_UNIT_CONVERSION,
            preferredSourceText = "10坪",
        )

        val composed = UtilityCandidateComposer.compose("10つぼ", emptyList(), result)

        assertEquals(listOf("10坪", "33.0579m²", "10つぼ"), composed.map { it.string })
        assertEquals(CANDIDATE_TYPE_UTILITY_LITERAL, composed.first().type)
    }

    @Test
    fun parsedJapaneseUnitReadingsProduceCanonicalFirstCandidates() {
        val provider = UtilityCandidateProvider()

        val shaku = UtilityCandidateComposer.compose(
            input = "10 しゃく",
            existingCandidates = listOf(candidate("10尺"), candidate("10 しゃく")),
            result = provider.provide("10 しゃく"),
        )
        val tsubo = UtilityCandidateComposer.compose(
            input = "10 つぼ",
            existingCandidates = listOf(candidate("10 つぼ")),
            result = provider.provide("10 つぼ"),
        )

        assertEquals("10尺", shaku.first().string)
        assertEquals("10坪", tsubo.first().string)
        assertEquals(CANDIDATE_TYPE_UTILITY_LITERAL, tsubo.first().type)
    }

    private fun candidate(text: String, type: Byte = 1) = Candidate(
        string = text,
        type = type,
        length = text.length.toUByte(),
        score = 0,
    )
}
