package com.kazumaproject.markdownhelperkeyboard.converter.number

enum class NumberCandidateFormat(val preferenceValue: String) {
    HALF_WIDTH("half_width"),
    FULL_WIDTH("full_width"),
    KANJI("kanji"),
}

data class NumberCandidateConfig(
    val enhanceCounterCandidates: Boolean = true,
    val order: List<NumberCandidateFormat> = NumberCandidateFormat.entries.toList(),
) {
    val normalizedOrder: List<NumberCandidateFormat> =
        (order + NumberCandidateFormat.entries).distinct()
}

/** Identity is independent of candidate type, score, and dictionary source. */
data class NumberCandidateVariant(
    val group: String,
    val format: NumberCandidateFormat,
    val style: Int = 0,
)
