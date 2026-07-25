package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node

internal object ExactInputPenaltyPolicy {
    private val zeroPrefixPenaltyWords: Set<String> = setOf(
        "にて",
        "しか",
    )

    fun shouldZeroPrefixPenalty(
        node: Node,
        inputLength: Int,
    ): Boolean =
        node.sPos == 0 &&
            node.sPos + node.len.toInt() == inputLength &&
            node.yomiUsed == node.tango &&
            node.yomiUsed in zeroPrefixPenaltyWords
}
