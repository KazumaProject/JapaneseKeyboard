package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquivalentPathPruningTest {

    @Test
    fun equivalentPathPruning_preservesCandidatesAndOrdering() {
        val result = FindPath().backwardAStarWithBunsetsu(
            graph = graphWithDominatedEquivalentPath(),
            length = 4,
            connectionIds = ShortArray(1),
            connectionMatrixSize = 1,
            n = 2,
        )

        assertEquals(listOf("期間は一日", "期間は二日"), result.candidates.map { it.string })
        assertEquals(listOf(0, 200), result.candidates.map { it.score })
        assertEquals(listOf("期間は一日", "期間は二日"), result.candidates.map { it.yomi })
        assertEquals(listOf(emptyList<Int>()), result.splitPatterns)
        assertEquals(
            mapOf("期間は一日" to emptyList<Int>(), "期間は二日" to emptyList()),
            result.splitPatternByCandidateString,
        )
    }

    private fun graphWithDominatedEquivalentPath(): MutableMap<Int, MutableList<Node>> =
        mutableMapOf(
            0 to mutableListOf(node("BOS", length = 0, start = 0)),
            1 to mutableListOf(node("期間", length = 1, start = 0)),
            2 to mutableListOf(node("は", length = 1, start = 1)),
            3 to mutableListOf(node("一", length = 1, start = 2)),
            4 to mutableListOf(
                node("日", length = 1, start = 3),
                node("一日", length = 2, start = 2, score = 100),
                node("二日", length = 2, start = 2, score = 200),
            ),
            5 to mutableListOf(node("EOS", length = 0, start = 5)),
        )

    @Test
    fun equivalentPathPruning_preservesCandidatesWhenSameSurfaceHasDifferentYomi() {
        val result = FindPath().backwardAStarWithBunsetsu(
            graph = graphWithSameSurfaceDifferentYomi(),
            length = 4,
            connectionIds = ShortArray(1),
            connectionMatrixSize = 1,
            n = 2,
        )

        assertEquals(listOf("期間は一日", "期間は二日"), result.candidates.map { it.string })
        assertEquals(listOf(0, 200), result.candidates.map { it.score })
        assertEquals(listOf("きかんはいちにち", "きかんはふつか"), result.candidates.map { it.yomi })
        assertEquals(listOf(emptyList<Int>()), result.splitPatterns)
        assertEquals(
            mapOf("期間は一日" to emptyList<Int>(), "期間は二日" to emptyList()),
            result.splitPatternByCandidateString,
        )
    }

    @Test
    fun equivalentPathPruning_reducesEvaluatedTransitions() {
        val unprunedConnectionMatrix = CountingCostTable()
        FindPath().backwardAStarWithBunsetsu(
            graph = graphWithDominatedEquivalentPath(),
            length = 4,
            connectionMatrix = unprunedConnectionMatrix,
            n = 2,
            candidateTrace = mutableListOf(),
        )
        val prunedConnectionMatrix = CountingCostTable()
        FindPath().backwardAStarWithBunsetsu(
            graph = graphWithDominatedEquivalentPath(),
            length = 4,
            connectionMatrix = prunedConnectionMatrix,
            n = 2,
        )

        assertTrue(
            "Expected pruning to reduce evaluated transitions: " +
                "${prunedConnectionMatrix.callCount} >= ${unprunedConnectionMatrix.callCount}",
            prunedConnectionMatrix.callCount < unprunedConnectionMatrix.callCount,
        )
    }

    private fun graphWithSameSurfaceDifferentYomi(): MutableMap<Int, MutableList<Node>> =
        mutableMapOf(
            0 to mutableListOf(node("BOS", length = 0, start = 0)),
            1 to mutableListOf(node("期間", length = 1, start = 0, yomiUsed = "きかん")),
            2 to mutableListOf(node("は", length = 1, start = 1, yomiUsed = "は")),
            3 to mutableListOf(node("一", length = 1, start = 2, yomiUsed = "いち")),
            4 to mutableListOf(
                node("日", length = 1, start = 3, yomiUsed = "にち"),
                node("一日", length = 2, start = 2, score = 100, yomiUsed = "ついたち"),
                node("二日", length = 2, start = 2, score = 200, yomiUsed = "ふつか"),
            ),
            5 to mutableListOf(node("EOS", length = 0, start = 5)),
        )

    private fun node(
        text: String,
        length: Short,
        start: Int,
        score: Int = 0,
        yomiUsed: String = text,
    ): Node =
        Node(
            l = 0,
            r = 0,
            score = score,
            f = score,
            g = score,
            tango = text,
            len = length,
            yomiUsed = yomiUsed,
            sPos = start,
        )

    private class CountingCostTable : ConnectionMatrix.CostTable {
        override val matrixSize: Int = 1
        override val entryCount: Int = 1
        var callCount: Int = 0
            private set

        override fun cost(rid: Int, lid: Int): Int {
            callCount++
            return 0
        }
    }
}
