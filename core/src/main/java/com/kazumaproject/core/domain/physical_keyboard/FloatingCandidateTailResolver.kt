package com.kazumaproject.core.domain.physical_keyboard

object FloatingCandidateTailResolver {
    fun resolveTail(originalInput: String, selectedCandidateLength: Int): String {
        return if (selectedCandidateLength >= 0 && originalInput.length > selectedCandidateLength) {
            originalInput.substring(selectedCandidateLength)
        } else {
            ""
        }
    }
}
