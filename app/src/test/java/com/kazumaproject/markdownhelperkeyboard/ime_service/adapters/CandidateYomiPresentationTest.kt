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
            readingTextSize = 14f
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
            readingTextSize = 14f
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
            readingTextSize = 14f
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
            readingTextSize = 14f
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
            readingTextSize = 14f
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
            readingTextSize = 14f
        )

        assertFalse(presentation.isVisible)
        assertEquals("", presentation.text)
    }

    @Test
    fun yomiTextSizeUsesTheIndependentReadingSize() {
        val presentation = resolveCandidateYomiPresentation(
            showCandidateYomiForLiveConversion = true,
            isFirstCandidate = true,
            suggestion = candidate(string = "候補", yomi = "よみ"),
            readingTextSize = 20f
        )

        assertEquals(20f, presentation.textSize, 0.001f)
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
