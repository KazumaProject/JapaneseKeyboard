package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.quantity.QuantityScoringModel

/** Weighted semantic rules over whole quantity nodes and typed dictionary word classes. */
internal class QuantityContextScorer(model: QuantityScoringModel) {
    private class State {
        val next = IntArray(126) { -1 }
        var adjustment = 0
        var hasChildren = false
    }
    private val states = arrayListOf(State())
    val maxOrder = model.rules.maxOfOrNull { it.features.size } ?: 0
    init {
        for (rule in model.rules) {
            var state = 0
            for (feature in rule.features) {
                require(java.lang.Long.bitCount(feature.classes) == 1)
                val symbol = java.lang.Long.numberOfTrailingZeros(feature.classes) +
                    if (feature.kind == QuantityScoringModel.FeatureKind.LEXICAL) 63 else 0
                var next = states[state].next[symbol]
                if (next < 0) {
                    next = states.size; states.add(State()); states[state].next[symbol] = next; states[state].hasChildren = true
                }
                state = next
            }
            states[state].adjustment += rule.adjustment
        }
    }

    fun starting(first: Node, second: Node?, third: Node?, fourth: Node?, fifth: Node?): Int {
        fun at(index: Int) = when (index) { 0 -> first; 1 -> second; 2 -> third; 3 -> fourth; else -> fifth }
        fun visit(state: Int, index: Int): Int {
            var total = states[state].adjustment
            if (index >= maxOrder) return total
            val node = at(index) ?: return total
            var quantity = node.quantityClasses
            while (quantity != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(quantity)
                val next = states[state].next[bit]
                if (next >= 0) total += visit(next, index + 1)
                quantity = quantity and (quantity - 1)
            }
            var lexical = node.lexicalClasses
            while (lexical != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(lexical)
                val next = states[state].next[63 + bit]
                if (next >= 0) total += visit(next, index + 1)
                lexical = lexical and (lexical - 1)
            }
            return total
        }
        return visit(0, 0)
    }

    fun contextLength(nodes: List<Node>): Int {
        fun matches(state: Int, at: Int): Boolean {
            if (at == nodes.size) return states[state].hasChildren
            val node = nodes[at]
            var quantity = node.quantityClasses
            while (quantity != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(quantity)
                val next = states[state].next[bit]
                if (next >= 0 && matches(next, at + 1)) return true
                quantity = quantity and (quantity - 1)
            }
            var lexical = node.lexicalClasses
            while (lexical != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(lexical)
                val next = states[state].next[63 + bit]
                if (next >= 0 && matches(next, at + 1)) return true
                lexical = lexical and (lexical - 1)
            }
            return false
        }
        for (size in minOf(nodes.size, maxOrder - 1) downTo 1) if (matches(0, nodes.size - size)) return size
        return 0
    }

    fun ending(nodes: List<Node>): Int {
        fun visit(state: Int, at: Int): Int {
            if (at == nodes.size) return states[state].adjustment
            var total = 0
            val node = nodes[at]
            var quantity = node.quantityClasses
            while (quantity != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(quantity)
                val next = states[state].next[bit]
                if (next >= 0) total += visit(next, at + 1)
                quantity = quantity and (quantity - 1)
            }
            var lexical = node.lexicalClasses
            while (lexical != 0L) {
                val bit = java.lang.Long.numberOfTrailingZeros(lexical)
                val next = states[state].next[63 + bit]
                if (next >= 0) total += visit(next, at + 1)
                lexical = lexical and (lexical - 1)
            }
            return total
        }
        var total = 0
        for (start in (nodes.size - maxOrder).coerceAtLeast(0) until nodes.lastIndex) total += visit(0, start)
        return total
    }
}
