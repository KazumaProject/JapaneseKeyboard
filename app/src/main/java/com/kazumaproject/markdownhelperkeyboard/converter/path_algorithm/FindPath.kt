package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.core.domain.extensions.isAllFullWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isAllHalfWidthNumericSymbol
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.JapaneseCandidateDedupeAction
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.JapaneseCandidateDedupeMode
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.JapaneseCandidateDedupeTrace
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.JapaneseCandidateIdentity
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.JapaneseCandidateSource
import timber.log.Timber
import java.util.PriorityQueue

class FindPath(
    private val ngramRuleScorerProvider: () -> NgramRuleScorer = { defaultNgramRuleScorer },
) {

    companion object {
        private val defaultNgramRuleScorer: NgramRuleScorer = NgramRuleScorer.createDefault()
    }

    fun backwardAStar(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        connectionMatrixSize: Int,
        n: Int,
        searchOptions: JapaneseSearchOptions = JapaneseSearchOptions(),
    ): MutableList<Candidate> {
        forwardDp(
            graph = graph,
            length = length,
            connectionIds = connectionIds,
            connectionMatrixSize = connectionMatrixSize,
            beamWidth = searchOptions.beamWidth,
        )

        val resultFinal = mutableListOf<Candidate>()
        val foundStrings = HashSet<String>()

        var sequence = 0L
        val pQueue = createPathPriorityQueue()

        val eos = graph[length + 1]?.get(0) ?: return resultFinal
        pQueue.add(
            PathElement(
                node = eos,
                next = null,
                gx = 0,
                fx = 0,
                wordCost = 0,
                structureCost = 0,
                splitPositions = IntArray(0),
                sequence = sequence++,
            )
        )

        while (pQueue.isNotEmpty()) {
            val element = pQueue.poll() ?: break
            val node = element.node

            if (node.tango == "BOS") {
                val pathNodes = getNodesFromPath(element)
                val stringFromNode = getStringFromPathNodes(pathNodes)
                val yomiUsedFromNode = getYomiUsedFromPathNodes(pathNodes)
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
                            element.fx + 2000
                        } else {
                            element.fx
                        },
                        leftId = pathNodes.firstOrNull()?.l,
                        rightId = pathNodes.firstOrNull()?.r,
                    )
                    resultFinal.add(candidate)
                }

                if (resultFinal.size >= n) {
                    return resultFinal
                }
            } else {
                val prevNodes = getPrevNodes2(
                    graph = graph,
                    node = node,
                    startPosition = node.sPos,
                ).flatten()

                for (prevNode in prevNodes) {
                    val edgeScore = getEdgeCost(
                        leftId = prevNode.l.toInt(),
                        rightId = node.r.toInt(),
                        connectionIds = connectionIds,
                        connectionMatrixSize = connectionMatrixSize,
                    )

                    val ngramAdjustment = ngramRuleScorerProvider().score(
                        prevNode = prevNode,
                        currentNode = node,
                    )

                    pQueue.add(
                        createPreviousPathElement(
                            prevNode = prevNode,
                            current = element,
                            edgeScore = edgeScore,
                            ngramAdjustment = ngramAdjustment,
                            sequence = sequence++,
                        )
                    )
                }
            }
        }

        return resultFinal
    }

    private fun forwardDp(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        connectionMatrixSize: Int,
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
                        connectionMatrixSize = connectionMatrixSize,
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
        connectionMatrixSize: Int,
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
                        connectionMatrixSize = connectionMatrixSize,
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
        connectionMatrixSize: Int,
    ): Int {
        require(connectionMatrixSize > 0) { "connectionMatrixSize must be positive: $connectionMatrixSize" }
        require(leftId in 0 until connectionMatrixSize && rightId in 0 until connectionMatrixSize) {
            "connection id out of range: leftId=$leftId, rightId=$rightId, matrixSize=$connectionMatrixSize"
        }
        val index = leftId * connectionMatrixSize + rightId
        require(index in connectionIds.indices) {
            "connection index out of range: index=$index, size=${connectionIds.size}, matrixSize=$connectionMatrixSize"
        }
        return connectionIds[index].toInt()
    }

    private fun createPathPriorityQueue(): PriorityQueue<PathElement> =
        PriorityQueue(
            compareBy<PathElement> { it.fx }
                .thenBy { it.node.sPos }
                .thenBy { it.node.len }
                .thenBy { System.identityHashCode(it.node) }
                .thenBy { it.sequence },
        )

    private fun createPreviousPathElement(
        prevNode: Node,
        current: PathElement,
        edgeScore: Int,
        ngramAdjustment: Int,
        sequence: Long,
    ): PathElement {
        val wordCostDiff = current.node.score
        val structureCostDiff = edgeScore + ngramAdjustment
        val gx = current.gx + wordCostDiff + structureCostDiff
        return PathElement(
            node = prevNode,
            next = current,
            gx = gx,
            fx = gx + prevNode.f,
            wordCost = current.wordCost + wordCostDiff,
            structureCost = current.structureCost + structureCostDiff,
            splitPositions = copySplitPositionsWithBoundary(current),
            sequence = sequence,
        )
    }

    private fun copySplitPositionsWithBoundary(current: PathElement): IntArray {
        val rightNode = current.node
        if (rightNode.tango == "EOS" || rightNode.sPos <= 0 || !isIndependentWord(rightNode.l)) {
            return current.splitPositions
        }
        return IntArray(current.splitPositions.size + 1).also { result ->
            result[0] = rightNode.sPos
            current.splitPositions.copyInto(result, destinationOffset = 1)
        }
    }

    private fun getNodesFromPath(element: PathElement): List<Node> {
        val result = mutableListOf<Node>()
        var cursor = element.next
        while (cursor != null && cursor.node.tango != "EOS") {
            result.add(cursor.node)
            cursor = cursor.next
        }
        return result
    }

    private fun getStringFromPathNodes(nodes: List<Node>): String =
        nodes.joinToString(separator = "") { it.tango }

    private fun getYomiUsedFromPathNodes(nodes: List<Node>): String =
        nodes.joinToString(separator = "") { it.yomiUsed }

    fun backwardAStarWithBunsetsu(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        connectionMatrixSize: Int,
        n: Int,
        input: String = "",
        options: JapaneseBunsetsuOptions = JapaneseBunsetsuOptions(),
    ): BunsetsuCandidateResult {
        forwardDp(
            graph = graph,
            length = length,
            connectionIds = connectionIds,
            connectionMatrixSize = connectionMatrixSize,
            beamWidth = options.searchOptions.beamWidth,
        )

        val resultFinal = mutableListOf<Candidate>()
        val splitPatterns = mutableListOf<List<Int>>()
        val splitPatternByCandidateString = linkedMapOf<String, List<Int>>()
        val splitPatternByCandidateIdentity = linkedMapOf<JapaneseCandidateIdentity, List<Int>>()
        val dedupeTraces = mutableListOf<JapaneseCandidateDedupeTrace>()
        val foundStrings = HashSet<String>()
        val foundIdentities = LinkedHashSet<JapaneseCandidateIdentity>()

        var sequence = 0L
        val pQueue = createPathPriorityQueue()

        graph[length + 1]?.get(0)?.let {
            pQueue.add(
                PathElement(
                    node = it,
                    next = null,
                    gx = 0,
                    fx = 0,
                    wordCost = 0,
                    structureCost = 0,
                    splitPositions = IntArray(0),
                    sequence = sequence++,
                )
            )
        }
            ?: return BunsetsuCandidateResult(emptyList(), emptyList())

        while (pQueue.isNotEmpty()) {
            val element = pQueue.poll() ?: break
            val node = element.node

            if (node.tango == "BOS") {
                val pathNodes = getNodesFromPath(element)
                val stringFromNode = getStringFromPathNodes(pathNodes)
                val yomiUsedFromNode = getYomiUsedFromPathNodes(pathNodes)
                val bunsetsuPositions = element.splitPositionList()
                val firstNode = pathNodes.firstOrNull()
                val identity = JapaneseCandidateIdentity(
                    value = stringFromNode,
                    key = yomiUsedFromNode,
                    leftId = firstNode?.l,
                    rightId = firstNode?.r,
                    splitPattern = bunsetsuPositions,
                    wordCost = element.wordCost,
                    structureCost = element.structureCost,
                    candidateSource = JapaneseCandidateSource.NBEST,
                )

                val valueAlreadySeen = !foundStrings.add(stringFromNode)
                val identityAlreadySeen = !foundIdentities.add(identity)
                val shouldDrop = when (options.candidateDedupeMode) {
                    JapaneseCandidateDedupeMode.LEGACY_VALUE -> valueAlreadySeen
                    JapaneseCandidateDedupeMode.IDENTITY -> identityAlreadySeen
                }

                if (options.includeDedupeTrace) {
                    val action = when {
                        shouldDrop && options.candidateDedupeMode == JapaneseCandidateDedupeMode.LEGACY_VALUE ->
                            JapaneseCandidateDedupeAction.DROPPED_VALUE_DUPLICATE

                        shouldDrop && options.candidateDedupeMode == JapaneseCandidateDedupeMode.IDENTITY ->
                            JapaneseCandidateDedupeAction.DROPPED_IDENTITY_DUPLICATE

                        valueAlreadySeen && options.candidateDedupeMode == JapaneseCandidateDedupeMode.IDENTITY ->
                            JapaneseCandidateDedupeAction.WOULD_DROP_BY_VALUE_ONLY

                        else -> JapaneseCandidateDedupeAction.RETAINED
                    }
                    dedupeTraces.add(
                        JapaneseCandidateDedupeTrace(
                            input = input,
                            identity = identity,
                            action = action,
                            reason = when (action) {
                                JapaneseCandidateDedupeAction.RETAINED -> "candidate retained"
                                JapaneseCandidateDedupeAction.DROPPED_VALUE_DUPLICATE ->
                                    "legacy value-only dedupe removed this candidate"

                                JapaneseCandidateDedupeAction.DROPPED_IDENTITY_DUPLICATE ->
                                    "identity dedupe removed an exact duplicate candidate"

                                JapaneseCandidateDedupeAction.WOULD_DROP_BY_VALUE_ONLY ->
                                    "same value has another key/lid/rid/split identity"
                            },
                        )
                    )
                }

                if (!shouldDrop) {
                    if (splitPatterns.none { it == bunsetsuPositions } && splitPatterns.size < 4) {
                        splitPatterns.add(bunsetsuPositions)
                    }
                    splitPatternByCandidateString.putIfAbsent(stringFromNode, bunsetsuPositions)
                    splitPatternByCandidateIdentity[identity] = bunsetsuPositions

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
                            element.fx + 2000
                        } else {
                            element.fx
                        },
                        leftId = firstNode?.l,
                        rightId = firstNode?.r,
                        japaneseCandidateIdentity = identity,
                    )
                    resultFinal.add(candidate)
                }

                if (resultFinal.size >= n) {
                    return BunsetsuCandidateResult(
                        candidates = resultFinal,
                        splitPatterns = splitPatterns,
                        splitPatternByCandidateString = splitPatternByCandidateString,
                        splitPatternByCandidateIdentity = splitPatternByCandidateIdentity,
                        dedupeTraces = dedupeTraces,
                    )
                }
            } else {
                val prevNodes = getPrevNodes2(
                    graph = graph,
                    node = node,
                    startPosition = node.sPos,
                ).flatten()

                for (prevNode in prevNodes) {
                    val edgeScore = getEdgeCost(
                        leftId = prevNode.l.toInt(),
                        rightId = node.r.toInt(),
                        connectionIds = connectionIds,
                        connectionMatrixSize = connectionMatrixSize,
                    )

                    val ngramAdjustment = ngramRuleScorerProvider().score(
                        prevNode = prevNode,
                        currentNode = node,
                    )

                    pQueue.add(
                        createPreviousPathElement(
                            prevNode = prevNode,
                            current = element,
                            edgeScore = edgeScore,
                            ngramAdjustment = ngramAdjustment,
                            sequence = sequence++,
                        )
                    )
                }
            }
        }

        return BunsetsuCandidateResult(
            candidates = resultFinal.sortedBy { it.score },
            splitPatterns = splitPatterns,
            splitPatternByCandidateString = splitPatternByCandidateString,
            splitPatternByCandidateIdentity = splitPatternByCandidateIdentity,
            dedupeTraces = dedupeTraces,
        )
    }

    fun backwardAStarWithBunsetsuWithLog(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        connectionMatrixSize: Int,
        n: Int,
    ): Pair<List<Candidate>, List<Int>> {
        val totalStartTime = System.currentTimeMillis()
        Timber.d("▼ backwardAStarWithBunsetsu 開始 (入力長: $length)")

        val result = backwardAStarWithBunsetsu(
            graph = graph,
            length = length,
            connectionIds = connectionIds,
            connectionMatrixSize = connectionMatrixSize,
            n = n,
        )

        val totalTime = System.currentTimeMillis() - totalStartTime
        Timber.d("▼ backwardAStarWithBunsetsu 完了 (全体: ${totalTime}ms)")

        return Pair(result.candidates, result.primarySplitPositions)
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
