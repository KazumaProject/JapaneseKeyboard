package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class BunsetsuCandidateResult(
    val candidates: List<Candidate>,
    val splitPatterns: List<List<Int>>,
    val splitPatternByCandidateString: Map<String, List<Int>> = emptyMap()
) {
    val primarySplitPositions: List<Int>
        get() = candidates.firstOrNull()
            ?.let { splitPatternByCandidateString[it.string] }
            ?: splitPatterns.firstOrNull().orEmpty()
}
