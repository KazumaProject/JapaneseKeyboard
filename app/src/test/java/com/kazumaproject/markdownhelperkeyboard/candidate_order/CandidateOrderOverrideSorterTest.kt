package com.kazumaproject.markdownhelperkeyboard.candidate_order

import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderScope
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
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

    @Test
    fun exactConversionNodePrefixAppliesSavedOrderToLongerInput() {
        val candidates = candidates("日を", "火を", "陽を")
        val result = CandidateOrderOverrideSorter.applyByConversionSegment(
            input = "ひを",
            candidates = candidates,
            overridesByInput = mapOf(
                "ひ" to overridesFor("ひ", "火" to 1, "日" to 2, "陽" to 3),
            ),
            candidateSegmentsByString = mapOf(
                "日を" to segments(0, 1, "日", 1, 2, "を"),
                "火を" to segments(0, 1, "火", 1, 2, "を"),
                "陽を" to segments(0, 1, "陽", 1, 2, "を"),
            ),
        )

        assertEquals(listOf("火を", "日を", "陽を"), result.words())
    }

    @Test
    fun textPrefixWithoutConversionBoundaryDoesNotMatch() {
        val candidates = candidates("日を", "火を")
        val result = CandidateOrderOverrideSorter.applyByConversionSegment(
            input = "ひを",
            candidates = candidates,
            overridesByInput = mapOf("ひ" to overridesFor("ひ", "火" to 1, "日" to 2)),
            candidateSegmentsByString = mapOf(
                "日を" to segments(0, 2, "日を"),
                "火を" to segments(0, 2, "火を"),
            ),
        )

        assertEquals(listOf("日を", "火を"), result.words())
    }

    @Test
    fun exactFullInputOrderTakesPriorityOverShorterSegmentOrder() {
        val candidates = candidates("日を", "火を")
        val result = CandidateOrderOverrideSorter.applyByConversionSegment(
            input = "ひを",
            candidates = candidates,
            overridesByInput = mapOf(
                "ひ" to overridesFor("ひ", "火" to 1, "日" to 2),
                "ひを" to exactOverridesFor("ひを", "日を" to 1, "火を" to 2),
            ),
            candidateSegmentsByString = mapOf(
                "日を" to segments(0, 1, "日", 1, 2, "を"),
                "火を" to segments(0, 1, "火", 1, 2, "を"),
            ),
        )

        assertEquals(listOf("日を", "火を"), result.words())
    }

    @Test
    fun savedOrderDoesNotMatchAConversionNodeLaterInInput() {
        val candidates = candidates("亜日", "亜火")
        val result = CandidateOrderOverrideSorter.applyByConversionSegment(
            input = "あひ",
            candidates = candidates,
            overridesByInput = mapOf("ひ" to overridesFor("ひ", "火" to 1, "日" to 2)),
            candidateSegmentsByString = mapOf(
                "亜日" to segments(0, 1, "亜", 1, 2, "日"),
                "亜火" to segments(0, 1, "亜", 1, 2, "火"),
            ),
        )

        assertEquals(listOf("亜日", "亜火"), result.words())
    }

    @Test
    fun exactShortInputOrderDoesNotAffectLongerInput() {
        val candidates = candidates("日を", "火を")
        val result = CandidateOrderOverrideSorter.applyByConversionSegment(
            input = "ひを",
            candidates = candidates,
            overridesByInput = mapOf(
                "ひ" to exactOverridesFor("ひ", "火" to 1, "日" to 2),
            ),
            candidateSegmentsByString = mapOf(
                "日を" to segments(0, 1, "日", 1, 2, "を"),
                "火を" to segments(0, 1, "火", 1, 2, "を"),
            ),
        )

        assertEquals(listOf("日を", "火を"), result.words())
    }

    @Test
    fun lexicalOrderDoesNotPromoteCandidateWithDifferentBaselineSegmentation() {
        val candidates = candidates("人多すぎ", "火と多すぎ")
        val result = CandidateOrderOverrideSorter.applyByConversionSegment(
            input = "ひとおおすぎ",
            candidates = candidates,
            overridesByInput = mapOf(
                "ひ" to overridesFor("ひ", "火" to 1, "日" to 2),
            ),
            candidateSegmentsByString = mapOf(
                "人多すぎ" to segments(0, 2, "人", 2, 6, "多すぎ"),
                "火と多すぎ" to segments(0, 1, "火", 1, 2, "と", 2, 6, "多すぎ"),
            ),
        )

        assertEquals(listOf("人多すぎ", "火と多すぎ"), result.words())
    }

    @Test
    fun lexicalOrderChangesOnlySlotsInBaselineSegmentationCohort() {
        val candidates = candidates("日を", "非対象", "火を")
        val result = CandidateOrderOverrideSorter.applyByConversionSegment(
            input = "ひを",
            candidates = candidates,
            overridesByInput = mapOf(
                "ひ" to overridesFor("ひ", "火" to 1, "日" to 2),
            ),
            candidateSegmentsByString = mapOf(
                "日を" to segments(0, 1, "日", 1, 2, "を"),
                "非対象" to segments(0, 2, "非対象"),
                "火を" to segments(0, 1, "火", 1, 2, "を"),
            ),
        )

        assertEquals(listOf("火を", "非対象", "日を"), result.words())
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
        overridesFor("しゅどう", *ranks)

    private fun overridesFor(
        input: String,
        vararg ranks: Pair<String, Int>,
    ): List<CandidateOrderOverrideEntity> = overridesForScope(
        input = input,
        scope = CandidateOrderScope.LEXICAL_UNIT,
        ranks = ranks,
    )

    private fun exactOverridesFor(
        input: String,
        vararg ranks: Pair<String, Int>,
    ): List<CandidateOrderOverrideEntity> = overridesForScope(
        input = input,
        scope = CandidateOrderScope.EXACT_INPUT,
        ranks = ranks,
    )

    private fun overridesForScope(
        input: String,
        scope: CandidateOrderScope,
        ranks: Array<out Pair<String, Int>>,
    ): List<CandidateOrderOverrideEntity> = ranks.map {
            CandidateOrderOverrideEntity(
                input = input,
                scope = scope.name,
                candidate = it.first,
                rank = it.second,
                createdAt = 1L,
                updatedAt = 1L
            )
        }

    private fun segments(
        vararg values: Any,
    ): List<CandidateConversionSegment> = values.toList().chunked(3).map { chunk ->
        CandidateConversionSegment(
            inputStart = chunk[0] as Int,
            inputEnd = chunk[1] as Int,
            output = chunk[2] as String,
        )
    }

    private fun List<Candidate>.words(): List<String> = map { it.string }
}
