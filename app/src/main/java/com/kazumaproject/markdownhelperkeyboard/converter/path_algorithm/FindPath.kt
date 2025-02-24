package com.kazumaproject.viterbi

import android.util.SparseArray
import androidx.core.util.size
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.Other.NUM_OF_CONNECTION_ID
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.maxKeyOrNull
import java.util.PriorityQueue

class FindPath {

    fun viterbi(
        graph: List<MutableList<MutableList<Node>>>,
        length: Int,
        connectionIds: ShortArray
    ): String {
        buildViterbi(graph, length, connectionIds)
        var node = graph[length + 1][0][0]
        val result: MutableList<String> = mutableListOf()
        while (node.tango != "BOS") {
            node.prev?.let {
                result.add(it.tango)
                node = it
            }
        }
        return result.asReversed().drop(1).joinToString(separator = "") { it }
    }

    fun backwardAStar(
        graph: SparseArray<MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        n: Int
    ): MutableList<Candidate> {
        forwardDp(
            graph,
            length,
            connectionIds
        )
        val resultFinal: MutableList<Candidate> = mutableListOf()
        val pQueue: PriorityQueue<Pair<Node, Int>> = PriorityQueue(compareBy { it.second })

        // EOS (End of Sentence) node is accessed from the graph using the key length + 1
        val eos = Pair(graph[length + 1]?.get(0) ?: return resultFinal, 0)
        pQueue.add(eos)

        while (pQueue.isNotEmpty()) {
            val node: Pair<Node, Int>? = pQueue.poll()

            node?.let {
                if (node.first.tango == "BOS") {
                    val stringFromNode = getStringFromNode(node.first)
                    if (!resultFinal.map { it.string }.contains(stringFromNode)) {
                        val candidate = Candidate(
                            string = stringFromNode,
                            type = (1).toByte(),
                            length = length.toUByte(),
                            score = if (Regex("\\d").containsMatchIn(stringFromNode)) node.second + 2000 else node.second,
                            leftId = node.first.next?.l,
                            rightId = node.first.next?.r
                        )
                        resultFinal.add(candidate)
                    }
                } else {
                    val prevNodesSparse = getPrevNodes2(graph, node.first, node.first.sPos)
                    for (i in 0 until prevNodesSparse.size) {
                        val nodeList = prevNodesSparse.valueAt(i)
                        for (prevNode in nodeList) {
                            val edgeScore = getEdgeCost(
                                prevNode.l.toInt(),
                                node.first.r.toInt(),
                                connectionIds
                            )
                            prevNode.g = node.first.g + edgeScore + node.first.score
                            prevNode.next = node.first
                            val result2 = Pair(prevNode, prevNode.g + prevNode.f)
                            pQueue.add(result2)
                        }
                    }
                }
                if (resultFinal.size >= n) {
                    return resultFinal
                }
            }
        }
        return resultFinal
    }

    private fun buildViterbi(
        graph: List<MutableList<MutableList<Node>>>,
        length: Int,
        connectionIds: ShortArray
    ) {
        for (i in 1..length + 1) {
            val nodes = graph[i].flatten()
            for (node in nodes) {
                val nodeCost = node.score
                var cost = Int.MAX_VALUE
                var shortestPrev: Node? = null
                val prevNodes = getPrevNodesForViterbi(
                    graph,
                    node,
                    i,
                ).flatten()
                for (prevNode in prevNodes) {
                    val edgeCost = getEdgeCost(
                        prevNode.l.toInt(),
                        node.r.toInt(),
                        connectionIds
                    )
                    val tempCost = prevNode.score + nodeCost + edgeCost
                    if (tempCost < cost) {
                        cost = tempCost
                        shortestPrev = prevNode
                    }
                }
                node.score = cost
                node.prev = shortestPrev
            }
        }
    }

    private fun forwardDp(
        graph: SparseArray<MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray
    ) {
        for (i in 1..length + 1) {
            val nodes = graph[i]
                ?: continue
            for (node in nodes) {
                val nodeScore = node.f
                var score = Int.MAX_VALUE
                var bestPrev: Node? = null
                val prevNodes =
                    getPrevNodes(graph, node, i)

                for (prev in prevNodes) {
                    val edgeCost = getEdgeCost(
                        prev.l.toInt(),
                        node.r.toInt(),
                        connectionIds
                    )
                    val tempCost = prev.f + nodeScore + edgeCost
                    if (tempCost < score) {
                        score = tempCost
                        bestPrev = prev
                    }
                }
                node.prev = bestPrev
                node.f = score
            }
        }
    }

    private fun getPrevNodes(
        graph: SparseArray<MutableList<Node>>,
        node: Node,
        startPosition: Int
    ): MutableList<Node> {
        val index = if (node.tango == "EOS") {
            val maxKey = graph.maxKeyOrNull() ?: return mutableListOf()
            maxKey - 1
        } else {
            startPosition - node.len
        }
        if (startPosition - node.len == 0) {
            return mutableListOf(BOS)
        }
        if (index < 0) return mutableListOf()
        return graph.get(index) ?: mutableListOf()
    }

    private fun getPrevNodes2(
        graph: SparseArray<MutableList<Node>>,
        node: Node,
        startPosition: Int
    ): SparseArray<MutableList<Node>> {
        val result = SparseArray<MutableList<Node>>()
        val index = if (node.tango == "EOS") {
            val maxKey = graph.maxKeyOrNull() ?: return result
            maxKey - 1
        } else {
            startPosition
        }
        if (startPosition == 0) {
            result.put(0, mutableListOf(BOS))
            return result
        }
        if (index < 0) return result
        val listAtIndex = graph.get(index) ?: mutableListOf()
        result.put(index, listAtIndex)
        return result
    }

    private fun getPrevNodesForViterbi(
        graph: List<MutableList<MutableList<Node>>>,
        node: Node,
        startPosition: Int,
    ): MutableList<MutableList<Node>> {
        if ((startPosition - node.len) == 0) return mutableListOf(mutableListOf(BOS))
        val index = if (node.tango == "EOS") startPosition - 1 else startPosition - node.len.toInt()
        if (index < 0) return mutableListOf()
        return graph[index]
    }

    private fun getEdgeCost(
        leftId: Int,
        rightId: Int,
        connectionIds: ShortArray
    ): Int {
        return connectionIds[leftId * NUM_OF_CONNECTION_ID + rightId].toInt()
    }

    private fun getStringFromNode(node: Node): String {
        var tempNode = node
        val result: MutableList<String> = mutableListOf()
        while (tempNode.tango != "EOS") {
            tempNode.next?.let {
                result.add(it.tango)
                tempNode = it
            }
        }
        return result.dropLast(1).joinToString("")
    }

}
