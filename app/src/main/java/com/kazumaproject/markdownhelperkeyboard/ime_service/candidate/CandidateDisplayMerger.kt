package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TEXT_MACRO
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/** Keeps the first candidate's behavior and order while preserving duplicate dictionary labels. */
object CandidateDisplayMerger {
    fun merge(candidates: List<Candidate>): List<Candidate> {
        val merged = LinkedHashMap<String, Candidate>()
        for (candidate in candidates) {
            val isMacro = candidate.type == CANDIDATE_TYPE_TEXT_MACRO
            val key = if (isMacro) "text-macro:${candidate.sourceId}" else "text:${candidate.string}"
            val first = merged[key]
            if (first == null) {
                merged[key] = candidate
            } else if (!isMacro && first.commitText == candidate.commitText &&
                !first.dictionaryCategories.containsAll(candidate.dictionaryCategories)
            ) {
                merged[key] = first.copy(
                    dictionaryCategories = first.dictionaryCategories + candidate.dictionaryCategories,
                )
            }
        }
        return merged.values.toList()
    }
}
