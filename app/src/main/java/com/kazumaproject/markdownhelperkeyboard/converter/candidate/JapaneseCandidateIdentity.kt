package com.kazumaproject.markdownhelperkeyboard.converter.candidate

enum class JapaneseCandidateSource {
    NBEST,
    DICTIONARY,
    UNKNOWN,
    REWRITER,
}

data class JapaneseCandidateIdentity(
    val value: String,
    val key: String,
    val contentValue: String? = null,
    val contentKey: String? = null,
    val leftId: Short?,
    val rightId: Short?,
    val splitPattern: List<Int>,
    val wordCost: Int? = null,
    val structureCost: Int? = null,
    val candidateSource: JapaneseCandidateSource = JapaneseCandidateSource.NBEST,
)

enum class JapaneseCandidateDedupeMode {
    LEGACY_VALUE,
    IDENTITY,
}

enum class JapaneseCandidateDedupeAction {
    RETAINED,
    DROPPED_VALUE_DUPLICATE,
    DROPPED_IDENTITY_DUPLICATE,
    WOULD_DROP_BY_VALUE_ONLY,
}

data class JapaneseCandidateDedupeTrace(
    val input: String,
    val identity: JapaneseCandidateIdentity,
    val action: JapaneseCandidateDedupeAction,
    val reason: String,
)
