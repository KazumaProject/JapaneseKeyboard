package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class BunsetsuCandidateResult(
    val candidates: List<Candidate>,
    val splitPatterns: List<List<Int>>
) {
    val primarySplitPositions: List<Int>
        get() = splitPatterns.firstOrNull().orEmpty()
}
