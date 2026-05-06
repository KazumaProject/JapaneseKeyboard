package com.kazumaproject.markdownhelperkeyboard.converter.glide

class QwertyGlideCandidateReranker(
    private val topKSelector: QwertyGlideTopKSelector = QwertyGlideTopKSelector()
) {
    fun rerank(
        candidates: List<QwertyGlideScoredWord>,
        previousText: String,
        limit: Int
    ): List<QwertyGlideScoredWord> {
        val contextAdjusted = candidates.asSequence().map { scored ->
            val contextCost = contextCost(scored.entry.word, previousText)
            scored.copy(totalCost = scored.totalCost + contextCost)
        }
        return topKSelector.selectScored(contextAdjusted.asIterable(), limit)
    }

    private fun contextCost(word: String, previousText: String): Float {
        if (previousText.isBlank() || word.isBlank()) return 0f
        return 0f
    }
}
