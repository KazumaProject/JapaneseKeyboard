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
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.EmptySystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.trace.BoundaryTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.CandidateTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.ForwardDpTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.PenaltyTrace
import timber.log.Timber
import java.util.IdentityHashMap
import java.util.PriorityQueue

class FindPath(
    private val ngramRuleScorerProvider: () -> NgramRuleScorer = { defaultNgramRuleScorer },
    private val systemNgramDictionaryProvider: () -> SystemNgramDictionary = { EmptySystemNgramDictionary },
    private var mozcSegmenterProvider: () -> MozcSegmenter? = { null },
    private var mozcBoundaryModeProvider: () -> MozcBoundaryMode = { MozcBoundaryMode.STRICT },
) {

    internal data class ForwardDpCache(
        val inputLength: Int,
        val conversionSignature: Int,
        val connectionMatrixIdentity: Int,
        val beamWidth: Int,
        val nodesBeforePreviousEnd: Map<Int, MutableList<Node>>,
    )

    internal data class PenaltyCache(
        val inputLength: Int,
        val conversionSignature: Int,
        val segmenter: MozcSegmenter,
    )

    class SessionState internal constructor() {
        internal var forwardDpCache: ForwardDpCache? = null
        internal var penaltyCache: PenaltyCache? = null
        internal val backwardSearchScratch = BackwardSearchScratch()
        internal var performanceProbeEnabled: Boolean = false
        internal var lastPenaltyNs: Long = 0
        internal var lastForwardDpNs: Long = 0
        internal var lastBackwardSearchNs: Long = 0
        internal var lastQueueElementCount: Int = 0
        internal var lastStateRejectionCount: Int = 0
        internal var lastExpansionCacheHitCount: Int = 0
        internal var lastExpansionCacheMissCount: Int = 0

        internal fun reset() {
            forwardDpCache = null
            penaltyCache = null
            backwardSearchScratch.reset()
            lastPenaltyNs = 0
            lastForwardDpNs = 0
            lastBackwardSearchNs = 0
            lastQueueElementCount = 0
            lastStateRejectionCount = 0
            lastExpansionCacheHitCount = 0
            lastExpansionCacheMissCount = 0
        }
    }

    @Volatile
    private var forwardDpCache: ForwardDpCache? = null

    fun createSessionState(): SessionState = SessionState()

    internal class PathQueueElement {
        lateinit var node: Node
        var priorityCost: Int = 0
        var backwardCost: Int = 0
        var next: PathQueueElement? = null
        var outputPathId: Int = 0
        var sourceMask: Int = 0

        fun set(
            node: Node,
            priorityCost: Int,
            backwardCost: Int,
            next: PathQueueElement?,
            outputPathId: Int,
            sourceMask: Int,
        ): PathQueueElement {
            this.node = node
            this.priorityCost = priorityCost
            this.backwardCost = backwardCost
            this.next = next
            this.outputPathId = outputPathId
            this.sourceMask = sourceMask
            return this
        }
    }

    /** Canonical persistent character paths make equal output text share one integer id. */
    internal class CharPathArena(initialNodeCapacity: Int = 512) {
        private var characters = CharArray(initialNodeCapacity.coerceAtLeast(2))
        private var nextIds = IntArray(characters.size)
        private var lengths = IntArray(characters.size)
        private var containsDigit = BooleanArray(characters.size)
        private var tableIds = IntArray(tableSize(initialNodeCapacity * 2))
        private var nodeCount = 1 // id 0 is the empty path

        fun reset() {
            nodeCount = 1
            tableIds.fill(0)
        }

        fun prepend(segment: String, suffixId: Int): Int {
            var result = suffixId
            for (index in segment.lastIndex downTo 0) {
                result = intern(segment[index], result)
            }
            return result
        }

        fun prependChar(character: Char, suffixId: Int): Int = intern(character, suffixId)

        fun asString(pathId: Int): String = buildString(lengths[pathId]) {
            var current = pathId
            while (current != 0) {
                append(characters[current])
                current = nextIds[current]
            }
        }

        fun asPositions(pathId: Int): List<Int> {
            val result = ArrayList<Int>(lengths[pathId])
            var current = pathId
            while (current != 0) {
                result.add(characters[current].code)
                current = nextIds[current]
            }
            return result
        }

        fun containsDigit(pathId: Int): Boolean = containsDigit[pathId]

        private fun intern(character: Char, nextId: Int): Int {
            if (nodeCount * 10 >= tableIds.size * 6) growTable()
            var slot = hash(character, nextId) and (tableIds.size - 1)
            while (true) {
                val existingId = tableIds[slot]
                if (existingId == 0) break
                if (characters[existingId] == character && nextIds[existingId] == nextId) {
                    return existingId
                }
                slot = (slot + 1) and (tableIds.size - 1)
            }
            if (nodeCount == characters.size) {
                characters = characters.copyOf(characters.size * 2)
                nextIds = nextIds.copyOf(characters.size)
                lengths = lengths.copyOf(characters.size)
                containsDigit = containsDigit.copyOf(characters.size)
            }
            val newId = nodeCount++
            characters[newId] = character
            nextIds[newId] = nextId
            lengths[newId] = lengths[nextId] + 1
            containsDigit[newId] = character.isDigit() || containsDigit[nextId]
            tableIds[slot] = newId
            return newId
        }

        private fun growTable() {
            tableIds = IntArray(tableIds.size * 2)
            for (id in 1 until nodeCount) {
                var slot = hash(characters[id], nextIds[id]) and (tableIds.size - 1)
                while (tableIds[slot] != 0) slot = (slot + 1) and (tableIds.size - 1)
                tableIds[slot] = id
            }
        }

        private companion object {
            fun tableSize(requested: Int): Int {
                var result = 1
                while (result < requested.coerceAtLeast(16)) result = result shl 1
                return result
            }

            fun hash(character: Char, nextId: Int): Int {
                var result = 31 * character.code + nextId
                result = result xor (result ushr 16)
                result *= -2048144789
                return result xor (result ushr 13)
            }
        }
    }

    internal class PathPriorityQueue(initialCapacity: Int = 256) {
        private var elements = arrayOfNulls<PathQueueElement>(initialCapacity.coerceAtLeast(1))
        var size: Int = 0
            private set

        fun isNotEmpty(): Boolean = size != 0

        fun clear() {
            for (index in 0 until size) elements[index] = null
            size = 0
        }

        fun add(element: PathQueueElement) {
            if (size == elements.size) elements = elements.copyOf(elements.size * 2)
            var index = size++
            while (index > 0) {
                val parentIndex = (index - 1) ushr 1
                val parent = checkNotNull(elements[parentIndex])
                if (compare(element, parent) >= 0) break
                elements[index] = parent
                index = parentIndex
            }
            elements[index] = element
        }

        fun poll(): PathQueueElement? {
            if (size == 0) return null
            val result = elements[0]
            val lastIndex = --size
            val last = elements[lastIndex]
            elements[lastIndex] = null
            if (lastIndex != 0) {
                var index = 0
                val half = size ushr 1
                while (index < half) {
                    var childIndex = (index shl 1) + 1
                    var child = checkNotNull(elements[childIndex])
                    val rightIndex = childIndex + 1
                    if (rightIndex < size) {
                        val right = checkNotNull(elements[rightIndex])
                        if (compare(right, child) < 0) {
                            childIndex = rightIndex
                            child = right
                        }
                    }
                    if (compare(checkNotNull(last), child) <= 0) break
                    elements[index] = child
                    index = childIndex
                }
                elements[index] = last
            }
            return result
        }

        private fun compare(first: PathQueueElement, second: PathQueueElement): Int {
            var comparison = first.priorityCost.compareTo(second.priorityCost)
            if (comparison == 0) comparison = first.node.sPos.compareTo(second.node.sPos)
            if (comparison == 0) comparison = first.node.len.compareTo(second.node.len)
            if (comparison == 0) {
                comparison = System.identityHashCode(first.node)
                    .compareTo(System.identityHashCode(second.node))
            }
            return comparison
        }
    }

    /**
     * Reusable working memory for one conversion session's K-best backward search.
     *
     * The old implementation recreated one queue element, output-path object, state-key object,
     * hash-map entry and priority-queue backing array for practically every explored lattice edge
     * on every key press.  None of those objects escape the query.  A Mozc-style session can own
     * this arena and reuse it after the previous query has completed, while the legacy path still
     * gets an isolated one-shot arena.
     */
    internal class BackwardSearchScratch {
        val queue = PathPriorityQueue()
        val nodeIds = IdentityHashMap<Node, Int>()
        val splitNodeIds = IdentityHashMap<Node, Int>()
        private val splitNodeIdByKey = HashMap<SplitNodeSemanticKey, Int>()
        val bestBackwardCostByState = StateCostTable()
        val bestSplitCostsByState = KBestStateCostTable(MAX_BUNSETSU_SPLIT_PATTERNS)
        val expansionCache = ExpansionCache()
        val charPathArena = CharPathArena()

        private val queueElementPool = ArrayList<PathQueueElement>()
        private var queueElementCount = 0
        var requiredSuffixNodeCount: Int = 0
            private set
        var stateRejectionCount: Int = 0
            private set
        var expansionCacheHitCount: Int = 0
        var expansionCacheMissCount: Int = 0

        fun begin(
            graph: MutableMap<Int, MutableList<Node>>,
            preserveNodeIds: Boolean,
            connectionMatrix: ConnectionMatrix.CostTable,
            ngramRuleScorer: NgramRuleScorer,
            mozcSegmenter: MozcSegmenter?,
            boundaryMode: MozcBoundaryMode?,
        ) {
            queue.clear()
            bestBackwardCostByState.clear()
            bestSplitCostsByState.clear()
            charPathArena.reset()
            queueElementCount = 0
            stateRejectionCount = 0
            expansionCacheHitCount = 0
            expansionCacheMissCount = 0
            if (!preserveNodeIds) {
                nodeIds.clear()
                expansionCache.reset()
                queueElementPool.clear()
            }
            expansionCache.configure(
                connectionMatrix = connectionMatrix,
                ngramRuleScorer = ngramRuleScorer,
                mozcSegmenter = mozcSegmenter,
                boundaryMode = boundaryMode,
            )
            requiredSuffixNodeCount = ngramRuleScorer.requiredSuffixNodeCount
            if (!nodeIds.containsKey(BOS)) nodeIds[BOS] = nodeIds.size
            graph.values.forEach { nodes ->
                nodes.forEach { node ->
                    if (!nodeIds.containsKey(node)) nodeIds[node] = nodeIds.size
                }
            }
        }

        fun reset() {
            queue.clear()
            nodeIds.clear()
            bestBackwardCostByState.clear()
            bestSplitCostsByState.clear()
            expansionCache.reset()
            queueElementPool.clear()
            charPathArena.reset()
            queueElementCount = 0
            stateRejectionCount = 0
            expansionCacheHitCount = 0
            expansionCacheMissCount = 0
        }

        fun queueElementCount(): Int = queueElementCount

        fun recordStateRejection() {
            stateRejectionCount++
        }

        fun queueElement(
            node: Node,
            priorityCost: Int,
            backwardCost: Int,
            next: PathQueueElement?,
            outputPathId: Int,
            sourceMask: Int,
        ): PathQueueElement {
            val element = if (queueElementCount < queueElementPool.size) {
                queueElementPool[queueElementCount]
            } else {
                PathQueueElement().also(queueElementPool::add)
            }
            queueElementCount++
            return element.set(node, priorityCost, backwardCost, next, outputPathId, sourceMask)
        }

        fun prependOutput(segment: String, suffixId: Int): Int =
            charPathArena.prepend(segment, suffixId)

        fun outputString(pathId: Int): String = charPathArena.asString(pathId)

        fun outputContainsDigit(pathId: Int): Boolean = charPathArena.containsDigit(pathId)

        fun prependSplitPosition(position: Int, suffixId: Int): Int =
            charPathArena.prependChar(position.toChar(), suffixId)

        fun splitPositions(pathId: Int): List<Int> = charPathArena.asPositions(pathId)

        fun prepareSplitNodeIds(
            graph: MutableMap<Int, MutableList<Node>>,
            ngramRuleScorer: NgramRuleScorer,
        ) {
            splitNodeIds.clear()
            splitNodeIdByKey.clear()
            fun register(node: Node) {
                val firstYomi = node.yomiUsed.firstOrNull()
                val key = SplitNodeSemanticKey(
                    startPosition = node.sPos,
                    leftId = node.l,
                    adjustedScore = node.adjustedScore,
                    forwardCost = node.f,
                    mozcNodeType = node.mozcNodeType.ordinal,
                    ngramWordClass = ngramRuleScorer.wordClass(node),
                    ngramLeftIdClass = ngramRuleScorer.leftIdClass(node),
                    ngramRightIdClass = ngramRuleScorer.rightIdClass(node),
                    startsWithAsciiAlphabet = firstYomi != null &&
                        (firstYomi in 'a'..'z' || firstYomi in 'A'..'Z'),
                )
                splitNodeIds[node] = splitNodeIdByKey.getOrPut(key) { splitNodeIdByKey.size }
            }
            register(BOS)
            graph.values.forEach { nodes -> nodes.forEach(::register) }
        }
    }

    internal data class SplitNodeSemanticKey(
        val startPosition: Int,
        val leftId: Short,
        val adjustedScore: Int,
        val forwardCost: Int,
        val mozcNodeType: Int,
        val ngramWordClass: Int,
        val ngramLeftIdClass: Int,
        val ngramRightIdClass: Int,
        val startsWithAsciiAlphabet: Boolean,
    )

    internal class ExpansionList(
        val previousNodes: Array<Node?>,
        val localCosts: IntArray,
        val size: Int,
    )

    /**
     * Memoizes the immutable part of a backward expansion.  For an appended input, most lattice
     * nodes and their following 3-node n-gram context are identical to the previous key press.
     * Boundary validation, matrix lookup and n-gram scoring for those states therefore need to be
     * performed only once per session.
     */
    internal class ExpansionCache(initialCapacity: Int = 256) {
        private var currentNode = IntArray(tableSize(initialCapacity))
        private var nextNode1 = IntArray(currentNode.size)
        private var nextNode2 = IntArray(currentNode.size)
        private var nextNode3 = IntArray(currentNode.size)
        private var adjustedScore = IntArray(currentNode.size)
        private var values = arrayOfNulls<ExpansionList>(currentNode.size)
        private var stamps = IntArray(currentNode.size)
        private var generation = 1
        private var entryCount = 0
        private var configured = false
        private var configuredConnectionMatrix: ConnectionMatrix.CostTable? = null
        private var configuredNgramRuleScorer: NgramRuleScorer? = null
        private var configuredMozcSegmenter: MozcSegmenter? = null
        private var configuredBoundaryMode: MozcBoundaryMode? = null

        fun configure(
            connectionMatrix: ConnectionMatrix.CostTable,
            ngramRuleScorer: NgramRuleScorer,
            mozcSegmenter: MozcSegmenter?,
            boundaryMode: MozcBoundaryMode?,
        ) {
            if (
                !configured ||
                configuredConnectionMatrix !== connectionMatrix ||
                configuredNgramRuleScorer !== ngramRuleScorer ||
                configuredMozcSegmenter !== mozcSegmenter ||
                configuredBoundaryMode != boundaryMode
            ) {
                reset()
                configured = true
                configuredConnectionMatrix = connectionMatrix
                configuredNgramRuleScorer = ngramRuleScorer
                configuredMozcSegmenter = mozcSegmenter
                configuredBoundaryMode = boundaryMode
            }
        }

        fun clear() {
            entryCount = 0
            generation++
            if (generation == 0) {
                stamps.fill(0)
                generation = 1
            }
        }

        fun reset() {
            clear()
            values.fill(null)
            configured = false
            configuredConnectionMatrix = null
            configuredNgramRuleScorer = null
            configuredMozcSegmenter = null
            configuredBoundaryMode = null
        }

        fun get(
            currentNode: Int,
            nextNode1: Int,
            nextNode2: Int,
            nextNode3: Int,
            adjustedScore: Int,
        ): ExpansionList? {
            var slot = hash(currentNode, nextNode1, nextNode2, nextNode3, adjustedScore) and
                (stamps.size - 1)
            while (stamps[slot] == generation) {
                if (
                    this.currentNode[slot] == currentNode &&
                    this.nextNode1[slot] == nextNode1 &&
                    this.nextNode2[slot] == nextNode2 &&
                    this.nextNode3[slot] == nextNode3 &&
                    this.adjustedScore[slot] == adjustedScore
                ) return values[slot]
                slot = (slot + 1) and (stamps.size - 1)
            }
            return null
        }

        fun put(
            currentNode: Int,
            nextNode1: Int,
            nextNode2: Int,
            nextNode3: Int,
            adjustedScore: Int,
            value: ExpansionList,
        ) {
            if ((entryCount + 1) * 10 >= stamps.size * 6) grow()
            var slot = hash(currentNode, nextNode1, nextNode2, nextNode3, adjustedScore) and
                (stamps.size - 1)
            while (stamps[slot] == generation) {
                if (
                    this.currentNode[slot] == currentNode &&
                    this.nextNode1[slot] == nextNode1 &&
                    this.nextNode2[slot] == nextNode2 &&
                    this.nextNode3[slot] == nextNode3 &&
                    this.adjustedScore[slot] == adjustedScore
                ) {
                    values[slot] = value
                    return
                }
                slot = (slot + 1) and (stamps.size - 1)
            }
            stamps[slot] = generation
            this.currentNode[slot] = currentNode
            this.nextNode1[slot] = nextNode1
            this.nextNode2[slot] = nextNode2
            this.nextNode3[slot] = nextNode3
            this.adjustedScore[slot] = adjustedScore
            values[slot] = value
            entryCount++
        }

        private fun grow() {
            val oldCurrentNode = currentNode
            val oldNextNode1 = nextNode1
            val oldNextNode2 = nextNode2
            val oldNextNode3 = nextNode3
            val oldAdjustedScore = adjustedScore
            val oldValues = values
            val oldStamps = stamps
            val oldGeneration = generation

            currentNode = IntArray(oldCurrentNode.size * 2)
            nextNode1 = IntArray(currentNode.size)
            nextNode2 = IntArray(currentNode.size)
            nextNode3 = IntArray(currentNode.size)
            adjustedScore = IntArray(currentNode.size)
            values = arrayOfNulls(currentNode.size)
            stamps = IntArray(currentNode.size)
            generation = 1
            entryCount = 0
            oldStamps.indices.forEach { slot ->
                if (oldStamps[slot] == oldGeneration) {
                    put(
                        currentNode = oldCurrentNode[slot],
                        nextNode1 = oldNextNode1[slot],
                        nextNode2 = oldNextNode2[slot],
                        nextNode3 = oldNextNode3[slot],
                        adjustedScore = oldAdjustedScore[slot],
                        value = checkNotNull(oldValues[slot]),
                    )
                }
            }
        }

        private companion object {
            fun tableSize(requested: Int): Int {
                var result = 1
                while (result < requested.coerceAtLeast(16)) result = result shl 1
                return result
            }

            fun hash(
                currentNode: Int,
                nextNode1: Int,
                nextNode2: Int,
                nextNode3: Int,
                adjustedScore: Int,
            ): Int {
                var result = currentNode
                result = 31 * result + nextNode1
                result = 31 * result + nextNode2
                result = 31 * result + nextNode3
                result = 31 * result + adjustedScore
                return result xor (result ushr 16)
            }
        }
    }

    /** Open-addressed state table; clearing it retains capacity and allocates no entry objects. */
    internal class StateCostTable(initialCapacity: Int = 256) {
        private var nodeIdentity = IntArray(tableSize(initialCapacity))
        private var nextIdentity1 = IntArray(nodeIdentity.size)
        private var nextIdentity2 = IntArray(nodeIdentity.size)
        private var nextIdentity3 = IntArray(nodeIdentity.size)
        private var outputPathId = IntArray(nodeIdentity.size)
        private var sourceMask = IntArray(nodeIdentity.size)
        private var costs = IntArray(nodeIdentity.size)
        private var stamps = IntArray(nodeIdentity.size)
        private var generation = 1
        private var entryCount = 0

        fun clear() {
            entryCount = 0
            generation++
            if (generation == 0) {
                stamps.fill(0)
                generation = 1
            }
        }

        fun putIfLower(
            nodeIdentity: Int,
            nextIdentity1: Int,
            nextIdentity2: Int,
            nextIdentity3: Int,
            outputPathId: Int,
            sourceMask: Int,
            cost: Int,
        ): Boolean {
            if ((entryCount + 1) * 10 >= stamps.size * 6) grow()
            var slot = hash(
                nodeIdentity,
                nextIdentity1,
                nextIdentity2,
                nextIdentity3,
                outputPathId,
                sourceMask,
            ) and (stamps.size - 1)
            while (stamps[slot] == generation) {
                if (
                    this.nodeIdentity[slot] == nodeIdentity &&
                    this.nextIdentity1[slot] == nextIdentity1 &&
                    this.nextIdentity2[slot] == nextIdentity2 &&
                    this.nextIdentity3[slot] == nextIdentity3 &&
                    this.sourceMask[slot] == sourceMask &&
                    this.outputPathId[slot] == outputPathId
                ) {
                    if (cost >= costs[slot]) return false
                    costs[slot] = cost
                    return true
                }
                slot = (slot + 1) and (stamps.size - 1)
            }
            stamps[slot] = generation
            this.nodeIdentity[slot] = nodeIdentity
            this.nextIdentity1[slot] = nextIdentity1
            this.nextIdentity2[slot] = nextIdentity2
            this.nextIdentity3[slot] = nextIdentity3
            this.outputPathId[slot] = outputPathId
            this.sourceMask[slot] = sourceMask
            costs[slot] = cost
            entryCount++
            return true
        }

        private fun grow() {
            val oldNodeIdentity = nodeIdentity
            val oldNextIdentity1 = nextIdentity1
            val oldNextIdentity2 = nextIdentity2
            val oldNextIdentity3 = nextIdentity3
            val oldOutputPathId = outputPathId
            val oldSourceMask = sourceMask
            val oldCosts = costs
            val oldStamps = stamps
            val oldGeneration = generation

            nodeIdentity = IntArray(oldNodeIdentity.size * 2)
            nextIdentity1 = IntArray(nodeIdentity.size)
            nextIdentity2 = IntArray(nodeIdentity.size)
            nextIdentity3 = IntArray(nodeIdentity.size)
            outputPathId = IntArray(nodeIdentity.size)
            sourceMask = IntArray(nodeIdentity.size)
            costs = IntArray(nodeIdentity.size)
            stamps = IntArray(nodeIdentity.size)
            generation = 1
            entryCount = 0
            oldStamps.indices.forEach { slot ->
                if (oldStamps[slot] == oldGeneration) {
                    putIfLower(
                        nodeIdentity = oldNodeIdentity[slot],
                        nextIdentity1 = oldNextIdentity1[slot],
                        nextIdentity2 = oldNextIdentity2[slot],
                        nextIdentity3 = oldNextIdentity3[slot],
                        outputPathId = oldOutputPathId[slot],
                        sourceMask = oldSourceMask[slot],
                        cost = oldCosts[slot],
                    )
                }
            }
        }

        private companion object {
            fun tableSize(requested: Int): Int {
                var result = 1
                while (result < requested.coerceAtLeast(16)) result = result shl 1
                return result
            }

            fun hash(
                nodeIdentity: Int,
                nextIdentity1: Int,
                nextIdentity2: Int,
                nextIdentity3: Int,
                outputPathId: Int,
                sourceMask: Int,
            ): Int {
                var result = nodeIdentity
                result = 31 * result + nextIdentity1
                result = 31 * result + nextIdentity2
                result = 31 * result + nextIdentity3
                result = 31 * result + outputPathId
                result = 31 * result + sourceMask
                return result xor (result ushr 16)
            }
        }
    }

    /** Primitive open-addressed table retaining the exact K best suffixes per lattice context. */
    internal class KBestStateCostTable(
        private val k: Int,
        initialCapacity: Int = 256,
    ) {
        private var nodeIdentity = IntArray(tableSize(initialCapacity))
        private var nextIdentity1 = IntArray(nodeIdentity.size)
        private var nextIdentity2 = IntArray(nodeIdentity.size)
        private var nextIdentity3 = IntArray(nodeIdentity.size)
        private var pathIds = IntArray(nodeIdentity.size * k)
        private var costs = IntArray(nodeIdentity.size * k)
        private var counts = ByteArray(nodeIdentity.size)
        private var stamps = IntArray(nodeIdentity.size)
        private var generation = 1
        private var entryCount = 0

        fun clear() {
            entryCount = 0
            generation++
            if (generation == 0) {
                stamps.fill(0)
                generation = 1
            }
        }

        fun putIfAmongLowest(
            nodeIdentity: Int,
            nextIdentity1: Int,
            nextIdentity2: Int,
            nextIdentity3: Int,
            pathId: Int,
            cost: Int,
        ): Boolean {
            if ((entryCount + 1) * 10 >= stamps.size * 6) grow()
            val slot = findSlot(nodeIdentity, nextIdentity1, nextIdentity2, nextIdentity3)
            if (stamps[slot] != generation) {
                stamps[slot] = generation
                this.nodeIdentity[slot] = nodeIdentity
                this.nextIdentity1[slot] = nextIdentity1
                this.nextIdentity2[slot] = nextIdentity2
                this.nextIdentity3[slot] = nextIdentity3
                counts[slot] = 1
                pathIds[slot * k] = pathId
                costs[slot * k] = cost
                entryCount++
                return true
            }

            val base = slot * k
            val count = counts[slot].toInt()
            for (index in 0 until count) {
                if (pathIds[base + index] == pathId) {
                    if (cost >= costs[base + index]) return false
                    costs[base + index] = cost
                    return true
                }
            }
            if (count < k) {
                pathIds[base + count] = pathId
                costs[base + count] = cost
                counts[slot] = (count + 1).toByte()
                return true
            }
            var worstIndex = 0
            for (index in 1 until k) {
                if (costs[base + index] > costs[base + worstIndex]) worstIndex = index
            }
            if (cost >= costs[base + worstIndex]) return false
            pathIds[base + worstIndex] = pathId
            costs[base + worstIndex] = cost
            return true
        }

        fun contains(
            nodeIdentity: Int,
            nextIdentity1: Int,
            nextIdentity2: Int,
            nextIdentity3: Int,
            pathId: Int,
            cost: Int,
        ): Boolean {
            val slot = findSlot(nodeIdentity, nextIdentity1, nextIdentity2, nextIdentity3)
            if (stamps[slot] != generation) return false
            val base = slot * k
            for (index in 0 until counts[slot].toInt()) {
                if (pathIds[base + index] == pathId && costs[base + index] == cost) return true
            }
            return false
        }

        private fun findSlot(
            nodeIdentity: Int,
            nextIdentity1: Int,
            nextIdentity2: Int,
            nextIdentity3: Int,
        ): Int {
            var slot = hash(
                nodeIdentity,
                nextIdentity1,
                nextIdentity2,
                nextIdentity3,
            ) and (stamps.size - 1)
            while (stamps[slot] == generation) {
                if (
                    this.nodeIdentity[slot] == nodeIdentity &&
                    this.nextIdentity1[slot] == nextIdentity1 &&
                    this.nextIdentity2[slot] == nextIdentity2 &&
                    this.nextIdentity3[slot] == nextIdentity3
                ) return slot
                slot = (slot + 1) and (stamps.size - 1)
            }
            return slot
        }

        private fun grow() {
            val oldNodeIdentity = nodeIdentity
            val oldNextIdentity1 = nextIdentity1
            val oldNextIdentity2 = nextIdentity2
            val oldNextIdentity3 = nextIdentity3
            val oldPathIds = pathIds
            val oldCosts = costs
            val oldCounts = counts
            val oldStamps = stamps
            val oldGeneration = generation

            nodeIdentity = IntArray(oldNodeIdentity.size * 2)
            nextIdentity1 = IntArray(nodeIdentity.size)
            nextIdentity2 = IntArray(nodeIdentity.size)
            nextIdentity3 = IntArray(nodeIdentity.size)
            pathIds = IntArray(nodeIdentity.size * k)
            costs = IntArray(nodeIdentity.size * k)
            counts = ByteArray(nodeIdentity.size)
            stamps = IntArray(nodeIdentity.size)
            generation = 1
            entryCount = 0
            oldStamps.indices.forEach { slot ->
                if (oldStamps[slot] == oldGeneration) {
                    val oldBase = slot * k
                    for (index in 0 until oldCounts[slot].toInt()) {
                        putIfAmongLowest(
                            nodeIdentity = oldNodeIdentity[slot],
                            nextIdentity1 = oldNextIdentity1[slot],
                            nextIdentity2 = oldNextIdentity2[slot],
                            nextIdentity3 = oldNextIdentity3[slot],
                            pathId = oldPathIds[oldBase + index],
                            cost = oldCosts[oldBase + index],
                        )
                    }
                }
            }
        }

        private companion object {
            fun tableSize(requested: Int): Int {
                var result = 1
                while (result < requested.coerceAtLeast(16)) result = result shl 1
                return result
            }

            fun hash(
                nodeIdentity: Int,
                nextIdentity1: Int,
                nextIdentity2: Int,
                nextIdentity3: Int,
            ): Int {
                var result = nodeIdentity
                result = 31 * result + nextIdentity1
                result = 31 * result + nextIdentity2
                result = 31 * result + nextIdentity3
                return result xor (result ushr 16)
            }
        }
    }

    companion object {
        private val defaultNgramRuleScorer: NgramRuleScorer = NgramRuleScorer.createDefault()
        private val bosNodes: List<Node> = listOf(BOS)
        private const val MAX_BUNSETSU_SPLIT_PATTERNS = 4
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
        cancellationCheck: () -> Unit = {},
        sessionState: SessionState? = null,
    ): MutableList<Candidate> = backwardAStar(
        graph = graph,
        length = length,
        connectionMatrix = ConnectionMatrix.fromShortArray(connectionIds, connectionMatrixSize),
        n = n,
        beamWidth = beamWidth,
        cancellationCheck = cancellationCheck,
        sessionState = sessionState,
    )

    fun backwardAStar(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionMatrix: ConnectionMatrix.CostTable,
        n: Int,
        beamWidth: Int = 20,
        cancellationCheck: () -> Unit = {},
        sessionState: SessionState? = null,
    ): MutableList<Candidate> {
        cancellationCheck()
        val effectiveBeamWidth = beamWidth.coerceAtLeast(1)
        val incrementalMetadata = graph as? IncrementalGraphMetadata
        val activeCache = if (sessionState != null) sessionState.forwardDpCache else forwardDpCache
        val reusableForwardDp = activeCache?.takeIf { cached ->
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
        forwardDp(
            graph = graph,
            length = length,
            connectionMatrix = connectionMatrix,
            beamWidth = effectiveBeamWidth,
            startPosition = forwardDpStartPosition,
            cancellationCheck = cancellationCheck,
        )
        val updatedCache = incrementalMetadata?.let {
            ForwardDpCache(
                inputLength = length,
                conversionSignature = it.conversionSignature,
                connectionMatrixIdentity = System.identityHashCode(connectionMatrix),
                beamWidth = effectiveBeamWidth,
                nodesBeforePreviousEnd = if (sessionState != null) {
                    // The session graph itself retains the pruned node lists and their forward
                    // costs.  Copying every position here would restore the allocation pattern
                    // that the session backend is intended to remove.
                    emptyMap()
                } else {
                    (0 until length).associateWithTo(LinkedHashMap()) { position ->
                        graph[position]?.toMutableList() ?: mutableListOf()
                    }
                },
            )
        }
        if (sessionState != null) {
            sessionState.forwardDpCache = updatedCache
        } else {
            forwardDpCache = updatedCache
        }

        val resultFinal = mutableListOf<Candidate>()
        val foundStrings = HashSet<String>()
        val ngramRuleScorer = ngramRuleScorerProvider()

        val searchScratch = sessionState?.backwardSearchScratch ?: BackwardSearchScratch()
        searchScratch.begin(
            graph = graph,
            preserveNodeIds = sessionState != null && reusableForwardDp != null,
            connectionMatrix = connectionMatrix,
            ngramRuleScorer = ngramRuleScorer,
            mozcSegmenter = null,
            boundaryMode = null,
        )
        val pQueue = searchScratch.queue

        graph[length + 1]?.get(0)?.let { eos ->
            pQueue.add(
                searchScratch.queueElement(
                    node = eos,
                    priorityCost = 0,
                    backwardCost = 0,
                    next = null,
                    outputPathId = 0,
                    sourceMask = 0,
                )
            )
        } ?: return resultFinal

        var cancellationPollCounter = 0
        while (pQueue.isNotEmpty()) {
            if (cancellationPollCounter++ and 0x3f == 0) cancellationCheck()
            val element = pQueue.poll() ?: break
            val currentNode = element.node

            if (currentNode.tango == "BOS") {
                val stringFromNode = searchScratch.outputString(element.outputPathId)
                val yomiUsedFromNode = getYomiUsedFromPath(element)

                if (foundStrings.add(stringFromNode)) {
                    val candidate = Candidate(
                        string = stringFromNode,
                        type = resolveCandidateType(
                            string = stringFromNode,
                            sources = candidateSourcesFromMask(element.sourceMask),
                        ),
                        yomi = yomiUsedFromNode,
                        length = length.toUByte(),
                        score = element.priorityCost,
                        leftId = element.next?.node?.l,
                        rightId = element.next?.node?.r,
                    )
                    resultFinal.add(candidate)
                }

                if (resultFinal.size >= n) {
                    return resultFinal
                }
            } else {
                if (sessionState != null) {
                    val expansions = cachedBackwardExpansions(
                        graph = graph,
                        element = element,
                        connectionMatrix = connectionMatrix,
                        ngramRuleScorer = ngramRuleScorer,
                        boundaryChecker = null,
                        scratch = searchScratch,
                    )
                    for (index in 0 until expansions.size) {
                        enqueueBackwardExpansion(
                            previousNode = checkNotNull(expansions.previousNodes[index]),
                            localCost = expansions.localCosts[index],
                            element = element,
                            scratch = searchScratch,
                        )
                    }
                } else {
                    val prevNodes = getPrevNodes2(
                        graph = graph,
                        node = currentNode,
                        startPosition = currentNode.sPos,
                    )
                    for (prevNode in prevNodes) {
                        val localCost = getEdgeCost(
                            rid = prevNode.r.toInt(),
                            lid = currentNode.l.toInt(),
                            connectionMatrix = connectionMatrix,
                        ) + currentNode.adjustedScore + ngramRuleScorer.score(
                            prevNode = prevNode,
                            currentNode = currentNode,
                            nextNode1 = element.next?.node,
                            nextNode2 = element.next?.next?.node,
                            nextNode3 = element.next?.next?.next?.node,
                        )
                        enqueueBackwardExpansion(prevNode, localCost, element, searchScratch)
                    }
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
                nodes.subList(beamWidth, nodes.size).clear()
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
    ): List<Node> {
        val index =
            if (node.tango == "EOS") {
                startPosition - 1
            } else {
                startPosition - node.len
            }

        if ((startPosition - node.len) == 0) return bosNodes
        if (index < 0) return emptyList()
        return graph[index] ?: emptyList()
    }

    private fun getPrevNodes2(
        graph: MutableMap<Int, MutableList<Node>>,
        node: Node,
        startPosition: Int,
    ): List<Node> {
        val index =
            if (node.tango == "EOS") {
                startPosition - 1
            } else {
                startPosition
            }

        if (startPosition == 0) return bosNodes
        if (index < 0) return emptyList()
        return graph[index] ?: emptyList()
    }

    private fun cachedBackwardExpansions(
        graph: MutableMap<Int, MutableList<Node>>,
        element: PathQueueElement,
        connectionMatrix: ConnectionMatrix.CostTable,
        ngramRuleScorer: NgramRuleScorer,
        boundaryChecker: MozcBoundaryChecker?,
        scratch: BackwardSearchScratch,
    ): ExpansionList {
        val currentNode = element.node
        val nodeIds = scratch.nodeIds
        val currentNodeId = nodeIds.getValue(currentNode)
        val suffixCount = scratch.requiredSuffixNodeCount
        val nextNode1Id = if (suffixCount >= 1) {
            element.next?.node?.let(nodeIds::getValue) ?: -1
        } else {
            -1
        }
        val nextNode2Id = if (suffixCount >= 2) {
            element.next?.next?.node?.let(nodeIds::getValue) ?: -1
        } else {
            -1
        }
        val nextNode3Id = if (suffixCount >= 3) {
            element.next?.next?.next?.node?.let(nodeIds::getValue) ?: -1
        } else {
            -1
        }
        scratch.expansionCache.get(
            currentNode = currentNodeId,
            nextNode1 = nextNode1Id,
            nextNode2 = nextNode2Id,
            nextNode3 = nextNode3Id,
            adjustedScore = currentNode.adjustedScore,
        )?.let {
            scratch.expansionCacheHitCount++
            return it
        }
        scratch.expansionCacheMissCount++

        val previousNodes = getPrevNodes2(
            graph = graph,
            node = currentNode,
            startPosition = currentNode.sPos,
        )
        val cachedNodes = arrayOfNulls<Node>(previousNodes.size)
        val localCosts = IntArray(previousNodes.size)
        var count = 0
        previousNodes.forEach { previousNode ->
            if (boundaryChecker != null) {
                val isEdge = previousNode.mozcNodeType == MozcNodeType.BOS ||
                    currentNode.mozcNodeType == MozcNodeType.EOS
                if (
                    boundaryChecker.check(previousNode, currentNode, isEdge) ==
                    MozcBoundaryCheckResult.INVALID
                ) return@forEach
            }
            cachedNodes[count] = previousNode
            localCosts[count] = getEdgeCost(
                rid = previousNode.r.toInt(),
                lid = currentNode.l.toInt(),
                connectionMatrix = connectionMatrix,
            ) + currentNode.adjustedScore + ngramRuleScorer.score(
                prevNode = previousNode,
                currentNode = currentNode,
                nextNode1 = element.next?.node,
                nextNode2 = element.next?.next?.node,
                nextNode3 = element.next?.next?.next?.node,
            )
            count++
        }
        return ExpansionList(cachedNodes, localCosts, count).also { result ->
            scratch.expansionCache.put(
                currentNode = currentNodeId,
                nextNode1 = nextNode1Id,
                nextNode2 = nextNode2Id,
                nextNode3 = nextNode3Id,
                adjustedScore = currentNode.adjustedScore,
                value = result,
            )
        }
    }

    private fun enqueueBackwardExpansion(
        previousNode: Node,
        localCost: Int,
        element: PathQueueElement,
        scratch: BackwardSearchScratch,
    ) {
        val backwardCost = element.backwardCost + localCost
        val outputPathId = if (previousNode.tango == "BOS") {
            element.outputPathId
        } else {
            scratch.prependOutput(previousNode.tango, element.outputPathId)
        }
        val sourceMask = element.sourceMask or previousNode.candidateSource.toMask()
        val nodeIds = scratch.nodeIds
        if (
            !scratch.bestBackwardCostByState.putIfLower(
                nodeIdentity = nodeIds.getValue(previousNode),
                nextIdentity1 = nodeIds.getValue(element.node),
                nextIdentity2 = element.next?.node?.let(nodeIds::getValue) ?: -1,
                nextIdentity3 = element.next?.next?.node?.let(nodeIds::getValue) ?: -1,
                outputPathId = outputPathId,
                sourceMask = sourceMask,
                cost = backwardCost,
            )
        ) {
            scratch.recordStateRejection()
            return
        }
        scratch.queue.add(
            scratch.queueElement(
                node = previousNode,
                priorityCost = backwardCost + previousNode.f +
                    if (scratch.outputContainsDigit(outputPathId)) 2000 else 0,
                backwardCost = backwardCost,
                next = element,
                outputPathId = outputPathId,
                sourceMask = sourceMask,
            ),
        )
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

    private fun CandidateSource.toMask(): Int = when (this) {
        CandidateSource.LEARNED_DICTIONARY -> 1
        CandidateSource.USER_DICTIONARY -> 2
        CandidateSource.SYSTEM, CandidateSource.UNKNOWN -> 0
    }

    private fun candidateSourcesFromMask(mask: Int): Sequence<CandidateSource> = sequence {
        if (mask and 1 != 0) yield(CandidateSource.LEARNED_DICTIONARY)
        if (mask and 2 != 0) yield(CandidateSource.USER_DICTIONARY)
    }

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
        cancellationCheck: () -> Unit = {},
        sessionState: SessionState? = null,
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
        cancellationCheck = cancellationCheck,
        sessionState = sessionState,
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
        sessionState: SessionState? = null,
    ): BunsetsuCandidateResult {
        cancellationCheck()
        val performanceState = sessionState?.takeIf { it.performanceProbeEnabled }
        val penaltyStartNs = if (performanceState != null) System.nanoTime() else 0L
        val incrementalMetadata = graph as? IncrementalGraphMetadata
        val mozcSegmenter = mozcSegmenterProvider()
        if (mozcSegmenter != null) {
            val reusablePenalty = sessionState?.penaltyCache?.takeIf { cached ->
                penaltyTrace == null &&
                    incrementalMetadata != null &&
                    incrementalMetadata.reusedThroughEndIndex == cached.inputLength &&
                    incrementalMetadata.conversionSignature == cached.conversionSignature &&
                    length == cached.inputLength + 1 &&
                    cached.segmenter === mozcSegmenter
            }
            if (reusablePenalty != null) {
                applyMozcPrefixSuffixPenaltyToNodes(
                    nodes = graph[reusablePenalty.inputLength].orEmpty(),
                    length = length,
                    segmenter = mozcSegmenter,
                    trace = null,
                )
                applyMozcPrefixSuffixPenaltyToNodes(
                    nodes = graph[length].orEmpty(),
                    length = length,
                    segmenter = mozcSegmenter,
                    trace = null,
                )
            } else {
                applyMozcPrefixSuffixPenalty(
                    graph = graph,
                    length = length,
                    segmenter = mozcSegmenter,
                    trace = penaltyTrace,
                )
            }
            sessionState?.penaltyCache = incrementalMetadata?.let {
                PenaltyCache(length, it.conversionSignature, mozcSegmenter)
            }
        } else {
            sessionState?.penaltyCache = null
        }
        if (performanceState != null) {
            performanceState.lastPenaltyNs = System.nanoTime() - penaltyStartNs
        }

        val effectiveBeamWidth = beamWidth.coerceAtLeast(1)
        val activeCache = if (sessionState != null) sessionState.forwardDpCache else forwardDpCache
        val reusableForwardDp = activeCache?.takeIf { cached ->
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

        val forwardDpStartNs = if (performanceState != null) System.nanoTime() else 0L
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
        if (performanceState != null) {
            performanceState.lastForwardDpNs = System.nanoTime() - forwardDpStartNs
        }

        val updatedCache = if (incrementalMetadata != null) {
            ForwardDpCache(
                inputLength = length,
                conversionSignature = incrementalMetadata.conversionSignature,
                connectionMatrixIdentity = System.identityHashCode(connectionMatrix),
                beamWidth = effectiveBeamWidth,
                nodesBeforePreviousEnd = if (sessionState != null) {
                    emptyMap()
                } else {
                    (0 until length).associateWithTo(LinkedHashMap()) { position ->
                        graph[position]?.toMutableList() ?: mutableListOf()
                    }
                },
            )
        } else {
            null
        }
        if (sessionState != null) {
            sessionState.forwardDpCache = updatedCache
        } else {
            forwardDpCache = updatedCache
        }
        val backwardSearchStartNs = if (performanceState != null) System.nanoTime() else 0L
        var completedQueueElementCount = 0
        var completedStateRejectionCount = 0
        var completedExpansionCacheHitCount = 0
        var completedExpansionCacheMissCount = 0
        fun retainCurrentSearchCounters(scratch: BackwardSearchScratch) {
            completedQueueElementCount += scratch.queueElementCount()
            completedStateRejectionCount += scratch.stateRejectionCount
            completedExpansionCacheHitCount += scratch.expansionCacheHitCount
            completedExpansionCacheMissCount += scratch.expansionCacheMissCount
        }
        fun recordBackwardSearchDuration() {
            if (performanceState != null) {
                performanceState.lastBackwardSearchNs = System.nanoTime() - backwardSearchStartNs
                val scratch = performanceState.backwardSearchScratch
                performanceState.lastQueueElementCount =
                    completedQueueElementCount + scratch.queueElementCount()
                performanceState.lastStateRejectionCount =
                    completedStateRejectionCount + scratch.stateRejectionCount
                performanceState.lastExpansionCacheHitCount =
                    completedExpansionCacheHitCount + scratch.expansionCacheHitCount
                performanceState.lastExpansionCacheMissCount =
                    completedExpansionCacheMissCount + scratch.expansionCacheMissCount
            }
        }

        val boundaryMode = mozcBoundaryModeProvider()
        val boundaryChecker = mozcSegmenter?.let {
            MozcBoundaryChecker(
                segmenter = it,
                mode = boundaryMode,
            )
        }

        val resultFinal = mutableListOf<Candidate>()
        val splitPatterns = mutableListOf<List<Int>>()
        val splitPatternByCandidateString = linkedMapOf<String, List<Int>>()
        val foundStrings = HashSet<String>()
        val ngramRuleScorer = ngramRuleScorerProvider()
        val systemNgramDictionary = systemNgramDictionaryProvider()
        val systemNgramMatchedCandidates = SmallSystemNgramMatchSet()
        val systemNgramMayAffectCandidates =
            systemNgramDictionary.ruleCount != 0 &&
                latticeMayContainSystemNgram(
                    graph = graph,
                    length = length,
                    dictionary = systemNgramDictionary,
                )
        val systemNgramSafetyCandidateCount = if (systemNgramDictionary.ruleCount == 0) {
            n
        } else {
            maxOf(n * 4, 32).coerceAtMost(64)
        }
        val internalCandidateCount = if (!systemNgramMayAffectCandidates) {
            n
        } else {
            systemNgramSafetyCandidateCount
        }

        val searchScratch = sessionState?.backwardSearchScratch ?: BackwardSearchScratch()
        searchScratch.begin(
            graph = graph,
            preserveNodeIds = sessionState != null && reusableForwardDp != null,
            connectionMatrix = connectionMatrix,
            ngramRuleScorer = ngramRuleScorer,
            mozcSegmenter = mozcSegmenter,
            boundaryMode = boundaryMode,
        )
        val pQueue = searchScratch.queue

        graph[length + 1]?.get(0)?.let {
            pQueue.add(
                searchScratch.queueElement(
                    node = it,
                    priorityCost = 0,
                    backwardCost = 0,
                    next = null,
                    outputPathId = 0,
                    sourceMask = 0,
                ),
            )
        }
            ?: return BunsetsuCandidateResult(emptyList(), emptyList()).also {
                recordBackwardSearchDuration()
            }

        var cancellationPollCounter = 0
        while (pQueue.isNotEmpty()) {
            if (cancellationPollCounter++ and 0x3f == 0) cancellationCheck()
            val element = pQueue.poll() ?: break
            val currentNode = element.node

            if (currentNode.tango == "BOS") {
                val stringFromNode = searchScratch.outputString(element.outputPathId)
                val yomiUsedFromNode = getYomiUsedFromPath(element)
                val totalCost = element.priorityCost
                candidateTrace?.add(
                    CandidateTrace(
                        candidate = stringFromNode,
                        yomi = yomiUsedFromNode,
                        totalCost = totalCost,
                        path = getPathStringsFromPath(element),
                    ),
                )

                if (foundStrings.add(stringFromNode)) {
                    if (pathMatchesSystemNgram(element, systemNgramDictionary)) {
                        systemNgramMatchedCandidates.add(stringFromNode)
                    }
                    val bunsetsuPositions = getBunsetsuPositionsFromPath(element)
                    if (
                        splitPatterns.none { it == bunsetsuPositions } &&
                        splitPatterns.size < MAX_BUNSETSU_SPLIT_PATTERNS
                    ) {
                        splitPatterns.add(bunsetsuPositions)
                    }
                    splitPatternByCandidateString[stringFromNode] = bunsetsuPositions

                    val candidate = Candidate(
                        string = stringFromNode,
                        type = resolveCandidateType(
                            string = stringFromNode,
                            sources = candidateSourcesFromMask(element.sourceMask),
                        ),
                        length = length.toUByte(),
                        yomi = yomiUsedFromNode,
                        score = totalCost,
                        leftId = element.next?.node?.l,
                        rightId = element.next?.node?.r,
                    )
                    resultFinal.add(candidate)
                }

                val enoughCandidates = resultFinal.size >= n
                val systemRuleAlreadyMatched = systemNgramMatchedCandidates.isNotEmpty()
                if (
                    systemNgramDictionary.ruleCount != 0 && !systemNgramMayAffectCandidates
                ) {
                    if (enoughCandidates) {
                        retainCurrentSearchCounters(searchScratch)
                        val preservedSplitPatterns = collectBunsetsuSplitPatterns(
                            graph = graph,
                            length = length,
                            initialPatterns = splitPatterns,
                            connectionMatrix = connectionMatrix,
                            ngramRuleScorer = ngramRuleScorer,
                            boundaryChecker = boundaryChecker,
                            mozcSegmenter = mozcSegmenter,
                            boundaryMode = boundaryMode,
                            scratch = searchScratch,
                            cancellationCheck = cancellationCheck,
                        )
                        val ranked = rankSystemNgramCandidates(
                            resultFinal,
                            systemNgramMatchedCandidates,
                            n,
                        )
                        recordBackwardSearchDuration()
                        return BunsetsuCandidateResult(
                            candidates = ranked,
                            splitPatterns = preservedSplitPatterns,
                            splitPatternByCandidateString = splitPatternByCandidateString,
                            systemNgramMatchedCandidates = systemNgramMatchedCandidates,
                        )
                    }
                } else if (
                    resultFinal.size >= internalCandidateCount ||
                        (enoughCandidates && systemRuleAlreadyMatched)
                ) {
                    val ranked = rankSystemNgramCandidates(resultFinal, systemNgramMatchedCandidates, n)
                    recordBackwardSearchDuration()
                    return BunsetsuCandidateResult(
                        candidates = ranked,
                        splitPatterns = splitPatterns,
                        splitPatternByCandidateString = splitPatternByCandidateString,
                        systemNgramMatchedCandidates = systemNgramMatchedCandidates,
                    )
                }
            } else {
                if (sessionState != null && boundaryTrace == null) {
                    val expansions = cachedBackwardExpansions(
                        graph = graph,
                        element = element,
                        connectionMatrix = connectionMatrix,
                        ngramRuleScorer = ngramRuleScorer,
                        boundaryChecker = boundaryChecker,
                        scratch = searchScratch,
                    )
                    for (index in 0 until expansions.size) {
                        enqueueBackwardExpansion(
                            previousNode = checkNotNull(expansions.previousNodes[index]),
                            localCost = expansions.localCosts[index],
                            element = element,
                            scratch = searchScratch,
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
                            val result = boundaryChecker.check(prevNode, currentNode, isEdge)
                            boundaryTrace?.add(
                                BoundaryTrace(
                                    leftTango = prevNode.tango,
                                    rightTango = currentNode.tango,
                                    leftRid = prevNode.r.toInt(),
                                    rightLid = currentNode.l.toInt(),
                                    isEdge = isEdge,
                                    isSingleSegment = boundaryMode == MozcBoundaryMode.ONLY_EDGE,
                                    result = result.name,
                                ),
                            )
                            if (result == MozcBoundaryCheckResult.INVALID) continue
                        }
                        val localCost = getEdgeCost(
                            rid = prevNode.r.toInt(),
                            lid = currentNode.l.toInt(),
                            connectionMatrix = connectionMatrix,
                        ) + currentNode.adjustedScore + ngramRuleScorer.score(
                            prevNode = prevNode,
                            currentNode = currentNode,
                            nextNode1 = element.next?.node,
                            nextNode2 = element.next?.next?.node,
                            nextNode3 = element.next?.next?.next?.node,
                        )
                        enqueueBackwardExpansion(prevNode, localCost, element, searchScratch)
                    }
                }
            }
        }

        val ranked = rankSystemNgramCandidates(resultFinal, systemNgramMatchedCandidates, n)
        recordBackwardSearchDuration()
        return BunsetsuCandidateResult(
            candidates = ranked,
            splitPatterns = splitPatterns,
            splitPatternByCandidateString = splitPatternByCandidateString,
            systemNgramMatchedCandidates = systemNgramMatchedCandidates,
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
        graph.values.forEach { nodes ->
            applyMozcPrefixSuffixPenaltyToNodes(nodes, length, segmenter, trace)
        }
    }

    private fun applyMozcPrefixSuffixPenaltyToNodes(
        nodes: List<Node>,
        length: Int,
        segmenter: MozcSegmenter,
        trace: MutableList<PenaltyTrace>?,
    ) {
        nodes.forEach nodeLoop@{ node ->
            if (node.mozcNodeType != MozcNodeType.NOR) {
                node.adjustedScore = node.score
                return@nodeLoop
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

    private fun rankSystemNgramCandidates(
        candidates: List<Candidate>,
        matched: Set<String>,
        requested: Int,
    ): List<Candidate> = candidates.sortedWith(
        compareByDescending<Candidate> { it.string in matched }.thenBy { it.score },
    ).take(requested)

    private fun pathMatchesSystemNgram(
        path: PathQueueElement,
        dictionary: SystemNgramDictionary,
    ): Boolean {
        if (dictionary.ruleCount == 0) return false
        var start = path.next
        while (start != null && start.node.tango != "EOS") {
            val second = start.next
            if (second == null || second.node.tango == "EOS") return false
            if (
                dictionary.matches(
                    node0 = start.node,
                    node1 = second.node,
                    node2 = second.next?.node,
                    node3 = second.next?.next?.node,
                    node4 = second.next?.next?.next?.node,
                )
            ) return true
            start = start.next
        }
        return false
    }

    /**
     * Proves that no packed system n-gram can occur in the pruned lattice before requesting the
     * old 32–64 candidate safety window.  Every candidate path consists of nodes whose reading
     * ranges are adjacent, so every matching rule must have a first pair represented by one of
     * these edges.  The packed dictionary's prefix index may return a conservative false positive
     * but never a false negative; unknown dictionary implementations keep the old search path.
     */
    private fun latticeMayContainSystemNgram(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        dictionary: SystemNgramDictionary,
    ): Boolean {
        for (leftEnd in 1 until length) {
            val leftNodes = graph[leftEnd] ?: continue
            for (leftNode in leftNodes) {
                if (leftNode.sPos + leftNode.len.toInt() != leftEnd) continue
                if (!dictionary.mayMatchFirstNode(leftNode)) continue
                for (rightEnd in leftEnd + 1..length) {
                    val rightNodes = graph[rightEnd] ?: continue
                    for (rightNode in rightNodes) {
                        if (
                            rightNode.sPos == leftEnd &&
                            dictionary.mayMatchFirstPair(leftNode, rightNode)
                        ) return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Finds the four lowest-cost distinct bunsetsu boundary patterns without enumerating output
     * strings.  Paths with identical node context and boundary suffix have identical remaining
     * costs, so homophones can share one state while candidate search remains fully unchanged.
     */
    private fun collectBunsetsuSplitPatterns(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        initialPatterns: List<List<Int>>,
        connectionMatrix: ConnectionMatrix.CostTable,
        ngramRuleScorer: NgramRuleScorer,
        boundaryChecker: MozcBoundaryChecker?,
        mozcSegmenter: MozcSegmenter?,
        boundaryMode: MozcBoundaryMode,
        scratch: BackwardSearchScratch,
        cancellationCheck: () -> Unit,
    ): List<List<Int>> {
        scratch.begin(
            graph = graph,
            preserveNodeIds = true,
            connectionMatrix = connectionMatrix,
            ngramRuleScorer = ngramRuleScorer,
            mozcSegmenter = mozcSegmenter,
            boundaryMode = boundaryMode,
        )
        scratch.prepareSplitNodeIds(graph, ngramRuleScorer)
        val queue = scratch.queue
        val eos = graph[length + 1]?.firstOrNull() ?: return emptyList()
        queue.add(
            scratch.queueElement(
                node = eos,
                priorityCost = 0,
                backwardCost = 0,
                next = null,
                outputPathId = 0,
                sourceMask = 0,
            ),
        )
        val result = ArrayList<List<Int>>(MAX_BUNSETSU_SPLIT_PATTERNS).apply {
            addAll(initialPatterns.take(MAX_BUNSETSU_SPLIT_PATTERNS))
        }
        if (result.size == MAX_BUNSETSU_SPLIT_PATTERNS) return result
        if (result.size >= 2) {
            val first = result[0]
            val second = result[1]
            if (!first.containsAll(second) && !second.containsAll(first)) {
                val combined = (first + second).distinct().sorted()
                if (result.none { it == combined }) result.add(combined)
                return result
            }
        }
        val examinedPatterns = ArrayList<List<Int>>(MAX_BUNSETSU_SPLIT_PATTERNS)
        var cancellationPollCounter = 0
        while (
            queue.isNotEmpty() &&
            result.size < MAX_BUNSETSU_SPLIT_PATTERNS &&
            examinedPatterns.size < MAX_BUNSETSU_SPLIT_PATTERNS &&
            !(result.size >= 3 && result.any { it.isEmpty() })
        ) {
            if (cancellationPollCounter++ and 0x3f == 0) cancellationCheck()
            val element = queue.poll() ?: break
            val currentNode = element.node
            if (currentNode.tango != "EOS") {
                val nodeIds = scratch.splitNodeIds
                val suffixCount = scratch.requiredSuffixNodeCount
                if (
                    !scratch.bestSplitCostsByState.contains(
                        nodeIdentity = nodeIds.getValue(currentNode),
                        nextIdentity1 = if (suffixCount >= 1) {
                            element.next?.node?.let(nodeIds::getValue) ?: -1
                        } else {
                            -1
                        },
                        nextIdentity2 = if (suffixCount >= 2) {
                            element.next?.next?.node?.let(nodeIds::getValue) ?: -1
                        } else {
                            -1
                        },
                        nextIdentity3 = if (suffixCount >= 3) {
                            element.next?.next?.next?.node?.let(nodeIds::getValue) ?: -1
                        } else {
                            -1
                        },
                        pathId = element.outputPathId,
                        cost = element.backwardCost,
                    )
                ) continue
            }
            if (currentNode.tango == "BOS") {
                val rawPositions = scratch.splitPositions(element.outputPathId)
                if (examinedPatterns.none { it == rawPositions }) {
                    examinedPatterns.add(rawPositions)
                    if (result.none { it == rawPositions } && result.size < 2) {
                        result.add(rawPositions)
                    } else {
                        shiftRefinedSplitPattern(rawPositions, result)?.let { shifted ->
                            if (result.none { it == shifted }) result.add(shifted)
                        }
                    }
                    if (result.size >= 2) {
                        val first = result[0]
                        val second = result[1]
                        if (!first.containsAll(second) && !second.containsAll(first)) {
                            val combined = (first + second).distinct().sorted()
                            if (result.none { it == combined }) result.add(combined)
                            return result
                        }
                    }
                }
                continue
            }
            val expansions = cachedBackwardExpansions(
                graph = graph,
                element = element,
                connectionMatrix = connectionMatrix,
                ngramRuleScorer = ngramRuleScorer,
                boundaryChecker = boundaryChecker,
                scratch = scratch,
            )
            for (index in 0 until expansions.size) {
                enqueueBunsetsuSplitExpansion(
                    previousNode = checkNotNull(expansions.previousNodes[index]),
                    localCost = expansions.localCosts[index],
                    element = element,
                    scratch = scratch,
                )
            }
        }
        return result
    }

    /**
     * A boundary-only search can encounter a refinement of an already returned segmentation
     * before it encounters the equivalent output-distinct path used by the normal candidate
     * search.  The old output-string de-duplication suppresses that refinement: the new boundary
     * moves the preceding split instead of retaining both adjacent splits.  Canonicalizing the
     * refinement here reproduces that observable split-pattern order without enumerating the 32
     * output strings that used to be searched solely for the system n-gram safety window.
     */
    private fun shiftRefinedSplitPattern(
        positions: List<Int>,
        earlierPatterns: List<List<Int>>,
    ): List<Int>? {
        if (earlierPatterns.any { it == positions }) return null
        // When the unsplit reading is already an explicit alternative, retaining both boundaries
        // is itself an observable refinement (rather than a duplicate boundary move).
        if (earlierPatterns.any { it.isEmpty() }) return positions
        val refinedFrom = earlierPatterns.firstOrNull { earlier ->
            positions.size > earlier.size && positions.containsAll(earlier)
        } ?: return null
        val addedBoundary = positions.firstOrNull { it !in refinedFrom } ?: return null
        val precedingBoundary = refinedFrom.lastOrNull { it < addedBoundary } ?: return null
        return positions.filterNot { it == precedingBoundary }
    }

    private fun enqueueBunsetsuSplitExpansion(
        previousNode: Node,
        localCost: Int,
        element: PathQueueElement,
        scratch: BackwardSearchScratch,
    ) {
        val backwardCost = element.backwardCost + localCost
        val currentNode = element.node
        val splitPathId = if (
            currentNode.tango != "EOS" &&
            currentNode.sPos > 0 &&
            isIndependentWord(currentNode.l)
        ) {
            scratch.prependSplitPosition(currentNode.sPos, element.outputPathId)
        } else {
            element.outputPathId
        }
        val nodeIds = scratch.splitNodeIds
        val suffixCount = scratch.requiredSuffixNodeCount
        if (
            !scratch.bestSplitCostsByState.putIfAmongLowest(
                nodeIdentity = nodeIds.getValue(previousNode),
                nextIdentity1 = if (suffixCount >= 1) nodeIds.getValue(currentNode) else -1,
                nextIdentity2 = if (suffixCount >= 2) {
                    element.next?.node?.let(nodeIds::getValue) ?: -1
                } else {
                    -1
                },
                nextIdentity3 = if (suffixCount >= 3) {
                    element.next?.next?.node?.let(nodeIds::getValue) ?: -1
                } else {
                    -1
                },
                pathId = splitPathId,
                cost = backwardCost,
            )
        ) {
            scratch.recordStateRejection()
            return
        }
        scratch.queue.add(
            scratch.queueElement(
                node = previousNode,
                priorityCost = backwardCost + previousNode.f,
                backwardCost = backwardCost,
                next = element,
                outputPathId = splitPathId,
                sourceMask = 0,
            ),
        )
    }

    /** Avoids allocating a hash table for the common one-match conversion path. */
    private class SmallSystemNgramMatchSet : AbstractMutableSet<String>() {
        private var first: String? = null
        private var second: String? = null
        private var third: String? = null
        private var fourth: String? = null
        private var overflow: LinkedHashSet<String>? = null

        override val size: Int
            get() = inlineSize() + (overflow?.size ?: 0)

        override fun contains(element: String): Boolean =
            first == element || second == element || third == element || fourth == element ||
                overflow?.contains(element) == true

        override fun add(element: String): Boolean {
            if (contains(element)) return false
            when (inlineSize()) {
                0 -> first = element
                1 -> second = element
                2 -> third = element
                3 -> fourth = element
                else -> return overflowSet().add(element)
            }
            return true
        }

        override fun iterator(): MutableIterator<String> = object : MutableIterator<String> {
            private var index = 0
            private val overflowIterator by lazy(LazyThreadSafetyMode.NONE) {
                overflow?.iterator() ?: emptyList<String>().iterator()
            }

            override fun hasNext(): Boolean = index < inlineSize() || overflowIterator.hasNext()

            override fun next(): String {
                if (index < inlineSize()) {
                    return when (index++) {
                        0 -> checkNotNull(first)
                        1 -> checkNotNull(second)
                        2 -> checkNotNull(third)
                        else -> checkNotNull(fourth)
                    }
                }
                return overflowIterator.next()
            }

            override fun remove(): Unit = throw UnsupportedOperationException()
        }

        private fun inlineSize(): Int = when {
            fourth != null -> 4
            third != null -> 3
            second != null -> 2
            first != null -> 1
            else -> 0
        }

        private fun overflowSet(): LinkedHashSet<String> = overflow ?: LinkedHashSet<String>().also {
            overflow = it
        }
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
    ): List<Int> {
        val positions = mutableListOf<Int>()
        var currentPosition = 0

        var current: PathQueueElement? = path
        while (current != null) {
            val node = current.node
            if (node.tango != "BOS" && node.tango != "EOS") {
                if (currentPosition > 0) {
                    // Mozc's boundary table constrains conversion paths; it does not represent
                    // the split points used by the IME's sequential bunsetsu conversion UI.
                    // Keep display splitting on the original POS-based rule.
                    val isBoundary = isIndependentWord(node.l)
                    if (isBoundary) {
                        positions.add(currentPosition)
                    }
                }
                currentPosition += node.len.toInt()
            }
            current = current.next
        }

        return positions
    }

    private fun getBunsetsuPositions(
        bosNode: Node,
    ): List<Int> {
        val positions = mutableListOf<Int>()
        var currentPosition = 0
        var tempNode = bosNode.next

        while (tempNode != null && tempNode.tango != "EOS") {
            if (currentPosition > 0) {
                val isBoundary = isIndependentWord(tempNode.l)
                if (isBoundary) {
                    positions.add(currentPosition)
                }
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
