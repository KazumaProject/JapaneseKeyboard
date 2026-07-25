package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.graph.IncrementalGraphMetadata
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenterData
import com.kazumaproject.markdownhelperkeyboard.converter.trace.PenaltyTrace
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.LinkedHashMap

class MozcPrefixSuffixPenaltyTest {

    @Test
    fun zeroesOnlyPrefixPenaltyForWhitelistedExactInput() {
        val graph = singleNodeGraph(tango = "にて")
        val target = graph.getValue(2).single()
        val traces = mutableListOf<PenaltyTrace>()

        FindPath(mozcSegmenterProvider = { segmenter() }).backwardAStarWithBunsetsu(
            graph = graph,
            length = 2,
            connectionIds = ShortArray(9),
            connectionMatrixSize = 3,
            n = 5,
            penaltyTrace = traces,
        )

        assertEquals(111, target.adjustedScore)
        assertEquals(0, traces.single { it.tango == "にて" }.prefixPenalty)
        assertEquals(11, traces.single { it.tango == "にて" }.suffixPenalty)
    }

    @Test
    fun keepsPrefixPenaltyWhenExactInputGuardsDoNotMatch() {
        val differentSurfaceGraph = singleNodeGraph(tango = "二手", yomiUsed = "にて")
        val unlistedWordGraph = singleNodeGraph(tango = "まで")
        val partialInputGraph = longerInputGraph(node("にて", l = 1, r = 2, score = 100, len = 2, sPos = 0))
        val findPath = FindPath(mozcSegmenterProvider = { segmenter() })

        findPath.backwardAStarWithBunsetsu(
            graph = differentSurfaceGraph,
            length = 2,
            connectionIds = ShortArray(9),
            connectionMatrixSize = 3,
            n = 5,
        )
        findPath.backwardAStarWithBunsetsu(
            graph = unlistedWordGraph,
            length = 2,
            connectionIds = ShortArray(9),
            connectionMatrixSize = 3,
            n = 5,
        )
        findPath.backwardAStarWithBunsetsu(
            graph = partialInputGraph,
            length = 3,
            connectionIds = ShortArray(9),
            connectionMatrixSize = 3,
            n = 5,
        )

        assertEquals(118, differentSurfaceGraph.getValue(2).single().adjustedScore)
        assertEquals(118, unlistedWordGraph.getValue(2).single().adjustedScore)
        assertEquals(107, partialInputGraph.getValue(2).single().adjustedScore)
    }

    @Test
    fun restoresPrefixPenaltyWhenWhitelistedWordStopsBeingExactAfterAppend() {
        val target = node("にて", l = 1, r = 2, score = 100, len = 2, sPos = 0)
        val initialGraph = IncrementalTestGraph(
            reusedThroughEndIndex = -1,
            conversionSignature = 42,
        ).apply {
            putAll(singleNodeGraph(target))
        }
        val findPath = FindPath(mozcSegmenterProvider = { segmenter() })
        val sessionState = findPath.createSessionState()

        findPath.backwardAStarWithBunsetsu(
            graph = initialGraph,
            length = 2,
            connectionIds = ShortArray(9),
            connectionMatrixSize = 3,
            n = 5,
            sessionState = sessionState,
        )
        assertEquals(111, target.adjustedScore)

        val appendedGraph = IncrementalTestGraph(
            reusedThroughEndIndex = 2,
            conversionSignature = 42,
        ).apply {
            putAll(longerInputGraph(target))
        }
        findPath.backwardAStarWithBunsetsu(
            graph = appendedGraph,
            length = 3,
            connectionIds = ShortArray(9),
            connectionMatrixSize = 3,
            n = 5,
            sessionState = sessionState,
        )

        assertEquals(107, target.adjustedScore)
    }

    @Test
    fun appliesPrefixAndSuffixOnlyToEligibleNormalNodesAndDoesNotDoubleAdd() {
        val graph = twoNodeGraph()
        val first = graph.getValue(1).single()
        val second = graph.getValue(2).single()
        val traces = mutableListOf<PenaltyTrace>()
        val findPath = FindPath(mozcSegmenterProvider = { segmenter() })

        findPath.backwardAStarWithBunsetsu(
            graph = graph,
            length = 2,
            connectionIds = ShortArray(9),
            connectionMatrixSize = 3,
            n = 5,
            penaltyTrace = traces,
        )

        assertEquals(107, first.adjustedScore)
        assertEquals(211, second.adjustedScore)
        assertEquals(2, traces.size)
        assertEquals(listOf("前", "後"), traces.map { it.tango })

        findPath.backwardAStarWithBunsetsu(
            graph = graph,
            length = 2,
            connectionIds = ShortArray(9),
            connectionMatrixSize = 3,
            n = 5,
            penaltyTrace = mutableListOf(),
        )

        assertEquals(107, first.adjustedScore)
        assertEquals(211, second.adjustedScore)
    }

