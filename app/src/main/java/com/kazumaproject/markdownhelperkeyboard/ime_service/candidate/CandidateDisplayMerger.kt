package com.kazumaproject.markdownhelperkeyboard.ime_service.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TEXT_MACRO
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_CONVERSION
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_KANA_LITERAL
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_KATAKANA
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_HALF_WIDTH_KATAKANA
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/** Keeps the first candidate's behavior and order while preserving duplicate display metadata. */
object CandidateDisplayMerger {
    fun merge(
        candidates: List<Candidate>,
        mergeDictionaryCategories: Boolean = true,
        separateMacroActions: Boolean = true,
    ): List<Candidate> {
        val merged = LinkedHashMap<String, Candidate>()
        for (candidate in candidates) {
            val isMacro = candidate.type == CANDIDATE_TYPE_TEXT_MACRO
            val key = if (isMacro && separateMacroActions) "text-macro:${candidate.sourceId}" else "text:${candidate.string}"
            val first = merged[key]
            if (first == null) {
                merged[key] = candidate
            } else if (!isMacro && first.commitText == candidate.commitText) {
                // These types share the length-based commit path. Retain the winner's reading,
                // score, and origins when an explicit kana variant has the same output and length.
                val inheritKanaType = (first.type == CANDIDATE_TYPE_CONVERSION || first.type == CANDIDATE_TYPE_KANA_LITERAL) &&
                    (candidate.type == CANDIDATE_TYPE_KATAKANA || candidate.type == CANDIDATE_TYPE_HALF_WIDTH_KATAKANA) &&
                    first.length == candidate.length && first.sourceId == null && first.presentation == null &&
                    first.dictionaryOrigins.isEmpty() && candidate.sourceId == null && candidate.presentation == null &&
                    candidate.dictionaryOrigins.isEmpty() && candidate.commitText == candidate.string
                val addCategories = mergeDictionaryCategories &&
                    !first.dictionaryCategories.containsAll(candidate.dictionaryCategories)
                if (inheritKanaType || addCategories) {
                    merged[key] = first.copy(
                        type = if (inheritKanaType) candidate.type else first.type,
                        dictionaryCategories = if (addCategories) {
                            first.dictionaryCategories + candidate.dictionaryCategories
                        } else first.dictionaryCategories,
                    )
                }
            }
        }
        return merged.values.toList()
    }
}
