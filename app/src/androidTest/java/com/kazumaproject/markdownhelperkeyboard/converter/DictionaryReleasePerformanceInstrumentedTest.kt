package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.ceil
import kotlin.system.measureNanoTime

/** Opt-in, identical corpus and settings for comparing two bundled dictionary releases on ART. */
@RunWith(AndroidJUnit4::class)
class DictionaryReleasePerformanceInstrumentedTest {
    @Test fun measureRelease() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("dictionaryReleasePerf") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.contains(".classifiedprobe"))
        val report = StringBuilder("kind\tcategories\tbackend\tmode\tbunsetsu\tinput\tround\tmeanMs\tp50Ms\tp95Ms\tmaxMs\n")
        fun memory(state: String) {
            repeat(3) { Runtime.getRuntime().gc(); SystemClock.sleep(50) }
            val heap = Runtime.getRuntime().run { totalMemory() - freeMemory() }
            report.appendLine("MEMORY\t$state\theapBytes=$heap\tnativeBytes=${Debug.getNativeHeapAllocatedSize()}\tpssKb=${Debug.getPss()}")
        }
        memory("beforeEngine")
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engineNs = measureNanoTime { entry.kanaKanjiEngine() }
        val engine = entry.kanaKanjiEngine()
        val repository = entry.userDictionaryRepository()
        report.appendLine("INITIALIZE\tms=${engineNs / 1e6}")
        memory("afterEngine")
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val previous = ClassifiedDictionaries.all.associate { it.category to store.isOptionalBundledEnabled(it.category) }
        val inputs = listOf("しょう", "ほのか", "かな", "あせんやく", "あいかわえき", "きょうはいいてんきですね")
        try {
            for (enabled in listOf(false, true)) {
                ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, enabled) }
                val loadNs = measureNanoTime { engine.applyDictionaryOverrideState(context) }
                assertTrue(engine.loadedClassifiedCategories() == if (enabled) ClassifiedDictionaries.categories else emptySet())
                report.appendLine("RELOAD\t$enabled\tms=${loadNs / 1e6}")
                memory("categories=$enabled-loaded")
                for (backend in ConversionBackend.entries) {
                    for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.CONVERSION, CandidateQueryMode.PREDICTION)) {
                        for (bunsetsu in listOf(false, true)) {
                            suspend fun convert(input: String) {
                                // A fresh session models a new composition and avoids measuring same-input state reuse.
                                val result = KanaKanjiConversionSession(engine, backend).query(KanaKanjiQueryRequest(
                                    input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = 4,
                                    enabledDictionaryCategories = if (enabled) ClassifiedDictionaries.categories else emptySet(),
                                    userDictionaryRepository = repository, learnRepository = null,
                                    omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
                                    typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 3000,
                                    omissionSearchOffsetScore = 1900, beamWidth = 20))
                                assertTrue("$backend $input", result.candidates.isNotEmpty())
                            }
                            for (input in inputs) {
                                val first = measureNanoTime { convert(input) } / 1e6
                                report.appendLine("FIRST\t$enabled\t$backend\t$mode\t$bunsetsu\t$input\t0\t$first")
                            }
                            repeat(10) { inputs.forEach { convert(it) } }
                            repeat(3) { round ->
                                val samples = inputs.associateWith { DoubleArray(50) }
                                repeat(50) { iteration ->
                                    inputs.forEach { input -> samples.getValue(input)[iteration] = measureNanoTime { convert(input) } / 1e6 }
                                }
                                for ((input, times) in samples) {
                                    val sorted = times.sorted()
                                    fun p(f: Double) = sorted[ceil(f * sorted.size).toInt() - 1]
                                    report.appendLine("WARM\t$enabled\t$backend\t$mode\t$bunsetsu\t$input\t$round\t${times.average()}\t${p(.5)}\t${p(.95)}\t${sorted.last()}")
                                }
                            }
                            memory("categories=$enabled-$backend-$mode-bunsetsu=$bunsetsu-afterConversions")
                            File(context.filesDir, "dictionary-release-performance.tsv").writeText(report.toString())
                            println("CLASSIFIED_PERF_PROGRESS enabled=$enabled backend=$backend mode=$mode bunsetsu=$bunsetsu")
                        }
                    }
                }
            }
        } finally {
            previous.forEach { (category, enabled) -> store.setOptionalBundledEnabled(category, enabled) }
            engine.applyDictionaryOverrideState(context)
            File(context.filesDir, "dictionary-release-performance.tsv").writeText(report.toString())
            println(report)
        }
    }
}
