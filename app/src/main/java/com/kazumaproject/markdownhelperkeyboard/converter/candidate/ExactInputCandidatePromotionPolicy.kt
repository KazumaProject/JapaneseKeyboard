package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * Promotes selected literal-input candidates without overriding learned choices.
 *
 * The stable priority order is:
 * 1. learned candidates,
 * 2. the first candidate whose output is exactly the current input,
 * 3. every other candidate.
 *
 * Callers may apply an explicit, user-saved order after this policy. Inputs or candidate lists that
 * require no reordering are returned unchanged so the normal conversion path does not allocate.
 */
internal class ExactInputCandidatePromoter(
    private val promotedInputs: Set<String>,
) {
    fun promote(
        input: String,
        candidates: List<Candidate>,
    ): List<Candidate> {
        if (candidates.size <= 1 || input !in promotedInputs) return candidates

        var exactIndex = -1
        var previousCategory = LEARNED_CATEGORY
        var needsReordering = false

        for (index in candidates.indices) {
            val candidate = candidates[index]
            if (exactIndex == -1 && candidate.string == input) {
                exactIndex = index
            }
            val category = when {
                candidate.type == CANDIDATE_TYPE_LEARNED_DICTIONARY -> LEARNED_CATEGORY
                index == exactIndex -> EXACT_INPUT_CATEGORY
                else -> OTHER_CATEGORY
            }
            if (category < previousCategory) {
                needsReordering = true
            }
            previousCategory = category
        }

        if (exactIndex == -1 || !needsReordering) return candidates

        val exactCandidate = candidates[exactIndex]
        val result = ArrayList<Candidate>(candidates.size)
        for (index in candidates.indices) {
            val candidate = candidates[index]
            if (candidate.type == CANDIDATE_TYPE_LEARNED_DICTIONARY) {
                result.add(candidate)
            }
        }
        if (exactCandidate.type != CANDIDATE_TYPE_LEARNED_DICTIONARY) {
            result.add(exactCandidate)
        }
        for (index in candidates.indices) {
            val candidate = candidates[index]
            if (
                candidate.type != CANDIDATE_TYPE_LEARNED_DICTIONARY &&
                index != exactIndex
            ) {
                result.add(candidate)
            }
        }
        return result
    }

    private companion object {
        const val LEARNED_CATEGORY = 0
        const val EXACT_INPUT_CATEGORY = 1
        const val OTHER_CATEGORY = 2
    }
}

internal object ExactInputCandidatePromotionPolicy {
    private val promoter = ExactInputCandidatePromoter(
        hashSetOf(
            "ての",
            "か？",
            "なにこれ？",
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
        ),
    )

    fun promote(
        input: String,
        candidates: List<Candidate>,
    ): List<Candidate> = promoter.promote(input, candidates)
}
