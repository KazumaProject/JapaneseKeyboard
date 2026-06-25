package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.JapaneseCandidateDedupeMode

data class JapaneseSearchOptions(
    val beamWidth: Int = 20,
    val nbestSize: Int? = null,
    val nbestCostDiff: Int? = null,
    val qualityMode: Boolean = false,
)

data class JapaneseBunsetsuOptions(
    val searchOptions: JapaneseSearchOptions = JapaneseSearchOptions(),
    val candidateDedupeMode: JapaneseCandidateDedupeMode = JapaneseCandidateDedupeMode.LEGACY_VALUE,
    val includeDedupeTrace: Boolean = true,
)

data class PathElement(
    val node: Node,
    val next: PathElement?,
    val gx: Int,
    val fx: Int,
    val wordCost: Int,
    val structureCost: Int,
    val splitPositions: IntArray,
    val sequence: Long = 0L,
) {
    fun splitPositionList(): List<Int> = splitPositions.toList()
}
