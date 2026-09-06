package com.kazumaproject.core.domain.physical_keyboard

import com.kazumaproject.core.data.floating_candidate.CandidateInputRange

data class FloatingCandidateComposition(
    val text: String,
    val selectedTextStart: Int,
    val selectedTextEndExclusive: Int,
    val tail: String,
)

/**
 * Builds the complete composing text after replacing an input range with a candidate.
 *
 * The old implementation inferred the range from Candidate.length. That value is not a display
 * range for every candidate, so the resolver now accepts the range explicitly and refuses to
 * manufacture a shorter composing string when the range is invalid.
 */
object FloatingCandidateCompositionResolver {
    fun resolve(
        originalInput: String,
        replacementText: String,
        inputRange: CandidateInputRange,
    ): FloatingCandidateComposition? {
        if (!inputRange.isValidFor(originalInput)) return null

        val prefix = originalInput.substring(0, inputRange.start)
        val tail = originalInput.substring(inputRange.endExclusive)
        val selectedTextStart = prefix.length
        val selectedTextEndExclusive = selectedTextStart + replacementText.length
        return FloatingCandidateComposition(
            text = prefix + replacementText + tail,
            selectedTextStart = selectedTextStart,
            selectedTextEndExclusive = selectedTextEndExclusive,
            tail = tail,
        )
    }
}
