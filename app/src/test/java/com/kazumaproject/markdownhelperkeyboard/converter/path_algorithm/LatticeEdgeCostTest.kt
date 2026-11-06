package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import org.junit.Assert.assertEquals
import org.junit.Test

class LatticeEdgeCostTest {
    @Test
    fun transitionCostKeepsTheModelPreferredNodeBeforePruning() {
        val path = FindPath()
        val connection = ConnectionMatrix.fromShortArray(ShortArray(4), 2)
        val model = LatticeEdgeCost { previous, current ->
            if (previous.tango == "BOS" && current.tango == "再審") -100 else 0
        }
        val baseline = path.backwardAStar(graph(), 1, connection, n = 1, beamWidth = 1).single()
        assertEquals("最新", baseline.string)

        val scoredGraph = graph()
        val scored = path.backwardAStar(
            scoredGraph, 1, connection, n = 1, beamWidth = 1, latticeEdgeCost = model,
        ).single()
        assertEquals("再審", scoredGraph.getValue(1).single().tango)
        assertEquals("再審", scored.string)
        assertEquals(-50, scored.score)
    }

    @Test
    fun bunsetsuSearchUsesTheSameTransitionCost() {
        val path = FindPath()
        val connection = ConnectionMatrix.fromShortArray(ShortArray(4), 2)
        val model = LatticeEdgeCost { _, current -> if (current.tango == "再審") -100 else 0 }
        val scored = path.backwardAStarWithBunsetsu(
            graph(), 1, connection, n = 1, beamWidth = 1,
            latticeEdgeCost = model, sessionState = path.createSessionState(),
        ).candidates.single()
        assertEquals("再審", scored.string)
        assertEquals(-50, scored.score)
    }

    private fun graph() = mutableMapOf(
        0 to mutableListOf(node("BOS", 0, 0)),
        1 to mutableListOf(node("最新", 1, 0), node("再審", 1, 0, score = 50)),
        2 to mutableListOf(node("EOS", 0, 2)),
    )

    private fun node(text: String, len: Short, start: Int, score: Int = 0) =
        Node(l = 0, r = 0, score = score, f = score, tango = text, len = len, yomiUsed = "さ", sPos = start)
}
