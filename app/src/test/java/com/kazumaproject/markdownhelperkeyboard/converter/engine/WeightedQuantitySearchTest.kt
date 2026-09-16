package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.Node
import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer
import com.kazumaproject.quantity.QuantityScoringModel
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WeightedQuantitySearchTest {
    @Test fun nestedQuantityBoundariesMatchExhaustiveNormalizedInterpretations() {
        val previous = QuantityRuntime.scoringModel
        val dictionary = QuantityRuntime.dictionary
        val model = QuantityScoringModel("a".repeat(64), "b".repeat(64),
            listOf(QuantityScoringModel.Lexeme("に", "二", 2, 1, 1, 1)), emptyList(), intArrayOf(0, 0, 0),
            emptyMap(), emptyList(), emptyList())
        try {
            QuantityRuntime.installScoringModel(model, model.posFingerprint, model.connectionFingerprint)
            QuantityRuntime.install(com.kazumaproject.quantity.QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(2)))
            val prefix = listOf(Node(1, 1, 10, 0, tango = "十", len = 3, yomiUsed = "じゅう", sPos = 0),
                Node(2, 2, 9, 0, tango = "重", len = 3, yomiUsed = "じゅう", sPos = 0))
            val suffix = listOf("二本", "２本", "日本").mapIndexed { index, text -> Node(2, 2, index + 2, 0,
                tango = text, len = 3, yomiUsed = "にほん", sPos = 3, isGeneratedNumber = index < 2, isVerifiedQuantity = index < 2) }
            val whole = Node(1, 2, 20, 0, tango = "12本", len = 6, yomiUsed = "じゅうにほん", sPos = 0,
                isGeneratedNumber = true, isVerifiedQuantity = true)
            val bos = Node(0, 0, 0, 0, tango = "BOS", len = 0, yomiUsed = "", sPos = 0, mozcNodeType = MozcNodeType.BOS)
            val eos = Node(0, 0, 0, 0, tango = "EOS", len = 0, yomiUsed = "", sPos = 6, mozcNodeType = MozcNodeType.EOS)
            val policy = NumberPathPolicy("じゅうにほん", PredictionConfig())
            val paths = prefix.flatMap { before -> suffix.map { listOf(before, it) } } + listOf(listOf(whole))
            val expected = paths.groupBy { policy.key(it.joinToString("") { node -> node.tango }, policy.spans(it)) }
                .mapValues { (_, paths) -> paths.minOf { path -> path.sumOf { it.score } } }
            val graph = mapOf(0 to listOf(bos), 3 to prefix, 6 to suffix + whole, 7 to listOf(eos))
            val actual = QuantityGuidedSearch(graph, policy, ConnectionMatrix.fromShortArray(ShortArray(9), 3),
                { _, _ -> true }, NgramRuleScorer(emptyList()), boundaryClass = { 0 }).candidates(null, requested = 20)
            assertEquals(expected, actual.associate { policy.key(it.string, it.numberSpans) to it.score })
            assertEquals(expected.size, actual.size)
        } finally {
            QuantityRuntime.install(dictionary)
            QuantityRuntime.installScoringModel(previous, previous?.posFingerprint.orEmpty(), previous?.connectionFingerprint.orEmpty())
        }
    }

    @Test fun systemMatchInsideQuantityAndFollowingWordSurvivesEquivalentSurfaceMerging() {
        val previous = QuantityRuntime.scoringModel
        val model = QuantityScoringModel("a".repeat(64), "b".repeat(64),
            listOf(QuantityScoringModel.Lexeme("に", "二", 2, 1, 1, 1)), emptyList(), intArrayOf(0, 0, 0),
            emptyMap(), emptyList(), emptyList())
        try {
            QuantityRuntime.installScoringModel(model, model.posFingerprint, model.connectionFingerprint)
            fun quantity(part: String, cost: Int) = Node(1, 1, cost, 0, tango = "2本", len = 3, yomiUsed = "にほん", sPos = 0,
                isGeneratedNumber = true, isVerifiedQuantity = true, lexicalParts = listOf(
                    com.kazumaproject.graph.LexicalPart(part, 1, 1), com.kazumaproject.graph.LexicalPart("本", 1, 1)))
            val bos = Node(0, 0, 0, 0, tango = "BOS", len = 0, yomiUsed = "", sPos = 0, mozcNodeType = MozcNodeType.BOS)
            val eos = Node(0, 0, 0, 0, tango = "EOS", len = 0, yomiUsed = "", sPos = 5, mozcNodeType = MozcNodeType.EOS)
            val buy = Node(1, 1, 0, 0, tango = "買う", len = 2, yomiUsed = "かう", sPos = 3)
            val system = object : com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramDictionary {
                override val ruleCount = 1
                override val storageBytes = 0
                override fun matches(node0: Node, node1: Node, node2: Node?, node3: Node?, node4: Node?) =
                    node0.tango == "二" && node1.tango == "本" && node2?.tango == "買う"
            }
            val graph = mapOf(0 to listOf(bos), 3 to listOf(quantity("2", 1), quantity("二", 100)), 5 to listOf(buy), 6 to listOf(eos))
            val result = QuantityGuidedSearch(graph, NumberPathPolicy("にほんかう", PredictionConfig()),
                ConnectionMatrix.fromShortArray(ShortArray(4), 2), { _, _ -> true }, NgramRuleScorer(emptyList()),
                boundaryClass = { 0 }, systemDictionary = system).candidates(null, requested = 1)
            assertEquals("2本買う", result.single().string)
            assertEquals(100, result.single().score)
        } finally { QuantityRuntime.installScoringModel(previous, previous?.posFingerprint.orEmpty(), previous?.connectionFingerprint.orEmpty()) }
    }

    @Test fun repeatedEquivalentSpellingsAndSegmentationsShareSuffixStreams() {
        val previous = QuantityRuntime.scoringModel
        val oldDictionary = QuantityRuntime.dictionary
        val model = QuantityScoringModel("a".repeat(64), "b".repeat(64),
            listOf(QuantityScoringModel.Lexeme("に", "二", 2, 1, 1, 1)), emptyList(), intArrayOf(0, 0, 0),
            emptyMap(), emptyList(), emptyList())
        try {
            QuantityRuntime.installScoringModel(model, model.posFingerprint, model.connectionFingerprint)
            QuantityRuntime.install(com.kazumaproject.quantity.QuantityDictionary(emptyList(), emptyList(), setOf(1), setOf(2)))
            val input = "にほんだけ".repeat(15)
            val nodes = ArrayList<Node>()
            for (at in 0 until input.length step 5) {
                for (surface in listOf("2本", "２本", "二本")) nodes.add(Node(1, 2, 2, 0,
                    tango = surface, len = 3, yomiUsed = "にほん", sPos = at, isGeneratedNumber = true, isVerifiedQuantity = true))
                nodes.add(Node(1, 1, 1, 0, tango = "二", len = 1, yomiUsed = "に", sPos = at))
                nodes.add(Node(2, 2, 1, 0, tango = "本", len = 2, yomiUsed = "ほん", sPos = at + 1))
                nodes.add(Node(2, 2, 12, 0, tango = "日本", len = 3, yomiUsed = "にほん", sPos = at))
                nodes.add(Node(2, 2, 1, 0, tango = "だけ", len = 2, yomiUsed = "だけ", sPos = at + 3))
            }
            val bos = Node(0, 0, 0, 0, tango = "BOS", len = 0, yomiUsed = "", sPos = 0, mozcNodeType = MozcNodeType.BOS)
            val eos = Node(0, 0, 0, 0, tango = "EOS", len = 0, yomiUsed = "", sPos = input.length, mozcNodeType = MozcNodeType.EOS)
            val graph = nodes.groupBy { it.sPos + it.len } + mapOf(0 to listOf(bos), input.length + 1 to listOf(eos))
            val policy = NumberPathPolicy(input, PredictionConfig())
            var polls = 0
            val candidates = QuantityGuidedSearch(graph, policy, ConnectionMatrix.fromShortArray(ShortArray(9), 3),
                { _, _ -> true }, NgramRuleScorer(emptyList()), cancellationCheck = {
                    check(++polls < 10000) { "Equivalent paths were enumerated" }
                }, boundaryClass = { 0 }).candidates(null, requested = 8)
            assertEquals(8, candidates.size)
            assertEquals("2本だけ".repeat(15), policy.key(candidates.first().string, candidates.first().numberSpans))
            assertEquals(listOf(45) + List(7) { 55 }, candidates.map { it.score })
            assertEquals(8, candidates.map { policy.key(it.string, it.numberSpans) }.distinct().size)
        } finally {
            QuantityRuntime.install(oldDictionary)
            QuantityRuntime.installScoringModel(previous, previous?.posFingerprint.orEmpty(), previous?.connectionFingerprint.orEmpty())
        }
    }

    @Test fun allWeightedInterpretationsMatchExhaustivePathsWithNegativeContextCosts() {
        val previous = QuantityRuntime.scoringModel
        val q = QuantityScoringModel.FeatureKind.QUANTITY
        val l = QuantityScoringModel.FeatureKind.LEXICAL
        val model = QuantityScoringModel("a".repeat(64), "b".repeat(64),
            listOf(QuantityScoringModel.Lexeme("に", "2", 2, 1, 1, 10)), emptyList(), intArrayOf(0, 0, 0),
            emptyMap(), emptyList(), listOf(
                QuantityScoringModel.Rule(listOf(QuantityScoringModel.Feature(q, 1), QuantityScoringModel.Feature(l, 2)), -200),
                QuantityScoringModel.Rule(listOf(QuantityScoringModel.Feature(l, 4), QuantityScoringModel.Feature(q, 1)), 77)))
        try {
            QuantityRuntime.installScoringModel(model, model.posFingerprint, model.connectionFingerprint)
            val scorer = NgramRuleScorer(emptyList()).withQuantityModel(model)
            val random = Random(192)
            for (withSystem in listOf(false, true)) repeat(30) {
                val matrix = ConnectionMatrix.fromShortArray(ShortArray(9) { random.nextInt(-5, 20).toShort() }, 3)
                val layers = List(5) { at -> List(2) { variant ->
                    Node(random.nextInt(1, 3).toShort(), random.nextInt(1, 3).toShort(), random.nextInt(5, 35), 0,
                        tango = "${('A'.code + at).toChar()}${('a'.code + variant).toChar()}", len = 1, yomiUsed = "あ", sPos = at,
                        quantityClasses = if (random.nextBoolean()) 1 else 0,
                        lexicalClasses = if (random.nextBoolean()) 2 else 4)
                } }
                val preferred = layers.map { it.last().tango }
                val system = object : com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramDictionary {
                    override val ruleCount = if (withSystem) 1 else 0
                    override val storageBytes = 0
                    override fun matches(node0: Node, node1: Node, node2: Node?, node3: Node?, node4: Node?) = withSystem &&
                        listOf(node0.tango, node1.tango, node2?.tango, node3?.tango, node4?.tango) == preferred
                }
                val bos = Node(0, 0, 0, 0, tango = "BOS", len = 0, yomiUsed = "", sPos = 0, mozcNodeType = MozcNodeType.BOS)
                val eos = Node(0, 0, 0, 0, tango = "EOS", len = 0, yomiUsed = "", sPos = 5, mozcNodeType = MozcNodeType.EOS)
                val graph = layers.mapIndexed { at, nodes -> at + 1 to nodes }.toMap() + mapOf(0 to listOf(bos), 6 to listOf(eos))
                val paths = layers.fold(listOf(emptyList<Node>())) { paths, choices -> paths.flatMap { path -> choices.map { path + it } } }
                val expected = paths.associate { path ->
                    val score = path.indices.sumOf { at -> path[at].score + matrix.cost((path.getOrNull(at - 1)?.r ?: 0).toInt(), path[at].l.toInt()) +
                        scorer.scoreEndingAt(path.take(at + 1)) } + matrix.cost(path.last().r.toInt(), 0)
                    path.joinToString("") { it.tango } to score
                }
                val actual = QuantityGuidedSearch(graph, NumberPathPolicy("あ".repeat(5), PredictionConfig()), matrix,
                    { _, _ -> true }, scorer, boundaryClass = { 0 }, systemDictionary = system).candidates(null, requested = 100)
                assertEquals(expected, actual.associate { it.string to it.score })
                val preferredText = preferred.joinToString("")
                val expectedOrder = expected.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> {
                    withSystem && it.key == preferredText
                }.thenBy { it.value }).map { it.value }
                assertEquals(expectedOrder, actual.map { it.score })
                if (withSystem) assertEquals(preferredText, actual.first().string)
            }
        } finally { QuantityRuntime.installScoringModel(previous, previous?.posFingerprint.orEmpty(), previous?.connectionFingerprint.orEmpty()) }
    }
}
