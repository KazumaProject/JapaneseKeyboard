package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.ngram_rule.NgramRuleScorerManager
import com.kazumaproject.markdownhelperkeyboard.repository.NgramRuleRepository
import com.kazumaproject.markdownhelperkeyboard.repository.NodeFeatureValue
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.ceil
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class NgramConversionPerformanceInstrumentedTest {
    @Test
    fun measureNAndRuleCountImpactOnRealConversion() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("ngramPerfProbe") == "true")
        val counts = (args.getString("ngramPerfCounts") ?: "0,10,100,500,1000")
            .split(',').mapNotNull { it.trim().toIntOrNull() }
        val iterations = args.getString("ngramPerfIterations")?.toIntOrNull() ?: 20
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, KanaKanjiEngineEntryPoint::class.java)
        val repository = entry.ngramRuleRepository()
        val manager = entry.ngramRuleScorerManager()
        val original = repository.loadEntities()
        val report = StringBuilder("n\truleCount\theapBytes\tallocatedBytesPerConversion\tgcCount\tp50Ms\tp95Ms\tfingerprint\n")
        try {
            repository.replaceAll(emptyList())
            manager.refreshNow()
            repeat(GLOBAL_WARMUP_CONVERSIONS) {
                convert(entry.kanaKanjiEngine(), entry.userDictionaryRepository())
            }
            for (order in 2..5) {
                for (count in counts) {
                    repository.replaceAll(emptyList())
                    manager.refreshNow()
                    forceGc()
                    val emptyRulesHeap = usedHeap()
                    repository.replaceAll(createWorstCaseRules(order, count))
                    manager.refreshNow()
                    forceGc()
                    val retainedRuleHeap = usedHeap() - emptyRulesHeap
                    repeat(5) { convert(entry.kanaKanjiEngine(), entry.userDictionaryRepository()) }
                    forceGc()
                    val allocatedBefore = runtimeStat("art.gc.bytes-allocated")
                    val gcBefore = runtimeStat("art.gc.gc-count")
                    val samples = DoubleArray(iterations)
                    var candidates: List<Candidate> = emptyList()
                    repeat(iterations) { index ->
                        val elapsed = measureNanoTime {
                            candidates = convert(entry.kanaKanjiEngine(), entry.userDictionaryRepository())
                        }
                        samples[index] = elapsed / 1_000_000.0
                    }
                    val allocatedAfter = runtimeStat("art.gc.bytes-allocated")
                    val gcAfter = runtimeStat("art.gc.gc-count")
                    report.appendLine(
                        listOf(
                            order, count, retainedRuleHeap,
                            (allocatedAfter - allocatedBefore) / iterations,
                            gcAfter - gcBefore,
                            percentile(samples, 0.50), percentile(samples, 0.95),
                            candidates.take(12).joinToString("|") { "${it.string}:${it.score}" }.hashCode(),
                        ).joinToString("\t"),
                    )
                }
            }
        } finally {
            repository.replaceAll(original)
            manager.refreshNow()
        }
        File(context.filesDir, "ngram-performance").apply { mkdirs() }
            .resolve("conversion.tsv").writeText(report.toString())
        println(report)
    }

    private fun createWorstCaseRules(order: Int, count: Int) = List(count) { index ->
        NgramRuleRepository.entityFromNodes(
            nodes = List(order) { nodeIndex ->
                if (nodeIndex == 0) NodeFeatureValue(word = "__never_match_$index") else NodeFeatureValue()
            },
            adjustment = -1,
        )
    }

    private suspend fun convert(engine: KanaKanjiEngine, repository: UserDictionaryRepository): List<Candidate> =
        engine.getCandidatesWithBunsetsuSeparation(
            input = "わたしはきのうともだちとえきまえであいました",
            n = 4,
            mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
            mozcUTNeologd = false, mozcUTWeb = false,
            userDictionaryRepository = repository, learnRepository = null,
            isOmissionSearchEnable = false,
            enableTypoCorrectionJapaneseFlick = false,
            enableTypoCorrectionQwertyEnglish = false,
            typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900,
            beamWidth = 20,
        ).candidates

    private fun forceGc() { repeat(3) { Runtime.getRuntime().gc(); Thread.sleep(50) } }
    private fun usedHeap(): Long = Runtime.getRuntime().run { totalMemory() - freeMemory() }
    private fun runtimeStat(name: String): Long = Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L
    private fun percentile(values: DoubleArray, fraction: Double): Double {
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)]
    }

    private companion object {
        const val GLOBAL_WARMUP_CONVERSIONS = 30
    }
}
