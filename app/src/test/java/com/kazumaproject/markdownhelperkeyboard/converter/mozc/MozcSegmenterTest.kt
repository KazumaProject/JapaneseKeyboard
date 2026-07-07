package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.graph.MozcNodeAttributes
import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MozcSegmenterTest {

    @Test
    fun boundaryMatchesMozcOrderingAndBitOrder() {
        val segmenter = MozcSegmenter(
            MozcSegmenterData(
                lNumElements = 2,
                rNumElements = 2,
                lTable = intArrayOf(0, 1),
                rTable = intArrayOf(0, 1),
                bitArrayData = byteArrayOf(0b00001000),
                boundaryData = intArrayOf(10, 20, 30, 40),
            ),
        )
        val normalLeft = node(l = 1.toShort(), r = 1.toShort(), tango = "左")
        val normalRight = node(l = 1.toShort(), r = 1.toShort(), tango = "右")

        assertTrue(segmenter.isBoundary(node(type = MozcNodeType.BOS), normalRight, false))
        assertTrue(segmenter.isBoundary(normalLeft, node(type = MozcNodeType.EOS), false))
        assertFalse(segmenter.isBoundary(normalLeft, normalRight, isSingleSegment = true))
        assertTrue(segmenter.isBoundary(normalLeft, normalRight, isSingleSegment = false))
        assertFalse(
            segmenter.isBoundary(
                normalLeft.copy(mozcAttributes = MozcNodeAttributes.STARTS_WITH_PARTICLE),
                normalRight,
                isSingleSegment = false,
            ),
        )
        assertEquals(30, segmenter.getPrefixPenalty(1))
        assertEquals(40, segmenter.getSuffixPenalty(1))
    }

    private fun node(
        l: Short = 0.toShort(),
        r: Short = 0.toShort(),
        tango: String = "N",
        type: MozcNodeType = MozcNodeType.NOR,
    ): Node =
        Node(
            l = l,
            r = r,
            score = 0,
            f = 0,
            tango = tango,
            len = 1.toShort(),
            yomiUsed = tango,
            sPos = 0,
            mozcNodeType = type,
        )
}
