package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import org.junit.Assert.assertEquals
import org.junit.Test

class EquivalentPathPruningTest {

    @Test
    fun equivalentPathPruning_preservesCandidatesAndOrdering() {
        val unpruned = FindPath(equivalentPathPruningEnabled = false)
            .backwardAStarWithBunsetsu(
                graph = graphWithDominatedEquivalentPath(),
                length = 4,
                connectionIds = ShortArray(1),
                connectionMatrixSize = 1,
                n = 2,
            )
        val pruned = FindPath(equivalentPathPruningEnabled = true)
            .backwardAStarWithBunsetsu(
                graph = graphWithDominatedEquivalentPath(),
                length = 4,
                connectionIds = ShortArray(1),
                connectionMatrixSize = 1,
                n = 2,
            )

        assertEquals(unpruned, pruned)
        assertEquals(listOf("pxab", "pxac"), pruned.candidates.map { it.string })
    }

    private fun graphWithDominatedEquivalentPath(): MutableMap<Int, MutableList<Node>> =
        mutableMapOf(
            0 to mutableListOf(node("BOS", length = 0, start = 0)),
            1 to mutableListOf(node("p", length = 1, start = 0)),
            2 to mutableListOf(node("x", length = 1, start = 1)),
            3 to mutableListOf(node("a", length = 1, start = 2)),
            4 to mutableListOf(
                node("b", length = 1, start = 3),
                node("ab", length = 2, start = 2, score = 100),
                node("ac", length = 2, start = 2, score = 200),
            ),
            5 to mutableListOf(node("EOS", length = 0, start = 5)),
        )

    private fun node(
        text: String,
        length: Short,
        start: Int,
        score: Int = 0,
    ): Node =
        Node(
            l = 0,
            r = 0,
            score = score,
            f = score,
            g = score,
            tango = text,
            len = length,
            yomiUsed = text,
            sPos = start,
        )
}
