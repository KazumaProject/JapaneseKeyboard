package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import java.util.PriorityQueue

data class MozcQueueElement(
    val node: MozcNode,
    val next: MozcQueueElement?,
    val gx: Int,
    val fx: Int,
    val structureGx: Int,
    val wordGx: Int,
)

data class MozcPath(
    val nodes: List<MozcNode>,
    val cost: Int,
    val structureCost: Int,
    val wordCost: Int,
    val innerBoundaryOffsets: List<Int>,
)

class MozcNBestGenerator(
    private val connector: MozcConnector,
    private val boundaryDetector: MozcBoundaryDetector,
    private val trace: MozcConverterTrace? = null,
) {
    fun generate(
        lattice: MozcLattice,
        options: MozcConversionOptions,
    ): List<MozcPath> {
        val queue = PriorityQueue<MozcQueueElement>(
            compareBy<MozcQueueElement> { it.fx }
                .thenBy { it.node.beginPos }
                .thenBy { it.node.endPos }
                .thenBy { System.identityHashCode(it.node) },
        )
        queue.add(
            MozcQueueElement(
                node = lattice.eosNode,
                next = null,
                gx = 0,
                fx = lattice.eosNode.totalCost,
                structureGx = 0,
                wordGx = 0,
            ),
        )

        val result = mutableListOf<MozcPath>()
        val seenSurface = hashSetOf<String>()
        var trials = 0
        while (queue.isNotEmpty() && result.size < options.nBest && trials < MAX_TRIALS) {
            trials++
            val top = queue.poll() ?: break
            trace?.nBestExpandedCount = (trace?.nBestExpandedCount ?: 0) + 1

            if (top.node.nodeType == MozcNodeType.BOS) {
                val path = pathFromElement(top)
                val surface = path.nodes.joinToString("") { it.value }
                if (surface.isNotEmpty() && seenSurface.add(surface)) {
                    result += path
                }
                continue
            }

            val rightNode = top.node
            for (leftNode in lattice.endNodes(rightNode.beginPos)) {
                if (leftNode.totalCost >= MozcViterbi.VERY_BIG_COST) continue
                if (rightNode.constrainedPrev != null && rightNode.constrainedPrev !== leftNode) {
                    continue
                }
                if (!isValidBoundary(leftNode, rightNode)) continue

                val transitionCost = connector.getTransitionCost(leftNode.rightId, rightNode.leftId)
                val rightWordCost = if (rightNode.nodeType == MozcNodeType.EOS) 0 else rightNode.wordCost
                val structureCost = if (
                    leftNode.nodeType == MozcNodeType.BOS ||
                    rightNode.nodeType == MozcNodeType.EOS
                ) {
                    0
                } else {
                    transitionCost
                }
                val gx = saturatedCost(top.gx, transitionCost, rightWordCost)
                val fx = saturatedCost(leftNode.totalCost, gx, 0)
                val structureGx = saturatedCost(top.structureGx, structureCost, 0)
                val wordGx = saturatedCost(top.wordGx, rightWordCost, 0)
                queue.add(
                    MozcQueueElement(
                        node = leftNode,
                        next = top,
                        gx = gx,
                        fx = fx,
                        structureGx = structureGx,
                        wordGx = wordGx,
                    ),
                )
            }
        }

        return result.sortedBy { it.cost }
    }

    private fun isValidBoundary(leftNode: MozcNode, rightNode: MozcNode): Boolean {
        if (leftNode.nodeType == MozcNodeType.BOS || rightNode.nodeType == MozcNodeType.EOS) {
            boundaryDetector.isBoundary(leftNode, rightNode)
            return true
        }
        boundaryDetector.isBoundary(leftNode, rightNode)
        if (isBetweenAlphabetUnknown(leftNode, rightNode)) return false
        return true
    }

    private fun isBetweenAlphabetUnknown(leftNode: MozcNode, rightNode: MozcNode): Boolean =
        leftNode.isUnknown &&
            rightNode.isUnknown &&
            leftNode.value.firstOrNull()?.isLetter() == true &&
            rightNode.value.firstOrNull()?.isLetter() == true

    private fun pathFromElement(element: MozcQueueElement): MozcPath {
        val nodes = mutableListOf<MozcNode>()
        var cursor = element.next
        while (cursor != null) {
            val node = cursor.node
            if (node.nodeType != MozcNodeType.EOS) {
                nodes += node
            }
            cursor = cursor.next
        }
        val innerBoundaries = mutableListOf<Int>()
        var keyOffset = 0
        for (index in 0 until nodes.lastIndex) {
            keyOffset += nodes[index].key.length
            if (boundaryDetector.isBoundary(nodes[index], nodes[index + 1])) {
                innerBoundaries += keyOffset
            }
        }
        return MozcPath(
            nodes = nodes,
            cost = element.gx,
            structureCost = element.structureGx,
            wordCost = element.wordGx,
            innerBoundaryOffsets = innerBoundaries,
        )
    }

    private fun saturatedCost(a: Int, b: Int, c: Int): Int {
        val sum = a.toLong() + b.toLong() + c.toLong()
        return sum.coerceIn(Int.MIN_VALUE.toLong(), MozcViterbi.VERY_BIG_COST.toLong()).toInt()
    }

    companion object {
        private const val MAX_TRIALS = 8192
    }
}
