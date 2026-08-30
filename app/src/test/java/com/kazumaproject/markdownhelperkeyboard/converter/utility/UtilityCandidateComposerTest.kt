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

    private fun candidate(text: String, type: Byte = 1) = Candidate(
        string = text,
        type = type,
        length = text.length.toUByte(),
        score = 0,
    )
}
