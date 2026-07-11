package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.ceil
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class PrefixConversionPerformanceInstrumentedTest {

    @Test
    fun measureExactReportedInputAsItIsTyped() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("prefixConversionPerfProbe") == "true")
        val label = arguments.getString("conversionPerfLabel") ?: "prefix-conversion"
        val sessions = arguments.getString("conversionPerfIterations")?.toIntOrNull() ?: 30
        val input = "わたしのなまえはなかのかもしれないですね"
        val prefixes = input.indices.map { input.substring(0, it + 1) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()

        val firstElapsed = runSession(engine, repository, prefixes).first
        repeat(2) { runSession(engine, repository, prefixes) }
        Runtime.getRuntime().gc()
        val bytesBefore = runtimeStat("art.gc.bytes-allocated")
        val gcBefore = runtimeStat("art.gc.gc-count")
        val sessionTotals = ArrayList<Double>(sessions)
        val perPrefix = Array(prefixes.size) { ArrayList<Double>(sessions) }
        var finalCandidates: List<Candidate> = emptyList()
        repeat(sessions) {
            val (totalMs, measurements) = runSession(engine, repository, prefixes)
            sessionTotals += totalMs
            measurements.forEachIndexed { index, measurement ->
                perPrefix[index] += measurement.elapsedMs
                if (index == prefixes.lastIndex) finalCandidates = measurement.candidates
            }
        }
        val allocatedBytes = runtimeStat("art.gc.bytes-allocated") - bytesBefore
        val gcCount = runtimeStat("art.gc.gc-count") - gcBefore

        val report = buildString {
            appendLine("label=$label")
            appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT}")
            appendLine("input=$input")
            appendLine("prefixCount=${prefixes.size}")
            appendLine("sessions=$sessions")
            appendLine("firstTypingTotalMs=$firstElapsed")
            appendLine("warmTypingTotalP50Ms=${percentile(sessionTotals, 0.50)}")
            appendLine("warmTypingTotalP95Ms=${percentile(sessionTotals, 0.95)}")
            appendLine("warmTypingTotalMaxMs=${sessionTotals.maxOrNull()}")
            appendLine("allocatedBytesPerSession=${allocatedBytes / sessions}")
            appendLine("allocatedBytesPerConversion=${allocatedBytes / (sessions * prefixes.size)}")
            appendLine("gcCount=$gcCount")
            appendLine("prefixMeasurementsMs")
            prefixes.forEachIndexed { index, prefix ->
                val values = perPrefix[index]
                appendLine("${prefix.length}\t$prefix\tavg=${values.average()}\tp50=${percentile(values, 0.50)}\tp95=${percentile(values, 0.95)}\tmax=${values.maxOrNull()}")
            }
            appendLine("finalCandidates")
            finalCandidates.take(12).forEach { appendLine("${it.string}\t${it.score}\t${it.yomi.orEmpty()}") }
        }
        val outputDir = File(context.filesDir, "conversion-perf").apply { mkdirs() }
        File(outputDir, "$label.txt").writeText(report)
        println(report)
    }

    private suspend fun runSession(
        engine: KanaKanjiEngine,
        repository: UserDictionaryRepository,
        prefixes: List<String>,
    ): Pair<Double, List<Measurement>> {
        val measurements = ArrayList<Measurement>(prefixes.size)
        val totalNs = measureNanoTime {
            prefixes.forEach { prefix ->
                lateinit var candidates: List<Candidate>
                val elapsedNs = measureNanoTime { candidates = engine.convertForProbe(prefix, repository) }
                measurements += Measurement(elapsedNs / 1_000_000.0, candidates)
            }
        }
        return totalNs / 1_000_000.0 to measurements
    }

    private suspend fun KanaKanjiEngine.convertForProbe(
        input: String,
        repository: UserDictionaryRepository,
    ): List<Candidate> = getCandidatesWithBunsetsuSeparation(
        input = input,
        n = 4,
        mozcUtPersonName = false,
        mozcUTPlaces = false,
        mozcUTWiki = false,
        mozcUTNeologd = false,
        mozcUTWeb = false,
        userDictionaryRepository = repository,
        learnRepository = null,
        isOmissionSearchEnable = false,
        enableTypoCorrectionJapaneseFlick = false,
        enableTypoCorrectionQwertyEnglish = false,
        typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900,
        beamWidth = 20,
    ).candidates

    private fun runtimeStat(name: String): Long = Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L

    private fun percentile(values: List<Double>, fraction: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)]
    }

    private data class Measurement(val elapsedMs: Double, val candidates: List<Candidate>)
}
