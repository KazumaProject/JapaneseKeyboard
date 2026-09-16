package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.*
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.*
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramRuntime
import com.kazumaproject.quantity.QuantityDictionary
import org.junit.Assert.*
import org.junit.Test

class QuantitySearchTest {
    private fun node(text: String, reading: String = text, start: Int = 0, cost: Int = 0) =
        Node(1, 1, cost, cost, tango = text, len = reading.length.toShort(), yomiUsed = reading, sPos = start)

    @Test fun customUnitsDoNotBorrowHomophonousProperNounCosts() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(2)))
            val lookup = mapOf(
                "さん" to listOf(NumberLexicon.Entry("三", 1, 1, 100)),
                "きろ" to listOf(NumberLexicon.Entry("帰路", 3, 3, 1), NumberLexicon.Entry("キロ", 2, 2, 300)),
                "こ" to listOf(NumberLexicon.Entry("個", 2, 2, 400)))
            val matrix = ConnectionMatrix.fromShortArray(ShortArray(16), 4)
            val config = NumberCandidateConfig(units = listOf(CustomNumberUnit("kg", "kg", "きろ")))
            val proof = ValidatedNumber.parseUncached("さんきろ", config).first { it.customUnit != null }
            val forms = NumberLexicon({ lookup[it].orEmpty() }, matrix).forms(proof)
            assertEquals(400, forms.single().cost)
            assertEquals(2.toShort(), forms.single().rightId)
            val withoutCounter = NumberLexicon({ lookup[it].orEmpty().filter { it.text != "キロ" } }, matrix).forms(proof)
            assertEquals(500, withoutCounter.single().cost)
        } finally { QuantityRuntime.install(old) }
    }

    @Test fun reducedForwardContextPreservesOverlappingAndCompositeScores() {
        val rules = listOf(
            NgramRule(listOf("前", "三", "本", "買う").map { NodeFeature(word = it) }, -101),
            NgramRule(listOf(NodeFeature(rightId = 1), NodeFeature(word = "本")), -7),
            NgramRule(listOf("本", "買う", "後").map { NodeFeature(word = it) }, -23))
        val scorer = NgramRuleScorer(rules)
        val macro = node("3本").copy(lexicalParts = listOf(LexicalPart("三", 1, 1), LexicalPart("本", 1, 1)))
        val path = listOf(node("無関係"), node("前"), macro, node("買う"), node("後"), node("末"))
        var tail = emptyList<Node>(); var actual = 0
        for (node in path) {
            actual += scorer.scoreEndingAt(tail + node)
            tail = scorer.futureContext(tail + node)
        }
        assertEquals(path.indices.sumOf { scorer.scoreEndingAt(path.take(it + 1)) }, actual)
        val literalOnly = NgramRuleScorer(listOf(rules.first()))
        assertTrue(literalOnly.futureContext(listOf(node("無関係"))).isEmpty())
    }

    @Test fun composedNodesPreserveEveryNgramCostInBothDirections() {
        val expanded = listOf("前", "三", "本", "買う", "後").map { node(it) }
        val scorer = NgramRuleScorer((2..5).flatMap { order ->
            expanded.windowed(order).map { NgramRule(it.map { n -> NodeFeature(word = n.tango) }, -order * 10) }
        })
        val macro = node("3本").copy(lexicalParts = listOf(LexicalPart("三", 1, 1), LexicalPart("本", 1, 1)))
        val composed = listOf(expanded[0], macro, expanded[3], expanded[4])
        fun backward(path: List<Node>) = path.indices.sumOf { i ->
            if (i + 1 == path.size) 0 else scorer.score(path[i], path[i + 1], path.getOrNull(i + 2), path.getOrNull(i + 3), path.getOrNull(i + 4))
        }
        fun forward(path: List<Node>) = path.indices.sumOf { scorer.scoreEndingAt(path.take(it + 1)) }
        assertEquals(backward(expanded), backward(composed))
        assertEquals(backward(expanded), forward(composed))
        assertEquals(backward(expanded), forward(expanded))
    }

    @Test fun constrainedSearchUsesNgramScoresAndRealBoundaryEdges() {
        val old = QuantityRuntime.dictionary
        try {
            QuantityRuntime.install(QuantityDictionary(listOf(listOf(QuantityDictionary.Feature.Quantity("本"), QuantityDictionary.Feature.Word("買う"))), emptyList()))
            SystemNgramRuntime.resetForTesting()
            val a = node("安", "あ", 0, 10)
            val b = node("適", "あ", 0, 100)
            val number = node("3本", "さんぼん", 1, 300).copy(isGeneratedNumber = true, lexicalParts = listOf(LexicalPart("三", 1, 1), LexicalPart("本", 1, 1)))
            val buy = node("買う", "かう", 5, 50)
            val bos = node("BOS", "", 0).copy(l = 0, r = 0, mozcNodeType = MozcNodeType.BOS)
            val eos = node("EOS", "", 8).copy(l = 0, r = 0, mozcNodeType = MozcNodeType.EOS)
            val graph = mapOf(0 to listOf(bos), 1 to listOf(a, b), 5 to listOf(number), 7 to listOf(buy), 8 to listOf(eos))
            val scorer = NgramRuleScorer(listOf(NgramRule(listOf("適", "三", "本", "買う").map { NodeFeature(word = it) }, -200)))
            val matrix = ConnectionMatrix.fromShortArray(ShortArray(4) { 7 }, 2)
            var bosChecked = false; var eosChecked = false
            val candidates = QuantityGuidedSearch(graph, NumberPathPolicy("あさんぼんかう", PredictionConfig()), matrix,
                { left, right ->
                    if (left === bos) bosChecked = true
                    if (right === eos) eosChecked = true
                    true
                }, scorer).candidates(null)
            assertEquals("適3本買う", candidates.single().string)
            assertEquals(100 + 300 + 50 + 4 * 7 - 200 + 2000, candidates.single().score)
            assertTrue(bosChecked && eosChecked)
            val blocked = QuantityGuidedSearch(graph, NumberPathPolicy("あさんぼんかう", PredictionConfig()), matrix,
                { _, right -> right !== eos }, scorer).candidates(null)
            assertTrue(blocked.isEmpty())
        } finally { QuantityRuntime.install(old); SystemNgramRuntime.resetForTesting() }
    }
}
