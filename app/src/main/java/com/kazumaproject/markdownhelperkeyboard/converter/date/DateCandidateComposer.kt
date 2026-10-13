package com.kazumaproject.markdownhelperkeyboard.converter.date

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

object DateCandidateComposer {
    private val dailyDateInputs = setOf("きょう", "きのう", "あした")
    private const val BASE_SCORE = 7000

    /** Reorders and filters only generated daily-date formats within their existing candidate slots. */
    fun compose(
        input: String,
        candidates: List<Candidate>,
        config: DateCandidateConfig = DateCandidateConfig(),
    ): List<Candidate> {
        if (input !in dailyDateInputs || candidates.none { it.dateFormat != null }) return candidates

        val order = config.normalizedOrder
        val rankByFormat = order.withIndex().associate { it.value to it.index }
        val enabledDateSlots = candidates.indices.filter { index ->
            candidates[index].dateFormat?.let { it in config.enabledFormats } == true
        }
        if (enabledDateSlots.isEmpty()) {
            return candidates.filter { it.dateFormat == null }
        }

        val orderedDateCandidates = candidates
            .filter { candidate -> candidate.dateFormat?.let { it in config.enabledFormats } == true }
            .sortedBy { candidate -> rankByFormat[candidate.dateFormat] ?: Int.MAX_VALUE }
            .map { candidate ->
                val rank = rankByFormat[candidate.dateFormat] ?: 0
                candidate.copy(score = BASE_SCORE + rank)
            }

        var nextDate = 0
        return buildList(candidates.size) {
            candidates.forEach { candidate ->
                when {
                    candidate.dateFormat == null -> add(candidate)
                    candidate.dateFormat !in config.enabledFormats -> Unit
                    else -> add(orderedDateCandidates[nextDate++])
                }
            }
        }
    }
}
