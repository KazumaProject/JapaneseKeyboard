package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node

/** A fresh lattice: no node can consume reading from both sides of a requested boundary. */
internal fun graphWithForcedBoundaries(
    input: String,
    splitPositions: List<Int>,
    source: Map<Int, List<Node>>,
): MutableMap<Int, MutableList<Node>> {
    require(splitPositions == splitPositions.distinct().sorted())
    require(splitPositions.all { it in 1 until input.length })
    val graphemes = android.icu.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT).apply { setText(input) }
    require(splitPositions.all(graphemes::isBoundary))
    if (source.isEmpty()) return mutableMapOf()
    val result = linkedMapOf<Int, MutableList<Node>>()
    source.forEach { (end, nodes) ->
        result[end] = nodes.filterTo(mutableListOf()) { node ->
            node.tango == "BOS" || node.tango == "EOS" ||
                splitPositions.none { boundary -> node.sPos < boundary && boundary < end }
        }
    }
    // Dictionary nodes crossing a requested boundary may have suppressed the graph builder's
    // unknown nodes. Provide a costly literal for each complete range so a path always exists.
    (listOf(0) + splitPositions + input.length).zipWithNext().forEach { (start, end) ->
        val reading = input.substring(start, end)
        val cost = 30000
        result.getOrPut(end) { mutableListOf() }.add(
            Node(l = 0, r = 0, score = cost, f = cost, g = cost, tango = reading,
                len = reading.length.toShort(), yomiUsed = reading, sPos = start,
                mozcNodeType = MozcNodeType.CON, candidateSource = CandidateSource.UNKNOWN)
        )
    }
    // Removing crossing nodes can leave dictionary nodes with no path from BOS. Drop those
    // before forward DP so Int.MAX_VALUE cannot overflow into an apparently cheap route.
    for (end in 1..input.length) {
        result[end]?.removeAll { node -> node.sPos > 0 && result[node.sPos].isNullOrEmpty() }
    }
    return result
}
