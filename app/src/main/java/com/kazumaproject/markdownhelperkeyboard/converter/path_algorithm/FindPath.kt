package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.core.domain.extensions.isAllFullWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isAllHalfWidthNumericSymbol
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.Other.NUM_OF_CONNECTION_ID
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import timber.log.Timber
import java.util.PriorityQueue

class FindPath(
    private val ngramRuleScorer: NgramRuleScorer = NgramRuleScorer.createDefault(),
) {

    fun backwardAStar(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        n: Int,
    ): MutableList<Candidate> {
        forwardDp(
            graph = graph,
            length = length,
            connectionIds = connectionIds,
        )

        val resultFinal = mutableListOf<Candidate>()
        val foundStrings = HashSet<String>()

        val pQueue: PriorityQueue<Pair<Node, Int>> =
            PriorityQueue(
                compareBy<Pair<Node, Int>> { it.second }
                    .thenBy { it.first.sPos }
                    .thenBy { it.first.len }
                    .thenBy { System.identityHashCode(it.first) },
            )

        val eos = Pair(graph[length + 1]?.get(0) ?: return resultFinal, 0)
        pQueue.add(eos)

        while (pQueue.isNotEmpty()) {
            val node = pQueue.poll() ?: break

            if (node.first.tango == "BOS") {
                val stringFromNode = getStringFromNode(node.first)
                val yomiUsedFromNode = getYomiUsedFromNode(node.first)

                if (foundStrings.add(stringFromNode)) {
                    val candidate = Candidate(
                        string = stringFromNode,
                        type = when {
                            stringFromNode.isAllFullWidthNumericSymbol() -> 30.toByte()
                            stringFromNode.isAllHalfWidthNumericSymbol() -> 31.toByte()
                            else -> 1.toByte()
                        },
                        yomi = yomiUsedFromNode,
                        length = length.toUByte(),
                        score = if (stringFromNode.any { it.isDigit() }) {
                            node.second + 2000
                        } else {
                            node.second
                        },
                        leftId = node.first.next?.l,
                        rightId = node.first.next?.r,
                    )
                    resultFinal.add(candidate)
                }

                if (resultFinal.size >= n) {
                    return resultFinal
                }
            } else {
                val prevNodes = getPrevNodes2(
                    graph = graph,
                    node = node.first,
                    startPosition = node.first.sPos,
                ).flatten()

                for (prevNode in prevNodes) {
                    val edgeScore = getEdgeCost(
                        leftId = prevNode.l.toInt(),
                        rightId = node.first.r.toInt(),
                        connectionIds = connectionIds,
                    )

                    val ngramAdjustment = ngramRuleScorer.score(
                        prevNode = prevNode,
                        currentNode = node.first,
                    )

                    prevNode.g = node.first.g + edgeScore + node.first.score + ngramAdjustment
                    prevNode.next = node.first

                    val result2 = Pair(prevNode, prevNode.g + prevNode.f)
                    pQueue.add(result2)
                }
            }
        }

        return resultFinal
    }

    private fun forwardDp(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        beamWidth: Int = 20,
    ) {
        for (i in 1..length + 1) {
            val nodes = graph[i] ?: continue

            for (node in nodes) {
                val nodeScore = node.f
                var score = Int.MAX_VALUE
                var bestPrev: Node? = null
                val prevNodes = getPrevNodes(graph, node, i)

                for (prev in prevNodes) {
                    val edgeCost = getEdgeCost(
                        leftId = prev.l.toInt(),
                        rightId = node.r.toInt(),
                        connectionIds = connectionIds,
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

            if (i <= length && nodes.size > beamWidth) {
                nodes.sortBy { it.f }
                graph[i] = nodes.take(beamWidth).toMutableList()
            }
        }
    }

    private fun forwardDpWithLog(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        beamWidth: Int = 20,
    ) {
        for (i in 1..length + 1) {
            val nodes = graph[i] ?: continue

            for (node in nodes) {
                val nodeScore = node.f
                var score = Int.MAX_VALUE
                var bestPrev: Node? = null
                val prevNodes = getPrevNodes(graph, node, i)

                for (prev in prevNodes) {
                    val edgeCost = getEdgeCost(
                        leftId = prev.l.toInt(),
                        rightId = node.r.toInt(),
                        connectionIds = connectionIds,
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

            if (i <= length && nodes.size > beamWidth) {
                val originalNodeCount = nodes.size
                nodes.sortBy { it.f }
                val prunedNodes = nodes.take(beamWidth).toMutableList()
                graph[i] = prunedNodes
                Timber.d("    - forwardDp 枝刈り @位置$i: $originalNodeCount -> ${prunedNodes.size} ノード")
            }
        }
    }

    private fun getPrevNodes(
        graph: MutableMap<Int, MutableList<Node>>,
        node: Node,
        startPosition: Int,
    ): MutableList<Node> {
        val index =
            if (node.tango == "EOS") {
                graph.keys.maxOrNull()?.minus(1) ?: return mutableListOf()
            } else {
                startPosition - node.len
            }

        if ((startPosition - node.len) == 0) return mutableListOf(BOS)
        if (index < 0) return mutableListOf()
        return graph[index] ?: mutableListOf()
    }

    private fun getPrevNodes2(
        graph: MutableMap<Int, MutableList<Node>>,
        node: Node,
        startPosition: Int,
    ): MutableList<MutableList<Node>> {
        val index =
            if (node.tango == "EOS") {
                graph.keys.maxOrNull()?.minus(1) ?: return mutableListOf()
            } else {
                startPosition
            }

        if (startPosition == 0) return mutableListOf(mutableListOf(BOS))
        if (index < 0) return mutableListOf()
        return mutableListOf(graph[index] ?: mutableListOf())
    }

    private fun getEdgeCost(
        leftId: Int,
        rightId: Int,
        connectionIds: ShortArray,
    ): Int {
        return connectionIds[leftId * NUM_OF_CONNECTION_ID + rightId].toInt()
    }

    private fun getStringFromNode(node: Node): String {
        var tempNode = node
        val result = mutableListOf<String>()

        while (tempNode.tango != "EOS") {
            tempNode.next?.let {
                result.add(it.tango)
                tempNode = it
            }
        }

        return result.dropLast(1).joinToString("")
    }

    private fun getYomiUsedFromNode(node: Node): String {
        var tempNode = node
        val result = mutableListOf<String>()

        while (tempNode.tango != "EOS") {
            tempNode.next?.let {
                result.add(it.yomiUsed)
                tempNode = it
            }
        }

        return result.dropLast(1).joinToString("")
    }

    fun backwardAStarWithBunsetsu(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        n: Int,
    ): BunsetsuCandidateResult {
        forwardDp(
            graph = graph,
            length = length,
            connectionIds = connectionIds,
        )

        val resultFinal = mutableListOf<Candidate>()
        val splitPatterns = mutableListOf<List<Int>>()
        val splitPatternByCandidateString = linkedMapOf<String, List<Int>>()
        val foundStrings = HashSet<String>()

        val pQueue: PriorityQueue<Pair<Node, Int>> =
            PriorityQueue(
                compareBy<Pair<Node, Int>> { it.second }
                    .thenBy { it.first.sPos }
                    .thenBy { it.first.len }
                    .thenBy { System.identityHashCode(it.first) },
            )

        graph[length + 1]?.get(0)?.let { pQueue.add(Pair(it, 0)) }
            ?: return BunsetsuCandidateResult(emptyList(), emptyList())

        while (pQueue.isNotEmpty()) {
            val node = pQueue.poll() ?: break

            if (node.first.tango == "BOS") {
                val stringFromNode = getStringFromNode(node.first)
                val yomiUsedFromNode = getYomiUsedFromNode(node.first)
                val bunsetsuPositions = getBunsetsuPositions(node.first)

                if (foundStrings.add(stringFromNode)) {
                    if (splitPatterns.none { it == bunsetsuPositions } && splitPatterns.size < 4) {
                        splitPatterns.add(bunsetsuPositions)
                    }
                    splitPatternByCandidateString[stringFromNode] = bunsetsuPositions

                    val candidate = Candidate(
                        string = stringFromNode,
                        type = when {
                            stringFromNode.isAllFullWidthNumericSymbol() -> 30.toByte()
                            stringFromNode.isAllHalfWidthNumericSymbol() -> 31.toByte()
                            else -> 1.toByte()
                        },
                        length = length.toUByte(),
                        yomi = yomiUsedFromNode,
                        score = if (stringFromNode.any { it.isDigit() }) {
                            node.second + 2000
                        } else {
                            node.second
                        },
                        leftId = node.first.next?.l,
                        rightId = node.first.next?.r,
                    )
                    resultFinal.add(candidate)
                }

                if (resultFinal.size >= n) {
                    return BunsetsuCandidateResult(
                        candidates = resultFinal,
                        splitPatterns = splitPatterns,
                        splitPatternByCandidateString = splitPatternByCandidateString,
                    )
                }
            } else {
                val prevNodes = getPrevNodes2(
                    graph = graph,
                    node = node.first,
                    startPosition = node.first.sPos,
                ).flatten()

                for (prevNode in prevNodes) {
                    val edgeScore = getEdgeCost(
                        leftId = prevNode.l.toInt(),
                        rightId = node.first.r.toInt(),
                        connectionIds = connectionIds,
                    )

                    val ngramAdjustment = ngramRuleScorer.score(
                        prevNode = prevNode,
                        currentNode = node.first,
                    )

                    prevNode.g = node.first.g + edgeScore + node.first.score + ngramAdjustment
                    prevNode.next = node.first

                    val result2 = Pair(prevNode, prevNode.g + prevNode.f)
                    pQueue.add(result2)
                }
            }
        }

        return BunsetsuCandidateResult(
            candidates = resultFinal.sortedBy { it.score },
            splitPatterns = splitPatterns,
            splitPatternByCandidateString = splitPatternByCandidateString,
        )
    }

    fun backwardAStarWithBunsetsuWithLog(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        n: Int,
    ): Pair<List<Candidate>, List<Int>> {
        val totalStartTime = System.currentTimeMillis()
        Timber.d("▼ backwardAStarWithBunsetsu 開始 (入力長: $length)")

        val forwardDpStartTime = System.currentTimeMillis()

        forwardDpWithLog(
            graph = graph,
            length = length,
            connectionIds = connectionIds,
        )

        val forwardDpTime = System.currentTimeMillis() - forwardDpStartTime
        Timber.d("  ├─ forwardDp 処理時間: ${forwardDpTime}ms")

        val backwardAStarStartTime = System.currentTimeMillis()

        val resultFinal = mutableListOf<Candidate>()
        var bestBunsetsuPositions: List<Int> = emptyList()
        val foundStrings = HashSet<String>()

        val pQueue: PriorityQueue<Pair<Node, Int>> =
            PriorityQueue(
                compareBy<Pair<Node, Int>> { it.second }
                    .thenBy { it.first.sPos }
                    .thenBy { it.first.len }
                    .thenBy { System.identityHashCode(it.first) },
            )

        graph[length + 1]?.get(0)?.let { pQueue.add(Pair(it, 0)) }
            ?: return Pair(emptyList(), emptyList())

        var loopCount = 0
        var maxQueueSize = 0

        while (pQueue.isNotEmpty()) {
            loopCount++
            maxQueueSize = maxOf(maxQueueSize, pQueue.size)

            val node = pQueue.poll() ?: break

            if (node.first.tango == "BOS") {
                val stringFromNode = getStringFromNode(node.first)
                val yomiUsedFromNode = getYomiUsedFromNode(node.first)
                val bunsetsuPositions = getBunsetsuPositions(node.first)

                if (foundStrings.add(stringFromNode)) {
                    if (resultFinal.isEmpty()) {
                        bestBunsetsuPositions = bunsetsuPositions
                    }

                    val candidate = Candidate(
                        string = stringFromNode,
                        type = when {
                            stringFromNode.isAllFullWidthNumericSymbol() -> 30.toByte()
                            stringFromNode.isAllHalfWidthNumericSymbol() -> 31.toByte()
                            else -> 1.toByte()
                        },
                        yomi = yomiUsedFromNode,
                        length = length.toUByte(),
                        score = if (stringFromNode.any { it.isDigit() }) {
                            node.second + 2000
                        } else {
                            node.second
                        },
                        leftId = node.first.next?.l,
                        rightId = node.first.next?.r,
                    )
                    resultFinal.add(candidate)
                }

                if (resultFinal.size >= n) {
                    val backwardAStarTime = System.currentTimeMillis() - backwardAStarStartTime
                    val totalTime = System.currentTimeMillis() - totalStartTime
                    Timber.d("  ├─ 後方A*探索 処理時間: ${backwardAStarTime}ms (早期リターン)")
                    Timber.d("  │  ├─ ループ回数: $loopCount 回")
                    Timber.d("  │  └─ pQueue最大サイズ: $maxQueueSize")
                    Timber.d("▼ backwardAStarWithBunsetsu 完了 (全体: ${totalTime}ms)")
                    return Pair(resultFinal, bestBunsetsuPositions)
                }
            } else {
                val prevNodes = getPrevNodes2(
                    graph = graph,
                    node = node.first,
                    startPosition = node.first.sPos,
                ).flatten()

                for (prevNode in prevNodes) {
                    val edgeScore = getEdgeCost(
                        leftId = prevNode.l.toInt(),
                        rightId = node.first.r.toInt(),
                        connectionIds = connectionIds,
                    )

                    val ngramAdjustment = ngramRuleScorer.score(
                        prevNode = prevNode,
                        currentNode = node.first,
                    )

                    prevNode.g = node.first.g + edgeScore + node.first.score + ngramAdjustment
                    prevNode.next = node.first

                    val result2 = Pair(prevNode, prevNode.g + prevNode.f)
                    pQueue.add(result2)
                }
            }
        }

        val backwardAStarTime = System.currentTimeMillis() - backwardAStarStartTime
        val totalTime = System.currentTimeMillis() - totalStartTime
        Timber.d("  ├─ 後方A*探索 処理時間: ${backwardAStarTime}ms")
        Timber.d("  │  ├─ ループ回数: $loopCount 回")
        Timber.d("  │  └─ pQueue最大サイズ: $maxQueueSize")
        Timber.d("▼ backwardAStarWithBunsetsu 完了 (全体: ${totalTime}ms)")

        return Pair(resultFinal.sortedBy { it.score }, bestBunsetsuPositions)
    }

    private fun getBunsetsuPositions(bosNode: Node): List<Int> {
        val positions = mutableListOf<Int>()
        var currentPosition = 0
        var tempNode = bosNode.next

        while (tempNode != null && tempNode.tango != "EOS") {
            if (currentPosition > 0 && isIndependentWord(tempNode.l)) {
                positions.add(currentPosition)
            }
            currentPosition += tempNode.len.toInt()
            tempNode = tempNode.next
        }

        return positions
    }

    private fun isIndependentWord(id: Short): Boolean {
        return when (val idInt = id.toInt()) {
            in 12..28, in 2590..2670 -> true
            in 577..856 -> true
            in 2390..2471 -> true
            in 1842..2195 -> idInt !in 1937..2040
            else -> false
        }
    }
}
