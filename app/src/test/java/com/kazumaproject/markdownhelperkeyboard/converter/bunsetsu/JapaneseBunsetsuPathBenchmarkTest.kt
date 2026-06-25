package com.kazumaproject.markdownhelperkeyboard.converter.bunsetsu

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.JapaneseCandidateDedupeMode
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.JapaneseBunsetsuOptions
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.JapaneseSearchOptions
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.ceil

class JapaneseBunsetsuPathBenchmarkTest {

    @Test
    fun benchmarkJapaneseBunsetsuPathStateSpeedAndMemoryStayBounded() {
        val cases = listOf(
            BenchmarkCase("short", "かみにかく", 4),
            BenchmarkCase("medium", "きょうのてんきははれです", 8),
            BenchmarkCase("long", "にほんごのれんぶんせつへんかん", 13),
            BenchmarkCase("bunsetsu", "わたしはがっこうにいきます", 12),
            BenchmarkCase("unknown_like", "みちのえきにいく", 8),
            BenchmarkCase("number_mixed", "きょうは2026ねんです", 11),
        )
        val rows = cases.map { runBenchmark(it) }
        writeBenchmarkRows(rows)

        rows.forEach { row ->
            assertTrue("${row.name} p95 too slow: ${row.p95Nanos}ns", row.p95Nanos < 50_000_000L)
            assertTrue("${row.name} memory delta too high: ${row.peakMemoryDeltaBytes}", row.peakMemoryDeltaBytes < 8_000_000L)
            assertTrue("${row.name} produced no candidates", row.maxCandidateCount > 0)
        }
    }

    private fun runBenchmark(case: BenchmarkCase): BenchmarkRow {
        repeat(WARMUP_ITERATIONS) {
            convert(case)
        }

        val runtime = Runtime.getRuntime()
        val elapsed = mutableListOf<Long>()
        var peakMemoryDelta = 0L
        var maxCandidateCount = 0
        repeat(MEASURE_ITERATIONS) {
            val beforeMemory = runtime.usedMemory()
            val start = System.nanoTime()
            val candidates = convert(case)
            val nanos = System.nanoTime() - start
            val afterMemory = runtime.usedMemory()
            elapsed.add(nanos)
            peakMemoryDelta = maxOf(peakMemoryDelta, (afterMemory - beforeMemory).coerceAtLeast(0L))
            maxCandidateCount = maxOf(maxCandidateCount, candidates)
        }

        elapsed.sort()
        return BenchmarkRow(
            name = case.name,
            input = case.input,
            p50Nanos = percentile(elapsed, 0.50),
            p95Nanos = percentile(elapsed, 0.95),
            peakMemoryDeltaBytes = peakMemoryDelta,
            maxCandidateCount = maxCandidateCount,
        )
    }

    private fun convert(case: BenchmarkCase): Int {
        val result = FindPath().backwardAStarWithBunsetsu(
            graph = benchmarkGraph(case.length),
            length = case.length,
            connectionIds = ShortArray(MATRIX_SIZE * MATRIX_SIZE),
            connectionMatrixSize = MATRIX_SIZE,
            n = 20,
            input = case.input,
            options = JapaneseBunsetsuOptions(
                searchOptions = JapaneseSearchOptions(beamWidth = 100),
                candidateDedupeMode = JapaneseCandidateDedupeMode.IDENTITY,
                includeDedupeTrace = false,
            ),
        )
        return result.candidates.size
    }

    private fun benchmarkGraph(length: Int): MutableMap<Int, MutableList<Node>> {
        val graph = linkedMapOf<Int, MutableList<Node>>()
        graph[0] = mutableListOf(BOS)
        for (start in 0 until length) {
            val singleEnd = start + 1
            graph.getOrPut(singleEnd) { mutableListOf() }.add(
                createNode(
                    tango = "単$start",
                    yomi = "よ$start",
                    len = 1,
                    sPos = start,
                    id = if (start % 3 == 0) 12 else 1,
                    score = 100 + start,
                )
            )
            if (start + 2 <= length) {
                graph.getOrPut(start + 2) { mutableListOf() }.add(
                    createNode(
                        tango = "複$start",
                        yomi = "よ${start}よ${start + 1}",
                        len = 2,
                        sPos = start,
                        id = if (start % 2 == 0) 13 else 1,
                        score = 160 + start,
                    )
                )
            }
        }
        graph[length + 1] = mutableListOf(
            createNode(
                tango = "EOS",
                yomi = "EOS",
                len = 0,
                sPos = length + 1,
                id = 0,
                score = 0,
            )
        )
        return graph
    }

    private fun createNode(
        tango: String,
        yomi: String,
        len: Short,
        sPos: Int,
        id: Short,
        score: Int,
    ): Node = Node(
        l = id,
        r = id,
        score = score,
        f = score,
        g = score,
        tango = tango,
        len = len,
        yomiUsed = yomi,
        sPos = sPos,
        prev = null,
        next = null,
    )

    private fun percentile(values: List<Long>, percentile: Double): Long {
        val index = (ceil(values.size * percentile).toInt() - 1).coerceIn(values.indices)
        return values[index]
    }

    private fun writeBenchmarkRows(rows: List<BenchmarkRow>) {
        val output = File("build/reports/japanese-bunsetsu/benchmark.tsv")
        output.parentFile?.mkdirs()
        output.printWriter().use { writer ->
            writer.println("case\tinput\tp50_ns\tp95_ns\tpeak_memory_delta_bytes\tmax_candidate_count")
            rows.forEach { row ->
                writer.println(
                    listOf(
                        row.name,
                        row.input,
                        row.p50Nanos.toString(),
                        row.p95Nanos.toString(),
                        row.peakMemoryDeltaBytes.toString(),
                        row.maxCandidateCount.toString(),
                    ).joinToString("\t")
                )
            }
        }
    }

    private fun Runtime.usedMemory(): Long = totalMemory() - freeMemory()

    private data class BenchmarkCase(
        val name: String,
        val input: String,
        val length: Int,
    )

    private data class BenchmarkRow(
        val name: String,
        val input: String,
        val p50Nanos: Long,
        val p95Nanos: Long,
        val peakMemoryDeltaBytes: Long,
        val maxCandidateCount: Int,
    )

    private companion object {
        const val MATRIX_SIZE = 32
        const val WARMUP_ITERATIONS = 5
        const val MEASURE_ITERATIONS = 20
    }
}
