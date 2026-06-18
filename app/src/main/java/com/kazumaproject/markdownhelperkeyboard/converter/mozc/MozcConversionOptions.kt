package com.kazumaproject.markdownhelperkeyboard.converter.mozc

data class MozcConversionOptions(
    val nBest: Int = 4,
    val disablePrefixPenalty: Boolean = false,
    val isSingleSegment: Boolean = false,
    val enableTrace: Boolean = false,
)
