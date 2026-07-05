package com.kazumaproject.markdownhelperkeyboard.converter.trace

data class CandidateTrace(
    val candidate: String,
    val yomi: String,
    val totalCost: Int,
    val path: List<String>,
)
