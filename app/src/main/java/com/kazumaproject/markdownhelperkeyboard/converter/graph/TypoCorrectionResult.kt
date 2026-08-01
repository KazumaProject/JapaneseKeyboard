package com.kazumaproject.markdownhelperkeyboard.converter.graph

data class TypoCorrectionResult(
    val yomi: String,
    val penaltyUsed: Int,
    /** LOUDS bit position reached by this result. Avoids traversing [yomi] a second time. */
    val nodeIndex: Int = -1,
)
