package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer

/** Lazily compiled scoring automaton. Equivalent lexical histories share an integer state. */
internal class QuantityScoreMachine(private val scorer: NgramRuleScorer,
    private val dictionary: com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramDictionary =
        com.kazumaproject.markdownhelperkeyboard.converter.ngram.EmptySystemNgramDictionary) {
    private data class Symbol(val word: Int, val left: Int, val right: Int, val system: Any)
    private val symbols = HashMap<Symbol, Int>()
    private val representatives = ArrayList<Node>()
    private val histories = arrayListOf(emptyList<Node>())
    private data class History(val scoring: List<Int>, val system: List<Any>)
    private val systemHistories = arrayListOf(emptyList<Node>())
    private val historyIds = hashMapOf(History(emptyList(), emptyList()) to 0)
    private val lexicalNodes = java.util.IdentityHashMap<Node, List<Node>>()
    private fun parts(node: Node): List<Node> = lexicalNodes.getOrPut(node) {
        if (node.lexicalParts.isEmpty()) listOf(node) else node.lexicalParts.map {
            Node(it.left, it.right, 0, 0, tango = it.text, len = 0, yomiUsed = "", sPos = 0)
        }
    }
    fun systemClass(node: Node): Any = if (dictionary.ruleCount == 0) 0 else parts(node).map(dictionary::lexicalClass)
    private val transitions = QuantityLongIndex()
    private val steps = QuantityIntRows(3)

    fun symbol(node: Node): Int {
        val key = Symbol(scorer.wordClass(node), scorer.leftIdClass(node), scorer.rightIdClass(node), systemClass(node))
        return symbols.getOrPut(key) { representatives.add(node); representatives.lastIndex }
    }

    fun step(state: Int, symbol: Int): Int {
        val key = (state.toLong() shl 32) or symbol.toLong()
        val cached = transitions[key]
        if (cached >= 0) return cached
        val context = histories[state] + representatives[symbol]
        val tail = scorer.futureContext(context)
        var matched = false
        val systemTail = if (dictionary.ruleCount == 0) emptyList() else {
            val old = systemHistories[state]
            val words = old + parts(representatives[symbol])
            for (end in old.size until words.size) {
                if (dictionary.matchesSingleNode(words[end])) matched = true
                for (start in maxOf(0, end - 4) until end) {
                    // Only the suffix ending here is new; never include lookahead
                    // that was not consumed by this automaton transition.
                    fun word(at: Int) = if (at <= end) words[at] else null
                    if (dictionary.matches(words[start], words[start + 1], word(start + 2), word(start + 3), word(start + 4))) matched = true
                }
            }
            val suffix = words.takeLast(4)
            val first = suffix.indexOfFirst(dictionary::mayMatchFirstNode)
            if (first < 0) emptyList() else suffix.drop(first)
        }
        val tailKey = History(tail.map(::symbol), systemTail.map(dictionary::lexicalClass))
        val next = historyIds.getOrPut(tailKey) { histories.add(tail); systemHistories.add(systemTail); histories.lastIndex }
        val id = steps.add()
        steps[id, 0] = next; steps[id, 1] = scorer.scoreEndingAt(context); steps[id, 2] = if (matched) 8 else 0
        transitions[key] = id
        return id
    }
    fun next(step: Int): Int = steps[step, 0]
    fun matchedMask(step: Int): Int = steps[step, 2]
    fun cost(step: Int): Int = steps[step, 1]
}
