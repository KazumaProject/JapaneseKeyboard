package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import org.junit.Assert.assertEquals
import org.junit.Test

class FindPathQueueOrderTest {
    private fun element(text: String, next: FindPath.PathQueueElement? = null) = FindPath.PathQueueElement().set(
        Node(l=2043, r=2011, score=0, f=0, tango=text, len=3, yomiUsed="さんぼん", sPos=0),
        priorityCost=1000, backwardCost=1000, next=next, outputPathId=0, sourceMask=0,
    )
    @Test fun equalCostsUseStableTextOrderAcrossFreshNodesAndReverseInsertion() {
        for (texts in listOf(listOf("三本", "3本", "３本"), listOf("３本", "3本", "三本"))) {
            val queue=FindPath.PathPriorityQueue()
            texts.forEach { queue.add(element(it)) }
            assertEquals(listOf("3本", "三本", "３本"), List(3) { queue.poll()!!.node.tango })
        }
    }
    @Test fun identicalCurrentNodesCompareTheirWholeSuffix() {
        val queue=FindPath.PathPriorityQueue()
        queue.add(element("BOS",element("三本")))
        queue.add(element("BOS",element("3本")))
        assertEquals("3本", queue.poll()!!.next!!.node.tango)
        assertEquals("三本", queue.poll()!!.next!!.node.tango)
    }
}
