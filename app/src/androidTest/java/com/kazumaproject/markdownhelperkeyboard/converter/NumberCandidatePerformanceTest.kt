package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.number.NumberCandidateConfig
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in comparison of the public conversion API; also runs against the original PR APK. */
@RunWith(AndroidJUnit4::class)
class NumberCandidatePerformanceTest {
    @Test fun measureNumericalAndOrdinaryConversions() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("numberBenchmark") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.packageName.endsWith(".freezeprobe"))
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        val enabled = arguments.getString("numberEnhance") != "false"
        val corpus = linkedMapOf(
            "ordinary" to listOf("きょう", "きょうはいいてんきですね", "にほん", "よしよし"),
            "counter" to listOf("いちまんえん", "にかげつ", "にじゅっぷん", "はつか"),
            "sentence" to listOf("にじゅっぷんまって", "にかげつかかる", "さんにんでいく", "はつかにいちまんえんはらう"),
            "invalid" to listOf("いっえん", "にびゃくえん", "にじゅっふん", "いちまんにまんえん"),
        )
        val report = StringBuilder("enhancement=$enabled\n")
        for (backend in ConversionBackend.entries) {
            val session = KanaKanjiConversionSession(engine, backend)
            for ((name, inputs) in corpus) {
                suspend fun convert(input: String): Int = session.query(KanaKanjiQueryRequest(
                    input = input, mode = CandidateQueryMode.CONVERSION, bunsetsuSeparation = true, n = 4,
                    mozcUtPersonName = false, mozcUtPlaces = false, mozcUtWiki = false, mozcUtNeologd = false, mozcUtWeb = false,
                    userDictionaryRepository = entry.userDictionaryRepository(), learnRepository = null,
                    omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false, typoCorrectionQwertyEnglishEnabled = false,
                    typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 3000, beamWidth = 20,
                    numberCandidateConfig = NumberCandidateConfig(enhanceCounterCandidates = enabled),
                )).candidates.size
                var consumed = 0
                repeat(80) { consumed += convert(inputs[it % inputs.size]) }
                val samples = LongArray(400)
                val before = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
                for (i in samples.indices) {
                    val start = System.nanoTime()
                    consumed += convert(inputs[i % inputs.size])
                    samples[i] = System.nanoTime() - start
                }
                val after = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
                val allocated = if (before != null && after != null) (after - before).toDouble() / samples.size else null
                report.appendLine("$backend/$name meanUs=${samples.average() / 1000} p95Us=${samples.sorted()[379] / 1000.0} allocatedBytes=$allocated consumed=$consumed")
            }
        }
        val text = report.toString()
        File(context.filesDir, "number-candidate-performance.txt").writeText(text)
        println("NUMBER_CANDIDATE_PERFORMANCE\n$text")
    }
}
