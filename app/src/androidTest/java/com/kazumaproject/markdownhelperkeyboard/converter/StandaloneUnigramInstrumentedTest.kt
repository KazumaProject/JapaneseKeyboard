package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.*
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
class StandaloneUnigramInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val entry by lazy { EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java) }
    private fun ordinary(): SystemNgramDictionary = CompositeSystemNgramDictionary(
        listOf("ngram/system_ngram.dat", "ngram/system_ngram_unigram.dat").map { path ->
            context.assets.open(path).use { PackedSystemNgramDictionary.read(it.readBytes()) }
        },
    )
    private fun request(input: String, n: Int = 4, mode: CandidateQueryMode = CandidateQueryMode.CONVERSION,
        bunsetsu: Boolean = true, omission: Boolean = false, typo: Boolean = false) = KanaKanjiQueryRequest(
        input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = n,
        userDictionaryRepository = entry.userDictionaryRepository(), learnRepository = null,
        omissionSearchEnabled = omission, typoCorrectionJapaneseFlickEnabled = typo,
        typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900, beamWidth = 20,
    )

    @Test fun wholeWordsCorrectionsAndCompoundScopeAcrossModesAndBackends() = runBlocking {
        val full = SystemNgramAssetLoader.load(context)
        val ordinary = ordinary()
        val rows = StringBuilder()
        try {
            for (backend in ConversionBackend.entries) {
                for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) {
                    for (bunsetsu in listOf(false, true)) {
                        val session = KanaKanjiConversionSession(entry.kanaKanjiEngine(), backend)
                        for (n in listOf(1, 4, 10)) {
                            for (word in listOf("しろ", "ごと", "したい", "はいる", "みた")) {
                                SystemNgramRuntime.install(full)
                                val result = session.query(request(word, n, mode, bunsetsu))
                                rows.appendLine("$backend\t$mode\t$bunsetsu\t$n\t$word\t${result.candidates.take(4)}")
                                assertEquals("$backend $mode bunsetsu=$bunsetsu n=$n $word", word, result.candidates.first().string)
                                assertTrue(result.candidates.first().standaloneUnigramMatched ||
                                    word in result.bunsetsuResult?.systemNgramMatchedCandidates.orEmpty())
                            }
                        }
                        for (input in listOf("しろい", "しろくろ", "しろあり", "しごと", "ふくをきる", "きょうはいいてんきです")) {
                            SystemNgramRuntime.install(ordinary)
                            val expected = session.query(request(input, mode = mode, bunsetsu = bunsetsu))
                            SystemNgramRuntime.install(full)
                            val actual = session.query(request(input, mode = mode, bunsetsu = bunsetsu))
                            assertEquals("compound $backend $mode $bunsetsu $input", expected.candidates, actual.candidates)
                            assertEquals(expected.bunsetsuResult?.splitPatterns, actual.bunsetsuResult?.splitPatterns)
                        }
                        for (n in listOf(1, 4, 10)) {
                            for ((input, word, omission) in listOf(Triple("こと", "ごと", true), Triple("ひいる", "はいる", false))) {
                                for (both in listOf(false, true)) {
                                    val req = request(input, n, mode, bunsetsu,
                                        omission = omission || both, typo = !omission || both)
                                    SystemNgramRuntime.install(ordinary)
                                    val expected = session.query(req)
                                    SystemNgramRuntime.install(full)
                                    val result = session.query(req)
                                    rows.appendLine("CORRECTED\t$backend\t$mode\t$bunsetsu\t$n\t$input\tboth=$both\t${result.candidates.take(4)}")
                                    assertEquals("corrected $backend $mode $bunsetsu n=$n $input both=$both", expected.candidates, result.candidates)
                                    assertEquals(expected.bunsetsuResult?.systemNgramMatchedCandidates,
                                        result.bunsetsuResult?.systemNgramMatchedCandidates)
                                    assertFalse(result.candidates.any { it.standaloneUnigramMatched && it.string == word })
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            File(context.filesDir, "standalone-behavior.tsv").writeText(rows.toString())
            SystemNgramRuntime.install(full)
        }
    }

    @Test fun measureConversionAndMemory() = runBlocking {
        if (InstrumentationRegistry.getArguments().getString("standalonePerf") != "true") return@runBlocking
        entry.kanaKanjiEngine() // Finish production initialization before measuring dictionary loading.
        SystemNgramRuntime.resetForTesting()
        val ordinary = ordinary()
        fun heapBytes() = Runtime.getRuntime().run { totalMemory() - freeMemory() }
        repeat(3) { Runtime.getRuntime().gc(); Thread.sleep(50) }
        val heapBefore = heapBytes()
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        val pssBefore = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss.toLong() * 1024
        val standalone = context.assets.open("ngram/system_ngram_standalone_unigram.dat").use {
            PackedSystemNgramDictionary.readStandalone(it.readBytes())
        }
        val full = CompositeSystemNgramDictionary(listOf(ordinary, standalone))
        SystemNgramRuntime.install(full)
        repeat(3) { Runtime.getRuntime().gc(); Thread.sleep(50) }
        val loadHeapDelta = heapBytes() - heapBefore
        val loadNativeDelta = Debug.getNativeHeapAllocatedSize() - nativeBefore
        val loadPssDelta = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss.toLong() * 1024 - pssBefore
        val report = StringBuilder("backend\tinput\tomission\ttypo\tstandalone\titerations\tp50Ms\tp95Ms\tp99Ms\tallocatedBytesPerConversion\theapBytes\tnativeHeapBytes\tpssBytes\tfirst\n")
        try {
            for (backend in ConversionBackend.entries) {
                for ((input, omission, typo) in listOf(Triple("しろ", false, false), Triple("はいる", false, false),
                    Triple("こと", true, false), Triple("ひいる", false, true), Triple("しろくろ", false, false),
                    Triple("このあぷりのへんかんこうほをこうそくにしたい", false, false))) {
                    val sessions = listOf(KanaKanjiConversionSession(entry.kanaKanjiEngine(), backend), KanaKanjiConversionSession(entry.kanaKanjiEngine(), backend))
                    val req = request(input, omission = omission, typo = typo)
                    repeat(30) { for (enabled in 0..1) {
                        SystemNgramRuntime.install(if (enabled == 1) full else ordinary)
                        sessions[enabled].query(req)
                    } }
                    val times = Array(2) { DoubleArray(100) }
                    repeat(100) { round -> for (offset in 0..1) {
                        val enabled = (round + offset) % 2
                        SystemNgramRuntime.install(if (enabled == 1) full else ordinary)
                        times[enabled][round] = measureNanoTime { sessions[enabled].query(req) } / 1e6
                    } }
                    for (enabled in 0..1) {
                        SystemNgramRuntime.install(if (enabled == 1) full else ordinary)
                        repeat(3) { Runtime.getRuntime().gc(); Thread.sleep(50) }
                        val heap = Runtime.getRuntime().run { totalMemory() - freeMemory() }
                        val native = Debug.getNativeHeapAllocatedSize()
                        val pss = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss.toLong() * 1024
                        val before = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
                        var first = ""
                        repeat(100) { first = sessions[enabled].query(req).candidates.first().string }
                        val allocated = (Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - before) / 100
                        val sorted = times[enabled].sorted()
                        fun p(f: Double) = sorted[ceil(f * sorted.size).toInt() - 1]
                        report.appendLine(listOf(backend,input,omission,typo,enabled==1,100,p(.5),p(.95),p(.99),allocated,heap,native,pss,first).joinToString("\t"))
                    }
                }
            }
            report.appendLine("standaloneRules=${standalone.ruleCount} standaloneStorageBytes=${standalone.storageBytes}")
            report.appendLine("standaloneLoadHeapDeltaBytes=$loadHeapDelta nativeDeltaBytes=$loadNativeDelta pssDeltaBytes=$loadPssDelta")
        } finally {
            File(context.filesDir, "standalone-performance.tsv").writeText(report.toString())
            println(report)
            SystemNgramRuntime.install(full)
        }
    }
}
