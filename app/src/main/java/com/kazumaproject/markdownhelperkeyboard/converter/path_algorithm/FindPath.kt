package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.core.domain.extensions.isAllFullWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isAllHalfWidthNumericSymbol
import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcBoundaryCheckResult
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcBoundaryChecker
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcBoundaryMode
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.trace.BoundaryTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.CandidateTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.ForwardDpTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.PenaltyTrace
import timber.log.Timber
import java.math.BigInteger
import java.util.IdentityHashMap
import java.util.PriorityQueue

class FindPath(
    private val ngramRuleScorerProvider: () -> NgramRuleScorer = { defaultNgramRuleScorer },
    private var mozcSegmenterProvider: () -> MozcSegmenter? = { null },
    private var mozcBoundaryModeProvider: () -> MozcBoundaryMode = { MozcBoundaryMode.STRICT },
    private val equivalentPathPruningEnabled: Boolean = true,
) {

    private data class PathQueueElement(
        val node: Node,
        val priorityCost: Int,
        val backwardCost: Int,
        val path: LinkedPathNode,
        val state: PathState,
        val equivalentKey: EquivalentPathKey?,
    )

    private data class LinkedPathNode(
        val node: Node,
        val next: LinkedPathNode?,
    )

    private data class NodeStateKey(
        val leftId: Short,
        val rightId: Short,
        val score: Int,
        val forwardCost: Int,
        val tango: String,
        val length: Short,
        val yomiUsed: String,
        val startPosition: Int,
        val adjustedScore: Int,
        val mozcNodeType: MozcNodeType,
        val mozcAttributes: Int,
    )

    private data class PathState(
        val surface: TextPath?,
        val yomi: TextPath?,
        val boundaries: BigInteger,
        val readingLength: Int,
    )

    private data class EquivalentPathKey(
        val node: NodeStateKey,
        val nextNode: NodeStateKey,
        val surface: TextPath?,
        val yomi: TextPath?,
        val boundaries: BigInteger,
        val readingLength: Int,
    )

    private class NodePairKey(
        private val left: Node,
        private val right: Node,
    ) {
        private val cachedHash =
            System.identityHashCode(left) * 31 + System.identityHashCode(right)

        override fun hashCode(): Int = cachedHash

        override fun equals(other: Any?): Boolean =
            other is NodePairKey && left === other.left && right === other.right
    }

    private class TextPath(
        private val piece: String,
        private val next: TextPath?,
        val length: Int,
        private val contentHash: Int,
    ) {
        override fun hashCode(): Int = contentHash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TextPath) return false
            if (length != other.length || contentHash != other.contentHash) return false

            var left: TextPath? = this
            var right: TextPath? = other
            var leftOffset = 0
            var rightOffset = 0
            while (left != null && right != null) {
                val count = minOf(
                    left.piece.length - leftOffset,
                    right.piece.length - rightOffset,
                )
                for (index in 0 until count) {
                    if (left.piece[leftOffset + index] != right.piece[rightOffset + index]) {
                        return false
                    }
                }
                leftOffset += count
                rightOffset += count
                if (leftOffset == left.piece.length) {
                    left = left.next
                    leftOffset = 0
                }
                if (rightOffset == right.piece.length) {
                    right = right.next
                    rightOffset = 0
                }
            }
            return left == null && right == null
        }
    }

    private class TextPathFactory {
        private val powersOf31 = mutableListOf(1)

        fun prepend(piece: String, next: TextPath?): TextPath? {
            if (piece.isEmpty()) return next
            val suffixLength = next?.length ?: 0
            while (powersOf31.size <= suffixLength) {
                powersOf31.add(powersOf31.last() * 31)
            }
            return TextPath(
                piece = piece,
                next = next,
                length = piece.length + suffixLength,
                contentHash = piece.hashCode() * powersOf31[suffixLength] +
                    (next?.hashCode() ?: 0),
            )
        }
    }

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
    ): BunsetsuCandidateResult {
        val mozcSegmenter = mozcSegmenterProvider()
        if (mozcSegmenter != null) {
            applyMozcPrefixSuffixPenalty(
                graph = graph,
                length = length,
                segmenter = mozcSegmenter,
                trace = penaltyTrace,
            )
        }

        forwardDpTraceSink = forwardDpTrace
        try {
            forwardDp(
                graph = graph,
                length = length,
                connectionMatrix = connectionMatrix,
                beamWidth = beamWidth.coerceAtLeast(1),
            )
        } finally {
            forwardDpTraceSink = null
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
        val pruneEquivalentPaths = equivalentPathPruningEnabled &&
            penaltyTrace == null &&
            forwardDpTrace == null &&
            boundaryTrace == null &&
            candidateTrace == null
        val textPathFactory = TextPathFactory()
        val nodeStateKeys = IdentityHashMap<Node, NodeStateKey>()
        val boundaryByEdge = HashMap<NodePairKey, Boolean>()
        val bestBackwardCostByEquivalentPath = HashMap<EquivalentPathKey, Int>()
        val emptyPathState = PathState(
            surface = null,
            yomi = null,
            boundaries = BigInteger.ZERO,
            readingLength = 0,
        )

        fun nodeStateKey(node: Node): NodeStateKey = nodeStateKeys.getOrPut(node) {
            NodeStateKey(
                leftId = node.l,
                rightId = node.r,
                score = node.score,
                forwardCost = node.f,
                tango = node.tango,
                length = node.len,
                yomiUsed = node.yomiUsed,
                startPosition = node.sPos,
                adjustedScore = node.adjustedScore,
                mozcNodeType = node.mozcNodeType,
                mozcAttributes = node.mozcAttributes,
            )
        }

        fun isBunsetsuBoundary(left: Node, right: Node): Boolean {
            if (left.tango == "BOS" || right.tango == "EOS") return false
            return boundaryByEdge.getOrPut(NodePairKey(left, right)) {
                if (mozcSegmenter != null) {
                    mozcSegmenter.isBoundary(
                        left = left,
                        right = right,
                        isSingleSegment = false,
                    )
                } else {
                    isIndependentWord(right.l)
                }
            }
        }

        fun prependPathState(
            node: Node,
            nextNode: Node,
            suffix: PathState,
        ): PathState {
            if (node.tango == "BOS" || node.tango == "EOS") return suffix

            val nodeLength = node.len.toInt()
            var boundaries = suffix.boundaries.shiftLeft(nodeLength)
            if (isBunsetsuBoundary(node, nextNode)) {
                boundaries = boundaries.setBit(nodeLength)
            }
            return PathState(
                surface = textPathFactory.prepend(node.tango, suffix.surface),
                yomi = textPathFactory.prepend(node.yomiUsed, suffix.yomi),
                boundaries = boundaries,
                readingLength = nodeLength + suffix.readingLength,
            )
        }

        val pQueue: PriorityQueue<PathQueueElement> =
            PriorityQueue(
                compareBy<PathQueueElement> { it.priorityCost }
                    .thenBy { it.node.sPos }
                    .thenBy { it.node.len }
                    .thenBy { System.identityHashCode(it.node) },
            )

        graph[length + 1]?.get(0)?.let {
            pQueue.add(
                PathQueueElement(
                    node = it,
                    priorityCost = 0,
                    backwardCost = 0,
                    path = LinkedPathNode(it, null),
                    state = emptyPathState,
                    equivalentKey = null,
                ),
            )
        }
            ?: return BunsetsuCandidateResult(emptyList(), emptyList())

        while (pQueue.isNotEmpty()) {
            val element = pQueue.poll() ?: break
            if (pruneEquivalentPaths) {
                val key = element.equivalentKey
                if (key != null && bestBackwardCostByEquivalentPath[key] != element.backwardCost) {
                    continue
                }
            }
            val currentNode = element.node

            if (currentNode.tango == "BOS") {
                val stringFromNode = getStringFromPath(element.path)
                val yomiUsedFromNode = getYomiUsedFromPath(element.path)
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
                        path = getPathStringsFromPath(element.path),
                    ),
                )

                if (foundStrings.add(stringFromNode)) {
                    val bunsetsuPositions = getBunsetsuPositionsFromPath(element.path, mozcSegmenter)
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
                        score = totalCost,
                        leftId = element.path.next?.node?.l,
                        rightId = element.path.next?.node?.r,
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

                    currentNode.next = element.path.next?.node
                    val ngramAdjustment = ngramRuleScorer.score(
                        prevNode = prevNode,
                        currentNode = currentNode,
                    )

                    val backwardCost = element.backwardCost +
                        edgeScore +
                        currentNode.adjustedScore +
                        ngramAdjustment
                    prevNode.g = backwardCost
                    prevNode.next = currentNode

                    val path = LinkedPathNode(prevNode, element.path)
                    val state = prependPathState(prevNode, currentNode, element.state)
                    val equivalentKey = if (pruneEquivalentPaths) {
                        EquivalentPathKey(
                            node = nodeStateKey(prevNode),
                            nextNode = nodeStateKey(currentNode),
                            surface = state.surface,
                            yomi = state.yomi,
                            boundaries = state.boundaries,
                            readingLength = state.readingLength,
                        )
                    } else {
                        null
                    }
                    if (equivalentKey != null) {
                        val bestCost = bestBackwardCostByEquivalentPath[equivalentKey]
                        if (bestCost != null && bestCost <= backwardCost) {
                            continue
                        }
                        bestBackwardCostByEquivalentPath[equivalentKey] = backwardCost
                    }

                    pQueue.add(
                        PathQueueElement(
                            node = prevNode,
                            priorityCost = backwardCost + prevNode.f,
                            backwardCost = backwardCost,
                            path = path,
                            state = state,
                            equivalentKey = equivalentKey,
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

    private fun getStringFromPath(path: LinkedPathNode): String {
        val result = StringBuilder()
        var current: LinkedPathNode? = path
        while (current != null) {
            val node = current.node
            if (node.tango != "BOS" && node.tango != "EOS") {
                result.append(node.tango)
            }
            current = current.next
        }
        return result.toString()
    }

    private fun getYomiUsedFromPath(path: LinkedPathNode): String {
        val result = StringBuilder()
        var current: LinkedPathNode? = path
        while (current != null) {
            val node = current.node
            if (node.tango != "BOS" && node.tango != "EOS") {
                result.append(node.yomiUsed)
            }
            current = current.next
        }
        return result.toString()
    }

    private fun getPathStringsFromPath(path: LinkedPathNode): List<String> {
        val result = mutableListOf<String>()
        var current: LinkedPathNode? = path
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
        path: LinkedPathNode,
        mozcSegmenter: MozcSegmenter?,
    ): List<Int> {
        val positions = mutableListOf<Int>()
        var currentPosition = 0
        var previousNode: Node? = null

        var current: LinkedPathNode? = path
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
