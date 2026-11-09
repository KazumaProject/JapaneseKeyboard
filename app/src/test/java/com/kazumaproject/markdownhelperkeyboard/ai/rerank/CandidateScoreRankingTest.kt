package com.kazumaproject.markdownhelperkeyboard.ai.rerank

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CandidateScoreRankingTest {
    private val candidates = listOf(
        Candidate("今日", 1, 3u, 100),
        Candidate("手動候補", 1, 3u, 10),
        Candidate("京", 1, 3u, 101),
    )
    private val targets = listOf(IndexedValue(0, candidates[0]), IndexedValue(2, candidates[2]))

    @Test
    fun fillsOnlyTheScoredPositions() {
        assertEquals(
            listOf(candidates[2], candidates[1], candidates[0]),
            CandidateScoreRanking.apply(candidates, targets, floatArrayOf(-2f, -1f)),
        )
    }

    @Test
    fun keepsOriginalOrderWhenTheModelResponseIsInvalid() {
        assertSame(candidates, CandidateScoreRanking.apply(candidates, targets, floatArrayOf(-2f, Float.NaN)))
        assertSame(candidates, CandidateScoreRanking.apply(candidates, targets, floatArrayOf(-2f)))
    }
}
