package com.kazumaproject.core.data.floating_candidate

/**
 * A half-open UTF-16 range in a composing input string.
 *
 * The range describes source text, not the length of the candidate output. Conversion can
 * change the number of UTF-16 code units between the reading and its displayed text.
 */
data class CandidateInputRange(
    val start: Int,
    val endExclusive: Int,
) {
    fun isValidFor(input: String): Boolean {
        return start >= 0 && start <= endExclusive && endExclusive <= input.length
    }
}
