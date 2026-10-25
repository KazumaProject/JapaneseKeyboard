package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ConversionCorrection
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.NBestCandidateCollector
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateCorrectionOptions
import org.junit.Assert.*
import org.junit.Test

class ZenzInputPolicyTest {
    private fun candidate(score: Int, mask: Int = 0, reading: String? = "がっこう") = Candidate(
        string = "学校", type = 1, length = 4u, score = score, yomi = reading, correctionMask = mask,
    )
    private val all = CandidateCorrectionOptions(true, true, true)

    @Test fun choosesMinimumScoreEvenWhenUncorrectedCandidateIsDisplayedFirst() {
        val collector = NBestCandidateCollector()
        val literal = candidate(2000, reading = "かつこう")
        val corrected = candidate(1000, ConversionCorrection.OMISSION)
        collector.collect(listOf(literal, corrected))
        assertSame(corrected, collector.best)
        assertEquals("がっこう", ZenzInputPolicy.reading("かつこう", collector.best, all))
    }

    @Test fun enabledCorrectionDoesNotPreferAHigherCostCorrectedCandidate() {
        val collector = NBestCandidateCollector()
        val literal = candidate(1000, reading = "かつこう")
        collector.collect(listOf(candidate(2000, ConversionCorrection.OMISSION), literal))
        assertSame(literal, collector.best)
        assertEquals("かつこう", ZenzInputPolicy.reading("かつこう", collector.best, all))
    }

    @Test fun tiesKeepOriginalNBestOrderAndEmptyResultsClearPreviousBest() {
        val collector = NBestCandidateCollector()
        val first = candidate(1000)
        collector.collect(listOf(first, candidate(1000, ConversionCorrection.OMISSION)))
        assertSame(first, collector.best)
        collector.collect(emptyList())
        assertNull(collector.best)
    }

    @Test fun onlyEnabledProvenanceCanChangeTheReading() {
        val cases = listOf(
            ConversionCorrection.OMISSION to CandidateCorrectionOptions(true, false, false),
            ConversionCorrection.JAPANESE_FLICK to CandidateCorrectionOptions(false, true, false),
            ConversionCorrection.ENGLISH_QWERTY to CandidateCorrectionOptions(false, false, true),
        )
        for ((mask, options) in cases) {
            assertEquals("がっこう", ZenzInputPolicy.reading("かつこう", candidate(1, mask), options))
            assertEquals("かつこう", ZenzInputPolicy.reading("かつこう", candidate(1, mask), CandidateCorrectionOptions(false, false, false)))
        }
        assertEquals("かつこう", ZenzInputPolicy.reading("かつこう", candidate(1, ConversionCorrection.JAPANESE_FLICK), cases[0].second))
    }

    @Test fun englishCorrectionReadingDoesNotChangeTheNormalCandidateYomi() {
        val candidate = Candidate(
            string = "Hello", type = 35, length = 5u, score = 9000,
            correctionMask = ConversionCorrection.ENGLISH_QWERTY, correctionReading = "hello",
        )
        assertNull(candidate.yomi)
        assertEquals("hello", ZenzInputPolicy.reading("hellp", candidate, CandidateCorrectionOptions(false, false, true)))
        assertEquals("hellp", ZenzInputPolicy.reading("hellp", candidate, CandidateCorrectionOptions(false, false, false)))
    }

    @Test fun differentReadingWithoutCorrectionProvenanceIsNotEnough() {
        assertEquals("かつこう", ZenzInputPolicy.reading("かつこう", candidate(1), all))
        assertEquals("かつこう", ZenzInputPolicy.reading("かつこう", null, all))
        for (reading in listOf(null, "")) {
            assertEquals("かつこう", ZenzInputPolicy.reading("かつこう", candidate(1, ConversionCorrection.OMISSION, reading), all))
        }
    }
}
