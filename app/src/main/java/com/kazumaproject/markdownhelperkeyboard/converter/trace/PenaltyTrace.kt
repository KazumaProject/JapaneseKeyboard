package com.kazumaproject.markdownhelperkeyboard.converter.trace

data class PenaltyTrace(
    val tango: String,
    val yomiUsed: String,
    val startPos: Int,
    val endPos: Int,
    val leftId: Int,
    val rightId: Int,
    val baseCost: Int,
    val prefixPenalty: Int,
    val suffixPenalty: Int,
    val adjustedCost: Int,
)
