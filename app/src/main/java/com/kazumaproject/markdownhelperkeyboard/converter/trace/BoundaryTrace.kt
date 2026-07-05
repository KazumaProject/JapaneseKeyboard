package com.kazumaproject.markdownhelperkeyboard.converter.trace

data class BoundaryTrace(
    val leftTango: String,
    val rightTango: String,
    val leftRid: Int,
    val rightLid: Int,
    val isEdge: Boolean,
    val isSingleSegment: Boolean,
    val result: String,
)
