package com.kazumaproject.markdownhelperkeyboard.converter.glide

import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGlideDecoderPerformanceTest {
    @Test
    fun benchmarkStyleDecodeTimesAndCandidateCountsStayBounded() {
        val metrics = mutableListOf<QwertyGlideDecodeMetrics>()
        val entries = QwertyGlideTestFixtures.dictionary(extraNoiseCount = 50_000) +
                QwertyGlideTestFixtures.sameBucketNoise(10_000, first = 'h', last = 'o') +
                QwertyGlideTestFixtures.sameBucketNoise(10_000, first = 's', last = 'g')
        val options = QwertyGlideDecodeOptions()
        val decoder = QwertyGlideDecoder(
            dictionaryProvider = QwertyGlideIndexedDictionaryProvider(entries),
            options = options,
            dictionaryReady = true,
            metricsListener = metrics::add
        )

        val cases = listOf(
            "short" to QwertyGlideTestFixtures.strokeFor("test"),
            "medium" to QwertyGlideTestFixtures.strokeFor("hello"),
            "long" to QwertyGlideTestFixtures.strokeFor("keyboard"),
            "ambiguous" to QwertyGlideTestFixtures.strokeFor("something", 20f, -20f, -20f, 20f)
        )

        for ((name, stroke) in cases) {
            decoder.clearCache()
            repeat(5) {
                decoder.decode(stroke, QwertyGlideTestFixtures.proximityInfo, "", 12)
                decoder.clearCache()
            }
            val sampleTimes = LongArray(30)
            val before = metrics.size
            repeat(sampleTimes.size) { index ->
                val startedAt = System.nanoTime()
                decoder.decode(stroke, QwertyGlideTestFixtures.proximityInfo, "", 12)
                sampleTimes[index] = (System.nanoTime() - startedAt) / 1_000_000L
                decoder.clearCache()
            }
            val caseMetrics = metrics.drop(before)
            val p50 = sampleTimes.percentile(50)
            val p95 = sampleTimes.percentile(95)
            val max = sampleTimes.maxOrNull() ?: 0L
            println("QWERTY glide $name decode: p50=${p50}ms p95=${p95}ms max=${max}ms full_score_max=${caseMetrics.maxOf { it.fullScoreCandidateCount }}")

            assertTrue("$name full scorer input should be bounded", caseMetrics.all { it.fullScoreCandidateCount <= options.fullScoreCandidateLimit })
            assertTrue("$name decode should not regress grossly: p95=${p95}ms", p95 <= 180L)
        }
    }

    private fun LongArray.percentile(percent: Int): Long {
        val sorted = sorted()
        val index = ((percent / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
