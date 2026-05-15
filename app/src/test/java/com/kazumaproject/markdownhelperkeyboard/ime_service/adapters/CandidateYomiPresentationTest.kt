package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateYomiPresentationTest {

    @Test
    fun yomiIsHiddenWhenShowFlagIsDisabled() {
        val presentation = resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = false,
            isFirstCandidate = true,
            suggestion = candidate(string = "候補", yomi = "よみ"),
            candidateTextSize = 14f
        )

        assertFalse(presentation.isVisible)
        assertEquals("", presentation.text)
    }

    @Test
    fun yomiIsShownWhenShowFlagIsEnabledAndYomiDiffersFromCandidateString() {
        val presentation = resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = true,
            isFirstCandidate = true,
            suggestion = candidate(string = "候補", yomi = "よみ"),
            candidateTextSize = 14f
        )

        assertTrue(presentation.isVisible)
        assertEquals("よみ", presentation.text)
    }

    @Test
    fun yomiIsHiddenWhenCandidateIsNotFirst() {
        val presentation = resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = true,
            isFirstCandidate = false,
            suggestion = candidate(string = "候補", yomi = "よみ"),
            candidateTextSize = 14f
        )

        assertFalse(presentation.isVisible)
        assertEquals("", presentation.text)
    }

    @Test
    fun yomiIsHiddenWhenYomiIsNull() {
        val presentation = resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = true,
            isFirstCandidate = true,
            suggestion = candidate(string = "候補", yomi = null),
            candidateTextSize = 14f
        )

        assertFalse(presentation.isVisible)
        assertEquals("", presentation.text)
    }

    @Test
    fun yomiIsHiddenWhenYomiIsEmpty() {
        val presentation = resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = true,
            isFirstCandidate = true,
            suggestion = candidate(string = "候補", yomi = ""),
            candidateTextSize = 14f
        )

        assertFalse(presentation.isVisible)
        assertEquals("", presentation.text)
    }

    @Test
    fun yomiIsHiddenWhenYomiMatchesCandidateString() {
        val presentation = resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = true,
            isFirstCandidate = true,
            suggestion = candidate(string = "候補", yomi = "候補"),
            candidateTextSize = 14f
        )

        assertFalse(presentation.isVisible)
        assertEquals("", presentation.text)
    }

    @Test
    fun yomiTextSizeIsSeventyTwoPercentOfCandidateTextSize() {
        val presentation = resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = true,
            isFirstCandidate = true,
            suggestion = candidate(string = "候補", yomi = "よみ"),
            candidateTextSize = 20f
        )

        assertEquals(20f * 0.72f, presentation.textSize, 0.001f)
    }

    private fun candidate(
        string: String,
        yomi: String?
    ): Candidate {
        return Candidate(
            string = string,
            type = 1.toByte(),
            length = string.length.toUByte(),
            score = 0,
            yomi = yomi
        )
    }
}
