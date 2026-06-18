package com.kazumaproject.markdownhelperkeyboard.converter.mozc

class MozcConverterTrace {
    var dictionaryNodeCount: Int = 0
    var omissionLookupCount: Int = 0
    var omissionNodeCount: Int = 0
    var unknownNodeCount: Int = 0
    var prefixPenaltyAppliedCount: Int = 0
    var suffixPenaltyAppliedCount: Int = 0
    var boundaryCheckCount: Int = 0
    var resegmentInsertedNodeCount: Int = 0
    var constrainedViterbiCount: Int = 0
    var nBestExpandedCount: Int = 0
    val unsupportedFilters: MutableList<String> = mutableListOf()
}
