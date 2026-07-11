package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.core.domain.extensions.isAllFullWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isAllHalfWidthNumericSymbol
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.graph.IncrementalGraphMetadata
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcBoundaryCheckResult
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcBoundaryChecker
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcBoundaryMode
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.trace.BoundaryTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.CandidateTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.ForwardDpTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.PenaltyTrace
import timber.log.Timber
import java.util.PriorityQueue

class FindPath(
    private val ngramRuleScorerProvider: () -> NgramRuleScorer = { defaultNgramRuleScorer },
    private var mozcSegmenterProvider: () -> MozcSegmenter? = { null },
    private var mozcBoundaryModeProvider: () -> MozcBoundaryMode = { MozcBoundaryMode.STRICT },
) {

    private data class ForwardDpCache(
        val inputLength: Int,
        val conversionSignature: Int,
        val connectionMatrixIdentity: Int,
        val beamWidth: Int,
        val nodesBeforePreviousEnd: Map<Int, MutableList<Node>>,
    )

    @Volatile
    private var forwardDpCache: ForwardDpCache? = null

    private data class PathQueueElement(
        val node: Node,
        val priorityCost: Int,
        val backwardCost: Int,
        val next: PathQueueElement?,
    )

    companion object {
        private val defaultNgramRuleScorer: NgramRuleScorer = NgramRuleScorer.createDefault()
    }

    private var forwardDpTraceSink: MutableList<ForwardDpTrace>? = null

    fun configureMozcParityProviders(
        mozcSegmenterProvider: () -> MozcSegmenter?,
        mozcBoundaryModeProvider: () -> MozcBoundaryMode = { MozcBoundaryMode.STRICT },
    ) {
        this.mozcSegmenterProvider = mozcSegmenterProvider
        this.mozcBoundaryModeProvider = mozcBoundaryModeProvider
    }

    fun backwardAStar(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        connectionMatrixSize: Int,
        n: Int,
        beamWidth: Int = 20,
    ): MutableList<Candidate> = backwardAStar(
        graph = graph,
        length = length,
        connectionMatrix = ConnectionMatrix.fromShortArray(connectionIds, connectionMatrixSize),
        n = n,
        beamWidth = beamWidth,
    )

    fun backwardAStar(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionMatrix: ConnectionMatrix.CostTable,
        n: Int,
        beamWidth: Int = 20,
    ): MutableList<Candidate> {
        forwardDp(
            graph = graph,
            length = length,
            connectionMatrix = connectionMatrix,
            beamWidth = beamWidth.coerceAtLeast(1),
        )

        val resultFinal = mutableListOf<Candidate>()
        val foundStrings = HashSet<String>()
        val ngramRuleScorer = ngramRuleScorerProvider()

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
                        type = resolveCandidateType(
                            string = stringFromNode,
                            sources = candidateSourcesFromNode(node.first),
                        ),
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
                )

                for (prevNode in prevNodes) {
                    val edgeScore = getEdgeCost(
                        rid = prevNode.r.toInt(),
                        lid = node.first.l.toInt(),
                        connectionMatrix = connectionMatrix,
                    )

                    val ngramAdjustment = ngramRuleScorer.score(
                        prevNode = prevNode,
                        currentNode = node.first,
                        nextNode1 = node.first.next,
                        nextNode2 = node.first.next?.next,
                        nextNode3 = node.first.next?.next?.next,
                    )

                    prevNode.g = node.first.g + edgeScore + node.first.adjustedScore + ngramAdjustment
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
        connectionMatrix: ConnectionMatrix.CostTable,
        beamWidth: Int = 20,
        startPosition: Int = 1,
        cancellationCheck: () -> Unit = {},
    ) {
        for (i in startPosition..length + 1) {
            cancellationCheck()
            val nodes = graph[i] ?: continue

            for (node in nodes) {
                val nodeScore = node.adjustedScore
                var score = Int.MAX_VALUE
                var bestPrev: Node? = null
                val prevNodes = getPrevNodes(graph, node, i)

                for (prev in prevNodes) {
                    val edgeCost = getEdgeCost(
                        rid = prev.r.toInt(),
                        lid = node.l.toInt(),
                        connectionMatrix = connectionMatrix,
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

            val traceSink = forwardDpTraceSink
            val beforePruning = traceSink?.let { nodes.toList() }
            if (i <= length && nodes.size > beamWidth) {
                nodes.sortBy { it.f }
                graph[i] = nodes.take(beamWidth).toMutableList()
            }
            traceSink?.add(
                ForwardDpTrace(
                    position = i,
                    nodeCountBeforePruning = beforePruning?.size ?: nodes.size,
                    nodeCountAfterPruning = graph[i]?.size ?: nodes.size,
                    keptNodes = (graph[i] ?: nodes).map { it.tango },
                    droppedNodes = if (beforePruning != null && i <= length && beforePruning.size > beamWidth) {
                        beforePruning.sortedBy { it.f }.drop(beamWidth).map { it.tango }
                    } else {
                        emptyList()
                    },
                ),
            )
        }
    }

    private fun forwardDpWithLog(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionMatrix: ConnectionMatrix.CostTable,
        beamWidth: Int = 20,
    ) {
        for (i in 1..length + 1) {
            val nodes = graph[i] ?: continue

            for (node in nodes) {
                val nodeScore = node.adjustedScore
                var score = Int.MAX_VALUE
                var bestPrev: Node? = null
                val prevNodes = getPrevNodes(graph, node, i)

                for (prev in prevNodes) {
                    val edgeCost = getEdgeCost(
                        rid = prev.r.toInt(),
                        lid = node.l.toInt(),
                        connectionMatrix = connectionMatrix,
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
    ): MutableList<Node> {
        val index =
            if (node.tango == "EOS") {
                graph.keys.maxOrNull()?.minus(1) ?: return mutableListOf()
            } else {
                startPosition
            }

        if (startPosition == 0) return mutableListOf(BOS)
        if (index < 0) return mutableListOf()
        return graph[index] ?: mutableListOf()
    }

    private fun getEdgeCost(
        rid: Int,
        lid: Int,
        connectionMatrix: ConnectionMatrix.CostTable,
    ): Int {
        return connectionMatrix.cost(rid, lid)
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

    private fun candidateSourcesFromNode(node: Node): Sequence<CandidateSource> = sequence {
        var current = node.next
        while (current != null && current.tango != "EOS") {
            yield(current.candidateSource)
            current = current.next
        }
    }

    private fun candidateSourcesFromPath(path: PathQueueElement): Sequence<CandidateSource> =
        generateSequence(path) { it.next }
            .map { it.node }
            .filter { it.tango != "BOS" && it.tango != "EOS" }
            .map { it.candidateSource }

    private fun resolveCandidateType(
        string: String,
        sources: Sequence<CandidateSource>,
    ): Byte {
        var containsUserDictionary = false
        for (source in sources) {
            when (source) {
                CandidateSource.LEARNED_DICTIONARY ->
                    return CANDIDATE_TYPE_LEARNED_DICTIONARY

                CandidateSource.USER_DICTIONARY -> containsUserDictionary = true
                CandidateSource.SYSTEM, CandidateSource.UNKNOWN -> Unit
            }
        }
        if (containsUserDictionary) return CANDIDATE_TYPE_USER_DICTIONARY
        return when {
            string.isAllFullWidthNumericSymbol() -> 30.toByte()
            string.isAllHalfWidthNumericSymbol() -> 31.toByte()
            else -> 1.toByte()
        }
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
        connectionMatrixSize: Int,
        n: Int,
        beamWidth: Int = 20,
        penaltyTrace: MutableList<PenaltyTrace>? = null,
        forwardDpTrace: MutableList<ForwardDpTrace>? = null,
        boundaryTrace: MutableList<BoundaryTrace>? = null,
        candidateTrace: MutableList<CandidateTrace>? = null,
    ): BunsetsuCandidateResult = backwardAStarWithBunsetsu(
        graph = graph,
        length = length,
        connectionMatrix = ConnectionMatrix.fromShortArray(connectionIds, connectionMatrixSize),
        n = n,
        beamWidth = beamWidth,
        penaltyTrace = penaltyTrace,
        forwardDpTrace = forwardDpTrace,
        boundaryTrace = boundaryTrace,
        candidateTrace = candidateTrace,
    )

    fun backwardAStarWithBunsetsu(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionMatrix: ConnectionMatrix.CostTable,
        n: Int,
        beamWidth: Int = 20,
        penaltyTrace: MutableList<PenaltyTrace>? = null,
        forwardDpTrace: MutableList<ForwardDpTrace>? = null,
        boundaryTrace: MutableList<BoundaryTrace>? = null,
        candidateTrace: MutableList<CandidateTrace>? = null,
        cancellationCheck: () -> Unit = {},
    ): BunsetsuCandidateResult {
        cancellationCheck()
        val mozcSegmenter = mozcSegmenterProvider()
        if (mozcSegmenter != null) {
            applyMozcPrefixSuffixPenalty(
                graph = graph,
                length = length,
                segmenter = mozcSegmenter,
                trace = penaltyTrace,
            )
        }

        val effectiveBeamWidth = beamWidth.coerceAtLeast(1)
        val incrementalMetadata = graph as? IncrementalGraphMetadata
        val reusableForwardDp = forwardDpCache?.takeIf { cached ->
            forwardDpTrace == null &&
                incrementalMetadata != null &&
                incrementalMetadata.reusedThroughEndIndex == cached.inputLength &&
                incrementalMetadata.conversionSignature == cached.conversionSignature &&
                length == cached.inputLength + 1 &&
                cached.connectionMatrixIdentity == System.identityHashCode(connectionMatrix) &&
                cached.beamWidth == effectiveBeamWidth
        }
        val forwardDpStartPosition = if (reusableForwardDp != null) {
            reusableForwardDp.nodesBeforePreviousEnd.forEach { (position, nodes) ->
                graph[position] = nodes
            }
            reusableForwardDp.inputLength
        } else {
            1
        }

        forwardDpTraceSink = forwardDpTrace
        try {
            forwardDp(
                graph = graph,
                length = length,
                connectionMatrix = connectionMatrix,
                beamWidth = effectiveBeamWidth,
                startPosition = forwardDpStartPosition,
                cancellationCheck = cancellationCheck,
            )
        } finally {
            forwardDpTraceSink = null
        }

        if (incrementalMetadata != null) {
            forwardDpCache = ForwardDpCache(
                inputLength = length,
                conversionSignature = incrementalMetadata.conversionSignature,
                connectionMatrixIdentity = System.identityHashCode(connectionMatrix),
                beamWidth = effectiveBeamWidth,
                nodesBeforePreviousEnd = (0 until length).associateWithTo(LinkedHashMap()) { position ->
                    graph[position]?.toMutableList() ?: mutableListOf()
                },
            )
        } else {
            forwardDpCache = null
        }

        val boundaryChecker = mozcSegmenter?.let {
            MozcBoundaryChecker(
                segmenter = it,
                mode = mozcBoundaryModeProvider(),
            )
        }

        val resultFinal = mutableListOf<Candidate>()
        val splitPatterns = mutableListOf<List<Int>>()
        val splitPatternByCandidateString = linkedMapOf<String, List<Int>>()
        val foundStrings = HashSet<String>()
        val ngramRuleScorer = ngramRuleScorerProvider()

        val pQueue: PriorityQueue<PathQueueElement> =
            PriorityQueue { first, second ->
                var comparison = first.priorityCost.compareTo(second.priorityCost)
                if (comparison == 0) {
                    comparison = first.node.sPos.compareTo(second.node.sPos)
                }
                if (comparison == 0) {
                    comparison = first.node.len.compareTo(second.node.len)
                }
                if (comparison == 0) {
                    comparison = System.identityHashCode(first.node)
                        .compareTo(System.identityHashCode(second.node))
                }
                comparison
            }

        graph[length + 1]?.get(0)?.let {
            pQueue.add(
                PathQueueElement(
                    node = it,
                    priorityCost = 0,
                    backwardCost = 0,
                    next = null,
                ),
            )
        }
            ?: return BunsetsuCandidateResult(emptyList(), emptyList())

        var cancellationPollCounter = 0
        while (pQueue.isNotEmpty()) {
            if (cancellationPollCounter++ and 0x3f == 0) cancellationCheck()
            val element = pQueue.poll() ?: break
            val currentNode = element.node

            if (currentNode.tango == "BOS") {
                val stringFromNode = getStringFromPath(element)
                val yomiUsedFromNode = getYomiUsedFromPath(element)
                val totalCost = if (stringFromNode.any { it.isDigit() }) {
                    element.priorityCost + 2000
                } else {
                    element.priorityCost
                }
                candidateTrace?.add(
                    CandidateTrace(
                        candidate = stringFromNode,
                        yomi = yomiUsedFromNode,
                        totalCost = totalCost,
                        path = getPathStringsFromPath(element),
                    ),
                )

                if (foundStrings.add(stringFromNode)) {
                    val bunsetsuPositions = getBunsetsuPositionsFromPath(element, mozcSegmenter)
                    if (splitPatterns.none { it == bunsetsuPositions } && splitPatterns.size < 4) {
                        splitPatterns.add(bunsetsuPositions)
                    }
                    splitPatternByCandidateString[stringFromNode] = bunsetsuPositions

                    val candidate = Candidate(
                        string = stringFromNode,
                        type = resolveCandidateType(
                            string = stringFromNode,
                            sources = candidateSourcesFromPath(element),
                        ),
                        length = length.toUByte(),
                        yomi = yomiUsedFromNode,
                        score = totalCost,
                        leftId = element.next?.node?.l,
                        rightId = element.next?.node?.r,
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
                    node = currentNode,
                    startPosition = currentNode.sPos,
                )

                for (prevNode in prevNodes) {
                    if (boundaryChecker != null) {
                        val isEdge = prevNode.mozcNodeType == MozcNodeType.BOS ||
                            currentNode.mozcNodeType == MozcNodeType.EOS
                        val result = boundaryChecker.check(
                            left = prevNode,
                            right = currentNode,
                            isEdge = isEdge,
                        )
                        boundaryTrace?.add(
                            BoundaryTrace(
                                leftTango = prevNode.tango,
                                rightTango = currentNode.tango,
                                leftRid = prevNode.r.toInt(),
                                rightLid = currentNode.l.toInt(),
                                isEdge = isEdge,
                                isSingleSegment = mozcBoundaryModeProvider() == MozcBoundaryMode.ONLY_EDGE,
                                result = result.name,
                            ),
                        )
                        if (result == MozcBoundaryCheckResult.INVALID) {
                            continue
                        }
                    }

                    val edgeScore = getEdgeCost(
                        rid = prevNode.r.toInt(),
                        lid = currentNode.l.toInt(),
                        connectionMatrix = connectionMatrix,
                    )

                    currentNode.next = element.next?.node
                    val ngramAdjustment = ngramRuleScorer.score(
                        prevNode = prevNode,
                        currentNode = currentNode,
                        nextNode1 = element.next?.node,
                        nextNode2 = element.next?.next?.node,
                        nextNode3 = element.next?.next?.next?.node,
                    )

                    val backwardCost = element.backwardCost +
                        edgeScore +
                        currentNode.adjustedScore +
                        ngramAdjustment
                    prevNode.g = backwardCost
                    prevNode.next = currentNode

                    pQueue.add(
                        PathQueueElement(
                            node = prevNode,
                            priorityCost = backwardCost + prevNode.f,
                            backwardCost = backwardCost,
                            next = element,
                        ),
                    )
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
        connectionMatrixSize: Int,
        n: Int,
        beamWidth: Int = 20,
    ): Pair<List<Candidate>, List<Int>> = backwardAStarWithBunsetsuWithLog(
        graph = graph,
        length = length,
        connectionMatrix = ConnectionMatrix.fromShortArray(connectionIds, connectionMatrixSize),
        n = n,
        beamWidth = beamWidth,
    )

    fun backwardAStarWithBunsetsuWithLog(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionMatrix: ConnectionMatrix.CostTable,
        n: Int,
        beamWidth: Int = 20,
    ): Pair<List<Candidate>, List<Int>> {
        val totalStartTime = System.currentTimeMillis()
        Timber.d("▼ backwardAStarWithBunsetsu 開始 (入力長: $length)")

        val forwardDpStartTime = System.currentTimeMillis()

        forwardDpWithLog(
            graph = graph,
            length = length,
            connectionMatrix = connectionMatrix,
            beamWidth = beamWidth.coerceAtLeast(1),
        )

        val forwardDpTime = System.currentTimeMillis() - forwardDpStartTime
        Timber.d("  ├─ forwardDp 処理時間: ${forwardDpTime}ms")

        val backwardAStarStartTime = System.currentTimeMillis()

        val resultFinal = mutableListOf<Candidate>()
        var bestBunsetsuPositions: List<Int> = emptyList()
        val foundStrings = HashSet<String>()
        val ngramRuleScorer = ngramRuleScorerProvider()

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
                        type = resolveCandidateType(
                            string = stringFromNode,
                            sources = candidateSourcesFromNode(node.first),
                        ),
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
                )

                for (prevNode in prevNodes) {
                    val edgeScore = getEdgeCost(
                        rid = prevNode.r.toInt(),
                        lid = node.first.l.toInt(),
                        connectionMatrix = connectionMatrix,
                    )

                    val ngramAdjustment = ngramRuleScorer.score(
                        prevNode = prevNode,
                        currentNode = node.first,
                        nextNode1 = node.first.next,
                        nextNode2 = node.first.next?.next,
                        nextNode3 = node.first.next?.next?.next,
                    )

                    prevNode.g = node.first.g + edgeScore + node.first.adjustedScore + ngramAdjustment
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

    private fun applyMozcPrefixSuffixPenalty(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        segmenter: MozcSegmenter,
        trace: MutableList<PenaltyTrace>?,
    ) {
        graph.values.flatten().forEach { node ->
            if (node.mozcNodeType != MozcNodeType.NOR) {
                node.adjustedScore = node.score
                return@forEach
            }

            val prefixPenalty = if (node.sPos == 0) {
                segmenter.getPrefixPenalty(node.l.toInt())
            } else {
                0
            }
            val suffixPenalty = if (node.sPos + node.len.toInt() == length) {
                segmenter.getSuffixPenalty(node.r.toInt())
            } else {
                0
            }

            node.adjustedScore = node.score + prefixPenalty + suffixPenalty
            if (prefixPenalty != 0 || suffixPenalty != 0) {
                trace?.add(
                    PenaltyTrace(
                        tango = node.tango,
                        yomiUsed = node.yomiUsed,
                        startPos = node.sPos,
                        endPos = node.sPos + node.len.toInt(),
                        leftId = node.l.toInt(),
                        rightId = node.r.toInt(),
                        baseCost = node.score,
                        prefixPenalty = prefixPenalty,
                        suffixPenalty = suffixPenalty,
                        adjustedCost = node.adjustedScore,
                    ),
                )
            }
        }
    }

    private fun getPathFromNode(node: Node): List<String> {
        var tempNode = node
        val result = mutableListOf<String>()

        while (tempNode.tango != "EOS") {
            tempNode.next?.let {
                if (it.tango != "EOS") {
                    result.add(it.tango)
                }
                tempNode = it
            } ?: break
        }

        return result
    }

    private fun getStringFromPath(path: PathQueueElement): String {
        val result = StringBuilder()
        var current: PathQueueElement? = path
        while (current != null) {
            val node = current.node
            if (node.tango != "BOS" && node.tango != "EOS") {
                result.append(node.tango)
            }
            current = current.next
        }
        return result.toString()
    }

    private fun getYomiUsedFromPath(path: PathQueueElement): String {
        val result = StringBuilder()
        var current: PathQueueElement? = path
        while (current != null) {
            val node = current.node
            if (node.tango != "BOS" && node.tango != "EOS") {
                result.append(node.yomiUsed)
            }
            current = current.next
        }
        return result.toString()
    }

    private fun getPathStringsFromPath(path: PathQueueElement): List<String> {
        val result = mutableListOf<String>()
        var current: PathQueueElement? = path
        while (current != null) {
            val node = current.node
            if (node.tango != "BOS" && node.tango != "EOS") {
                result.add(node.tango)
            }
            current = current.next
        }
        return result
    }

    private fun getBunsetsuPositionsFromPath(
        path: PathQueueElement,
        mozcSegmenter: MozcSegmenter?,
    ): List<Int> {
        val positions = mutableListOf<Int>()
        var currentPosition = 0
        var previousNode: Node? = null

        var current: PathQueueElement? = path
        while (current != null) {
            val node = current.node
            if (node.tango != "BOS" && node.tango != "EOS") {
                val prev = previousNode
                if (currentPosition > 0 && prev != null) {
                    val isBoundary = if (mozcSegmenter != null) {
                        mozcSegmenter.isBoundary(
                            left = prev,
                            right = node,
                            isSingleSegment = false,
                        )
                    } else {
                        isIndependentWord(node.l)
                    }
                    if (isBoundary) {
                        positions.add(currentPosition)
                    }
                }
                currentPosition += node.len.toInt()
                previousNode = node
            }
            current = current.next
        }

        return positions
    }

    private fun getBunsetsuPositions(
        bosNode: Node,
        mozcSegmenter: MozcSegmenter? = null,
    ): List<Int> {
        val positions = mutableListOf<Int>()
        var currentPosition = 0
        var previousNode = bosNode
        var tempNode = bosNode.next

        while (tempNode != null && tempNode.tango != "EOS") {
            if (currentPosition > 0) {
                val isBoundary = if (mozcSegmenter != null) {
                    mozcSegmenter.isBoundary(
                        left = previousNode,
                        right = tempNode,
                        isSingleSegment = false,
                    )
                } else {
                    isIndependentWord(tempNode.l)
                }
                if (isBoundary) {
                    positions.add(currentPosition)
                }
            }
            currentPosition += tempNode.len.toInt()
            previousNode = tempNode
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
