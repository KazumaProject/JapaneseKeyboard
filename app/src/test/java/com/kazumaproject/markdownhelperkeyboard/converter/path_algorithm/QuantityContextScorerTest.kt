package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.graph.LexicalPart
import com.kazumaproject.quantity.QuantityScoringModel
import org.junit.Assert.*
import org.junit.Test

class QuantityContextScorerTest {
    private fun node(text: String, quantity: Long = 0, lexical: Long = 0) = Node(1, 1, 10, 10,
        tango = text, len = 1, yomiUsed = text, sPos = 0, quantityClasses = quantity, lexicalClasses = lexical)
    private fun model() = QuantityScoringModel("a".repeat(64), "b".repeat(64),
        listOf(QuantityScoringModel.Lexeme("に", "2", 2, 1, 1, 10)), emptyList(), intArrayOf(0, 0, 0),
        emptyMap(), emptyList(), listOf(
            QuantityScoringModel.Rule(listOf(QuantityScoringModel.Feature(QuantityScoringModel.FeatureKind.QUANTITY, 1),
                QuantityScoringModel.Feature(QuantityScoringModel.FeatureKind.LEXICAL, 2)), -100),
            QuantityScoringModel.Rule(listOf(QuantityScoringModel.Feature(QuantityScoringModel.FeatureKind.QUANTITY, 1),
                QuantityScoringModel.Feature(QuantityScoringModel.FeatureKind.LEXICAL, 4),
                QuantityScoringModel.Feature(QuantityScoringModel.FeatureKind.LEXICAL, 2)), -200)))
    @Test fun semanticAndLexicalScoresAreEachChargedOnceInBothDirections() {
        val base = NgramRuleScorer(listOf(NgramRule(listOf(NodeFeature(word = "三"), NodeFeature(word = "百")), -7)))
        val scorer = base.withQuantityModel(model())
        val quantity = node("350円", quantity = 1).copy(lexicalParts = listOf(
            LexicalPart("三", 1, 1), LexicalPart("百", 1, 1), LexicalPart("五十", 1, 1), LexicalPart("円", 1, 1)))
        for ((path, cost) in listOf(listOf(quantity, node("使う", lexical = 2)) to -107,
            listOf(quantity, node("を", lexical = 4), node("使っ", lexical = 2), node("た")) to -207)) {
            val backward = path.indices.sumOf { index ->
                path.getOrNull(index + 1)?.let { scorer.score(path[index], it, path.getOrNull(index + 2),
                    path.getOrNull(index + 3), path.getOrNull(index + 4)) } ?: 0
            }
            val forward = path.indices.sumOf { scorer.scoreEndingAt(path.take(it + 1)) }
            assertEquals(cost, backward); assertEquals(cost, forward)
            assertTrue(scorer.futureContext(path.take(1)).first() === quantity)
        }
        assertNotEquals(scorer.wordClass(quantity), scorer.wordClass(quantity.copy(quantityClasses = 0)))
        assertEquals(0, scorer.score(node("救援"), node("使う", lexical = 2)))
    }
}
