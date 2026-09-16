package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.graph.LexicalPart
import java.util.IdentityHashMap

/** 2〜5ノード補正の判定とスコア加算を担当する。 */
class NgramRuleScorer(
    private val rules: List<NgramRule>,
    private val quantityModel: com.kazumaproject.quantity.QuantityScoringModel? = null,
) {
    private val semantic = quantityModel?.takeIf { it.rules.isNotEmpty() }?.let(::QuantityContextScorer)
    internal fun withQuantityModel(model: com.kazumaproject.quantity.QuantityScoringModel): NgramRuleScorer =
        NgramRuleScorer(rules, model)
    private val relevantWordClasses: Map<String, Int> = rules
        .asSequence()
        .flatMap { it.nodes.asSequence() }
        .mapNotNull { it.word }
        .distinct()
        .withIndex()
        .associate { (index, word) -> word to index + 1 }
    private val relevantLeftIdClasses: IntArray = rules
        .asSequence()
        .flatMap { it.nodes.asSequence() }
        .mapNotNull { it.leftId }
        .distinct()
        .withIndex()
        .toList().let { ids ->
            IntArray((ids.maxOfOrNull { it.value.toInt() and 0xffff } ?: -1) + 1).also { classes ->
                for ((index, id) in ids) classes[id.toInt() and 0xffff] = index + 1
            }
        }
    private val relevantRightIdClasses: IntArray = rules
        .asSequence()
        .flatMap { it.nodes.asSequence() }
        .mapNotNull { it.rightId }
        .distinct()
        .withIndex()
        .toList().let { ids ->
            IntArray((ids.maxOfOrNull { it.value.toInt() and 0xffff } ?: -1) + 1).also { classes ->
                for ((index, id) in ids) classes[id.toInt() and 0xffff] = index + 1
            }
        }
    private val rulesByOrderAndCurrentWord: Array<Map<String, List<NgramRule>>> =
        Array(NgramRule.MAX_NODE_COUNT + 1) { emptyMap() }
    private val wildcardRulesByOrder: Array<List<NgramRule>> =
        Array(NgramRule.MAX_NODE_COUNT + 1) { emptyList() }
    private val firstFeatures = rules.map { it.nodes.first() }.distinct()
    private val maxOrderWithRules: Int
    internal val contextNodeCount: Int get() = (maxOf(maxOrderWithRules, semantic?.maxOrder ?: 0) - 1).coerceAtLeast(0)
    internal val requiredSuffixNodeCount: Int

    init {
        for (order in NgramRule.MIN_NODE_COUNT..NgramRule.MAX_NODE_COUNT) {
            val rulesOfOrder = rules.filter { it.nodes.size == order }
            rulesByOrderAndCurrentWord[order] = rulesOfOrder
                .filter { it.nodes[1].word != null }
                .groupBy { requireNotNull(it.nodes[1].word) }
            wildcardRulesByOrder[order] = rulesOfOrder.filter { it.nodes[1].word == null }
        }
        maxOrderWithRules = rules.maxOfOrNull { it.nodes.size } ?: 0
        requiredSuffixNodeCount = (maxOf(maxOrderWithRules, semantic?.maxOrder ?: 0) - 2).coerceAtLeast(0)
    }

    /** Compatibility constructor while callers migrate to the common model. */
    constructor(
        twoNodeRules: List<TwoNodeRule>,
        threeNodeRules: List<ThreeNodeRule>,
    ) : this(
        rules = twoNodeRules.map {
            NgramRule(listOf(it.prev, it.current), it.adjustment)
        } + threeNodeRules.map {
            NgramRule(listOf(it.first, it.second, it.third), it.adjustment)
        },
    )

    private class Composite(val parts: List<LexicalPart>, var wordClass: Int? = null) {
        val nodes by lazy { parts.map { Node(it.left, it.right, 0, 0, tango = it.text, len = 0, yomiUsed = "", sPos = 0) } }
    }
    // Parts are immutable and shared by copied path nodes. Identity lookup avoids
    // allocating or hashing an entire feature signature on every search transition.
    // Bound retained input data because a scorer can outlive a conversion session.
    private val composites = IdentityHashMap<List<LexicalPart>, Composite>()
    private var cachedPartCount = 0
    private fun composite(parts: List<LexicalPart>): Composite = synchronized(composites) {
        composites[parts] ?: run {
            if (composites.size >= 1024 || cachedPartCount + parts.size > 16384) {
                composites.clear()
                cachedPartCount = 0
            }
            cachedPartCount += parts.size
            Composite(parts)
                .also { composites[parts] = it }
        }
    }
    private fun expand(node: Node): List<Node> =
        if (node.lexicalParts.isEmpty()) listOf(node) else composite(node.lexicalParts).nodes

    fun score(prevNode: Node, currentNode: Node, nextNode1: Node? = currentNode.next,
              nextNode2: Node? = nextNode1?.next, nextNode3: Node? = nextNode2?.next): Int =
        lexicalScore(prevNode, currentNode, nextNode1, nextNode2, nextNode3) +
            (semantic?.starting(prevNode, currentNode, nextNode1, nextNode2, nextNode3) ?: 0)

    private fun lexicalScore(prevNode: Node, currentNode: Node, nextNode1: Node?, nextNode2: Node?, nextNode3: Node?): Int {
        if (maxOrderWithRules == 0) return 0
        if (prevNode.lexicalParts.isEmpty()) {
            if (firstFeatures.none { it.matches(prevNode) }) return 0
        } else if (prevNode.lexicalParts.none { part -> firstFeatures.any { feature ->
            (feature.word == null || feature.word == part.text) &&
                (feature.leftId == null || feature.leftId == part.left) &&
                (feature.rightId == null || feature.rightId == part.right)
        } }) return 0
        if (prevNode.lexicalParts.isEmpty() && currentNode.lexicalParts.isEmpty() &&
            nextNode1?.lexicalParts.isNullOrEmpty() && nextNode2?.lexicalParts.isNullOrEmpty() && nextNode3?.lexicalParts.isNullOrEmpty())
            return scoreRaw(prevNode, currentNode, nextNode1, nextNode2, nextNode3)
        val first = if (prevNode.lexicalParts.isEmpty()) null else composite(prevNode.lexicalParts).nodes
        val second = if (currentNode.lexicalParts.isEmpty()) null else composite(currentNode.lexicalParts).nodes
        val third = nextNode1?.takeIf { it.lexicalParts.isNotEmpty() }?.let { composite(it.lexicalParts).nodes }
        val fourth = nextNode2?.takeIf { it.lexicalParts.isNotEmpty() }?.let { composite(it.lexicalParts).nodes }
        val fifth = nextNode3?.takeIf { it.lexicalParts.isNotEmpty() }?.let { composite(it.lexicalParts).nodes }
        val firstSize = first?.size ?: 1
        val secondSize = second?.size ?: 1
        val thirdSize = third?.size ?: if (nextNode1 == null) 0 else 1
        val fourthSize = fourth?.size ?: if (nextNode2 == null) 0 else 1
        fun at(index: Int): Node? {
            var offset = index
            if (offset < firstSize) return first?.get(offset) ?: prevNode
            offset -= firstSize
            if (offset < secondSize) return second?.get(offset) ?: currentNode
            offset -= secondSize
            if (offset < thirdSize) return third?.get(offset) ?: nextNode1
            offset -= thirdSize
            if (offset < fourthSize) return fourth?.get(offset) ?: nextNode2
            offset -= fourthSize
            return fifth?.getOrNull(offset) ?: nextNode3?.takeIf { offset == 0 }
        }
        var total = 0
        for (index in 0 until firstSize) {
            val current = at(index + 1) ?: continue
            total += scoreRaw(at(index)!!, current, at(index + 2), at(index + 3), at(index + 4))
        }
        return total
    }

    private fun scoreRaw(
        prevNode: Node,
        currentNode: Node,
        nextNode1: Node? = currentNode.next,
        nextNode2: Node? = nextNode1?.next,
        nextNode3: Node? = nextNode2?.next,
    ): Int {
        if (prevNode.tango == "BOS" || currentNode.tango == "EOS") return 0
        if (maxOrderWithRules == 0) return 0

        var total = 0L
        for (order in NgramRule.MIN_NODE_COUNT..maxOrderWithRules) {
            if (order >= 3 && (nextNode1 == null || nextNode1.tango == "EOS")) continue
            if (order >= 4 && (nextNode2 == null || nextNode2.tango == "EOS")) continue
            if (order >= 5 && (nextNode3 == null || nextNode3.tango == "EOS")) continue

            total += scoreBucket(
                rulesByOrderAndCurrentWord[order][currentNode.tango],
                prevNode,
                currentNode,
                nextNode1,
                nextNode2,
                nextNode3,
            )
            total += scoreBucket(
                wildcardRulesByOrder[order],
                prevNode,
                currentNode,
                nextNode1,
                nextNode2,
                nextNode3,
            )
        }

        return total.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    private val forwardPrefixes = rules.flatMap { rule ->
        (1 until rule.nodes.size).map { rule.nodes.take(it) }
    }.distinct().groupBy { it.size to it.first().word }
    private val forwardContextLengths = object : LinkedHashMap<List<Triple<Int, Int, Int>>, Int>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Triple<Int, Int, Int>>, Int>?) = size > 4096
    }

    /** Only a suffix matching a rule prefix can affect a future N-gram score. */
    internal fun futureContext(nodes: List<Node>): List<Node> {
        if (contextNodeCount == 0) return emptyList()
        if (semantic == null) return futureLexicalContext(nodes)
        val lexical = futureLexicalContext(nodes)
        var remaining = lexical.size
        var macroCount = 0
        for (at in nodes.indices.reversed()) {
            if (remaining <= 0) break
            remaining -= nodes[at].lexicalParts.size.coerceAtLeast(1)
            macroCount++
        }
        // Preserve both channels without flattening a quantity boundary or
        // retaining histories that no lexical or semantic rule can observe.
        return nodes.takeLast(maxOf(macroCount, semantic.contextLength(nodes)))
    }

    private fun futureLexicalContext(nodes: List<Node>): List<Node> {
        if (maxOrderWithRules == 0) return emptyList()
        val tail = nodes.flatMap(::expand).takeLast(contextNodeCount)
        val key = tail.map { Triple(wordClass(it), leftIdClass(it), rightIdClass(it)) }
        val retained = synchronized(forwardContextLengths) {
            forwardContextLengths.getOrPut(key) {
                (tail.size downTo 1).firstOrNull { size ->
                    val suffix = tail.takeLast(size)
                    fun matches(prefix: List<NodeFeature>) = prefix.indices.all { prefix[it].matches(suffix[it]) }
                    forwardPrefixes[size to suffix.first().tango].orEmpty().any(::matches) ||
                        forwardPrefixes[size to null].orEmpty().any(::matches)
                } ?: 0
            }
        }
        return tail.takeLast(retained)
    }

    /** Forward evaluation: charge each rule once, when its last node is appended. */
    internal fun scoreEndingAt(nodes: List<Node>): Int =
        rawScoreEndingAt(nodes).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    internal fun rawScoreEndingAt(nodes: List<Node>): Long =
        rawLexicalScoreEndingAt(nodes) + (semantic?.ending(nodes) ?: 0)

    private fun rawLexicalScoreEndingAt(nodes: List<Node>): Long {
        if (maxOrderWithRules == 0) return 0L
        val expanded = if (nodes.all { it.lexicalParts.isEmpty() }) nodes else nodes.flatMap(::expand)
        val added = nodes.lastOrNull()?.let { it.lexicalParts.size.coerceAtLeast(1) } ?: 0
        var total = 0L
        for (end in expanded.size - added + 1..expanded.size) for (order in NgramRule.MIN_NODE_COUNT..maxOrderWithRules) {
            if (end < order) continue
            val part = expanded.subList(end - order, end)
            if (part.first().tango == "BOS" || part.last().tango == "EOS") continue
            total += scoreBucket(rulesByOrderAndCurrentWord[order][part[1].tango],
                part[0], part[1], part.getOrNull(2), part.getOrNull(3), part.getOrNull(4))
            total += scoreBucket(wildcardRulesByOrder[order],
                part[0], part[1], part.getOrNull(2), part.getOrNull(3), part.getOrNull(4))
        }
        return total
    }

    /** Exact equivalence classes for every node feature observable by this scorer. */
    private val compositeClasses = java.util.concurrent.ConcurrentHashMap<List<Triple<Int, Int, Int>>, Int>()
    private data class SemanticClass(val word: Int, val quantity: Long, val lexical: Long)
    private val semanticClasses = HashMap<SemanticClass, Int>()
    internal fun wordClass(node: Node): Int {
        val word = lexicalWordClass(node)
        if (semantic == null || node.quantityClasses == 0L && node.lexicalClasses == 0L) return word
        val key = SemanticClass(word, node.quantityClasses, node.lexicalClasses)
        return synchronized(semanticClasses) { semanticClasses.getOrPut(key) { -semanticClasses.size - 1 } }
    }
    private fun lexicalWordClass(node: Node): Int {
        if (node.lexicalParts.isEmpty()) return relevantWordClasses[node.tango] ?: 0
        val composite = composite(node.lexicalParts)
        return synchronized(composite) {
            composite.wordClass ?: run {
                val signature = node.lexicalParts.map { Triple(relevantWordClasses[it.text] ?: 0,
                    relevantLeftIdClasses.classAt(it.left), relevantRightIdClasses.classAt(it.right)) }
                synchronized(compositeClasses) {
                    compositeClasses.getOrPut(signature) { relevantWordClasses.size + compositeClasses.size + 1 }
                }.also { composite.wordClass = it }
            }
        }
    }

    private fun IntArray.classAt(id: Short): Int {
        val index = id.toInt() and 0xffff
        return if (index < size) this[index] else 0
    }

    internal fun leftIdClass(node: Node): Int = relevantLeftIdClasses.classAt(node.l)

    internal fun rightIdClass(node: Node): Int = relevantRightIdClasses.classAt(node.r)

    private fun scoreBucket(
        rules: List<NgramRule>?,
        node0: Node,
        node1: Node,
        node2: Node?,
        node3: Node?,
        node4: Node?,
    ): Long {
        if (rules.isNullOrEmpty()) return 0L
        var total = 0L
        for (rule in rules) {
            val features = rule.nodes
            if (!features[0].matches(node0) || !features[1].matches(node1)) continue
            if (features.size >= 3 && !features[2].matches(node2 ?: continue)) continue
            if (features.size >= 4 && !features[3].matches(node3 ?: continue)) continue
            if (features.size >= 5 && !features[4].matches(node4 ?: continue)) continue
            total += rule.adjustment.toLong()
        }
        return total
    }

    companion object {
        /**
         * 現時点の実経路ログ
         *   布 -> で -> 服   = 12154
         *   布 -> で -> 拭く = 12886
         * 差分 732 を参考に、まずは控えめな補正値を入れる。
         *
         * 必要に応じてここへルールを追加していく。
         */
        fun createDefault(): NgramRuleScorer {
            return NgramRuleScorer(defaultRules())
        }

        fun defaultRules(): List<NgramRule> =
            defaultTwoNodeRules().map { NgramRule(listOf(it.prev, it.current), it.adjustment) } +
                defaultThreeNodeRules().map {
                    NgramRule(listOf(it.first, it.second, it.third), it.adjustment)
                }

        fun defaultTwoNodeRules(): List<TwoNodeRule> = listOf(
                TwoNodeRule(
                    prev = NodeFeature(
                        word = "粋で"
                    ),
                    current = NodeFeature(
                        word = "いなせ"
                    ),
                    adjustment = -12000,
                )
            )

        fun defaultThreeNodeRules(): List<ThreeNodeRule> = listOf(
                ThreeNodeRule(
                    first = NodeFeature(leftId = 1851, rightId = 1851),
                    second = NodeFeature(word = "で"),
                    third = NodeFeature(word = "拭く"),
                    adjustment = -2000,
                ),
                ThreeNodeRule(
                    first = NodeFeature(leftId = 1851, rightId = 1851),
                    second = NodeFeature(word = "を"),
                    third = NodeFeature(word = "吹く"),
                    adjustment = -2000,
                ),
                ThreeNodeRule(
                    first = NodeFeature(word = "精度"),
                    second = NodeFeature(word = "が"),
                    third = NodeFeature(word = "高い"),
                    adjustment = -2000,
                ),
                ThreeNodeRule(
                    first = NodeFeature(word = "精度"),
                    second = NodeFeature(word = "の"),
                    third = NodeFeature(word = "高い"),
                    adjustment = -2000,
                ),
                ThreeNodeRule(
                    first = NodeFeature(word = "衛星"),
                    second = NodeFeature(word = "は"),
                    third = NodeFeature(word = "分離"),
                    adjustment = -2000,
                ),
                ThreeNodeRule(
                    first = NodeFeature(word = "衛星"),
                    second = NodeFeature(word = "が"),
                    third = NodeFeature(word = "分離"),
                    adjustment = -2000,
                ),
            )
    }
}
