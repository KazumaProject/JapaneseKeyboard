package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ExactInputCandidatePromotionPolicyTest {

    @Test
    fun promotesEveryConfiguredLiteralInput() {
        configuredInputs.forEach { input ->
            val candidates = listOf(
                candidate("変換候補"),
                candidate(input),
                candidate("別候補"),
            )

            val result = ExactInputCandidatePromotionPolicy.promote(input, candidates)

            assertEquals(input, result.first().string)
            assertEquals(candidates.toSet(), result.toSet())
        }
    }

    @Test
    fun keepsAllLearnedCandidatesAheadOfExactInput() {
        val candidates = listOf(
            candidate("通常候補"),
            candidate("学習2", CANDIDATE_TYPE_LEARNED_DICTIONARY),
            candidate("ふ"),
            candidate("学習1", CANDIDATE_TYPE_LEARNED_DICTIONARY),
            candidate("その他"),
        )

        val result = ExactInputCandidatePromotionPolicy.promote("ふ", candidates)

        assertEquals(
            listOf("学習2", "学習1", "ふ", "通常候補", "その他"),
            result.map { it.string },
        )
    }

    @Test
    fun learnedExactInputStaysInLearnedGroupWithoutDuplication() {
        val candidates = listOf(
            candidate("通常候補"),
            candidate("む", CANDIDATE_TYPE_LEARNED_DICTIONARY),
            candidate("別の学習", CANDIDATE_TYPE_LEARNED_DICTIONARY),
        )

        val result = ExactInputCandidatePromotionPolicy.promote("む", candidates)

        assertEquals(listOf("む", "別の学習", "通常候補"), result.map { it.string })
        assertEquals(1, result.count { it.string == "む" })
    }

    @Test
    fun segmentedAndFallbackOutputsNeedOnlyFinalStringEquality() {
        val candidates = listOf(
            candidate("過？"),
            candidate("か？"),
            candidate("可？"),
        )

        val result = ExactInputCandidatePromotionPolicy.promote("か？", candidates)

        assertEquals("か？", result.first().string)
    }

    @Test
    fun returnsOriginalListWhenInputIsNotConfigured() {
        val candidates = listOf(candidate("候補"), candidate("そのまま"))

        val result = ExactInputCandidatePromotionPolicy.promote("そのまま", candidates)

        assertSame(candidates, result)
    }

    @Test
    fun returnsOriginalListWhenExactCandidateIsMissing() {
        val candidates = listOf(candidate("無"), candidate("夢"))

        val result = ExactInputCandidatePromotionPolicy.promote("む", candidates)

        assertSame(candidates, result)
    }

    @Test
    fun returnsOriginalListWhenPriorityOrderIsAlreadySatisfied() {
        val candidates = listOf(
            candidate("学習", CANDIDATE_TYPE_LEARNED_DICTIONARY),
            candidate("ほ"),
            candidate("補"),
        )

        val result = ExactInputCandidatePromotionPolicy.promote("ほ", candidates)

        assertSame(candidates, result)
    }

    private fun candidate(
        string: String,
        type: Byte = 1,
    ): Candidate = Candidate(
        string = string,
        type = type,
        length = string.length.toUByte(),
        score = 0,
    )

    private companion object {
        val configuredInputs = listOf(
            "ての",
            "か？",
            "え",
            "き",
            "く",
            "け",
            "こ",
            "さ",
            "し",
            "せ",
            "そ",
            "た",
            "ち",
            "て",
            "ひ",
            "ふ",
            "ほ",
            "み",
            "む",
            "め",
            "ゆ",
            "り",
        )
    }
}
