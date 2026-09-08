package com.kazumaproject.core.domain.physical_keyboard

import com.kazumaproject.core.data.floating_candidate.CandidateInputRange

/** Source reading is never inferred from a rendered candidate or from overlapping suffixes. */
data class PhysicalCandidateCompositionSession(
    val queryText: String,
    val trailingText: String,
    val generation: Long,
) {
    val sourceText: String get() = queryText + trailingText

    fun resolve(replacement: String, readingLength: Int): FloatingCandidateComposition? {
        if (readingLength !in 1..queryText.length) return null
        return FloatingCandidateCompositionResolver.resolve(
            sourceText, replacement, CandidateInputRange(0, readingLength)
        )
    }

    fun commit(composition: FloatingCandidateComposition, commitAll: Boolean): PhysicalCandidateCommit {
        return if (commitAll) {
            PhysicalCandidateCommit(composition.text, "")
        } else {
            PhysicalCandidateCommit(
                composition.text.substring(0, composition.selectedTextEndExclusive),
                composition.tail,
            )
        }
    }
}

data class PhysicalCandidateCommit(val committedText: String, val remainingReading: String)
