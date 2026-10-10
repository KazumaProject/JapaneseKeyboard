package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.graph.FlickCorrectionInput
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.ceil
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class FlickCorrectionPerformanceInstrumentedTest {
    @Test fun regressionCasesAndContinuousTypingLatency() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        val repository = entry.userDictionaryRepository()
        fun session() = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
        val literal = session()
        val legacy = session()
        val improved = session()
        improved.enablePerformanceProbe()
        fun request(input: String, enhanced: Boolean, old: Boolean = false) = KanaKanjiQueryRequest(
            input, CandidateQueryMode.PREDICTION, true, 4, false, false, false, false, false,
            repository, null, false, old, false, 3000, 1900, 20,
            flickCorrectionInput = if (enhanced) FlickCorrectionInput(input) else null,
        )
        val corpus = InstrumentationRegistry.getInstrumentation().context.assets
            .open("flick_correction_corpus.tsv").bufferedReader().use { it.readLines() }
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        val normal = corpus.filter { it[0] == "normal" }
        val errors = corpus.filter { it[0] != "normal" }
        var unchanged = 0
        val normalChanges = ArrayList<String>()
        for (row in normal) {
            val before = literal.query(request(row[1], false)).candidates.firstOrNull()?.string
            val after = improved.query(request(row[1], true)).candidates.firstOrNull()?.string
            if (before == after) unchanged++ else normalChanges.add("${row[1]}: $before -> $after")
        }
        var oldTop3 = 0
        var newTop3 = 0
        val errorResults = ArrayList<String>()
        for (row in errors) {
            val before = legacy.query(request(row[1], false, true)).candidates
            val after = improved.query(request(row[1], true)).candidates
            fun recovered(candidates: List<Candidate>) = candidates.take(3).any { it.yomi == row[2] }
            if (recovered(before)) oldTop3++
            if (recovered(after)) newTop3++
            errorResults.add("${row[1]} -> ${row[2]}: old=${before.take(3).map { it.string }}, new=${after.take(3).map { it.string }}")
        }
        val phrases = listOf("このあぷりのへんかんこうほをこうそくにしたい",
            "こんちはせんせい", "こんにちちは", "よしろく")
        val offSamples = ArrayList<Long>()
        val oldSamples = ArrayList<Long>()
        val newSamples = ArrayList<Long>()
        val additionalSamples = ArrayList<Long>()
        val graphSamples = ArrayList<Long>()
        val backwardSamples = ArrayList<Long>()
        val forwardSamples = ArrayList<Long>()
        val penaltySamples = ArrayList<Long>()
        var maximumQueueElements = 0
        repeat(15) { iteration ->
            for (phrase in phrases) {
                for (length in 1..phrase.length) {
                    val input = phrase.take(length)
                    // Interleave the treatments to avoid temperature/load bias between batches.
                    val treatments = listOf(0, 1, 2).let { it.drop(iteration % 3) + it.take(iteration % 3) }
                    val durations = LongArray(3)
                    for (treatment in treatments) durations[treatment] = measureNanoTime {
                        when (treatment) {
                            0 -> literal.query(request(input, false))
                            1 -> legacy.query(request(input, false, true))
                            else -> improved.query(request(input, true))
                        }
                    }
                    if (iteration >= 3) {
                        offSamples.add(durations[0]); oldSamples.add(durations[1]); newSamples.add(durations[2])
                        additionalSamples.add(durations[2] - durations[1])
                        improved.performanceSnapshot()?.let { snapshot ->
                            graphSamples.add(snapshot.graphNs)
                            backwardSamples.add(snapshot.backwardSearchNs)
                            forwardSamples.add(snapshot.forwardDpNs)
                            penaltySamples.add(snapshot.penaltyNs)
                            maximumQueueElements = maxOf(maximumQueueElements, snapshot.queueElementCount)
                        }
                    }
                }
            }
        }
        fun p95(samples: List<Long>): Double = samples.sorted()[ceil(samples.size * 0.95).toInt() - 1] / 1_000_000.0
        val report = buildString {
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}")
            appendLine("normalFirstPreserved=$unchanged/${normal.size}")
            appendLine("legacyTop3=$oldTop3/${errors.size}")
            appendLine("improvedTop3=$newTop3/${errors.size}")
            appendLine("samples=${newSamples.size}, warmupSequences=3, measuredSequences=12")
            appendLine("offP95Ms=${p95(offSamples)}")
            appendLine("legacyCorrectionP95Ms=${p95(oldSamples)}")
            appendLine("improvedCorrectionP95Ms=${p95(newSamples)}")
            appendLine("additionalP95Ms=${p95(newSamples) - p95(oldSamples)}")
            appendLine("pairedAdditionalP95Ms=${p95(additionalSamples)}")
            appendLine("graphP95Ms=${p95(graphSamples)}, backwardP95Ms=${p95(backwardSamples)}, maximumQueueElements=$maximumQueueElements")
            appendLine("forwardP95Ms=${p95(forwardSamples)}, penaltyP95Ms=${p95(penaltySamples)}")
            appendLine("normalChanges=$normalChanges")
            errorResults.forEach { appendLine(it) }
        }
        File(context.filesDir, "conversion-perf").apply { mkdirs() }.resolve("flick-correction.txt").writeText(report)
        println(report)
        assertTrue(report, unchanged * 100 >= normal.size * 99)
        assertTrue(report, newTop3 > oldTop3)
        // Enforce wall-clock budgets only in a controlled device/build run; debug and
        // emulator timings still appear in the report but are not stable CI gates.
        if (InstrumentationRegistry.getArguments().getString("enforceFlickLatencyBudget") == "true") {
            assertTrue(report, p95(additionalSamples) <= 10.0)
        }
    }
}
