package com.kazumaproject.markdownhelperkeyboard.converter.graph

data class TypoCorrectionResult(
    val yomi: String,
    val penaltyUsed: Int,
    /** LOUDS bit position reached by this result. Avoids traversing [yomi] a second time. */
    val nodeIndex: Int = -1,
    val consumedLength: Int = yomi.length,
    val edits: List<FlickCorrectionEdit> = emptyList(),
    val modifierOmissionOccurred: Boolean = false,
    val costUnits: Int = penaltyUsed * 1000,
)
