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
                candidate(string = "今日", length = 3u.toUByte()),
                candidate(string = "きょ", length = 2u.toUByte()),
                candidate(string = "今日", length = 2u.toUByte()),
                candidate(string = "", length = 3u.toUByte()),
                candidate(string = "京", length = 3u.toUByte()),
            )
        )

        assertEquals(listOf("今日", "京"), result.map { it.string })
    }

    @Test
    fun duplicateStringsKeepFirstCandidateAfterLengthFiltering() {
        val result = filterCandidateOrderEditableCandidates(
            reading = "きょう",
            candidates = listOf(
                candidate(string = "今日", length = 2u.toUByte(), score = 100),
                candidate(string = "今日", length = 3u.toUByte(), score = 200),
                candidate(string = "今日", length = 3u.toUByte(), score = 300),
            )
        )

        assertEquals(1, result.size)
        assertEquals(200, result.first().score)
    }

    private fun candidate(
        string: String,
        length: UByte,
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
