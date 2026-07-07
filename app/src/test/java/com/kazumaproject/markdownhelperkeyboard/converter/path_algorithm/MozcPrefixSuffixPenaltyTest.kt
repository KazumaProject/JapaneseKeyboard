package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenterData
import com.kazumaproject.markdownhelperkeyboard.converter.trace.PenaltyTrace
import org.junit.Assert.assertEquals
import org.junit.Test

class MozcPrefixSuffixPenaltyTest {

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

    private fun node(
        tango: String,
        l: Short,
        r: Short,
        score: Int = 0,
        len: Short,
        sPos: Int,
        type: MozcNodeType = MozcNodeType.NOR,
    ): Node =
        Node(
            l = l,
            r = r,
            score = score,
            f = score,
            tango = tango,
            len = len,
            yomiUsed = tango,
            sPos = sPos,
            mozcNodeType = type,
        )
}
