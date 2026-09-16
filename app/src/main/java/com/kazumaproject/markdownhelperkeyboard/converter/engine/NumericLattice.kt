package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.LexicalPart
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer

/** Finite observations of a lexical path for the two 1–5-gram scorers. */
class NumericPathObserver internal constructor(
    private val scorer: NgramRuleScorer,
    private val dictionary: SystemNgramDictionary,
) {
    internal val configurationSignature = 31 * (31 * System.identityHashCode(scorer) + System.identityHashCode(dictionary)) + System.identityHashCode(QuantityRuntime.scoringModel)
    private data class Symbol(val word: Int, val left: Int, val right: Int, val system: Any)
    private data class Key(val prefix: List<Int>, val suffix: List<Int>, val internalCost: Long, val backwardCost: Int, val matched: Boolean)
    private val symbols = HashMap<Symbol, Int>()
    private val representatives = ArrayList<Node>()
    private val states = arrayListOf(Key(emptyList(), emptyList(), 0, 0, false))
    private val stateIds = hashMapOf(states[0] to 0)
    private val steps = HashMap<Long, Int>()
    private val partSymbols = HashMap<LexicalPart, Int>()

    internal fun append(state: Int, parts: List<LexicalPart>): Int {
        var next = state
        for (part in parts) {
            val symbol = partSymbols.getOrPut(part) {
                val node = Node(part.left, part.right, 0, 0, tango = part.text, len = 0, yomiUsed = "", sPos = 0)
                val key = Symbol(scorer.wordClass(node), scorer.leftIdClass(node), scorer.rightIdClass(node), dictionary.lexicalClass(node))
                symbols.getOrPut(key) { representatives.add(node); representatives.lastIndex }
            }
            next = step(next, symbol)
        }
        return next
    }

    private fun step(state: Int, symbol: Int): Int = steps.getOrPut((state.toLong() shl 32) or symbol.toLong()) {
        val old = states[state]
        val tail = old.suffix.map { representatives[it] } + representatives[symbol]
        val matched = old.matched || dictionary.matchesSingleNode(tail.last()) ||
            (0 until tail.lastIndex).any { start ->
                dictionary.matches(tail[start], tail[start + 1], tail.getOrNull(start + 2),
                    tail.getOrNull(start + 3), tail.getOrNull(start + 4))
            }
        val key = Key(if (old.prefix.size < 4) old.prefix + symbol else old.prefix,
            (old.suffix + symbol).takeLast(4), old.internalCost + scorer.rawScoreEndingAt(tail),
            old.backwardCost + if (tail.size == 5) scorer.score(tail[0], tail[1], tail[2], tail[3], tail[4]) else 0, matched)
        stateIds.getOrPut(key) { states.add(key); states.lastIndex }
    }
}

/**
 * Shared weighted numeric DAG. Only paths with identical boundary POS and
 * identical observations by both scorers may dominate one another. A retained
 * vertex stores a predecessor, not a copied lexical sequence.
 */
internal class NumericLattice(private val observer: NumericPathObserver?) {
    class Vertex(val left: Short, val right: Short, val cost: Int, val observation: Any,
                 val previous: Vertex?, val parts: List<LexicalPart>) {
        fun materialize(): List<LexicalPart> {
            val chain = ArrayList<Vertex>()
            var vertex: Vertex? = this
            while (vertex != null) { chain.add(vertex); vertex = vertex.previous }
            return buildList { for (i in chain.indices.reversed()) addAll(chain[i].parts) }
        }
    }
    private data class Key(val left: Short, val right: Short, val observation: Any)
    class Row internal constructor() { internal val vertices = LinkedHashMap<Any, Vertex>() }
    fun row() = Row()
    fun vertices(row: Row): Collection<Vertex> = row.vertices.values
    fun add(row: Row, previous: Vertex?, entry: NumberLexicon.Entry, connection: Int) {
        val observation = if (observer != null) observer.append(previous?.observation as? Int ?: 0, entry.parts)
            else (previous?.observation as? List<*>).orEmpty() + entry.parts
        val left = previous?.left ?: entry.left
        val cost = (previous?.cost ?: 0) + connection + entry.cost
        val key = Key(left, entry.right, observation)
        val old = row.vertices[key]
        if (old == null || cost < old.cost) row.vertices[key] = Vertex(left, entry.right, cost, observation, previous, entry.parts)
    }
}
