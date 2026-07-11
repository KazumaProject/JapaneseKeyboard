package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.ceil
import kotlin.system.measureNanoTime

class NgramRuleScorerPerformanceProbeTest {
    @Test
    fun measureTwoThroughFiveNodeScorerPerformanceAndMemory() {
        assumeTrue(
            System.getProperty("ngramPerfProbe") == "true" ||
                System.getenv("NGRAM_PERF_PROBE") == "true",
        )
        val counts = (System.getProperty("ngramPerfCounts") ?: System.getenv("NGRAM_PERF_COUNTS") ?: "0,10,100,500,1000")
            .split(',').mapNotNull { it.trim().toIntOrNull() }
        val warmup = (System.getProperty("ngramPerfWarmup") ?: System.getenv("NGRAM_PERF_WARMUP"))
            ?.toIntOrNull() ?: 10_000
        val iterations = (System.getProperty("ngramPerfIterations") ?: System.getenv("NGRAM_PERF_ITERATIONS"))
            ?.toIntOrNull() ?: 100_000
        val report = StringBuilder("n\truleCount\tdistribution\tbuildNs\tbuildAllocatedBytes\tretainedHeapBytes\tp50Ns\tp95Ns\tallocatedBytesPerScore\n")

        // Exclude one-time class loading and JIT setup from retained-heap deltas.
        createScorer(2, 1, Distribution.DISTRIBUTED)
        forceGc()

        for (order in 2..5) {
            for (count in counts) {
                for (distribution in Distribution.entries) {
                    forceGc()
                    val heapBefore = usedHeap()
                    val allocatedBeforeBuild = allocatedBytes()
                    lateinit var scorer: NgramRuleScorer
                    val buildNs = measureNanoTime { scorer = createScorer(order, count, distribution) }
                    val buildAllocated = delta(allocatedBeforeBuild, allocatedBytes())
                    val retainedScorers = ArrayList<NgramRuleScorer>(RETAINED_SCORER_COPIES).apply {
                        add(scorer)
                        repeat(RETAINED_SCORER_COPIES - 1) {
                            add(createScorer(order, count, distribution))
                        }
                    }
                    forceGc()
                    val retained = (usedHeap() - heapBefore) / RETAINED_SCORER_COPIES
                    val path = createPath()
                    repeat(warmup) { scorer.score(path[0], path[1], path[2], path[3], path[4]) }
                    val samples = LongArray(20)
                    val allocatedBeforeScore = allocatedBytes()
                    repeat(samples.size) { sample ->
                        samples[sample] = measureNanoTime {
                            repeat(iterations / samples.size) {
                                scorer.score(path[0], path[1], path[2], path[3], path[4])
                            }
                        } / (iterations / samples.size)
                    }
                    val scoreAllocated = delta(allocatedBeforeScore, allocatedBytes())
                    report.appendLine(
                        listOf(
                            order, count, distribution.name, buildNs, buildAllocated, retained,
                            percentile(samples, 0.50), percentile(samples, 0.95),
                            if (scoreAllocated >= 0) scoreAllocated / iterations else -1,
                        ).joinToString("\t"),
                    )
                    check(retainedScorers.size == RETAINED_SCORER_COPIES)
                }
            }
        }
        val output = File("build/reports/ngram-performance").apply { mkdirs() }
        File(output, "scorer.tsv").writeText(report.toString())
        println(report)
    }

    private fun createScorer(order: Int, count: Int, distribution: Distribution): NgramRuleScorer =
        NgramRuleScorer(
            List(count) { index ->
                val current = when (distribution) {
                    Distribution.DISTRIBUTED -> "current-$index"
                    Distribution.SAME_CURRENT -> "は"
                    Distribution.WILDCARD_CURRENT -> null
                }
                NgramRule(
                    nodes = List(order) { nodeIndex ->
                        when (nodeIndex) {
                            0 -> NodeFeature(word = if (index == 0) "私" else "missing-$index")
                            1 -> NodeFeature(word = current)
                            else -> NodeFeature()
                        }
                    },
                    adjustment = -1,
                )
            },
        )

    private fun createPath(): List<Node> = listOf("私", "は", "布", "を", "拭く").map { word ->
        Node(1, 1, 0, 0, tango = word, len = 1, yomiUsed = word, sPos = 0)
    }

    private fun forceGc() {
        repeat(3) { System.gc(); Thread.sleep(30) }
    }

    private fun usedHeap(): Long = Runtime.getRuntime().run { totalMemory() - freeMemory() }

    private fun allocatedBytes(): Long = runCatching {
        val factory = Class.forName("java.lang.management.ManagementFactory")
        val bean = factory.getMethod("getThreadMXBean").invoke(null)
        val type = Class.forName("com.sun.management.ThreadMXBean")
        if (type.getMethod("isThreadAllocatedMemoryEnabled").invoke(bean) != true) {
            type.getMethod("setThreadAllocatedMemoryEnabled", java.lang.Boolean.TYPE).invoke(bean, true)
        }
        type.getMethod("getCurrentThreadAllocatedBytes").invoke(bean) as Long
    }.getOrDefault(-1L)

    private fun delta(before: Long, after: Long): Long = if (before >= 0 && after >= before) after - before else -1

    private fun percentile(values: LongArray, fraction: Double): Long {
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)]
    }

    private enum class Distribution { DISTRIBUTED, SAME_CURRENT, WILDCARD_CURRENT }

    private companion object {
        const val RETAINED_SCORER_COPIES = 8
    }
}
