package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.NUM_OF_CONNECTION_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class NgramRuleScorerTest {

    @Test
    fun conversion_prioritizesIkideInasena_whenTwoNodeRuleAdded() {
        val connectionIds = createConnectionIds()

        val noRuleFindPath = FindPath(
            ngramRuleScorerProvider = {
                NgramRuleScorer(
                    twoNodeRules = emptyList(),
                    threeNodeRules = emptyList(),
                )
            }
        )
        val noRuleTop = noRuleFindPath.backwardAStar(
            graph = createIkinaInasenaGraph(),
            length = 2,
            connectionIds = connectionIds,
            n = 3,
        ).first().string
        assertEquals("意気でいなせな", noRuleTop)

        val withRuleFindPath = FindPath(
            ngramRuleScorerProvider = {
                NgramRuleScorer(
                    twoNodeRules = listOf(
                        TwoNodeRule(
                            prev = NodeFeature(word = "粋で"),
                            current = NodeFeature(word = "いなせな"),
                            adjustment = -6000,
                        ),
                    ),
                    threeNodeRules = emptyList(),
                )
            }
        )
        val withRuleTop = withRuleFindPath.backwardAStar(
            graph = createIkinaInasenaGraph(),
            length = 2,
            connectionIds = connectionIds,
            n = 3,
        ).first().string

        assertEquals("粋でいなせな", withRuleTop)
    }

    @Test
    fun score_appliesTwoNodeRule_whenMatched() {
        val scorer = NgramRuleScorer(
            twoNodeRules = listOf(
                TwoNodeRule(
                    prev = NodeFeature(word = "布"),
                    current = NodeFeature(word = "で"),
                    adjustment = -300,
                ),
            ),
            threeNodeRules = emptyList(),
        )

        val prev = createNode(tango = "布")
        val current = createNode(tango = "で")

        val score = scorer.score(prevNode = prev, currentNode = current)

        assertEquals(-300, score)
    }

    @Test
    fun score_appliesThreeNodeRule_whenMatched() {
        val scorer = NgramRuleScorer(
            twoNodeRules = emptyList(),
            threeNodeRules = listOf(
                ThreeNodeRule(
                    first = NodeFeature(word = "布"),
                    second = NodeFeature(word = "で"),
                    third = NodeFeature(word = "拭く"),
                    adjustment = -1200,
                ),
            ),
        )

        val prev = createNode(tango = "布")
        val current = createNode(tango = "で")
        val next = createNode(tango = "拭く")
        current.next = next

        val score = scorer.score(prevNode = prev, currentNode = current)

        assertEquals(-1200, score)
    }

    @Test
    fun score_appliesTwoAndThreeNodeRules_whenMatched() {
        val scorer = NgramRuleScorer(
            twoNodeRules = listOf(
                TwoNodeRule(
                    prev = NodeFeature(word = "布"),
                    current = NodeFeature(word = "で"),
                    adjustment = -300,
                ),
            ),
            threeNodeRules = listOf(
                ThreeNodeRule(
                    first = NodeFeature(word = "布"),
                    second = NodeFeature(word = "で"),
                    third = NodeFeature(word = "拭く"),
                    adjustment = -1200,
                ),
            ),
        )

        val prev = createNode(tango = "布")
        val current = createNode(tango = "で")
        val next = createNode(tango = "拭く")
        current.next = next

        val score = scorer.score(prevNode = prev, currentNode = current)

        assertEquals(-1500, score)
    }

    @Test
    fun benchmark_twoNodeRules_scaling() {
        val results = benchmarkRuleScaling(mode = BenchmarkMode.TWO_NODE_ONLY)

        println("[NgramRuleScorer] TWO_NODE_ONLY benchmark (count -> ns/op)")
        results.forEach { (count, nsPerOp) ->
            println("$count -> $nsPerOp")
        }

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun benchmark_threeNodeRules_scaling() {
        val results = benchmarkRuleScaling(mode = BenchmarkMode.THREE_NODE_ONLY)

        println("[NgramRuleScorer] THREE_NODE_ONLY benchmark (count -> ns/op)")
        results.forEach { (count, nsPerOp) ->
            println("$count -> $nsPerOp")
        }

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun benchmark_mixedRules_scaling() {
        val results = benchmarkRuleScaling(mode = BenchmarkMode.MIXED)

        println("[NgramRuleScorer] MIXED benchmark (count -> ns/op)")
        results.forEach { (count, nsPerOp) ->
            println("$count -> $nsPerOp")
        }

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun benchmark_conversion_twoNodeRules_scaling() {
        val results = benchmarkConversionScaling(mode = BenchmarkMode.TWO_NODE_ONLY)

        println("[NgramRuleScorer] CONVERSION TWO_NODE_ONLY benchmark (count -> ns/op)")
        results.forEach { (count, nsPerOp) ->
            println("$count -> $nsPerOp")
        }

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun benchmark_conversion_threeNodeRules_scaling() {
        val results = benchmarkConversionScaling(mode = BenchmarkMode.THREE_NODE_ONLY)

        println("[NgramRuleScorer] CONVERSION THREE_NODE_ONLY benchmark (count -> ns/op)")
        results.forEach { (count, nsPerOp) ->
            println("$count -> $nsPerOp")
        }

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun benchmark_conversion_mixedRules_scaling() {
        val results = benchmarkConversionScaling(mode = BenchmarkMode.MIXED)

        println("[NgramRuleScorer] CONVERSION MIXED benchmark (count -> ns/op)")
        results.forEach { (count, nsPerOp) ->
            println("$count -> $nsPerOp")
        }

        assertTrue(results.isNotEmpty())
    }

    private fun benchmarkRuleScaling(mode: BenchmarkMode): List<Pair<Int, Long>> {
        val prev = createNode(tango = "布")
        val current = createNode(tango = "で")
        val next = createNode(tango = "拭く")
        current.next = next

        val ruleCounts = listOf(0, 10, 100, 500, 1000)
        val warmupIterations = 10_000
        val measureIterations = 100_000

        return ruleCounts.map { count ->
            val scorer = createScorer(mode = mode, count = count)

            repeat(warmupIterations) {
                scorer.score(prevNode = prev, currentNode = current)
            }

            var checksum = 0L
            val elapsedNs = measureNanoTime {
                repeat(measureIterations) {
                    checksum += scorer.score(prevNode = prev, currentNode = current).toLong()
                }
            }

            // Keep checksum in play so the JIT cannot trivially remove score() calls.
            if (checksum == Long.MIN_VALUE) {
                throw AssertionError("unreachable checksum")
            }
            count to (elapsedNs / measureIterations)
        }
    }

    private fun benchmarkConversionScaling(mode: BenchmarkMode): List<Pair<Int, Long>> {
        val ruleCounts = listOf(0, 10, 100, 500, 1000)
        val warmupIterations = 100
        val measureIterations = 400
        val connectionIds = createConnectionIds()

        return ruleCounts.map { count ->
            val scorer = createScorer(mode = mode, count = count)
            val findPath = FindPath(ngramRuleScorerProvider = { scorer })

            repeat(warmupIterations) {
                val graph = createBenchmarkGraph()
                findPath.backwardAStar(
                    graph = graph,
                    length = 3,
                    connectionIds = connectionIds,
                    n = 5,
                )
            }

            var checksum = 0L
            val elapsedNs = measureNanoTime {
                repeat(measureIterations) {
                    val graph = createBenchmarkGraph()
                    val candidates = findPath.backwardAStar(
                        graph = graph,
                        length = 3,
                        connectionIds = connectionIds,
                        n = 5,
                    )
                    checksum += (candidates.firstOrNull()?.score ?: 0).toLong()
                }
            }

            if (checksum == Long.MIN_VALUE) {
                throw AssertionError("unreachable checksum")
            }
            count to (elapsedNs / measureIterations)
        }
    }

    private fun createScorer(mode: BenchmarkMode, count: Int): NgramRuleScorer {
        val twoNodeRules = when (mode) {
            BenchmarkMode.TWO_NODE_ONLY,
            BenchmarkMode.MIXED,
            -> List(count) { index ->
                TwoNodeRule(
                    prev = NodeFeature(word = "布"),
                    current = NodeFeature(word = "で"),
                    adjustment = -(index + 1),
                )
            }

            BenchmarkMode.THREE_NODE_ONLY -> emptyList()
        }

        val threeNodeRules = when (mode) {
            BenchmarkMode.THREE_NODE_ONLY,
            BenchmarkMode.MIXED,
            -> List(count) { index ->
                ThreeNodeRule(
                    first = NodeFeature(word = "布"),
                    second = NodeFeature(word = "で"),
                    third = NodeFeature(word = "拭く"),
                    adjustment = -(index + 1),
                )
            }

            BenchmarkMode.TWO_NODE_ONLY -> emptyList()
        }

        return NgramRuleScorer(
            twoNodeRules = twoNodeRules,
            threeNodeRules = threeNodeRules,
        )
    }

    private fun createConnectionIds(): ShortArray {
        // We only use IDs 0 and 1 in this synthetic graph.
        return ShortArray(NUM_OF_CONNECTION_ID * 2) { 0 }
    }

    private fun createBenchmarkGraph(): MutableMap<Int, MutableList<Node>> {
        val graph = mutableMapOf<Int, MutableList<Node>>()

        graph[0] = mutableListOf(createNode(tango = "BOS", l = 0, r = 0, len = 0, sPos = 0, score = 0))

        graph[1] = mutableListOf(
            createNode(tango = "布", score = 30, sPos = 0),
            createNode(tango = "不", score = 40, sPos = 0),
            createNode(tango = "府", score = 55, sPos = 0),
            createNode(tango = "負", score = 60, sPos = 0),
        )

        graph[2] = mutableListOf(
            createNode(tango = "で", score = 25, sPos = 1),
            createNode(tango = "は", score = 40, sPos = 1),
            createNode(tango = "が", score = 45, sPos = 1),
            createNode(tango = "を", score = 50, sPos = 1),
        )

        graph[3] = mutableListOf(
            createNode(tango = "拭く", score = 20, sPos = 2),
            createNode(tango = "服", score = 28, sPos = 2),
            createNode(tango = "吹く", score = 35, sPos = 2),
            createNode(tango = "福", score = 45, sPos = 2),
        )

        graph[4] = mutableListOf(createNode(tango = "EOS", l = 0, r = 0, len = 0, sPos = 4, score = 0))

        return graph
    }

    private fun createIkinaInasenaGraph(): MutableMap<Int, MutableList<Node>> {
        val graph = mutableMapOf<Int, MutableList<Node>>()

        graph[0] = mutableListOf(createNode(tango = "BOS", l = 0, r = 0, len = 0, sPos = 0, score = 0))

        graph[1] = mutableListOf(
            createNode(tango = "意気で", score = 1000, sPos = 0),
            createNode(tango = "粋で", score = 5000, sPos = 0),
        )

        graph[2] = mutableListOf(
            createNode(tango = "いなせな", score = 1000, sPos = 1),
        )

        graph[3] = mutableListOf(createNode(tango = "EOS", l = 0, r = 0, len = 0, sPos = 3, score = 0))

        return graph
    }

    private fun createNode(
        tango: String,
        l: Short = 1,
        r: Short = 1,
        len: Short = 1,
        sPos: Int = 0,
        score: Int = 0,
    ): Node {
        return Node(
            l = l,
            r = r,
            score = score,
            f = score,
            g = 0,
            tango = tango,
            len = len,
            yomiUsed = tango,
            sPos = sPos,
            prev = null,
            next = null,
        )
    }

    private enum class BenchmarkMode {
        TWO_NODE_ONLY,
        THREE_NODE_ONLY,
        MIXED,
    }
}
