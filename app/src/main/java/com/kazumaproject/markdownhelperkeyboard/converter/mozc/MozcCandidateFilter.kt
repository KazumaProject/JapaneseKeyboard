package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

class MozcCandidateFilter(
    private val trace: MozcConverterTrace? = null,
) {
    fun filter(candidates: List<MozcConvertedCandidate>): List<Candidate> {
        trace?.unsupportedFilters?.add("suggestion_filter/inappropriate-word data is not bundled")
        val bestBySurface = linkedMapOf<String, Candidate>()
        for (converted in candidates) {
            val candidate = converted.candidate
            if (candidate.string.isEmpty()) continue
            val adjusted = suppressUnknownOnlyPriority(candidate, converted.path)
            val existing = bestBySurface[adjusted.string]
            if (existing == null || adjusted.score < existing.score) {
                bestBySurface[adjusted.string] = adjusted
            }
        }
        return bestBySurface.values.sortedBy { it.score }
    }

    private fun suppressUnknownOnlyPriority(candidate: Candidate, path: MozcPath): Candidate {
        val unknownOnly = path.nodes.isNotEmpty() && path.nodes.all { it.isUnknown }
        return if (unknownOnly && path.nodes.size > 1) {
            candidate.copy(score = candidate.score + UNKNOWN_ONLY_PENALTY)
        } else {
            candidate
        }
    }

    companion object {
        private const val UNKNOWN_ONLY_PENALTY = 5000
    }
}
