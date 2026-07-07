package com.kazumaproject.markdownhelperkeyboard.converter.trace

data class GraphNodeTrace(
    val input: String,
    val yomiUsed: String,
    val tango: String,
    val startPos: Int,
    val endPos: Int,
    val leftId: Int,
    val rightId: Int,
    val wordCost: Int,
    val source: String,
    val event: String,
)
