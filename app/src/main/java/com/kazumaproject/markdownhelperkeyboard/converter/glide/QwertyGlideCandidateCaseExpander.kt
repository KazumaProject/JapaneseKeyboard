package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import java.util.Locale

class QwertyGlideCandidateCaseExpander {
    fun expand(
        candidates: List<Candidate>,
        limit: Int
    ): List<Candidate> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        val lowestScoreByString = LinkedHashMap<String, Candidate>()
        fun add(candidate: Candidate) {
            val existing = lowestScoreByString[candidate.string]
            if (existing == null || candidate.score < existing.score) {
                lowestScoreByString[candidate.string] = candidate
            }
        }

        for (candidate in candidates) {
            add(candidate)
            if (candidate.string.isEmpty()) continue

            val capitalized = candidate.string.replaceFirstChar { char ->
                char.uppercase(Locale.ROOT)
            }
            add(
                candidate.copy(
                    string = capitalized,
                    length = capitalized.length.toUByte(),
                    score = candidate.score + CAPITALIZED_SCORE_OFFSET
                )
            )

            val uppercase = candidate.string.uppercase(Locale.ROOT)
            add(
                candidate.copy(
                    string = uppercase,
                    length = uppercase.length.toUByte(),
                    score = candidate.score + UPPERCASE_SCORE_OFFSET
                )
            )
        }

        return lowestScoreByString.values
            .sortedBy { it.score }
            .take(limit)
    }

    companion object {
        const val CAPITALIZED_SCORE_OFFSET = 1500
        const val UPPERCASE_SCORE_OFFSET = 3000
    }
}
