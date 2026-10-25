package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlin.math.ceil
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in measurement using the packaged dictionaries and production DI. Reports stay in filesDir. */
@RunWith(AndroidJUnit4::class)
class ZenzCorrectionPerformanceInstrumentedTest {
    @Test
    fun measureCorrectionConversion() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("zenzCorrectionPerf") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = StringBuilder()
        fun memory(label: String) {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            val rt = Runtime.getRuntime()
            report.appendLine("memory,$label,pssKb=${info.totalPss},javaUsedBytes=${rt.totalMemory()-rt.freeMemory()},nativeUsedBytes=${Debug.getNativeHeapAllocatedSize()},javaPssKb=${info.dalvikPss},nativePssKb=${info.nativePss}")
        }
        memory("beforeEngine")
        val initStart = SystemClock.elapsedRealtimeNanos()
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        val repository = entry.userDictionaryRepository()
        report.appendLine("engineInitNs=${SystemClock.elapsedRealtimeNanos()-initStart}")
        memory("engineLoaded")
        if (args.getString("coldOnly") == "true") {
            val session = KanaKanjiConversionSession(engine, ConversionBackend.INCREMENTAL_SESSION)
            val start = SystemClock.elapsedRealtimeNanos()
            val result = session.query(KanaKanjiQueryRequest(
                input="かつこう", mode=CandidateQueryMode.PREDICTION, bunsetsuSeparation=true,
                n=4, userDictionaryRepository=repository, learnRepository=null,
                omissionSearchEnabled=true, typoCorrectionJapaneseFlickEnabled=false,
                typoCorrectionQwertyEnglishEnabled=false, typoCorrectionOffsetScore=3000,
                omissionSearchOffsetScore=1900, beamWidth=20,
            ))
            report.appendLine("coldFirstQueryNs=${SystemClock.elapsedRealtimeNanos()-start},candidate=${result.candidates.firstOrNull()}")
            memory("firstQuery")
            File(context.filesDir,"zenz-correction-perf.txt").writeText(report.toString())
            return@runBlocking
        }
        val warmups = args.getString("warmups")?.toInt() ?: 20
        val iterations = args.getString("iterations")?.toInt() ?: 100
        val sets = args.getString("sets")?.toInt() ?: 3
        val memoryOnly = args.getString("memoryOnly") == "true"
        val selectionOnly = args.getString("selectionOnly") == "true"
        fun filter(name: String) = args.getString(name)?.split(",")?.toSet()
        val selectedConditions = filter("conditions")
        val selectedBackends = filter("backends")
        val selectedInputs = filter("inputs")
        val selectedOperations = filter("operations")
        val conditions = listOf("none", "omission", "flick", "both", "qwerty")
        report.appendLine("filters: conditions=$selectedConditions,backends=$selectedBackends,inputs=$selectedInputs,operations=$selectedOperations")
        report.appendLine("device=${android.os.Build.MODEL},api=${android.os.Build.VERSION.SDK_INT},warmups=$warmups,iterations=$iterations,sets=$sets,memoryOnly=$memoryOnly")
        for (condition in conditions.filter { selectedConditions == null || it in selectedConditions }) {
            val inputs = if (condition == "qwerty") listOf("hello", "hellp", "keyboarf")
                else listOf("きょう", "かつこう", "とえけい", "このあぷりのへんかんこうほをこうそくにしたい")
            for (backend in ConversionBackend.entries.filter { selectedBackends == null || it.name in selectedBackends }) {
                val session = KanaKanjiConversionSession(engine, backend)
                if (selectionOnly) session.enablePerformanceProbe()
                fun request(input: String) = KanaKanjiQueryRequest(
                    input=input, mode=CandidateQueryMode.PREDICTION, bunsetsuSeparation=true,
                    n=4, userDictionaryRepository=repository, learnRepository=null,
                    omissionSearchEnabled=condition == "omission" || condition == "both",
                    typoCorrectionJapaneseFlickEnabled=condition == "flick" || condition == "both",
                    typoCorrectionQwertyEnglishEnabled=condition == "qwerty",
                    typoCorrectionOffsetScore=3000, omissionSearchOffsetScore=1900, beamWidth=20,
                )
                for (input in inputs.filter { selectedInputs == null || it in selectedInputs }) {
                    val sequence = (1..input.length).map { input.take(it) } +
                        (input.length-1 downTo 1).map { input.take(it) }
                    for ((kind, commands) in listOf("single" to listOf(input), "edit" to sequence)
                        .filter { selectedOperations == null || it.first in selectedOperations }) {
                        repeat(warmups) { for (command in commands) session.query(request(command)) }
                        val samples = ArrayList<Long>()
                        val selectionSamples = ArrayList<Long>()
                        repeat(if (memoryOnly) 1 else sets) { set ->
                            repeat(iterations) { iteration ->
                                for (command in commands) {
                                    val start = SystemClock.elapsedRealtimeNanos()
                                    val result = session.query(request(command))
                                    samples += SystemClock.elapsedRealtimeNanos()-start
                                    if (selectionOnly) {
                                        val ns = result.javaClass.methods.firstOrNull { it.name == "getNBestSelectionNs" }?.invoke(result) as? Long
                                        if (ns != null) selectionSamples += ns
                                    }
                                }
                                if (memoryOnly && iteration + 1 in listOf(10, 25, 50, 100)) {
                                    memory("$condition/$backend/$input/$kind/sequence${iteration+1}")
                                }
                            }
                            val sorted=samples.sorted()
                            fun pct(p: Double) = sorted[(ceil(sorted.size*p).toInt()-1).coerceIn(sorted.indices)]/1e6
                            report.appendLine("time,$condition,$backend,$input,$kind,set=$set,count=${samples.size},meanMs=${samples.average()/1e6},p50Ms=${pct(.5)},p95Ms=${pct(.95)}")
                            if (selectionSamples.isNotEmpty()) {
                                report.appendLine("selection,$condition,$backend,$input,$kind,set=$set,count=${selectionSamples.size},meanNs=${selectionSamples.average()},maxNs=${selectionSamples.max()}")
                                selectionSamples.clear()
                            }
                            samples.clear()
                        }
                        if (memoryOnly) {
                            System.gc()
                            SystemClock.sleep(100)
                            memory("$condition/$backend/$input/$kind/retained")
                            val allocatedBefore = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
                            val gcBefore = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull()
                            repeat(10) { for (command in commands) session.query(request(command)) }
                            val allocatedAfter = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
                            val gcAfter = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull()
                            report.appendLine("allocation,$condition,$backend,$input,$kind,commands=${10*commands.size},bytes=${if(allocatedBefore != null && allocatedAfter != null) allocatedAfter-allocatedBefore else "unavailable"},gc=${if(gcBefore != null && gcAfter != null) gcAfter-gcBefore else "unavailable"}")
                        }
                    }
                    val result=session.query(request(input))
                    val metadata = result.javaClass.methods.firstOrNull { it.name == "getBestNBestCandidate" }?.invoke(result)
                    report.appendLine("candidate,$condition,$backend,$input,bestNBest=$metadata,display=${result.candidates.take(4)}")
                    File(context.filesDir,"zenz-correction-perf.txt").writeText(report.toString())
                }
            }
        }
        memory("completed")
        File(context.filesDir,"zenz-correction-perf.txt").writeText(report.toString())
    }
}
