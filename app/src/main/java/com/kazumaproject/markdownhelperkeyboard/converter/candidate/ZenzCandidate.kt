package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class ZenzCandidate(
    val string: String,
    val type: Byte,
    val length: UByte,
    val score: Int,
    val originalString: String,
    val leftId: Short? = null,
    val rightId: Short? = null
)
