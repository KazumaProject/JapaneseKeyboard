package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * Exact input/output correspondence for one node on a conversion path.
 *
 * Input offsets are UTF-16 indices into the original reading. Keeping offsets instead of slicing
 * the completed candidate is required because readings and converted strings can have different
 * lengths (for example, "きょう" and "今日").
 */
data class CandidateConversionSegment(
    val inputStart: Int,
    val inputEnd: Int,
    val output: String,
)

