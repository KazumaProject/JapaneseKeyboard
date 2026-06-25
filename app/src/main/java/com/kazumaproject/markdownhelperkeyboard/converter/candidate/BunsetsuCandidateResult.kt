package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class BunsetsuCandidateResult(
    val candidates: List<Candidate>,
    val splitPatterns: List<List<Int>>,
    val splitPatternByCandidateString: Map<String, List<Int>> = emptyMap(),
    val splitPatternByCandidateIdentity: Map<JapaneseCandidateIdentity, List<Int>> = emptyMap(),
    val dedupeTraces: List<JapaneseCandidateDedupeTrace> = emptyList(),
    val valueDuplicateCount: Int = dedupeTraces.count {
        it.action == JapaneseCandidateDedupeAction.DROPPED_VALUE_DUPLICATE ||
            it.action == JapaneseCandidateDedupeAction.WOULD_DROP_BY_VALUE_ONLY
    },
    val identityDuplicateCount: Int = dedupeTraces.count {
        it.action == JapaneseCandidateDedupeAction.DROPPED_IDENTITY_DUPLICATE
    },
) {
    val primarySplitPositions: List<Int>
        get() = candidates.firstOrNull()
            ?.japaneseCandidateIdentity
            ?.let { splitPatternByCandidateIdentity[it] }
            ?: candidates.firstOrNull()
            ?.let { splitPatternByCandidateString[it.string] }
            ?: splitPatterns.firstOrNull().orEmpty()
}
