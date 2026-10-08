package com.kazumaproject.markdownhelperkeyboard.converter.candidate

internal const val MAX_BUNSETSU_SPLIT_PATTERNS = 4

data class BunsetsuCandidateResult(
    val candidates: List<Candidate>,
    val splitPatterns: List<List<Int>>,
    val splitPatternByCandidateString: Map<String, List<Int>> = emptyMap(),
    val systemNgramMatchedCandidates: Set<String> = emptySet(),
) {
    val primarySplitPositions: List<Int>
        get() = candidates.firstOrNull()
            ?.let { splitPatternByCandidateString[it.string] }
            ?: splitPatterns.firstOrNull().orEmpty()
}

/** Preserve the independently searched alternatives when candidate paths are rewritten. */
internal fun BunsetsuCandidateResult.withUpdatedCandidatePaths(
    candidates: List<Candidate>,
    updatedSplits: Map<String, List<Int>>,
): BunsetsuCandidateResult {
    if (updatedSplits == splitPatternByCandidateString) return copy(candidates = candidates)

    val changedPatterns = splitPatternByCandidateString.entries.filter { (text, oldPattern) ->
        updatedSplits[text]?.let { it != oldPattern } == true
    }.mapTo(hashSetOf()) { it.value }
    val retainedPatterns = splitPatterns.flatMap { pattern ->
        if (pattern !in changedPatterns) listOf(pattern)
        else splitPatternByCandidateString.entries.filter { it.value == pattern }.map { (text, _) ->
            // An unchanged lexical or protected candidate may still use the original boundary.
            updatedSplits[text] ?: pattern
        }
    }
    val addedPatterns = updatedSplits.filterKeys { it !in splitPatternByCandidateString }.values
    return copy(
        candidates = candidates,
        splitPatternByCandidateString = updatedSplits,
        splitPatterns = (retainedPatterns + addedPatterns).distinct().take(MAX_BUNSETSU_SPLIT_PATTERNS),
    )
}