    @Test
    fun leavesScoresUnchangedWhenMozcSegmenterIsDisabled() {
        val graph = twoNodeGraph()

        FindPath().backwardAStarWithBunsetsu(
            graph = graph,
            length = 2,
            connectionIds = ShortArray(9),
            connectionMatrixSize = 3,
            n = 5,
        )

        assertEquals(100, graph.getValue(1).single().adjustedScore)
        assertEquals(200, graph.getValue(2).single().adjustedScore)
        assertEquals(0, graph.getValue(0).single().adjustedScore)
        assertEquals(0, graph.getValue(3).single().adjustedScore)
    }

    private fun segmenter(): MozcSegmenter =
        MozcSegmenter(
            MozcSegmenterData(
                lNumElements = 3,
                rNumElements = 3,
                lTable = intArrayOf(0, 1, 2),
                rTable = intArrayOf(0, 1, 2),
                bitArrayData = byteArrayOf(0),
                boundaryData = intArrayOf(0, 0, 7, 0, 0, 11),
            ),
        )

    private fun twoNodeGraph(): MutableMap<Int, MutableList<Node>> =
        mutableMapOf(
            0 to mutableListOf(
                node("BOS", type = MozcNodeType.BOS, l = 0.toShort(), r = 0.toShort(), len = 0.toShort(), sPos = 0),
            ),
            1 to mutableListOf(
                node("前", l = 1.toShort(), r = 1.toShort(), score = 100, len = 1.toShort(), sPos = 0),
            ),
            2 to mutableListOf(
                node("後", l = 2.toShort(), r = 2.toShort(), score = 200, len = 1.toShort(), sPos = 1),
            ),
            3 to mutableListOf(
                node("EOS", type = MozcNodeType.EOS, l = 0.toShort(), r = 0.toShort(), len = 0.toShort(), sPos = 3),
            ),
        )

    private fun singleNodeGraph(
        tango: String,
        yomiUsed: String = tango,
    ): MutableMap<Int, MutableList<Node>> =
        singleNodeGraph(node(tango, l = 1, r = 2, score = 100, len = 2, sPos = 0, yomiUsed = yomiUsed))

    private fun singleNodeGraph(target: Node): MutableMap<Int, MutableList<Node>> =
        mutableMapOf(
            0 to mutableListOf(
                node("BOS", type = MozcNodeType.BOS, l = 0, r = 0, len = 0, sPos = 0),
            ),
            2 to mutableListOf(target),
            3 to mutableListOf(
                node("EOS", type = MozcNodeType.EOS, l = 0, r = 0, len = 0, sPos = 3),
            ),
        )

    private fun longerInputGraph(target: Node): MutableMap<Int, MutableList<Node>> =
        mutableMapOf(
            0 to mutableListOf(
                node("BOS", type = MozcNodeType.BOS, l = 0, r = 0, len = 0, sPos = 0),
            ),
            2 to mutableListOf(target),
            3 to mutableListOf(
                node("後", l = 1, r = 1, score = 200, len = 1, sPos = 2),
            ),
            4 to mutableListOf(
                node("EOS", type = MozcNodeType.EOS, l = 0, r = 0, len = 0, sPos = 4),
            ),
        )

    private fun node(
        tango: String,
        l: Short,
        r: Short,
        score: Int = 0,
        len: Short,
        sPos: Int,
        type: MozcNodeType = MozcNodeType.NOR,
        yomiUsed: String = tango,
    ): Node =
        Node(
            l = l,
            r = r,
            score = score,
            f = score,
            tango = tango,
            len = len,
            yomiUsed = yomiUsed,
            sPos = sPos,
            mozcNodeType = type,
        )

    private class IncrementalTestGraph(
        override val reusedThroughEndIndex: Int,
        override val conversionSignature: Int,
    ) : LinkedHashMap<Int, MutableList<Node>>(), IncrementalGraphMetadata
}
