package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.TripleDictionaryData
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedWriter
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

/** Opt-in ART comparison; run the identical test APK against both isolated application APKs. */
@RunWith(AndroidJUnit4::class)
class DictionaryReleasePerformanceInstrumentedTest {
    private data class Probe(val id: String, val group: String, val input: String)
    private lateinit var context: Context
    private lateinit var engine: KanaKanjiEngine
    private lateinit var repository: UserDictionaryRepository
    private lateinit var report: BufferedWriter
    private var consumedCandidates = 0L

    @Test fun measureRelease(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("dictionaryReleasePerf") == "true")
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName.contains(".classifiedprobe"))
        File(context.filesDir, "dictionary-release-performance.tsv").bufferedWriter().use { writer ->
            report = writer
            report.appendLine("ENV\tsuite=broad-v2\tmodel=${Build.MODEL}\tsdk=${Build.VERSION.SDK_INT}\tn=4\tbeam=20\tcompleteRounds=3\tsamplesPerRound=10\ttypingRounds=3\tpeakSampleSleepMs=20\tcompleteIncludesSessionInit=true")
            memory("beforeEngine")
            val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
            val begin = System.nanoTime()
            engine = entry.kanaKanjiEngine()
            repository = entry.userDictionaryRepository()
            report.appendLine("INITIALIZE\tms=${(System.nanoTime() - begin) / 1e6}")
            val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
            val previous = ClassifiedDictionaries.all.associate { it.category to store.isOptionalBundledEnabled(it.category) }
            try {
                // Decode representatives from the same shipped data on both builds. This is not a cold-load benchmark.
                ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, true) }
                engine.applyDictionaryOverrideState(context)
                val corpus = corpus()
                assertEquals(128, corpus.size)
                assertEquals(128, corpus.map { it.input }.toSet().size)
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(corpus.joinToString("\n") {
                    "${it.id}\t${it.group}\t${it.input}"
                }.toByteArray()).joinToString("") { "%02x".format(it) }
                report.appendLine("CORPUS_SHA256\t$fingerprint")
                corpus.forEach { report.appendLine("CORPUS\t${it.id}\t${it.group}\t${it.input.length}\t${it.input}") }
                // All 128 inputs in conversion; a length-stratified subset in prediction-capable modes.
                val predictionCorpus = corpus.filter { it.group == "repro" } +
                    corpus.filter { it.group == "dictionary" }.sortedBy { it.input.length }
                        .filterIndexed { index, _ -> index % 9 == 4 } +
                    corpus.filter { it.group == "sentence" }.take(16)
                assertEquals(32, predictionCorpus.size)
                val typingCorpus = listOf(corpus.single { it.id == "sentence-0-128" }, corpus.single { it.id == "sentence-7-128" })
                for (enabled in listOf(false, true)) {
                    ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, enabled) }
                    engine.applyDictionaryOverrideState(context)
                    assertEquals(if (enabled) ClassifiedDictionaries.categories else emptySet(), engine.loadedClassifiedCategories())
                    memory("categories=$enabled-loaded")
                    for (backend in ConversionBackend.entries) {
                        for (mode in listOf(CandidateQueryMode.CONVERSION, CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION)) {
                            for (bunsetsu in listOf(false, true)) {
                                val inputs = if (mode == CandidateQueryMode.CONVERSION) corpus else predictionCorpus
                                complete(inputs, enabled, backend, mode, bunsetsu)
                                memory("complete/$enabled/$backend/$mode/$bunsetsu")
                                progress("complete/$enabled/$backend/$mode/$bunsetsu")
                            }
                        }
                        for (bunsetsu in listOf(false, true)) {
                            typing(typingCorpus, enabled, backend, bunsetsu)
                            memory("typing/$enabled/$backend/$bunsetsu")
                            memoryWorkload(corpus, enabled, backend, bunsetsu)
                            progress("typing-and-memory/$enabled/$backend/$bunsetsu")
                        }
                    }
                }
                report.appendLine("CONSUMED\t$consumedCandidates")
            } finally {
                previous.forEach { (category, enabled) -> store.setOptionalBundledEnabled(category, enabled) }
                engine.applyDictionaryOverrideState(context)
                report.flush()
            }
        }
    }

    private fun request(input: String, enabled: Boolean, mode: CandidateQueryMode, bunsetsu: Boolean) = KanaKanjiQueryRequest(
        input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = 4,
        enabledDictionaryCategories = if (enabled) ClassifiedDictionaries.categories else emptySet(),
        userDictionaryRepository = repository, learnRepository = null,
        omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
        typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900, beamWidth = 20,
    )

    private suspend fun timed(session: KanaKanjiConversionSession?, request: KanaKanjiQueryRequest,
                              freshBackend: ConversionBackend? = null): Double {
        val begin = System.nanoTime()
        val result = (session ?: KanaKanjiConversionSession(engine, requireNotNull(freshBackend))).query(request)
        val elapsed = (System.nanoTime() - begin) / 1e6
        assertTrue("Empty candidates: ${request.input}", result.candidates.isNotEmpty())
        consumedCandidates += result.candidates.size
        return elapsed
    }

    private fun stats(kind: String, enabled: Boolean, backend: ConversionBackend, mode: CandidateQueryMode,
                      bunsetsu: Boolean, probe: Probe, round: Int, values: List<Double>) {
        val sorted = values.sorted()
        fun percentile(p: Double) = sorted[ceil(p * sorted.size).toInt() - 1]
        report.appendLine("$kind\t$enabled\t$backend\t$mode\t$bunsetsu\t${probe.id}\t${probe.group}\t${probe.input.length}\t$round\t${values.size}\t${values.average()}\t${percentile(.5)}\t${percentile(.95)}\t${sorted.last()}")
    }

    private suspend fun complete(inputs: List<Probe>, enabled: Boolean, backend: ConversionBackend,
                                 mode: CandidateQueryMode, bunsetsu: Boolean) {
        suspend fun convert(probe: Probe) = timed(null, request(probe.input, enabled, mode, bunsetsu), backend)
        repeat(2) { inputs.forEach { convert(it) } }
        val pooled = inputs.associateWith { ArrayList<Double>(30) }
        repeat(3) { round ->
            val samples = inputs.associateWith { ArrayList<Double>(10) }
            repeat(10) { iteration ->
                // Rotate inputs to avoid timing one reading repeatedly in isolation.
                inputs.indices.forEach { index ->
                    val probe = inputs[(index + iteration + round) % inputs.size]
                    samples.getValue(probe).add(convert(probe))
                }
            }
            inputs.forEach { probe ->
                val values = samples.getValue(probe)
                pooled.getValue(probe).addAll(values)
                stats("ROUND", enabled, backend, mode, bunsetsu, probe, round, values)
            }
        }
        inputs.forEach { stats("COMPLETE", enabled, backend, mode, bunsetsu, it, -1, pooled.getValue(it)) }
    }

    private suspend fun typing(inputs: List<Probe>, enabled: Boolean, backend: ConversionBackend, bunsetsu: Boolean) {
        repeat(3) { round ->
            inputs.forEach { probe ->
                val session = KanaKanjiConversionSession(engine, backend)
                suspend fun step(action: String, input: String, mode: CandidateQueryMode = CandidateQueryMode.NO_TAB_DEFAULT) {
                    val value = timed(session, request(input, enabled, mode, bunsetsu))
                    report.appendLine("STEP\t$enabled\t$backend\t$mode\t$bunsetsu\t${probe.id}\t$action\t${input.length}\t$round\t$value")
                }
                for (length in 1..128) step("append", probe.input.take(length))
                for (length in 127 downTo 96) step("delete", probe.input.take(length))
                val replacement = "ぶんしょうをすいこうしてほぞんします".take(16)
                for (length in 1..replacement.length) step("replace", probe.input.take(96) + replacement.take(length))
                step("convert", probe.input.take(96) + replacement, CandidateQueryMode.CONVERSION)
                report.flush()
            }
        }
    }

    private fun memory(label: String) {
        repeat(3) { Runtime.getRuntime().gc(); SystemClock.sleep(50) }
        val values = memoryValues()
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra("temperature", -1)
        val thermal = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(PowerManager::class.java).currentThermalStatus else -1
        report.appendLine("MEMORY\t$label\theapBytes=${values[0]}\tnativeBytes=${values[1]}\tpssKb=${values[2]}\tgcCount=${Debug.getRuntimeStat("art.gc.gc-count")}\tallocatedBytes=${Debug.getRuntimeStat("art.gc.bytes-allocated")}\tbatteryDeciC=$battery\tthermal=$thermal")
        report.flush()
    }

    private fun memoryValues() = longArrayOf(Runtime.getRuntime().run { totalMemory() - freeMemory() },
        Debug.getNativeHeapAllocatedSize(), Debug.getPss().toLong())

    private suspend fun memoryWorkload(inputs: List<Probe>, enabled: Boolean, backend: ConversionBackend, bunsetsu: Boolean) {
        val session = KanaKanjiConversionSession(engine, backend)
        val stable = inputs.single { it.id == "sentence-0-128" }
        timed(session, request(stable.input, enabled, CandidateQueryMode.NO_TAB_DEFAULT, bunsetsu))
        memory("retained/$enabled/$backend/$bunsetsu/cycle=0")
        val running = AtomicBoolean(true)
        val peaks = longArrayOf(0, 0, 0)
        val sampler = Thread {
            while (running.get()) {
                val values = memoryValues()
                for (index in peaks.indices) peaks[index] = maxOf(peaks[index], values[index])
                try { Thread.sleep(20) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
        try {
            repeat(5) { cycle ->
                repeat(50) { index ->
                    timed(session, request(inputs[(cycle * 50 + index) % inputs.size].input, enabled, CandidateQueryMode.NO_TAB_DEFAULT, bunsetsu))
                }
                // Keep the final input/session size fixed before comparing retained memory over cycles.
                timed(session, request(stable.input, enabled, CandidateQueryMode.NO_TAB_DEFAULT, bunsetsu))
                memory("retained/$enabled/$backend/$bunsetsu/cycle=${cycle + 1}")
            }
        } finally {
            running.set(false); sampler.interrupt(); sampler.join()
        }
        report.appendLine("SAMPLED_PEAK\t$enabled\t$backend\t$bunsetsu\theapBytes=${peaks[0]}\tnativeBytes=${peaks[1]}\tpssKb=${peaks[2]}\tsleepMs=20")
        report.flush()
    }

    private fun progress(label: String) {
        report.flush()
        println("CLASSIFIED_PERF_PROGRESS $label consumed=$consumedCandidates")
    }

    private fun corpus(): List<Probe> {
        @Suppress("UNCHECKED_CAST")
        val dictionaries = KanaKanjiEngine::class.java.getDeclaredField("classifiedDictionaries")
            .apply { isAccessible = true }.get(engine) as Map<DictionaryCategory, TripleDictionaryData>
        val result = mutableListOf<Probe>()
        val used = mutableSetOf<String>()
        listOf("あ", "しょう", "ほのか", "かな", "かみ", "こうしょう").forEachIndexed { index, input ->
            result.add(Probe("repro-$index", "repro", input)); used.add(input)
        }
        ClassifiedDictionaries.all.forEach { spec ->
            val data = dictionaries.getValue(spec.category)
            val readings = data.yomiTrie.predictiveSearch("", data.succinctBitVectorLBSYomi)
                .filter { it.length in 2..128 }.sortedWith(compareBy<String> { it.length }.thenBy { it })
            for ((name, fraction) in listOf("median" to .5, "long" to .95, "longest" to 1.0)) {
                val start = ((readings.size - 1) * fraction).toInt()
                val input = if (name == "longest") readings.asReversed().first { it !in used }
                    else (readings.drop(start) + readings.take(start)).first { it !in used }
                used.add(input); result.add(Probe("${spec.id}-$name", "dictionary", input))
            }
        }
        paragraphs.forEachIndexed { index, text ->
            assertTrue("Paragraph $index must contain at least 128 characters", text.length >= 128)
            for (length in listOf(32, 64, 96, 128)) {
                val input = text.take(length)
                assertTrue(used.add(input))
                result.add(Probe("sentence-$index-$length", "sentence", input))
            }
        }
        return result
    }

    private val paragraphs = listOf(
        "きょうはあさからあめがふっていましたが、ひるすぎにはそらがはれてきたので、ともだちとえきまえのとしょかんまであるいていきました。としょかんではらいしゅうのりょこうについてしらべ、でんしゃのじこくやびじゅつかんのかいかんじかんをてちょうにまとめました。かえりみちにはちいさなきっさてんにより、あたたかいおちゃをのみながらいちにちのできごとをはなしました。",
        "らいげつのかいぎにむけて、たんとうしゃごとのさぎょうよていとしりょうのかくにんじょうきょうをまとめています。あたらしくついかされたようぼうについては、ひつようなきのうとじっそうにかかるじかんをせいりし、らいしゅうまでにかんけいしゃへせつめいするよていです。へんこうがあるばあいはりゆうとえいきょうするはんいをきろくし、かくにんがおわったものからじゅんばんにこうしんします。",
        "びょういんでしんさつをうけたあと、やっきょくでくすりののみかたとほぞんほうほうについてせつめいをうけました。たいちょうのへんかやのみわすれがあったばあいは、じぶんではんだんせずにいしややくざいしへそうだんすることになっています。つぎのよやくびまでのあいだは、すいみんじかんとしょくじのないようをまいにちきろくし、きになるしょうじょうがあればてちょうにかきとめておきます。",
        "とうきょうえきからしんかんせんにのり、きょうとえきでちかてつにのりかえてしゅくはくさきへむかいます。にもつをあずけたあとは、ちかくのじんじゃやはくぶつかんをまわり、よるはまちのしょくどうでちいきのりょうりをたのしむよていです。あめがふったときのために、しつないでけんがくできるしせつと、あるくきょりがみじかいべつのけいろもしらべておきました。かえりのでんしゃのきっぷもかくにんずみです。",
        "あたらしいしょうせつをよみはじめるまえに、としょかんのしょうかいぶんとさくしゃのいんたびゅーをよみました。ものがたりのぶたいになったまちのれきしや、とうじょうじんぶつどうしのかんけいをすこししっておくと、ことばのいみやばめんのへんかがわかりやすくなります。よみおわったあとは、いんしょうにのこったせりふとかんそうをきろくし、ともだちとそれぞれのかいしゃくについてはなすつもりです。",
        "しゅうまつはかぞくといっしょにちかくのこうえんへでかけ、おべんとうをたべてからかわぞいのみちをさんぽしました。はなのなまえがかかれたあんないばんをみながらあるき、みつけたとりやこんちゅうのしゃしんをとりました。ゆうがたにはかいものをすませ、いえにもどってからあしたのじゅんびをしました。つぎのやすみにはもうすこしとおくのやまへいけるように、ひつようなもちものをかぞくでそうだんしています。",
        "おんらいんでちゅうもんしたしょうひんがとどいたので、かたばんとふぞくひんをかくにんしてからせつめいしょをよみました。せっていのてじゅんはおもったよりかんたんでしたが、ふるいでーたをうつすまえにほぞんさきをもういちどかくにんするひつようがありました。さぎょうがおわったあとにはきろくをのこし、おなじことをするひとがこまらないように、ちゅういてんとわかりにくかったことばをまとめました。",
        "2026ねん10がつのこうしんでは、APIのへんこうとAndroidのたいおうじょうきょうをかくにんしました。PRのせつめいには、さいげんてじゅん、へんこうないよう、てすとけっかをきさいし、32もじから128もじまでのにゅうりょくをつかってそくどとめもりをそくていします。にゅうりょくのとちゅうでさくじょやかきなおしをしたばあいにも、こうほのじゅんいとよみがただしくこうしんされるかをしらべます。",
    )
}
