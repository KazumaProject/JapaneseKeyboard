package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.EmptySystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer
import com.kazumaproject.quantity.QuantityDictionary
import com.kazumaproject.quantity.QuantityScoringModel
import org.junit.Assert.*
import org.junit.Test

class QuantityScoringModelIntegrationTest {
    private fun model() = QuantityScoringModel("a".repeat(64), "b".repeat(64), listOf(
        QuantityScoringModel.Lexeme("じゅう", "十", 10, 1, 1, 10),
        QuantityScoringModel.Lexeme("に", "二", 2, 1, 1, 20)), listOf(QuantityScoringModel.UnitLexeme("ほん", "本",
            QuantityScoringModel.UnitRole.COUNTER, 1, 1, 30)), intArrayOf(-5, 0, -7),
        emptyMap(), emptyList(), emptyList())

    @Test fun registeredAtomicValuesAboveSixteenDigitsKeepTypedLexicalScoring() {
        val previous = QuantityRuntime.scoringModel
        val dictionary = QuantityRuntime.dictionary
        try {
            val original = model()
            val source = QuantityScoringModel(original.posFingerprint, original.connectionFingerprint,
                listOf(QuantityScoringModel.Lexeme("いち", "一", 1, 1, 1, 10),
                    QuantityScoringModel.Lexeme("けい", "京", 10_000_000_000_000_000L, 1, 1, 20)),
                listOf(QuantityScoringModel.UnitLexeme("こ", "個", QuantityScoringModel.UnitRole.COUNTER, 1, 1, 30)),
                intArrayOf(0, -5, 0), emptyMap(), emptyList(), emptyList())
            QuantityRuntime.installScoringModel(source, source.posFingerprint, source.connectionFingerprint)
            QuantityRuntime.install(QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(1)))
            val unit = CustomNumberUnit("large", "束", "たば", specialReadings = listOf(
                SpecialNumberReading(10_000_000_000_000_000L, "たくさん")))
            val proof = ValidatedNumber.parseAll("たくさん", NumberCandidateConfig(units = listOf(unit))).single()
            val result = NumberLexicon({ emptyList() }, ConnectionMatrix.fromShortArray(ShortArray(4), 2)).forms(proof).single()
            assertEquals("10000000000000000束", result.text)
            assertEquals(listOf("一", "京", "束"), result.parts.map { it.text })
            assertEquals(55, result.cost)
        } finally {
            QuantityRuntime.install(dictionary)
            QuantityRuntime.installScoringModel(previous, previous?.posFingerprint.orEmpty(), previous?.connectionFingerprint.orEmpty())
        }
    }

    @Test fun mismatchedDictionaryGenerationCannotActivateTheModel() {
        val previous = QuantityRuntime.scoringModel
        try {
            val model = model()
            assertFalse(QuantityRuntime.installScoringModel(model, "c".repeat(64), model.connectionFingerprint))
            assertNull(QuantityRuntime.scoringModel)
            assertFalse(QuantityRuntime.installScoringModel(model, model.posFingerprint, "c".repeat(64)))
            assertNull(QuantityRuntime.scoringModel)
        } finally { QuantityRuntime.installScoringModel(previous, previous?.posFingerprint.orEmpty(), previous?.connectionFingerprint.orEmpty()) }
    }

    @Test fun missingTypedCounterCannotBorrowAnOrdinaryLookupEntry() {
        val previous = QuantityRuntime.scoringModel
        val dictionary = QuantityRuntime.dictionary
        try {
            val model = model()
            QuantityRuntime.installScoringModel(model, model.posFingerprint, model.connectionFingerprint)
            QuantityRuntime.install(QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(1)))
            val lexicon = NumberLexicon({ reading ->
                when (reading) {
                    "えん" -> listOf(NumberLexicon.Entry("円", 1, 1, 1))
                    else -> emptyList()
                }
            }, ConnectionMatrix.fromShortArray(ShortArray(4), 2))
            val proof = ValidatedNumber.parseAll("にえん", NumberCandidateConfig()).single()
            assertTrue(lexicon.forms(proof).isEmpty())
        } finally {
            QuantityRuntime.install(dictionary)
            QuantityRuntime.installScoringModel(previous, previous?.posFingerprint.orEmpty(), previous?.connectionFingerprint.orEmpty())
        }
    }

    @Test fun clockKeepsBothParsedQuantitiesAndTheirInternalConnections() {
        val previous = QuantityRuntime.scoringModel
        val dictionary = QuantityRuntime.dictionary
        try {
            val original = model()
            val model = QuantityScoringModel(original.posFingerprint, original.connectionFingerprint,
                original.numericLexemes + listOf(
                    QuantityScoringModel.Lexeme("さん", "三", 3, 1, 1, 40),
                    QuantityScoringModel.Lexeme("ご", "五", 5, 1, 1, 50)),
                listOf(QuantityScoringModel.UnitLexeme("じ", "時", QuantityScoringModel.UnitRole.COUNTER, 1, 1, 60),
                    QuantityScoringModel.UnitLexeme("ふん", "分", QuantityScoringModel.UnitRole.COUNTER, 1, 1, 70)),
                original.constructionWeights, emptyMap(), emptyList(), emptyList())
            QuantityRuntime.installScoringModel(model, model.posFingerprint, model.connectionFingerprint)
            QuantityRuntime.install(QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(1)))
            val lexicon = NumberLexicon({ emptyList() }, ConnectionMatrix.fromShortArray(ShortArray(4) { 3 }, 2))
            val proof = ValidatedNumber.parse("にじゅうさんじごふん")!!
            assertEquals(23 to 5, proof.clock)
            assertEquals("にじゅうさん", proof.clockParts!!.first.cardinalReading)
            assertEquals("ご", proof.clockParts!!.second.cardinalReading)
            val result = lexicon.forms(proof).single()
            assertEquals("23時5分", result.text)
            assertEquals(listOf("二", "十", "三", "時", "五", "分"), result.parts.map { it.text })
            assertEquals(253, result.cost) // 250 lexical + 15 connections - 5 product - 7 addition.
        } finally {
            QuantityRuntime.install(dictionary)
            QuantityRuntime.installScoringModel(previous, previous?.posFingerprint.orEmpty(), previous?.connectionFingerprint.orEmpty())
        }
    }

    @Test fun typedNumericArcsApplyOnlyTheNewCompositionEdges() {
        val previous = QuantityRuntime.scoringModel
        val dictionary = QuantityRuntime.dictionary
        try {
            val model = model()
            QuantityRuntime.installScoringModel(model, model.posFingerprint, model.connectionFingerprint)
            QuantityRuntime.install(QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(1)))
            val lexicon = NumberLexicon({ reading ->
                if (reading == "ほん") listOf(NumberLexicon.Entry("本", 1, 1, 30)) else emptyList()
            }, ConnectionMatrix.fromShortArray(ShortArray(4) { 3 }, 2),
                NumericPathObserver(NgramRuleScorer(emptyList()), EmptySystemNgramDictionary))
            val proof = ValidatedNumber.parseAll("じゅうにほん", NumberCandidateConfig()).single()
            assertEquals(12L, proof.cardinalExpression!!.value)
            val result = lexicon.forms(proof).single()
            assertEquals("12本", result.text)
            assertEquals(59, result.cost)
            assertEquals(listOf("十", "二", "本"), result.parts.map { it.text })
            val path = com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath({ NgramRuleScorer(emptyList()) })
            val cost = path.numberFormCost(result, proof.reading, ConnectionMatrix.fromShortArray(ShortArray(4) { 3 }, 2))
            assertEquals(65, cost)
            for (order in NumberCandidateOrder.entries) {
                val generated = NumberCandidateGenerator.generate(proof.reading, PredictionConfig(numberCandidateOrder = order)) { NumberCandidateGenerator.SemanticScore(cost, result.leftId, result.rightId) }
                assertEquals(order.indices.map { proof.basicForms[it] }, generated.map { it.string })
                assertEquals(setOf(cost), generated.map { it.score }.toSet())
                assertTrue(generated.all { it.leftId == result.leftId && it.rightId == result.rightId })
            }
            assertTrue(NumberCandidateGenerator.generate(proof.reading, PredictionConfig()) { null }.isEmpty())
        } finally {
            QuantityRuntime.install(dictionary)
            QuantityRuntime.installScoringModel(previous, previous?.posFingerprint.orEmpty(), previous?.connectionFingerprint.orEmpty())
        }
    }
}
