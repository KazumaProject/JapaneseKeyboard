package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import com.kazumaproject.graph.Node

interface SystemNgramDictionary {
    val ruleCount: Int
    val storageBytes: Int

    /** Equal classes must be interchangeable at every position of every rule. */
    fun lexicalClass(node: Node): Any = com.kazumaproject.graph.LexicalPart(node.tango, node.l, node.r)

    /**
     * Number of trailing lexical nodes needed to detect future matches (at most four).
     * Unknown implementations conservatively retain the old first-node history.
     * A completed unigram never needs continuation history.
     */
    fun continuationLength(nodes: List<Node>): Int {
        for (start in maxOf(0, nodes.size - 4) until nodes.size) {
            if (mayMatchFirstNode(nodes[start])) return nodes.size - start
        }
        return 0
    }

    /** Equal classes must be interchangeable in retained continuation history. */
    fun historyClass(node: Node): Any = lexicalClass(node)

    /** Returns true when a single conversion node matches a unigram rule. */
    fun matchesSingleNode(node: Node): Boolean = false

    fun matches(
        node0: Node,
        node1: Node,
        node2: Node?,
        node3: Node?,
        node4: Node?,
    ): Boolean

    /**
     * Returns false only when no rule can start with this adjacent node pair.
     *
     * Implementations which do not have a prefix index deliberately return true.  This method is
     * used only to prove that scoreless system n-gram reranking cannot affect the current lattice;
     * a false positive merely keeps the existing bounded N-best search, while a false negative
     * would change candidates and is therefore forbidden.
     */
    fun mayMatchFirstPair(node0: Node, node1: Node): Boolean = true

    /** Returns false only when no rule can use [node] as its first node. */
    fun mayMatchFirstNode(node: Node): Boolean = true
}

object EmptySystemNgramDictionary : SystemNgramDictionary {
    override fun lexicalClass(node: Node): Any = 0
    override fun continuationLength(nodes: List<Node>): Int = 0
    override val ruleCount: Int = 0
    override val storageBytes: Int = 0

    override fun matches(
        node0: Node,
        node1: Node,
        node2: Node?,
        node3: Node?,
        node4: Node?,
    ): Boolean = false

    override fun mayMatchFirstPair(node0: Node, node1: Node): Boolean = false

    override fun mayMatchFirstNode(node: Node): Boolean = false
}
