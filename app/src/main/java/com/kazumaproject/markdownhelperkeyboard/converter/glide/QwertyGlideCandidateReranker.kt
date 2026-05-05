package com.kazumaproject.markdownhelperkeyboard.converter.glide

class QwertyGlideCandidateReranker {
    fun rerank(
        candidates: List<QwertyGlideScoredWord>,
        previousText: String,
        limit: Int
    ): List<QwertyGlideScoredWord> {
        val contextAdjusted = candidates.map { scored ->
            val contextCost = contextCost(scored.entry.word, previousText)
            scored.copy(totalCost = scored.totalCost + contextCost)
        }
        return contextAdjusted
            .groupBy { it.entry.word }
            .map { (_, values) -> values.minBy { it.totalCost } }
            .sortedWith(compareBy<QwertyGlideScoredWord> { it.totalCost }.thenBy { it.entry.word })
            .take(limit)
    }

    private fun contextCost(word: String, previousText: String): Float {
        if (previousText.isBlank() || word.isBlank()) return 0f
        return 0f
    }
}
