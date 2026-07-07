package com.kazumaproject.markdownhelperkeyboard.converter.trace

data class ConversionTrace(
    val input: String,
    val graphNodes: List<GraphNodeTrace>,
    val penaltyEvents: List<PenaltyTrace>,
    val forwardDpEvents: List<ForwardDpTrace>,
    val boundaryEvents: List<BoundaryTrace>,
    val finalCandidates: List<CandidateTrace>,
)
