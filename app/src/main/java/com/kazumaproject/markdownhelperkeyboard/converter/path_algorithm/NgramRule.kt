package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node

/**
 * ノード照合用の特徴量。
 *
 * null はワイルドカード。
 * word / leftId / rightId を必要に応じて組み合わせて使う。
 */
data class NodeFeature(
    val word: String? = null,
    val leftId: Short? = null,
    val rightId: Short? = null,
) {
    fun matches(node: Node): Boolean {
        if (word != null && node.tango != word) return false
        if (leftId != null && node.l != leftId) return false
        if (rightId != null && node.r != rightId) return false
        return true
    }
}

/**
 * 2〜5 ノードの補正ルール。
 *
 * nodes[0] -> nodes[1] の辺を展開するときに評価し、nodes[2] 以降は
 * 後方探索で確定済みの suffix と照合する。
 */
data class NgramRule(
    val nodes: List<NodeFeature>,
    val adjustment: Int,
) {
    init {
        require(nodes.size in MIN_NODE_COUNT..MAX_NODE_COUNT) {
            "N-gram node count must be between $MIN_NODE_COUNT and $MAX_NODE_COUNT: ${nodes.size}"
        }
    }

    companion object {
        const val MIN_NODE_COUNT = 2
        const val MAX_NODE_COUNT = 5
    }
}

/**
 * 2ノード補正ルール。
 *
 * prev -> current
 */
data class TwoNodeRule(
    val prev: NodeFeature,
    val current: NodeFeature,
    val adjustment: Int,
)

/**
 * 3ノード補正ルール。
 *
 * first -> second -> third
 */
data class ThreeNodeRule(
    val first: NodeFeature,
    val second: NodeFeature,
    val third: NodeFeature,
    val adjustment: Int,
)
