package com.kazumaproject.markdownhelperkeyboard.ai.rerank

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

internal object CandidateScoreRanking {
    private const val DICTIONARY_SCALE = 0.001f

    fun apply(
        original: List<Candidate>,
        targets: List<IndexedValue<Candidate>>,
        scores: FloatArray,
    ): List<Candidate> {
        if (targets.size != scores.size || scores.any { !it.isFinite() }) return original
        val reordered = targets.indices.sortedByDescending { scores[it] - DICTIONARY_SCALE * targets[it].value.score }
        val result = original.toMutableList()
        targets.indices.forEach { i -> result[targets[i].index] = targets[reordered[i]].value }
        return result
    }
}
