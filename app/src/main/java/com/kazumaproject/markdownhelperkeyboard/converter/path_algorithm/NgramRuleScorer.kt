package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node

/**
 * 2ノード / 3ノード補正の判定とスコア加算を担当する。
 *
 * backward 探索時には
 *   prevNode -> currentNode -> currentNode.next
 * の形で見られるので、
 * 3ノード補正は currentNode.next を third として評価する。
 *
 * 速度面を考慮して、まず word をキーにした粗い絞り込みを行い、
 * その後で NodeFeature.matches(...) で最終判定する。
 */
class NgramRuleScorer(
    twoNodeRules: List<TwoNodeRule>,
    threeNodeRules: List<ThreeNodeRule>,
) {
    private val twoNodeRulesByCurrentWord: Map<String?, List<TwoNodeRule>> =
        twoNodeRules.groupBy { it.current.word }

    private val threeNodeRulesBySecondWord: Map<String?, List<ThreeNodeRule>> =
        threeNodeRules.groupBy { it.second.word }

    fun score(
        prevNode: Node,
        currentNode: Node,
    ): Int {
        if (prevNode.tango == "BOS" || currentNode.tango == "EOS") return 0

        var total = 0

        val twoNodeCandidates = buildList {
            addAll(twoNodeRulesByCurrentWord[currentNode.tango].orEmpty())
            addAll(twoNodeRulesByCurrentWord[null].orEmpty())
        }
        for (rule in twoNodeCandidates) {
            if (rule.prev.matches(prevNode) && rule.current.matches(currentNode)) {
                total += rule.adjustment
            }
        }

        val nextNode = currentNode.next
        if (nextNode != null && nextNode.tango != "EOS") {
            val threeNodeCandidates = buildList {
                addAll(threeNodeRulesBySecondWord[currentNode.tango].orEmpty())
                addAll(threeNodeRulesBySecondWord[null].orEmpty())
            }
            for (rule in threeNodeCandidates) {
                if (
                    rule.first.matches(prevNode) &&
                    rule.second.matches(currentNode) &&
                    rule.third.matches(nextNode)
                ) {
                    total += rule.adjustment
                }
            }
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
            val twoNodeRules = emptyList<TwoNodeRule>()

            val threeNodeRules = listOf(
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

            return NgramRuleScorer(
                twoNodeRules = twoNodeRules,
                threeNodeRules = threeNodeRules,
            )
        }
    }
}
