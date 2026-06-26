package com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter

import com.kazumaproject.markdownhelperkeyboard.converter.mozc.lattice.MozcLattice
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcNode
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcNodeType
import java.util.PriorityQueue

class MozcKotlinNBestGenerator(
    private val connector: MozcKotlinConnector,
) {
    fun generate(
        lattice: MozcLattice,
        maxCandidates: Int,
    ): List<MozcCandidate> {
        if (maxCandidates <= 0) return emptyList()

        val results = mutableListOf<MozcCandidate>()
        val queue = PriorityQueue<PathState>(compareBy<PathState> { it.cost }.thenBy { it.value })
        queue += PathState(
            pos = 0,
            lastNode = lattice.bosNode,
            nodes = emptyList(),
            cost = 0,
            value = "",
        )

        val inputLength = lattice.key.length
        while (queue.isNotEmpty() && results.size < maxCandidates) {
            val state = queue.poll() ?: break
            if (state.pos == inputLength) {
                if (state.nodes.isNotEmpty() && state.value.isNotBlank()) {
                    val eosCost = connector.getTransitionCost(state.lastNode.rid, lattice.eosNode.lid)
                    results += state.toCandidate(lattice.key, state.cost + eosCost)
                }
                continue
            }

            for (node in lattice.beginNodes(state.pos)) {
                if (node.nodeType != MozcNodeType.NOR_NODE || node.endPos <= state.pos) continue
                val transitionCost = connector.getTransitionCost(state.lastNode.rid, node.lid)
                queue += PathState(
                    pos = node.endPos,
                    lastNode = node,
                    nodes = state.nodes + node,
                    cost = state.cost + transitionCost + node.wcost,
                    value = state.value + node.value,
                )
            }
        }
        return results
    }

    private data class PathState(
        val pos: Int,
        val lastNode: MozcNode,
        val nodes: List<MozcNode>,
        val cost: Int,
        val value: String,
    ) {
        fun toCandidate(input: String, totalCost: Int): MozcCandidate {
            val wcost = nodes.sumOf { it.wcost }
            val structureCost = totalCost - wcost
            return MozcCandidate(
                key = input,
                value = value,
                contentKey = input,
                contentValue = value,
                consumedKeySize = input.length,
                cost = totalCost,
                wcost = wcost,
                structureCost = structureCost,
                lid = nodes.first().lid,
                rid = nodes.last().rid,
            )
        }
    }
}
