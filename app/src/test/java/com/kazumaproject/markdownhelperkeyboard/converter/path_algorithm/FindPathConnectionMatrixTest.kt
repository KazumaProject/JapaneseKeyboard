package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import org.junit.Assert.assertEquals
import org.junit.Test

class FindPathConnectionMatrixTest {

    @Test
    fun backwardAStar_usesPassed2672ConnectionMatrixWidth() {
        val connectionIds = ShortArray(2672 * 2672)
        connectionIds[1 * 2672 + 0] = (-123).toShort()
        connectionIds[1 * 2670 + 0] = 456.toShort()
        connectionIds[0 * 2672 + 1] = 1000.toShort()
        connectionIds[2671 * 2672 + 0] = 456.toShort()

        val candidates = FindPath().backwardAStar(
            graph = singleWordGraph(leftId = 2671.toShort(), rightId = 1.toShort()),
            length = 1,
            connectionIds = connectionIds,
            connectionMatrixSize = 2672,
            n = 1,
        )

        assertEquals(-123, candidates.single().score)
    }

    @Test
    fun backwardAStar_usesBuiltIn2670ConnectionMatrixWidth() {
        val matrixSize = 2670
        val connectionIds = ShortArray(matrixSize * matrixSize)
        connectionIds[1 * matrixSize + 0] = (-70).toShort()
        connectionIds[0 * matrixSize + 1] = 500.toShort()
        connectionIds[2668 * matrixSize + 0] = 999.toShort()

        val candidates = FindPath().backwardAStar(
            graph = singleWordGraph(leftId = 2668.toShort(), rightId = 1.toShort()),
            length = 1,
            connectionIds = connectionIds,
            connectionMatrixSize = matrixSize,
            n = 1,
        )

        assertEquals(-70, candidates.single().score)
    }

    private fun singleWordGraph(
        leftId: Short = 1,
        rightId: Short = 1,
    ): MutableMap<Int, MutableList<Node>> =
        mutableMapOf(
            0 to mutableListOf(createNode(tango = "BOS", l = 0, r = 0, len = 0, sPos = 0)),
            1 to mutableListOf(createNode(tango = "語", l = leftId, r = rightId, len = 1, sPos = 0)),
            2 to mutableListOf(createNode(tango = "EOS", l = 0, r = 0, len = 0, sPos = 2)),
        )

    private fun createNode(
        tango: String,
        l: Short,
        r: Short,
        len: Short,
        sPos: Int,
    ): Node =
        Node(
            l = l,
            r = r,
            score = 0,
            f = 0,
            g = 0,
            tango = tango,
            len = len,
            yomiUsed = tango,
            sPos = sPos,
            prev = null,
            next = null,
        )
}
