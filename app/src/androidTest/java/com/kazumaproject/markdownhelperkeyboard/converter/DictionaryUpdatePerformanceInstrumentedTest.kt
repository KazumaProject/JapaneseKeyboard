package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Build
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
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Run the identical test APK against both app APKs; results belong outside the repository. */
@RunWith(AndroidJUnit4::class)
class DictionaryUpdatePerformanceInstrumentedTest {
    @Test fun compareUpdate() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("dictionaryUpdatePerf") == "true")
        val context: Context = ApplicationProvider.getApplicationContext()
        check(context.packageName.contains(".classifiedprobe"))
        val sentence = "きょうはあさからあめがふっていましたが、ひるすぎにはそらがはれてきたので、ともだちとえきまえのとしょかんまであるいていきました。としょかんではらいしゅうのりょこうについてしらべ、でんしゃのじこくやびじゅつかんのかいかんじかんをてちょうにまとめました。かえりみちにはちいさなきっさてんにより、あたたかいおちゃをのみながらいちにちのできごとをはなしました。"
        val mixed = "2026ねん10がつのこうしんでは、APIのへんこうとAndroidのたいおうじょうきょうをかくにんしました。PRのせつめいには、さいげんてじゅん、へんこうないよう、てすとけっかをきさいし、32もじから128もじまでのにゅうりょくをつかってそくどとめもりをそくていします。にゅうりょくのとちゅうでさくじょやかきなおしをしたばあいにもこうほをかくにんします。"
        val inputs = listOf("あ", "しょう", "ほのか", "かな", "かみ", "こうしょう",
            "かくたつ", "くりき", "しゅうじ", "まつだ", "あいがな", "あいあいえす",
            "とうきょう", "しぶやえき", "にほんご", "じんこうちのう", "あせんやく", "なつめそうせき") +
            listOf(16, 32, 64, 96, 128).map { sentence.take(it) } + mixed.take(128)
        check(inputs.size == 24 && inputs.distinct().size == 24)
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val previous = ClassifiedDictionaries.all.associate { it.category to store.isOptionalBundledEnabled(it.category) }
        ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, false) }
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        File(context.filesDir, "dictionary-update-performance.tsv").bufferedWriter().use { out ->
            fun emit(line: String) { out.appendLine(line); out.flush() }
            fun memory(): LongArray = longArrayOf(Runtime.getRuntime().run { totalMemory() - freeMemory() },
                Debug.getNativeHeapAllocatedSize(), Debug.getPss().toLong() * 1024)
            fun retained(): LongArray {
                repeat(3) { Runtime.getRuntime().gc(); SystemClock.sleep(100) }
                return memory()
            }
            fun values(value: LongArray) = "javaBytes=${value[0]}\tnativeBytes=${value[1]}\tpssBytes=${value[2]}"
            val corpusHash = MessageDigest.getInstance("SHA-256").digest(inputs.joinToString("\n").toByteArray())
                .joinToString("") { "%02x".format(it) }
            emit("ENV\tversion=${args.getString("dictionaryVersion")}\tmodel=${Build.MODEL}\tsdk=${Build.VERSION.SDK_INT}\tcorpusSha256=$corpusHash\tinputs=${inputs.size}\twarmup=5\trounds=3\tsamplesPerRound=10\tn=4\tbeam=20\tlatencyIncludesSessionInit=true\tuiIncluded=false")
            inputs.forEachIndexed { index, input -> emit("CORPUS\t$index\t${input.length}\t$input") }
            emit("MEMORY\tbeforeEngine\t${values(retained())}")
            val init = System.nanoTime()
            val engine = entry.kanaKanjiEngine()
            val repository = entry.userDictionaryRepository()
            emit("INIT\tms=${(System.nanoTime() - init) / 1e6}")
            var consumed = 0L
            fun request(input: String, enabled: Boolean, mode: CandidateQueryMode, bunsetsu: Boolean) = KanaKanjiQueryRequest(
                input = input, mode = mode, bunsetsuSeparation = bunsetsu, n = 4,
                enabledDictionaryCategories = if (enabled) ClassifiedDictionaries.categories else emptySet(),
                userDictionaryRepository = repository, learnRepository = null,
                omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
                typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 3000,
                omissionSearchOffsetScore = 1900, beamWidth = 20,
            )
            suspend fun query(input: String, enabled: Boolean, backend: ConversionBackend,
                              mode: CandidateQueryMode, bunsetsu: Boolean): Long {
                val started = System.nanoTime()
                val result = KanaKanjiConversionSession(engine, backend).query(request(input, enabled, mode, bunsetsu))
                val elapsed = System.nanoTime() - started
                assertTrue("Empty candidates for $input", result.candidates.isNotEmpty())
                consumed += result.candidates.size
                return elapsed
            }
            try {
                for (enabled in listOf(false, true)) {
                    ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, enabled) }
                    val started = System.nanoTime()
                    engine.applyDictionaryOverrideState(context)
                    emit("LOAD\t$enabled\tcategories=${engine.loadedClassifiedCategories().size}\tms=${(System.nanoTime() - started) / 1e6}")
                    emit("MEMORY\tloaded/$enabled\t${values(retained())}")
                    for (backend in ConversionBackend.entries) {
                        for (mode in listOf(CandidateQueryMode.CONVERSION, CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION)) {
                            for (bunsetsu in listOf(false, true)) {
                                repeat(5) { inputs.forEach { query(it, enabled, backend, mode, bunsetsu) } }
                                repeat(3) { round ->
                                    repeat(10) { iteration ->
                                        inputs.indices.forEach { offset ->
                                            val index = (offset + round + iteration) % inputs.size
                                            val ns = query(inputs[index], enabled, backend, mode, bunsetsu)
                                            out.appendLine("LATENCY\t$enabled\t$backend\t$mode\t$bunsetsu\t$index\t${inputs[index].length}\t$round\t$ns")
                                        }
                                    }
                                }
                                out.flush()
                                println("DICTIONARY_UPDATE_PERF $enabled/$backend/$mode/$bunsetsu")
                            }
                        }
                        // Separate sampling phase so PSS sampling does not distort the latency samples.
                        val before = retained()
                        val allocatedBefore = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
                        val running = AtomicBoolean(true)
                        val peak = before.copyOf()
                        val sampler = Thread {
                            while (running.get()) {
                                val current = memory()
                                for (index in peak.indices) peak[index] = maxOf(peak[index], current[index])
                                try { Thread.sleep(20) } catch (_: InterruptedException) { break }
                            }
                        }.apply { isDaemon = true; start() }
                        try {
                            repeat(10) { cycle -> inputs.indices.forEach { index ->
                                query(inputs[(index + cycle) % inputs.size], enabled, backend, CandidateQueryMode.CONVERSION, false)
                            } }
                        } finally { running.set(false); sampler.interrupt(); sampler.join() }
                        val allocated = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - allocatedBefore
                        emit("WORKLOAD\t$enabled\t$backend\tqueries=${10 * inputs.size}\tallocatedBytes=$allocated\tbeforeJavaBytes=${before[0]}\tbeforeNativeBytes=${before[1]}\tbeforePssBytes=${before[2]}\tpeakJavaBytes=${peak[0]}\tpeakNativeBytes=${peak[1]}\tpeakPssBytes=${peak[2]}\tsampleMs=20")
                        emit("MEMORY\tafterWorkload/$enabled/$backend\t${values(retained())}")
                    }
                }
                emit("CONSUMED\t$consumed")
            } finally {
                previous.forEach { (category, enabled) -> store.setOptionalBundledEnabled(category, enabled) }
                engine.applyDictionaryOverrideState(context)
            }
        }
    }
}
