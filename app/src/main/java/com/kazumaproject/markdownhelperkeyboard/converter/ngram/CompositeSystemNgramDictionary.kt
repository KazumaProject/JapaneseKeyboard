package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import com.kazumaproject.graph.Node

/**
 * Presents the version-3 n-gram and version-4 unigram assets as one scoreless dictionary.
 *
 * The two binary formats share the same packed storage layout but have different matching
 * arities. Keeping the physical readers separate lets each reader retain its strict validation
 * while the path search can query both through the existing runtime gate.
 */
class CompositeSystemNgramDictionary(
    private val dictionaries: List<SystemNgramDictionary>,
) : SystemNgramDictionary {
    init {
        require(dictionaries.isNotEmpty()) { "At least one system n-gram dictionary is required" }
    }

    override val ruleCount: Int = dictionaries.sumOf { it.ruleCount }
    override val storageBytes: Int = dictionaries.sumOf { it.storageBytes }

    override fun matchesSingleNode(node: Node): Boolean =
        dictionaries.any { it.matchesSingleNode(node) }

    override fun matches(
        node0: Node,
        node1: Node,
        node2: Node?,
        node3: Node?,
        node4: Node?,
    ): Boolean = dictionaries.any { dictionary ->
        dictionary.matches(node0, node1, node2, node3, node4)
    }

    override fun mayMatchFirstPair(node0: Node, node1: Node): Boolean =
        dictionaries.any { it.mayMatchFirstPair(node0, node1) }

    override fun mayMatchFirstNode(node: Node): Boolean =
        dictionaries.any { it.mayMatchFirstNode(node) }
}
