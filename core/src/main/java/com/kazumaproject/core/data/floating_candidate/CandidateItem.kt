package com.kazumaproject.core.data.floating_candidate

data class CandidateItem(
    val word: String,
    val length: UByte,
    val candidateType: Byte = 0,
    val sourceId: Long? = null,
    /** Input range replaced by this candidate, expressed as UTF-16 offsets. */
    val inputRange: CandidateInputRange? = null,
    /** UTF-16 length of the complete composing input represented by this item. */
    val compositionInputLength: Int? = null,
    /** Normalized bare TeX used by the app-side Canvas renderer, when this is a formula item. */
    val formulaSource: String? = null,
    /** Commit/display fallback kept so a renderer failure never loses the candidate text. */
    val formulaFallbackText: String? = null,
)
