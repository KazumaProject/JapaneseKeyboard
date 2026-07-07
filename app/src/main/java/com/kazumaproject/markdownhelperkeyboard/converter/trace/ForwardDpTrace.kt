package com.kazumaproject.markdownhelperkeyboard.converter.trace

data class ForwardDpTrace(
    val position: Int,
    val nodeCountBeforePruning: Int,
    val nodeCountAfterPruning: Int,
    val keptNodes: List<String>,
    val droppedNodes: List<String>,
)
