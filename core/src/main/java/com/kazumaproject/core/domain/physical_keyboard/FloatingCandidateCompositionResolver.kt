package com.kazumaproject.core.domain.physical_keyboard

import com.kazumaproject.core.data.floating_candidate.CandidateInputRange

data class FloatingCandidateComposition(
    val text: String,
    val selectedTextStart: Int,
    val selectedTextEndExclusive: Int,
    val tail: String,
)

/**
 * Rebuilds the complete composing text after replacing a source range with a candidate.
 *
 * This is deliberately independent from Android's InputConnection. Callers must provide the
 * source range explicitly; inferring a range from the candidate output would be incorrect when
 * conversion changes the text length.
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
