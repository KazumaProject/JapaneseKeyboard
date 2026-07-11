package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node

/** 2〜5ノード補正の判定とスコア加算を担当する。 */
class NgramRuleScorer(
    rules: List<NgramRule>,
) {
    private val rulesByOrderAndCurrentWord: Array<Map<String, List<NgramRule>>> =
        Array(NgramRule.MAX_NODE_COUNT + 1) { emptyMap() }
    private val wildcardRulesByOrder: Array<List<NgramRule>> =
        Array(NgramRule.MAX_NODE_COUNT + 1) { emptyList() }
    private val maxOrderWithRules: Int

    init {
        for (order in NgramRule.MIN_NODE_COUNT..NgramRule.MAX_NODE_COUNT) {
            val rulesOfOrder = rules.filter { it.nodes.size == order }
            rulesByOrderAndCurrentWord[order] = rulesOfOrder
                .filter { it.nodes[1].word != null }
                .groupBy { requireNotNull(it.nodes[1].word) }
            wildcardRulesByOrder[order] = rulesOfOrder.filter { it.nodes[1].word == null }
        }
        maxOrderWithRules = rules.maxOfOrNull { it.nodes.size } ?: 0
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

    fun score(
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
