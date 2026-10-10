package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickCorrectionKind

/** Scores include the dictionary, connections, N-gram rules, and gesture/edit costs. */
internal object FlickCorrectionRanking {
    fun merge(literal: List<Candidate>, mixed: List<Candidate>, limit: Int): MutableList<Candidate> {
        val best = literal.firstOrNull() ?: return mixed.take(limit).toMutableList()
        val literalKeys = literal.mapTo(HashSet()) { it.commitText to it.length }
        val corrections = mixed.filter { it.flickCorrection != null }
            .filterNot { (it.commitText to it.length) in literalKeys }
            .sortedBy { it.score }
            .mapIndexed { rank, candidate ->
                val info = checkNotNull(candidate.flickCorrection)
                val trailingInsertion = info.edits.any {
                    it.kind == FlickCorrectionKind.MISSING &&
                        it.inputStart == candidate.length.toInt()
                }
                val touchSupported = info.edits.all { it.supportedByTouch }
                val structuralEdit = info.edits.size == 1 && when (info.edits.single().kind) {
                    FlickCorrectionKind.MISSING, FlickCorrectionKind.EXTRA, FlickCorrectionKind.TRANSPOSE -> true
                    else -> false
                }
                val margin = if (touchSupported) 6000 else 8000
                val canPromote = (touchSupported || structuralEdit) && !trailingInsertion &&
                    candidate.score.toLong() + margin < best.score &&
                    best.type != CANDIDATE_TYPE_USER_DICTIONARY &&
                    best.type != CANDIDATE_TYPE_LEARNED_DICTIONARY
                val suggestionCeiling = literal.getOrNull(2)?.score?.minus(1) ?: best.score + 4500
                val showNearTop = !trailingInsertion && info.edits.size == 1 && info.costUnits <= 2800 &&
                    candidate.score.toLong() <= best.score.toLong() + 4500
                when {
                    canPromote -> candidate
                    showNearTop -> candidate.copy(score = candidate.score.coerceAtMost(suggestionCeiling + rank)
                        .coerceAtLeast(best.score + 1 + rank))
                    candidate.score <= best.score -> candidate.copy(score = best.score + 1 + rank)
                    else -> candidate
                }
            }
        return (literal + corrections)
            .sortedWith(compareBy<Candidate> { it.score }.thenBy { it.flickCorrection != null })
            .distinctBy { it.commitText to it.length }
            .take(limit)
            .toMutableList()
    }

    /** A prediction or modifier-omission candidate for the same committed range stays unlabelled. */
    fun preferLiteralDuplicates(candidates: List<Candidate>, inputLength: Int): List<Candidate> {
        val literals = LinkedHashMap<Pair<String, Int>, Candidate>()
        for (candidate in candidates) if (candidate.flickCorrection == null) {
            literals.putIfAbsent(candidate.commitText to candidate.length.toInt().coerceAtMost(inputLength), candidate)
        }
        return candidates.map { candidate ->
            if (candidate.flickCorrection == null) candidate else
                literals[candidate.commitText to candidate.length.toInt().coerceAtMost(inputLength)] ?: candidate
        }.distinctBy { it.commitText to it.length }
    }
}
