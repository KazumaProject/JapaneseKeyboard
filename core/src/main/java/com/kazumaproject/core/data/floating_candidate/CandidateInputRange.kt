package com.kazumaproject.core.data.floating_candidate

/**
 * A half-open UTF-16 range in the input that a candidate replaces.
 *
 * Candidate output length is deliberately not used here. Japanese conversion can change the
 * number of UTF-16 code units between the reading and the displayed text.
 */
data class CandidateInputRange(
    val start: Int,
    val endExclusive: Int,
) {
    fun isValidFor(input: String): Boolean {
        return start >= 0 && start <= endExclusive && endExclusive <= input.length
    }
}
