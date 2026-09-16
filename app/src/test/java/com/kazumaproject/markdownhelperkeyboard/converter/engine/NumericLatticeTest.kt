package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.LexicalPart
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.EmptySystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRule
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NodeFeature
import org.junit.Assert.*
import org.junit.Test

class NumericLatticeTest {
    private fun node(text: String) = Node(1, 1, 0, 0, tango = text, len = 0, yomiUsed = "", sPos = 0)
    private fun entry(text: String, cost: Int) = NumberLexicon.Entry(text, 1, 1, cost)

    @Test fun irrelevantLexicalAlternativesDoNotGrowWithNumberLength() {
        val lattice = NumericLattice(NumericPathObserver(NgramRuleScorer(emptyList()), EmptySystemNgramDictionary))
        var row = lattice.row()
        lattice.add(row, null, entry("一", 10), 0)
        repeat(60) {
            val next = lattice.row()
            for (previous in lattice.vertices(row)) {
                lattice.add(next, previous, entry("二", 20), 0)
                lattice.add(next, previous, entry("2", 30), 0)
            }
            row = next
            assertEquals(1, lattice.vertices(row).size)
        }
        val best = lattice.vertices(row).single()
        assertEquals(1210, best.cost)
        assertEquals(61, best.materialize().size)
    }

    @Test fun sharedStatesPreserveExhaustiveCostsAndSystemMatchesInExternalContexts() {
        val scorer = NgramRuleScorer(listOf(
            NgramRule(listOf("前", "A", "B").map { NodeFeature(word = it) }, -19),
            NgramRule(listOf("B", "A", "後").map { NodeFeature(word = it) }, -23),
            NgramRule(List(5) { NodeFeature(word = "A") }, 31),
            NgramRule(listOf(NodeFeature(rightId = 1), NodeFeature(word = "B")), -7)))
        val system = object : SystemNgramDictionary {
            override val ruleCount = 1
            override val storageBytes = 0
            override fun matches(node0: Node, node1: Node, node2: Node?, node3: Node?, node4: Node?) =
                listOf(node0.tango, node1.tango, node2?.tango, node3?.tango, node4?.tango) == listOf("A", "B", "B", "A", "B")
        }
        val lattice = NumericLattice(NumericPathObserver(scorer, system))
        var row = lattice.row()
        lattice.add(row, null, entry("A", 1), 0)
        var exhaustive = listOf(listOf(LexicalPart("A", 1, 1)) to 1)
        repeat(11) { index ->
            val choices = listOf(entry("A", index + 2), entry("B", 15 - index))
            val next = lattice.row()
            for (previous in lattice.vertices(row)) for (choice in choices) lattice.add(next, previous, choice, 3)
            row = next
            exhaustive = exhaustive.flatMap { (parts, cost) -> choices.map { parts + it.parts to cost + it.cost + 3 } }
        }
        fun evaluate(paths: List<Pair<List<LexicalPart>, Int>>, prefix: String, suffix: String, backward: Boolean): Map<Boolean, Int> =
            paths.map { (parts, cost) ->
                val nodes = listOf(node(prefix)) + parts.map { node(it.text) } + node(suffix)
                val matched = nodes.indices.any { start -> nodes.getOrNull(start + 1)?.let {
                    system.matches(nodes[start], it, nodes.getOrNull(start + 2), nodes.getOrNull(start + 3), nodes.getOrNull(start + 4))
                } == true }
                val scored = listOf(node(prefix), node("quantity").copy(lexicalParts = parts), node(suffix))
                val adjustment = if (backward) (0 until scored.lastIndex).sumOf { index ->
                    scorer.score(scored[index], scored[index + 1], scored.getOrNull(index + 2), scored.getOrNull(index + 3), scored.getOrNull(index + 4))
                } else scored.indices.sumOf { scorer.scoreEndingAt(scored.take(it + 1)) }
                matched to cost + adjustment
            }.groupBy({ it.first }, { it.second }).mapValues { it.value.min() }
        val reduced = lattice.vertices(row).map { it.materialize() to it.cost }
        assertTrue(reduced.size < exhaustive.size)
        for (backward in listOf(false, true)) for (prefix in listOf("前", "A", "B")) for (suffix in listOf("後", "A", "B"))
            assertEquals("$backward/$prefix/$suffix", evaluate(exhaustive, prefix, suffix, backward), evaluate(reduced, prefix, suffix, backward))
    }
}
