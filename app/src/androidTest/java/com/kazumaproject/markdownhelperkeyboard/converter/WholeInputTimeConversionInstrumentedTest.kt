package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Build
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.number.NumberCandidateConfig
import com.kazumaproject.markdownhelperkeyboard.converter.number.NumberCandidateFormat
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The identical harness runs against both APKs; it does not switch production behavior. */
@RunWith(AndroidJUnit4::class)
class WholeInputTimeConversionInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val entry get() = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
    private val args get() = InstrumentationRegistry.getArguments()
    private val times = linkedMapOf(
        "くじごふん" to listOf("9時5分", "９時５分", "九時五分"),
        "よじごふん" to listOf("4時5分", "４時５分", "四時五分"),
        "よじきゅうふん" to listOf("4時9分", "４時９分", "四時九分"),
        "じゅうじごふん" to listOf("10時5分", "１０時５分", "十時五分"),
        "じゅうにじさんじゅっぷん" to listOf("12時30分", "１２時３０分", "十二時三十分"),
    )
    private val controls = listOf(
        "くじびき", "よじのぼる", "くじにあたる", "じゅうしょ", "にほんご",
        "はつかにせんえんはらう", "くじごふんにあう", "-くじごふん", "いちてんごえん",
        "くじ", "ごふん", "よじご", "くじろくじゅっぷん", "さんじゅうじごふん",
        "わたしはきのうともだちとえきまえであいました", "いちまんえん", "0002", "ふたり",
    )

    @Test fun snapshotCandidates() = runBlocking {
        assumeTrue(args.getString("wholeTimeSnapshot") == "true")
        val entry = entry
        val rows = JSONArray()
        val orders = permutations(NumberCandidateFormat.entries.toList())
        val changed = args.getString("wholeTimeChanged") == "true"
        for (backend in ConversionBackend.entries) for (mode in CandidateQueryMode.entries)
            for (bunsetsu in listOf(false, true)) for (n in listOf(1, 4, 8)) {
                val session = KanaKanjiConversionSession(entry.kanaKanjiEngine(), backend)
                for (input in times.keys + controls) {
                    val configs = if (input in times) orders.map { NumberCandidateConfig(order = it) } + NumberCandidateConfig(false)
                        else listOf(NumberCandidateConfig(), NumberCandidateConfig(false))
                    for (config in configs) {
                        val result = session.query(request(input, mode, bunsetsu, n, config))
                        val label = "$backend/$mode/$bunsetsu/$n/$input/${config.enhanceCounterCandidates}/${config.order}"
                        if (changed && input in times && config.enhanceCounterCandidates) {
                            val expected = config.normalizedOrder.map { times.getValue(input)[it.ordinal] }
                            assertEquals(label, expected, result.candidates.take(3).map { it.string })
                            assertEquals(label, 1, result.candidates.count { it.string == expected.first() })
                            result.candidates.take(3).forEach { candidate ->
                                assertEquals(label, candidate.string, candidate.commitText)
                                assertEquals(label, candidate.string, candidate.conversionSegments.joinToString("") { it.output })
                                assertEquals(label, 0, candidate.conversionSegments.first().inputStart)
                                assertEquals(label, input.length, candidate.conversionSegments.last().inputEnd)
                                assertTrue(label, candidate.conversionSegments.all { it.numericIdentity != null })
                                if (mode != CandidateQueryMode.EISUKANA) {
                                    assertEquals(label, candidate.conversionSegments, result.candidateSegmentsByString[candidate.string])
                                }
                            }
                            result.bunsetsuResult?.let {
                                assertEquals(label, emptyList<Int>(), it.primarySplitPositions)
                            }
                        }
                        rows.put(JSONObject().put("key", label).put("input", input)
                            .put("enabled", config.enhanceCounterCandidates)
                            .put("candidates", JSONArray(result.candidates.map { it.toString() }))
                            .put("paths", JSONObject(result.candidateSegmentsByString.mapValues { it.value.toString() }))
                            .put("splits", JSONObject(result.bunsetsuResult?.splitPatternByCandidateString.orEmpty().mapValues { it.value.toString() })))
                    }
                }
            }
        File(context.filesDir, "whole-time-snapshot.json").writeText(rows.toString())
        println("WHOLE_TIME_SNAPSHOT cases=${rows.length()} changed=$changed")
    }

    @Test fun measureConversion() = runBlocking {
        assumeTrue(args.getString("wholeTimePerf") == "true")
        val entry = entry
        val engine = entry.kanaKanjiEngine()
        val iterations = args.getString("wholeTimeIterations")?.toInt() ?: 300
        val warmup = args.getString("wholeTimeWarmup")?.toInt() ?: 100
        data class Workload(val name: String, val backend: ConversionBackend, val inputs: List<String>,
            val enabled: Boolean = true, val fresh: Boolean = false, val mode: CandidateQueryMode = CandidateQueryMode.NO_TAB_DEFAULT)
        fun typing(input: String) = (1..input.length).map { input.take(it) } + (input.length - 1 downTo 1).map { input.take(it) }
        val normal = listOf("にほんご", "くじびき", "じゅうしょ", "きょうはいいてんきですね", "いちまんえん", "わたしはきのうともだちとえきまえであいました")
        val workloads = ConversionBackend.entries.flatMap { backend -> listOf(
            Workload("normal/$backend", backend, normal),
            Workload("time/$backend", backend, times.keys.toList()),
            Workload("time-off/$backend", backend, times.keys.toList(), enabled = false),
        ) } + listOf(
            Workload("fresh-session/normal", ConversionBackend.INCREMENTAL_SESSION, normal, fresh = true),
            Workload("fresh-session/time", ConversionBackend.INCREMENTAL_SESSION, times.keys.toList(), fresh = true),
            Workload("typing/normal", ConversionBackend.INCREMENTAL_SESSION, typing("にほんご")),
            Workload("typing/time", ConversionBackend.INCREMENTAL_SESSION, typing("くじごふん")),
            Workload("english-kana/normal", ConversionBackend.LEGACY, normal, mode = CandidateQueryMode.EISUKANA),
            Workload("english-kana/time", ConversionBackend.LEGACY, times.keys.toList(), mode = CandidateQueryMode.EISUKANA),
        )
        var blackHole = 0L
        val reports = JSONArray()
        for (workload in workloads) {
            val session = KanaKanjiConversionSession(engine, workload.backend)
            val requests = workload.inputs.map { request(it, workload.mode, false, 8, NumberCandidateConfig(workload.enabled)) }
            suspend fun convert(index: Int) {
                val result = (if (workload.fresh) KanaKanjiConversionSession(engine, workload.backend) else session)
                    .query(requests[index % requests.size])
                blackHole += result.candidates.size + (result.candidates.firstOrNull()?.string?.length ?: 0)
            }
            repeat(warmup) { convert(it) }
            settleGc()
            val baseline = memory()
            val allocBefore = stat("art.gc.bytes-allocated")
            val gcBefore = stat("art.gc.gc-count")
            val gcTimeBefore = stat("art.gc.gc-time")
            val samples = LongArray(iterations)
            repeat(iterations) { index ->
                val start = System.nanoTime()
                convert(index)
                samples[index] = System.nanoTime() - start
            }
            val allocAfter = stat("art.gc.bytes-allocated")
            val gcAfter = stat("art.gc.gc-count")
            val gcTimeAfter = stat("art.gc.gc-time")
            val immediate = memory()
            // A separate retention workload is excluded from latency/allocation measurements.
            repeat(1_000) { convert(it) }
            settleGc()
            val retained = memory()
            val sorted = samples.sortedArray()
            fun percentile(p: Double) = sorted[((sorted.size - 1) * p).toInt()] / 1_000.0
            reports.put(JSONObject().put("workload", workload.name).put("iterations", iterations)
                .put("averageUs", samples.average() / 1_000).put("p50Us", percentile(.50))
                .put("p95Us", percentile(.95)).put("p99Us", percentile(.99))
                .put("allocatedBytesPerCall", if (allocBefore != null && allocAfter != null) (allocAfter - allocBefore).toDouble() / iterations else JSONObject.NULL)
                .put("gcCount", if (gcBefore != null && gcAfter != null) gcAfter - gcBefore else JSONObject.NULL)
                .put("gcTimeMs", if (gcTimeBefore != null && gcTimeAfter != null) gcTimeAfter - gcTimeBefore else JSONObject.NULL)
                .put("baseline", baseline).put("immediate", immediate).put("retained", retained)
                .put("samplesNs", JSONArray(samples.toList())))
        }
        val report = JSONObject().put("label", args.getString("wholeTimeLabel")).put("device", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT).put("fingerprint", Build.FINGERPRINT)
            .put("warmup", warmup).put("blackHole", blackHole).put("workloads", reports)
        File(context.filesDir, "whole-time-perf.json").writeText(report.toString())
        println("WHOLE_TIME_PERF workloads=${reports.length()} iterations=$iterations")
    }

    private fun request(input: String, mode: CandidateQueryMode, bunsetsu: Boolean, n: Int, config: NumberCandidateConfig) = KanaKanjiQueryRequest(
        input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = n,
        mozcUtPersonName = false, mozcUtPlaces = false, mozcUtWiki = false, mozcUtNeologd = false, mozcUtWeb = false,
        userDictionaryRepository = entry.userDictionaryRepository(), learnRepository = null, omissionSearchEnabled = false,
        typoCorrectionJapaneseFlickEnabled = false, typoCorrectionQwertyEnglishEnabled = false,
        typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 3000, beamWidth = 20,
        collectCandidateSegments = true, numberCandidateConfig = config,
    )

    private fun stat(name: String) = Debug.getRuntimeStat(name)?.toLongOrNull()
    private fun memory(): JSONObject {
        val runtime = Runtime.getRuntime()
        val info = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        return JSONObject().put("javaHeapBytes", runtime.totalMemory() - runtime.freeMemory())
            .put("nativeHeapBytes", Debug.getNativeHeapAllocatedSize()).put("pssKb", info.totalPss)
            .put("javaPssKb", info.getMemoryStat("summary.java-heap")).put("nativePssKb", info.getMemoryStat("summary.native-heap"))
            .put("codePssKb", info.getMemoryStat("summary.code")).put("graphicsPssKb", info.getMemoryStat("summary.graphics"))
    }
    private fun settleGc() { repeat(2) { Runtime.getRuntime().gc(); System.runFinalization(); Thread.sleep(100) } }
    private fun <T> permutations(values: List<T>): List<List<T>> = if (values.isEmpty()) listOf(emptyList())
        else values.flatMap { first -> permutations(values - first).map { listOf(first) + it } }
}
