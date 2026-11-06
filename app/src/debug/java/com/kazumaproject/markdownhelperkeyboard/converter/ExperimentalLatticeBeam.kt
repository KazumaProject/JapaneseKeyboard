package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.graph.Node
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/** Searches partial paths in the dictionary lattice using scores for their complete text prefixes. */
internal class ExperimentalLatticeBeam(
    private val beamWidth: Int,
    private val modelWeight: Int,
    private val maxScoredPaths: Int = beamWidth * 2,
    private val scoreAt: (Int, Int) -> Boolean = { _, _ -> true },
    private val scorePrefixes: suspend (Array<String>) -> FloatArray,
) {
    init {
        require(beamWidth in 1..40 && modelWeight >= 0 && maxScoredPaths in 1..beamWidth * 2)
    }

    private data class Path(
        val node: Node,
        val text: String,
        val dictionaryCost: Int,
        val priority: Int,
        val modelCost: Int,
    )

    suspend fun search(
        graph: Map<Int, List<Node>>,
        connection: ConnectionMatrix.CostTable,
        readingLength: Int,
        count: Int,
        allowedEdge: (Node, Node) -> Boolean = { _, _ -> true },
    ): List<String> {
        val bos = graph[0]?.firstOrNull() ?: return emptyList()
        val eos = graph[readingLength + 1]?.firstOrNull() ?: return emptyList()
        val frontiers = Array(readingLength + 1) { emptyList<Path>() }
        frontiers[0] = listOf(Path(bos, "", 0, 0, 0))
        val prefixScores = HashMap<String, Float>()
        val coroutine = currentCoroutineContext()

        for (end in 1..readingLength) {
            coroutine.ensureActive()
            val expanded = ArrayList<Path>()
            for (node in graph[end].orEmpty()) {
                if (node.sPos !in 0 until end || node.sPos + node.len != end) continue
                for (previous in frontiers[node.sPos]) {
                    if (!allowedEdge(previous.node, node)) continue
                    val transition = connection.cost(previous.node.r.toInt(), node.l.toInt()) + node.adjustedScore
                    val dictionaryCost = previous.dictionaryCost + transition
                    val eosCost = if (end == readingLength) {
                        if (!allowedEdge(node, eos)) continue
                        connection.cost(node.r.toInt(), eos.l.toInt())
                    } else {
                        0
                    }
                    expanded.add(
                        Path(
                            node, previous.text + node.tango, dictionaryCost,
                            previous.priority + transition + eosCost, previous.modelCost,
                        )
                    )
                }
            }
            val shortlist = expanded.sortedBy(Path::priority)
                .distinctBy { it.text to it.node.r }
                .take(beamWidth * 2)
            if (shortlist.isEmpty()) continue
            val requested = if (modelWeight > 0 && scoreAt(end, readingLength)) {
                shortlist.asSequence().map(Path::text).distinct().take(maxScoredPaths).toSet()
            } else {
                emptySet()
            }
            val unseen = requested.filterNot(prefixScores::containsKey)
            if (unseen.isNotEmpty()) {
                val scores = scorePrefixes(unseen.toTypedArray())
                check(scores.size == unseen.size)
                unseen.forEachIndexed { index, text -> prefixScores[text] = scores[index] }
            }
            val center = if (requested.size < shortlist.size) {
                requested.mapNotNull { prefixScores[it]?.takeIf(Float::isFinite) }.average().takeIf(Double::isFinite)
            } else {
                null
            }
            val ranked = shortlist.mapNotNull { path ->
                val score = if (path.text in requested) prefixScores.getValue(path.text) else null
                if (score != null && !score.isFinite()) return@mapNotNull null
                val modelCost = if (score == null) path.modelCost else {
                    (-modelWeight * (score - (center ?: 0.0))).roundToInt()
                }
                val finalConnection = if (end == readingLength) {
                    connection.cost(path.node.r.toInt(), eos.l.toInt())
                } else {
                    0
                }
                path.copy(priority = path.dictionaryCost + finalConnection + modelCost, modelCost = modelCost)
            }
            frontiers[end] = ranked.sortedBy(Path::priority).take(beamWidth)
        }
        return frontiers[readingLength].asSequence().map(Path::text).distinct().take(count).toList()
    }
}
