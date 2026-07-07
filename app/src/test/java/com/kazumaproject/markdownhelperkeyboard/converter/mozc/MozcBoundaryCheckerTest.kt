package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import org.junit.Assert.assertEquals
import org.junit.Test

class MozcBoundaryCheckerTest {

    @Test
    fun strictRequiresBoundaryToMatchEdge() {
        val checker = MozcBoundaryChecker(boundarySegmenter(boundary = true), MozcBoundaryMode.STRICT)

        assertEquals(MozcBoundaryCheckResult.VALID, checker.check(node(), node(), isEdge = true))
        assertEquals(MozcBoundaryCheckResult.INVALID, checker.check(node(), node(), isEdge = false))
    }

    @Test
    fun onlyMidAllowsWeakConnectedEdge() {
        val checker = MozcBoundaryChecker(boundarySegmenter(boundary = false), MozcBoundaryMode.ONLY_MID)

        assertEquals(
            MozcBoundaryCheckResult.VALID_WEAK_CONNECTED,
            checker.check(node(), node(), isEdge = true),
        )
        assertEquals(MozcBoundaryCheckResult.VALID, checker.check(node(), node(), isEdge = false))
    }

    @Test
    fun onlyEdgeUsesSingleSegmentBoundarySemantics() {
        val checker = MozcBoundaryChecker(boundarySegmenter(boundary = true), MozcBoundaryMode.ONLY_EDGE)

        assertEquals(MozcBoundaryCheckResult.INVALID, checker.check(node(), node(), isEdge = true))
        assertEquals(MozcBoundaryCheckResult.VALID, checker.check(node(), node(), isEdge = false))
    }

    @Test
    fun conNodeAlwaysValidAndAlphabetAdjacencyInvalid() {
        val checker = MozcBoundaryChecker(boundarySegmenter(boundary = true), MozcBoundaryMode.STRICT)

        assertEquals(
            MozcBoundaryCheckResult.VALID,
            checker.check(node(type = MozcNodeType.CON), node(), isEdge = false),
        )
        assertEquals(
            MozcBoundaryCheckResult.INVALID,
            checker.check(node(yomi = "a"), node(yomi = "b"), isEdge = true),
        )
    }

    private fun boundarySegmenter(boundary: Boolean): MozcSegmenter {
        val bit = if (boundary) 0b00001000.toByte() else 0
        return MozcSegmenter(
            MozcSegmenterData(
                lNumElements = 2,
                rNumElements = 2,
                lTable = intArrayOf(0, 1),
                rTable = intArrayOf(0, 1),
                bitArrayData = byteArrayOf(bit),
                boundaryData = intArrayOf(0, 0, 0, 0),
            ),
        )
    }

    private fun node(
        yomi: String = "あ",
        type: MozcNodeType = MozcNodeType.NOR,
    ): Node =
        Node(
            l = 1.toShort(),
            r = 1.toShort(),
            score = 0,
            f = 0,
            tango = yomi,
            len = yomi.length.toShort(),
            yomiUsed = yomi,
            sPos = 0,
            mozcNodeType = type,
        )
}
