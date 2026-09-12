package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import org.junit.Assert.*
import org.junit.Test

class NumberCandidateReviewRegressionTest {
    @Test fun aMixedNumericNodeDoesNotBypassTheCompleteReadingCheck() {
        val c = Candidate("全5", 1, 3u, 0, yomi = "ぜんご", conversionSegments = listOf(
            CandidateConversionSegment(0, 3, "全5", "ぜんご")))
        assertFalse(NumberCandidatePolicy.eligible("ぜんご", c))
    }

    @Test fun numericCommitTextCannotBorrowTheDisplayedTextSegments() {
        val c = Candidate("前後", 1, 3u, 0, yomi = "ぜんご", commitText = "全5", conversionSegments = listOf(
            CandidateConversionSegment(0, 3, "前後", "ぜんご")))
        assertFalse(NumberCandidatePolicy.eligible("ぜんご", c))
    }

    @Test fun anExplicitSegmentDoesNotExemptUnrelatedNumericCommitText() {
        val c = Candidate("登録語", 1, 3u, 0, yomi = "ぜんご", commitText = "1005", conversionSegments = listOf(
            CandidateConversionSegment(0, 3, "登録語", "ぜんご", source = com.kazumaproject.graph.CandidateSource.USER_DICTIONARY)))
        assertFalse(NumberCandidatePolicy.eligible("ぜんご", c))
        assertTrue(NumberCandidatePolicy.eligible("ぜんご", c.copy(commitText = "登録語")))
    }

    @Test fun oldDictionaryNumbersCannotBeReboundToAnotherInput() {
        val c = Candidate("3", 1, 2u, 0, yomi = "さん", conversionSegments = listOf(
            CandidateConversionSegment(0, 2, "3", "さん")))
        assertFalse(NumberCandidatePolicy.eligible("ろく", c))
        assertFalse(NumberCandidatePolicy.eligible("ろく", c.copy(yomi = null)))
        assertTrue(NumberCandidatePolicy.eligible("さん", c))
    }

    @Test fun userDictionaryPathsStillValidateAutomaticNeighbours() {
        val prefix = CandidateConversionSegment(0, 1, "登録", "あ", source = com.kazumaproject.graph.CandidateSource.USER_DICTIONARY)
        val bad = Candidate("登録全5", 1, 4u, 0, yomi = "あぜんご", conversionSegments = listOf(
            prefix, CandidateConversionSegment(1, 4, "全5", "ぜんご")))
        assertFalse(NumberCandidatePolicy.eligible("あぜんご", bad))
        val good = Candidate("登録4時", 1, 3u, 0, yomi = "あよじ", conversionSegments = listOf(
            prefix, CandidateConversionSegment(1, 2, "4", "よ"), CandidateConversionSegment(2, 3, "時", "じ")))
        assertTrue(NumberCandidatePolicy.eligible("あよじ", good))
    }

    @Test fun malformedNumericReadingsCannotHideInMixedKanjiHistory() {
        for (surface in listOf("全五", "全５", "全5")) {
            val history = Candidate(surface, CANDIDATE_TYPE_LEARNED_DICTIONARY, 3u, 0, yomi = "ぜんご")
            assertFalse(surface, NumberCandidatePolicy.eligible("ぜんご", history))
            val path = history.copy(conversionSegments = listOf(CandidateConversionSegment(0, 3, surface, "ぜんご")))
            assertFalse(surface, NumberCandidatePolicy.eligible("ぜんご", path))
        }
        assertTrue(NumberCandidatePolicy.eligible("ごかん", Candidate("五感", 1, 3u, 0, yomi = "ごかん")))
    }

    @Test fun incompleteInputCannotBorrowACompletedPredictionReading() {
        val raw = Candidate("10", 1, 3u, 0, yomi = "じゅう")
        assertFalse(NumberCandidatePolicy.eligible("じゅ", raw))
        val cached = NumberCandidatePolicy.filter("じゅう", listOf(raw), PredictionConfig()).single()
        assertFalse(NumberCandidatePolicy.eligible("じゅ", cached))
        assertFalse(NumberCandidatePolicy.eligible("", raw))
        assertTrue(NumberCandidatePolicy.eligible("じゅう", cached))
    }

    @Test fun selectingANumericNotationCommitsThatSameNotation() {
        val proof = requireNotNull(ValidatedNumber.parse("さん"))
        val generated = Candidate("3", 18, 2u, 0, yomi = "さん", number = proof, commitText = "三")
        assertFalse(NumberCandidatePolicy.eligible("さん", generated))
        assertFalse(NumberCandidatePolicy.eligible("さん", generated.copy(number = null, generatedNumber = false)))
        assertTrue(NumberCandidatePolicy.eligible("さん", generated.copy(commitText = "3")))
    }

    @Test fun theFestivalNameIsNotAnInvalidNumber() {
        // 七五三 names a festival, not 753. Kanagawa public cultural material:
        // https://www.pref.kanagawa.jp/documents/79520/culture11.pdf
        val c = Candidate("七五三", 1, 5u, 0, yomi = "しちごさん", conversionSegments = listOf(
            CandidateConversionSegment(0, 5, "七五三", "しちごさん")))
        assertTrue(NumberCandidatePolicy.eligible("しちごさん", c))
    }
}
