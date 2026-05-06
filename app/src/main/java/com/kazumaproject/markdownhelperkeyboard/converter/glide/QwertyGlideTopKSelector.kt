package com.kazumaproject.markdownhelperkeyboard.converter.glide

import java.util.PriorityQueue

class QwertyGlideTopKSelector {
    fun selectScored(
        candidates: Iterable<QwertyGlideScoredWord>,
        limit: Int
    ): List<QwertyGlideScoredWord> {
        if (limit <= 0) return emptyList()
        val bestByWord = linkedMapOf<String, QwertyGlideScoredWord>()
        for (candidate in candidates) {
            val existing = bestByWord[candidate.entry.word]
            if (existing == null || finalComparator.compare(candidate, existing) < 0) {
                bestByWord[candidate.entry.word] = candidate
            }
        }
        return boundedTopK(bestByWord.values, limit, finalComparator)
    }

    fun selectPrefiltered(
        candidates: Iterable<QwertyGlidePrefilteredCandidate>,
        limit: Int
    ): List<QwertyGlidePrefilteredCandidate> {
        if (limit <= 0) return emptyList()
        return boundedTopK(candidates, limit, prefilterComparator)
    }

    private fun <T> boundedTopK(
        candidates: Iterable<T>,
        limit: Int,
        bestFirstComparator: Comparator<T>
    ): List<T> {
        val worstFirstComparator = Comparator<T> { left, right ->
            bestFirstComparator.compare(right, left)
        }
        val heap = PriorityQueue(limit + 1, worstFirstComparator)
        for (candidate in candidates) {
            if (heap.size < limit) {
                heap.add(candidate)
            } else if (bestFirstComparator.compare(candidate, heap.peek()) < 0) {
                heap.poll()
                heap.add(candidate)
            }
        }
        return heap.toList().sortedWith(bestFirstComparator)
    }

    companion object {
        val finalComparator: Comparator<QwertyGlideScoredWord> =
            compareBy<QwertyGlideScoredWord> { it.totalCost }
                .thenBy { it.entry.word }
                .thenBy { it.entry.wordCost }

        val prefilterComparator: Comparator<QwertyGlidePrefilteredCandidate> =
            compareBy<QwertyGlidePrefilteredCandidate> { it.cheapCost }
                .thenBy { it.entry.word }
                .thenBy { it.entry.wordCost }
    }
}
