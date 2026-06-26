package com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter

import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcCandidate

class MozcKotlinCandidateFilter {
    fun filter(candidates: List<MozcCandidate>): List<MozcCandidate> {
        val seen = HashSet<String>()
        return candidates.filter { candidate ->
            candidate.value.isNotBlank() && seen.add(candidate.value)
        }
    }
}
