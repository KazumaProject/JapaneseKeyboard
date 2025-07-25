package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.core.domain.extensions.isAllFullWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isAllHalfWidthNumericSymbol
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.Other.NUM_OF_CONNECTION_ID
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import java.util.PriorityQueue

class FindPath {

    fun backwardAStar(
        graph: MutableMap<Int, MutableList<Node>>, // Adjusted type
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
        val pQueue: PriorityQueue<Pair<Node, Int>> =
            PriorityQueue(
                compareBy<Pair<Node, Int>> { it.second }    // ①総コスト
                    .thenBy { it.first.sPos }               // ②開始位置（小さいほど前）
                    .thenBy { it.first.len }                // ③単語長
                    .thenBy { System.identityHashCode(it.first) } // ④メモリ上の一意性
            )

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
                            type = when {
                                stringFromNode.isAllFullWidthNumericSymbol() -> (30).toByte()
                                stringFromNode.isAllHalfWidthNumericSymbol() -> (31).toByte()
                                else -> (1).toByte()
                            },
                            length = length.toUByte(),
                            score = if (stringFromNode.any { it.isDigit() }) node.second + 2000 else node.second,
                            leftId = node.first.next?.l,
                            rightId = node.first.next?.r
                        )
                        resultFinal.add(candidate)
                    }
                } else {
                    val prevNodes = getPrevNodes2(graph, node.first, node.first.sPos).flatten()
                    for (prevNode in prevNodes) {
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
                if (resultFinal.size >= n) {
                    return resultFinal
                }
            }
        }
        return resultFinal
    }

    private fun forwardDp(
        graph: MutableMap<Int, MutableList<Node>>,
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
        graph: MutableMap<Int, MutableList<Node>>,
        node: Node,
        startPosition: Int
    ): MutableList<Node> {
        val index =
            if (node.tango == "EOS") graph.keys.maxOrNull()?.minus(1) ?: return mutableListOf()
            else startPosition - node.len
        if ((startPosition - node.len) == 0) return mutableListOf(BOS)
        if (index < 0) return mutableListOf()
        return graph[index] ?: mutableListOf()
    }

    private fun getPrevNodes2(
        graph: MutableMap<Int, MutableList<Node>>,
        node: Node,
        startPosition: Int
    ): MutableList<MutableList<Node>> {
        val index =
            if (node.tango == "EOS") graph.keys.maxOrNull()?.minus(1) ?: return mutableListOf()
            else startPosition
        if (startPosition == 0) return mutableListOf(mutableListOf(BOS))
        if (index < 0) return mutableListOf()
        return mutableListOf(graph[index] ?: mutableListOf())
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
