package com.kazumaproject.markdownhelperkeyboard.candidate_order

import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideSorter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CandidateOrderOverrideSorterTest {

    @Test
    fun emptyOverridesKeepsOriginalOrder() {
        val candidates = candidates("主導", "手動", "修道")

        val result = CandidateOrderOverrideSorter.apply(candidates, emptyList())

        assertSame(candidates, result)
        assertEquals(listOf("主導", "手動", "修道"), result.words())
    }

    @Test
    fun savedRanksMoveMatchedCandidates() {
        val candidates = candidates("主導", "手動", "修道")
        val overrides = overrides("手動" to 1, "主導" to 2)

        val result = CandidateOrderOverrideSorter.apply(candidates, overrides)

        assertEquals(listOf("手動", "主導", "修道"), result.words())
    }

    @Test
    fun missingSavedCandidateIsNotAdded() {
        val candidates = candidates("主導", "修道")
        val overrides = overrides("手動" to 1, "主導" to 2)

        val result = CandidateOrderOverrideSorter.apply(candidates, overrides)

        assertEquals(listOf("主導", "修道"), result.words())
    }

    @Test
    fun unsetCandidatesKeepRelativeOrder() {
        val candidates = candidates("主導", "手動", "修道", "修道院")
        val overrides = overrides("手動" to 1)

        val result = CandidateOrderOverrideSorter.apply(candidates, overrides)

        assertEquals(listOf("手動", "主導", "修道", "修道院"), result.words())
    }

    @Test
    fun singleCandidateReturnsOriginalList() {
        val candidates = candidates("主導")
        val overrides = overrides("主導" to 1)

        val result = CandidateOrderOverrideSorter.apply(candidates, overrides)

        assertSame(candidates, result)
        assertEquals(listOf("主導"), result.words())
    }

    @Test
    fun sameRankIsStableByOriginalIndex() {
        val candidates = candidates("主導", "手動", "修道")
        val overrides = overrides("手動" to 1, "主導" to 1)

        val result = CandidateOrderOverrideSorter.apply(candidates, overrides)

        assertEquals(listOf("主導", "手動", "修道"), result.words())
    }

    @Test
    fun partialOverrideMovesOnlyConfiguredCandidateToTop() {
        val candidates = candidates("主導", "手動", "修道", "酒道")
        val overrides = overrides("修道" to 1)

        val result = CandidateOrderOverrideSorter.apply(candidates, overrides)

        assertEquals(listOf("修道", "主導", "手動", "酒道"), result.words())
    }

    private fun candidates(vararg words: String): List<Candidate> =
        words.map {
            Candidate(
                string = it,
                type = 1,
                length = it.length.toUByte(),
                score = 0
            )
        }

    private fun overrides(vararg ranks: Pair<String, Int>): List<CandidateOrderOverrideEntity> =
        ranks.map {
            CandidateOrderOverrideEntity(
                input = "しゅどう",
                candidate = it.first,
                rank = it.second,
                createdAt = 1L,
                updatedAt = 1L
            )
        }

    private fun List<Candidate>.words(): List<String> = map { it.string }
}
