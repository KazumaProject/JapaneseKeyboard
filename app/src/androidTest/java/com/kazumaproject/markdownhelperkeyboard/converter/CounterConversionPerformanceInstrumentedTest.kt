package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Build
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.ceil

/** Same harness is executed on the untouched dev engine and the integrated engine. */
@RunWith(AndroidJUnit4::class)
class CounterConversionPerformanceInstrumentedTest {
    @Test
    fun measureProductionConversion() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("counterPerf") == "true")
        val label = args.getString("counterPerfLabel") ?: "run"
        val warmups = args.getString("counterPerfWarmups")?.toInt() ?: 100
        val iterations = args.getString("counterPerfIterations")?.toInt() ?: 1000
        val context = ApplicationProvider.getApplicationContext<Context>()
        gc()
        val applicationMemory = memory()
        val started = System.nanoTime()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        val repository = entry.userDictionaryRepository()
        engine.initializeOptionalDictionaryStateFromCurrentSources()
        val engineColdNs = System.nanoTime() - started
        gc()
        val loadedMemory = memory()
        val report = StringBuilder()
        report.appendLine("label=$label")
        report.appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} api=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.joinToString()}")
        report.appendLine("warmupRounds=$warmups iterationsPerInput=$iterations n=8 beam=20 optionalMozc=false learn=false omission=false typo=false")
        report.appendLine("application=$applicationMemory")
        report.appendLine("engineColdNs=$engineColdNs loaded=$loadedMemory")
        val legacy = KanaKanjiConversionSession(engine, ConversionBackend.LEGACY)
        for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.CONVERSION)) {
            for (input in CORPUS) {
                val cold = System.nanoTime()
                val result = legacy.query(request(input, repository, mode))
                report.appendLine("first $mode $input ns=${System.nanoTime()-cold} candidates=${result.candidates.take(8).joinToString("|"){it.string}}")
            }
            repeat(warmups) { for (input in CORPUS) legacy.query(request(input, repository, mode)) }
            // Interleave inputs so cached repeated-input timings cannot hide sentence costs.
            val samples = CORPUS.associateWith { LongArray(iterations) }
            gc()
            val before = memory()
            val allocatedBefore = stat("art.gc.bytes-allocated")
            val gcBefore = stat("art.gc.gc-count")
            var checksum = 0L
            repeat(iterations) { index ->
                for (input in CORPUS) {
                    val begin = System.nanoTime()
                    val result = legacy.query(request(input, repository, mode))
                    samples.getValue(input)[index] = System.nanoTime()-begin
                    checksum += result.candidates.sumOf { it.string.length }.toLong()
                }
            }
            val allocatedAfter = stat("art.gc.bytes-allocated")
            val gcAfter = stat("art.gc.gc-count")
            val immediate = memory()
            gc()
            report.appendLine("memory $mode before=$before immediate=$immediate settled=${memory()} allocatedBytes=${delta(allocatedBefore,allocatedAfter)} gcCount=${delta(gcBefore,gcAfter)} calls=${iterations*CORPUS.size} checksum=$checksum")
            samples.forEach { (input, values) ->
                report.appendLine("latency $mode $input ${stats(values)}")
                report.appendLine("raw $mode $input ${values.joinToString(",")}")
            }
        }
        val sessionInputs = listOf("ねこがさんびきいる", "ごごさんじはんにあう", "ひゃくにじゅうさんぼんをかう")
        val incremental = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
        suspend fun type(input: String): Long {
            incremental.query(request("", repository, CandidateQueryMode.CONVERSION))
            var checksum = 0L
            for (end in 1..input.length) {
                checksum += incremental.query(request(input.take(end), repository, CandidateQueryMode.CONVERSION)).candidates.size
            }
            return checksum
        }
        repeat(warmups) { for (input in sessionInputs) type(input) }
        gc()
        val before = memory()
        val allocatedBefore = stat("art.gc.bytes-allocated")
        val gcBefore = stat("art.gc.gc-count")
        val samples = sessionInputs.associateWith { LongArray(iterations) }
        var checksum = 0L
        repeat(iterations) { index -> for (input in sessionInputs) {
            val begin = System.nanoTime()
            checksum += type(input)
            samples.getValue(input)[index] = System.nanoTime()-begin
        } }
        val allocatedAfter = stat("art.gc.bytes-allocated")
        val gcAfter = stat("art.gc.gc-count")
        val immediate = memory()
        gc()
        report.appendLine("memory incremental before=$before immediate=$immediate settled=${memory()} allocatedBytes=${delta(allocatedBefore,allocatedAfter)} gcCount=${delta(gcBefore,gcAfter)} sequences=${iterations*sessionInputs.size} checksum=$checksum")
        samples.forEach { (input, values) ->
            report.appendLine("latency incremental $input ${stats(values)}")
            report.appendLine("raw incremental $input ${values.joinToString(",")}")
        }
        val output = File(context.filesDir,"counter-perf").apply { mkdirs() }.resolve("$label.txt")
        output.writeText(report.toString())
        println(report.lines().filterNot { it.startsWith("raw ") }.joinToString("\n"))
    }
    companion object {
        val CORPUS = listOf("いっぽん", "ひゃくにじゅうさんぼん", "さんびき", "ひとり", "ごごさんじはん", "にじゅうさんじごじゅうきゅうふんごじゅうきゅうびょう", "ひゃくにじゅうさんぼんをかう", "ねこがさんびきいる", "ごごさんじはんにあう", "ほんをさんさつとえんぴつをにほんかう", "いっちょうにせんさんびゃくよんじゅうごおくろくせんななひゃくはちじゅうきゅうまんいっせんにひゃくさんじゅうよんえん", "いちほん", "よしよし", "きょうはいいてんきですね")
        fun request(input: String, repository: UserDictionaryRepository, mode: CandidateQueryMode = CandidateQueryMode.CONVERSION, bunsetsu: Boolean = true) = KanaKanjiQueryRequest(
            input=input, mode=mode, bunsetsuSeparation=bunsetsu, n=8,
            mozcUtPersonName=false, mozcUtPlaces=false, mozcUtWiki=false, mozcUtNeologd=false, mozcUtWeb=false,
            userDictionaryRepository=repository, learnRepository=null, omissionSearchEnabled=false,
            typoCorrectionJapaneseFlickEnabled=false, typoCorrectionQwertyEnglishEnabled=false,
            typoCorrectionOffsetScore=3000, omissionSearchOffsetScore=3000, beamWidth=20, collectCandidateSegments=true,
        )
        fun stats(samples: LongArray): String {
            val sorted = samples.sortedArray()
            fun p(f: Double) = sorted[(ceil(sorted.size*f).toInt()-1).coerceIn(sorted.indices)]
            return "count=${samples.size} p50Ns=${p(.5)} p95Ns=${p(.95)} p99Ns=${p(.99)} maxNs=${sorted.last()}"
        }
        fun memory(): String {
            val runtime=Runtime.getRuntime()
            return "heapBytes=${runtime.totalMemory()-runtime.freeMemory()},nativeBytes=${Debug.getNativeHeapAllocatedSize()},pssKb=${Debug.getPss()}"
        }
        fun stat(key: String) = Debug.getRuntimeStat(key)?.toLongOrNull()
        fun delta(before: Long?, after: Long?) = if(before != null && after != null) after-before else null
        fun gc() { repeat(2) { Runtime.getRuntime().gc(); System.runFinalization(); Thread.sleep(100) } }
    }
}
