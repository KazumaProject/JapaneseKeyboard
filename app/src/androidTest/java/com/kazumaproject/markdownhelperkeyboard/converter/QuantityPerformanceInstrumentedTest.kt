package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in device diagnostics; the same test APK also runs against the baseline application APK. */
@RunWith(AndroidJUnit4::class)
class QuantityPerformanceInstrumentedTest {
    @Test fun captureCorpus() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quantityCorpusProbe") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        val phrases = args.getString("quantityInputs")?.split(',') ?: listOf(
            "わたしはよんふんまつ", "りんごをさんこかう", "さんびゃくごじゅうえんつかう",
            "にじゅうさんじごふんにでる", "さんまいとにまい", "はつかまでまつ",
            "このあぷりのへんかんこうほをこうそくにしたい",
        )
        val output = JSONArray()
        for (backend in ConversionBackend.entries) for (bunsetsu in listOf(false, true)) for (phrase in phrases) {
            val session = KanaKanjiConversionSession(engine, backend)
            val inputs = (1..phrase.length).map { phrase.take(it) } +
                listOf(phrase.dropLast(2), phrase.dropLast(1), phrase, phrase.take(2), phrase)
            for ((index, input) in inputs.withIndex()) {
                val request = KanaKanjiQueryRequest(input, CandidateQueryMode.PREDICTION, bunsetsu, 4,
                    false, false, false, false, false, entry.userDictionaryRepository(), null,
                    false, false, false, 3000, 1900, 20, PredictionConfig(), true)
                val beforeBytes = stat("art.gc.bytes-allocated")
                val beforeGc = stat("art.gc.gc-count")
                val beforeGcTime = stat("art.gc.gc-time")
                val start = System.nanoTime()
                val result = session.query(request)
                val elapsed = System.nanoTime() - start
                val allocated = stat("art.gc.bytes-allocated") - beforeBytes
                val gcCount = stat("art.gc.gc-count") - beforeGc
                val gcTime = stat("art.gc.gc-time") - beforeGcTime
                val candidates = JSONArray()
                for (candidate in result.candidates) {
                    candidates.put(JSONObject().put("text", candidate.string).put("score", candidate.score)
                        .put("type", candidate.type.toInt()).put("length", candidate.length.toInt())
                        .put("reading", candidate.yomi).put("left", candidate.leftId?.toInt())
                        .put("right", candidate.rightId?.toInt()).put("spans", candidate.numberSpans.toString()))
                }
                assertTrue("$input must have candidates", candidates.length() > 0)
                val row = JSONObject().put("backend", backend.name).put("bunsetsu", bunsetsu)
                    .put("phrase", phrase).put("index", index).put("input", input).put("candidates", candidates)
                    .put("segments", result.candidateSegmentsByString.toSortedMap().toString())
                    .put("splits", result.bunsetsuResult?.splitPatterns.toString())
                    .put("splitByCandidate", result.bunsetsuResult?.splitPatternByCandidateString?.toSortedMap().toString())
                    .put("systemMatches", result.bunsetsuResult?.systemNgramMatchedCandidates?.sorted().toString())
                    .put("elapsedNs", elapsed).put("allocatedBytes", allocated)
                    .put("gcCount", gcCount).put("gcTimeMs", gcTime)
                if (backend == ConversionBackend.INCREMENTAL_SESSION) {
                    val state = field(session, "incrementalState")!!
                    val workspace = field(field(state, "pathState")!!, "quantityWorkspace")!!
                    row.put("quantityStates", field(field(workspace, "states")!!, "size"))
                        .put("quantityEdges", field(field(workspace, "edges")!!, "size"))
                }
                output.put(row)
            }
        }
        File(context.filesDir, "conversion-perf").apply { mkdirs() }
            .resolve("quantity-corpus.json").writeText(output.toString(2))
    }
    @Test fun repeatedLongAndShortEditsKeepRetainedMemoryBounded() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("quantityRetentionProbe") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        val inputs = listOf("わたしはよんふんまってりんごをさんこかってひゃくえんはらう", "わ", "わたしはよん", "わたしはよんふんまつ")
        val report = JSONArray()
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            suspend fun edit() {
                for (input in inputs) session.query(KanaKanjiQueryRequest(input, CandidateQueryMode.PREDICTION,
                    true, 4, false, false, false, false, false, entry.userDictionaryRepository(), null,
                    false, false, false, 3000, 1900, 20))
            }
            repeat(10) { edit() }
            val heaps = ArrayList<Long>()
            for (batch in 0..3) {
                if (batch > 0) repeat(25) { edit() }
                repeat(3) { Runtime.getRuntime().gc(); Thread.sleep(50) }
                val heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                heaps.add(heap)
                val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                report.put(JSONObject().put("backend", backend.name).put("edits", batch * 100)
                    .put("javaHeapBytes", heap).put("totalPssKb", memory.totalPss))
            }
            assertTrue("Retained heap grew across repeated edits: $heaps", heaps.last() - heaps.first() < 8L * 1024 * 1024)
        }
        File(context.filesDir, "conversion-perf").apply { mkdirs() }
            .resolve("quantity-retention.json").writeText(report.toString(2))
    }

    private fun stat(name: String) = Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L
    private fun field(value: Any, name: String): Any? = value.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(value)
}
