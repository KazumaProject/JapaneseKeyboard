package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.*
import org.junit.Test

class GemmaNbestScoringTest {
    private val request = GemmaNbestRequest("はし", "川を越えるための", listOf("箸", "橋", "端"))
    private fun candidate(text: String, type: Byte = 1, length: UByte = 2u) = Candidate(text, type, length, 100)

    @Test fun scoresOnlyFourUniqueWholeInputNbestCandidatesAndKeepsOriginalOrder() {
        val source = listOf(candidate("箸"), candidate("はし", 3), candidate("橋"), candidate("箸"),
            candidate("端"), candidate("梯"), candidate("嘴"), candidate("葉", length = 1u))
        val snapshot = GemmaNbestRequest.from("はし", "文".repeat(200), source)!!
        assertEquals(listOf("箸", "橋", "端", "梯"), snapshot.candidates)
        assertEquals(128, snapshot.leftContext.length)
        assertEquals("箸", source.first().string)
    }

    @Test fun predictionAndUtilityFirstCandidatesAndIncompleteReadingsDoNotProduceRequests() {
        assertNull(GemmaNbestRequest.from("はし", "", listOf(candidate("別の動作", 100), candidate("箸"), candidate("橋"))))
        assertNull(GemmaNbestRequest.from("a", "", listOf(candidate("箸"), candidate("橋"))))
        assertNull(GemmaNbestRequest.from("はし", "", listOf(candidate("箸"), candidate("箸"))))
    }

    @Test fun japanesePunctuationDoesNotDisableWholeInputScoring() {
        assertNotNull(GemmaNbestRequest.from("はし？", "", listOf(
            candidate("箸？", length = 3u), candidate("橋？", length = 3u))))
    }

    @Test fun aDifferentWinnerIsTheOnlyPublishedAlternative() {
        val scores = GemmaTextScores(floatArrayOf(-5f, -1f, -4f), intArrayOf(1, 1, 1))
        assertEquals("橋", GemmaNbestScoring.alternative(request, GemmaNbestMethod.LABELS, scores))
        val probabilities = GemmaNbestScoring.relativeProbabilities(request, GemmaNbestMethod.LABELS, scores)!!
        assertEquals(1.0, probabilities.sum(), 1e-9)
        assertTrue(probabilities[1] > probabilities[0])
        assertNull(GemmaNbestScoring.alternative(request, GemmaNbestMethod.LABELS,
            GemmaTextScores(floatArrayOf(-1f, -1f, -3f), intArrayOf(1, 1, 1))))
    }

    @Test fun fullTextUsesMeanLogLikelihoodWhileLabelsMustBeSingleTokens() {
        val scores = GemmaTextScores(floatArrayOf(-2f, -3f, -5f), intArrayOf(1, 3, 1))
        assertEquals("橋", GemmaNbestScoring.alternative(request, GemmaNbestMethod.FULL_TEXT, scores))
        assertNull(GemmaNbestScoring.alternative(request, GemmaNbestMethod.LABELS, scores))
    }

    @Test fun invalidNativeScoresCannotPublishOrInventACandidate() {
        for (scores in listOf(
            GemmaTextScores(floatArrayOf(-1f), intArrayOf(1)),
            GemmaTextScores(floatArrayOf(-1f, Float.NaN, -2f), intArrayOf(1, 1, 1)),
            GemmaTextScores(floatArrayOf(-1f, -2f, Float.NEGATIVE_INFINITY), intArrayOf(1, 1, 1)),
            GemmaTextScores(floatArrayOf(-1f, -2f, -3f), intArrayOf(1, 0, 1)),
        )) assertNull(GemmaNbestScoring.alternative(request, GemmaNbestMethod.FULL_TEXT, scores))
    }
}
