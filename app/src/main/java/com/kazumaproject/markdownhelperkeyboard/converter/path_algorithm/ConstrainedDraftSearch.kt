package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS

internal data class DraftConstraint(val text: String, val exact: Boolean) {
    fun matches(surface: String): Boolean {
        val normalized = normalizeDraftSurface(surface)
        return if (exact) normalized == text else normalized.startsWith(text)
    }

    fun advance(matched: Int, surface: String): Int {
        val remaining = text.length - matched
        val compared = minOf(remaining, surface.length)
        if ((exact && surface.length > remaining) ||
            !surface.regionMatches(0, text, matched, compared)
        ) {
            return -1
        }
        return matched + compared
    }
}

// The native evaluator changes ASCII spaces to ideographic spaces in candidate tokens.
private fun normalizeDraftSurface(surface: String): String = surface.replace(' ', '\u3000')

/** Searches the unpruned dictionary lattice using the same word and connection costs as forward DP. */
internal class ConstrainedDraftSearch(
    private val graph: Map<Int, List<Node>>,
    private val length: Int,
    private val connectionMatrix: ConnectionMatrix.CostTable,
) {
    private class Path(
        val surface: String,
        val rightId: Int,
        val matched: Int,
        val cost: Long,
        val previous: Path?,
    )

    fun find(constraint: DraftConstraint, cancellationCheck: () -> Unit): String? {
        cancellationCheck()
        if (constraint.text.isEmpty()) return null
        val eos = graph[length + 1]?.firstOrNull { it.mozcNodeType == MozcNodeType.EOS } ?: return null
        val pathsByEnd = Array(length + 1) { mutableListOf<Path>() }
        pathsByEnd[0].add(Path("", BOS.r.toInt(), 0, 0, null))
        for (end in 1..length) {
            cancellationCheck()
            for (node in graph[end].orEmpty()) {
                cancellationCheck()
                val previousPaths = pathsByEnd[node.sPos]
                if (previousPaths.isEmpty()) continue
                val surface = normalizeDraftSurface(node.tango)
                val pathsByMatch = mutableMapOf<Int, Path>()
                for (previous in previousPaths) {
                    val matched = constraint.advance(previous.matched, surface)
                    if (matched < 0) continue
                    val cost = previous.cost + connectionMatrix.cost(previous.rightId, node.l.toInt()) +
                        node.adjustedScore
                    val current = pathsByMatch[matched]
                    if (current == null || cost < current.cost) {
                        pathsByMatch[matched] = Path(node.tango, node.r.toInt(), matched, cost, previous)
                    }
                }
                pathsByEnd[end].addAll(pathsByMatch.values)
            }
        }
        cancellationCheck()
        val best = pathsByEnd[length].asSequence()
            .filter { it.matched == constraint.text.length }
            .minByOrNull { it.cost + connectionMatrix.cost(it.rightId, eos.l.toInt()) + eos.adjustedScore }
            ?: return null
        return generateSequence(best) { it.previous }.toList().asReversed().joinToString("") { it.surface }
    }
}
