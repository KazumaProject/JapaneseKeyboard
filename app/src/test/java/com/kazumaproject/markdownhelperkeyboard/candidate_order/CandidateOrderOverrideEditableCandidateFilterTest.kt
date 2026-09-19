package com.kazumaproject.markdownhelperkeyboard.candidate_order

import com.kazumaproject.markdownhelperkeyboard.candidate_order.ui.filterCandidateOrderEditableCandidates
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateOrderOverrideEditableCandidateFilterTest {

    @Test
    fun filtersByCandidateLengthAndNonBlankString() {
        val result = filterCandidateOrderEditableCandidates(
            reading = "きょう",
            candidates = listOf(
                candidate(string = "今日", length = 3),
                candidate(string = "きょ", length = 2),
                candidate(string = "今日", length = 2),
                candidate(string = "", length = 3),
                candidate(string = "京", length = 3),
            )
        )

        assertEquals(listOf("今日", "京"), result.map { it.string })
    }

    @Test
    fun duplicateStringsKeepFirstCandidateAfterLengthFiltering() {
        val result = filterCandidateOrderEditableCandidates(
            reading = "きょう",
            candidates = listOf(
                candidate(string = "今日", length = 2, score = 100),
                candidate(string = "今日", length = 3, score = 200),
                candidate(string = "今日", length = 3, score = 300),
            )
        )

        assertEquals(1, result.size)
        assertEquals(200, result.first().score)
    }

    private fun candidate(
        string: String,
        length: Int,
        score: Int = 0
    ): Candidate {
        return Candidate(
            string = string,
            type = 1,
            length = length,
            score = score
        )
    }
}
