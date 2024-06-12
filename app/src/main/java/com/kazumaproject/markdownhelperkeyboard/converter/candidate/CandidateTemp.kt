package com.kazumaproject.markdownhelperkeyboard.converter.candidate

data class CandidateTemp(
    val string: String,
    val wordCost: Int,
    val leftId: Short? = null,
    val rightId: Short? = null,
)
