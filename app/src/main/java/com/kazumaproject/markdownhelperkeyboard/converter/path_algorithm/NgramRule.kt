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
