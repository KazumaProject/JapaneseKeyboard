package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.ceil
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConversionSessionPerformanceProbeTest {

    @Test
    fun compareContinuousInputBackends() = runBlocking {
        assumeTrue(
            System.getProperty("conversionSessionPerfProbe") == "true" ||
                System.getenv("CONVERSION_SESSION_PERF_PROBE") == "true",
        )

        val phrase = "このあぷりのへんかんこうほをこうそくにしたい"
        val warmup = 5
        val iterations = 20
        val legacyEngine = TestEngineFactory.create()
        val incrementalEngine = TestEngineFactory.create()
        val repository = mock<UserDictionaryRepository>()
        whenever(repository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())

        repeat(warmup) {
            runSequence(legacyEngine, ConversionBackend.LEGACY, phrase, repository)
            runSequence(
                incrementalEngine,
                ConversionBackend.INCREMENTAL_SESSION,
                phrase,
                repository,
            )
        }

        val legacySamples = ArrayList<Long>(iterations * phrase.length)
        val incrementalSamples = ArrayList<Long>(iterations * phrase.length)
        var legacyLast = ""
        var incrementalLast = ""
        repeat(iterations) {
            legacyLast = runSequence(
                legacyEngine,
                ConversionBackend.LEGACY,
                phrase,
                repository,
                legacySamples,
            )
            incrementalLast = runSequence(
                incrementalEngine,
                ConversionBackend.INCREMENTAL_SESSION,
                phrase,
                repository,
                incrementalSamples,
            )
        }

        check(legacyLast == incrementalLast) {
            "Candidate mismatch: legacy=$legacyLast incremental=$incrementalLast"
        }
        val legacy = Stats.from(legacySamples)
        val incremental = Stats.from(incrementalSamples)
        val report = buildString {
            appendLine("phrase=$phrase")
            appendLine("warmup=$warmup")
            appendLine("iterations=$iterations")
            appendLine("commands=${iterations * phrase.length}")
            appendLine("legacy=$legacy")
            appendLine("incrementalSession=$incremental")
            appendLine("p50Speedup=${legacy.p50Us / incremental.p50Us}")
            appendLine("p95Speedup=${legacy.p95Us / incremental.p95Us}")
            appendLine("candidate=$legacyLast")
        }
        File("build/reports/conversion-perf").apply { mkdirs() }
            .resolve("conversion-session.txt")
            .writeText(report)
        println(report)
    }

    private suspend fun runSequence(
        engine: com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine,
        backend: ConversionBackend,
        phrase: String,
        repository: UserDictionaryRepository,
        samples: MutableList<Long>? = null,
    ): String {
        val session = KanaKanjiConversionSession(engine, backend)
        var firstCandidate = ""
        for (length in 1..phrase.length) {
            val request = request(phrase.substring(0, length), repository)
            val elapsed = measureNanoTime {
                firstCandidate = session.query(request).candidates.firstOrNull()?.string.orEmpty()
            }
            samples?.add(elapsed)
        }
        return firstCandidate
    }

    private fun request(
        input: String,
        repository: UserDictionaryRepository,
    ) = KanaKanjiQueryRequest(
        input = input,
        mode = CandidateQueryMode.PREDICTION,
        bunsetsuSeparation = true,
        n = 4,
        mozcUtPersonName = false,
        mozcUtPlaces = false,
        mozcUtWiki = false,
        mozcUtNeologd = false,
        mozcUtWeb = false,
        userDictionaryRepository = repository,
        learnRepository = null,
        omissionSearchEnabled = false,
        typoCorrectionJapaneseFlickEnabled = false,
        typoCorrectionQwertyEnglishEnabled = false,
        typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900,
        beamWidth = 20,
    )

    private data class Stats(
        val meanUs: Double,
        val p50Us: Double,
        val p95Us: Double,
        val p99Us: Double,
    ) {
        companion object {
            fun from(samplesNs: List<Long>): Stats {
                val sorted = samplesNs.sorted()
                fun percentile(value: Double): Double {
                    val index = (ceil(sorted.size * value).toInt() - 1).coerceIn(sorted.indices)
                    return sorted[index] / 1_000.0
                }
                return Stats(
                    meanUs = samplesNs.average() / 1_000.0,
                    p50Us = percentile(0.50),
                    p95Us = percentile(0.95),
                    p99Us = percentile(0.99),
                )
            }
        }
    }
}
