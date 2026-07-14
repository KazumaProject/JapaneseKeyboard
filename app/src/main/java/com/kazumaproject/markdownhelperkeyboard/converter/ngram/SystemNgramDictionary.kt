package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import com.kazumaproject.graph.Node

interface SystemNgramDictionary {
    val ruleCount: Int
    val storageBytes: Int

    fun matches(
        node0: Node,
        node1: Node,
        node2: Node?,
        node3: Node?,
        node4: Node?,
    ): Boolean
}

object EmptySystemNgramDictionary : SystemNgramDictionary {
    override val ruleCount: Int = 0
    override val storageBytes: Int = 0

    override fun matches(
        node0: Node,
        node1: Node,
        node2: Node?,
        node3: Node?,
        node4: Node?,
    ): Boolean = false
}
