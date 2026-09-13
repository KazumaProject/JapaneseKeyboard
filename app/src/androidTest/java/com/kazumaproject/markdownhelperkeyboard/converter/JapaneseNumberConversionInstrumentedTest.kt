package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Build
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class JapaneseNumberConversionInstrumentedTest {

    @Test
    fun numericDatesWorkOnTheMinimumSupportedApiWithGenerationOffAndOn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = EntryPointAccessors.fromApplication(context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java).kanaKanjiEngine()
        val dates = mapOf("101" to "1月1日", "229" to "2月29日", "430" to "4月30日", "1231" to "12月31日")
        for (enabled in listOf(false, true)) {
            val config = com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig(japaneseNumberCandidatesEnabled = enabled)
            for ((input, output) in dates) {
                val candidates = engine.getCandidatesEnglishKana(input, config)
                assertTrue("$input/$enabled", candidates.any { it.string == output && it.commitText == output })
                val restored = Candidate(output, 1, input.length.toUByte(), 0, yomi = input)
                assertTrue(com.kazumaproject.markdownhelperkeyboard.converter.engine.NumberCandidatePolicy.eligible(input, restored, config))
            }
            for (input in listOf("100", "230", "431", "631", "931", "1131", "1301")) {
                val candidates = engine.getCandidatesEnglishKana(input, config)
                assertTrue(input, candidates.none { it.string.matches(Regex("[0-9]+月[0-9]+日")) })
            }
            val spoken = engine.getCandidatesEnglishKana("ひゃくいち", config)
            assertEquals(enabled, spoken.any { it.string == "1月1日" })
        }
        assertTrue(engine.getCandidatesEnglishKana("ひゃくいち").any { it.generatedNumber && it.string == "1月1日" })
    }

    @Test
    fun verifyCorrectnessAndMeasureProductionPaths() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()

        val forbiddenByInput = linkedMapOf(
            // Osaka/Irodori complete-reading policy; these were incorrectly accepted by old tests.
            "しじゅう" to setOf("40", "４０", "四十"),
            "じゅうよ" to setOf("14", "１４", "十四"),
            "くにん" to setOf("9人", "９人", "九人"),
            "よしよし" to setOf("4444", "４４４４", "8383", "８３８３", "四千四百四十四"),
            "しせん" to setOf("4000", "４０００", "4,000", "四千"),
            "くちょう" to setOf("9000000000000", "９００００００００００００", "九兆", "9兆"),
            "ちょうせん" to setOf("1000000001000", "一兆一千"),
            "ごご" to setOf("55", "５５", "五十五"),
            "さんご" to setOf("35", "３５", "三十五"),
            "しえん" to setOf("4円", "４円"),
            "しじ" to setOf("4時", "４時"),
            "いちいち" to setOf("11", "１１", "十一"),
            "さんさん" to setOf("33", "３３", "三十三"),
            "ろくろく" to setOf("66", "６６", "六十六"),
            "よせん" to setOf("4000", "４０００", "4,000", "四千"),
            "くせん" to setOf("9000", "９０００", "9,000", "九千"),
        )

        reportStage("correctness English/kana")
        val englishKanaResults = linkedMapOf<String, List<Candidate>>()
        forbiddenByInput.forEach { (input, forbidden) ->
            val candidates = engine.getCandidatesEnglishKana(input, com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig(japaneseNumberCandidatesEnabled = true))
            englishKanaResults[input] = candidates
            assertEquals(input, candidates.first().string)
            assertTrue(
                "$input unexpectedly generated ${candidates.map { it.string }}",
                candidates.none { it.string in forbidden },
            )
        }

        val validInputs = linkedMapOf(
            "よんせん" to setOf("4000", "四千"),
            "きゅうちょう" to setOf("9000000000000", "九兆"),
            "さんにん" to setOf("3人"),
            "にじゅっぷん" to setOf("20分"),
            "ろくじ" to setOf("6時"),
            "にじゅうよじ" to setOf("24時"),
            "くじ" to setOf("9時"),
            "いっちょう" to setOf("1000000000000", "一兆"),
            "よんじゅう" to setOf("40", "四十"),
            "じゅうよん" to setOf("14", "十四"),
            "よにん" to setOf("4人"),
            "よんえん" to setOf("4円"),
            "きゅうえん" to setOf("9円"),
            "きゅうにん" to setOf("9人"),
            "いっぷん" to setOf("1分"),
            "ろっぷん" to setOf("6分"),
            "はっぷん" to setOf("8分"),
        )
        validInputs.forEach { (input, required) ->
            val candidates = engine.getCandidatesEnglishKana(input, com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig(japaneseNumberCandidatesEnabled = true))
            englishKanaResults[input] = candidates
            val values = candidates.mapTo(hashSetOf()) { it.string }
            assertTrue("$input missing $required from $values", values.containsAll(required))
        }

        reportStage("correctness standard dictionary")
        val standardResults = linkedMapOf<String, List<Candidate>>()
        forbiddenByInput.forEach { (input, forbidden) ->
            val candidates = engine.convertOriginal(input, repository)
            standardResults[input] = candidates
            val lexical = exactLexicalDictionaryOutputs(engine, input)
            assertTrue(
                "$input unexpectedly generated ${candidates.map { it.string }}",
                candidates.none { it.string in forbidden && !(it.string in lexical &&
                    it.commitText == it.string && !it.generatedNumber && it.number == null) },
            )
        }

        assertTrue("stored しじゅう -> 四十 must remain lexical",
            "四十" in exactLexicalDictionaryOutputs(engine, "しじゅう") &&
                standardResults.getValue("しじゅう").any { it.string == "四十" && it.commitText == "四十" && !it.generatedNumber })

        val benchmarkCorpus = (forbiddenByInput.keys + validInputs.keys).toList()
        reportStage("benchmark English/kana")
        val englishKanaBenchmark = measureConversions(
            label = "englishKana",
            corpus = benchmarkCorpus,
            warmupIterations = 1_000,
            measuredIterations = 20_000,
        ) { input ->
            engine.getCandidatesEnglishKana(input, com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig(japaneseNumberCandidatesEnabled = true))
        }
        reportStage("benchmark standard dictionary")
        val standardBenchmark = measureConversions(
            label = "standardDictionary",
            corpus = benchmarkCorpus,
            warmupIterations = 30,
            measuredIterations = 500,
        ) { input ->
            engine.convertOriginal(input, repository)
        }

        val report = buildString {
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("api=${Build.VERSION.SDK_INT}")
            appendLine("abi=${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("correctness=PASS")
            appendLine("forbiddenCaseCount=${forbiddenByInput.size}")
            appendLine("validCaseCount=${validInputs.size}")
            appendLine(englishKanaBenchmark.asReportLine())
            appendLine(standardBenchmark.asReportLine())
            appendLine("englishKanaCandidates")
            englishKanaResults.forEach { (input, candidates) ->
                appendLine("$input\t${candidates.take(16).joinToString(" / ") { it.string }}")
            }
            appendLine("standardCandidates")
            standardResults.forEach { (input, candidates) ->
                appendLine("$input\t${candidates.take(16).joinToString(" / ") { it.string }}")
            }
        }

        val outputDir = File(context.filesDir, "conversion-perf").apply { mkdirs() }
        File(outputDir, "japanese-number-conversion.txt").writeText(report)
        println("JAPANESE_NUMBER_CONVERSION_REPORT\n$report")
    }

    // The spoken-number generator's forbidden forms do not prohibit independently stored
    // lexical words (for example しじゅう -> 四十). Read the dictionary, not filtered output.
    private fun exactLexicalDictionaryOutputs(engine: KanaKanjiEngine, input: String): Set<String> {
        fun field(name: String): Any = requireNotNull(KanaKanjiEngine::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(engine))
        val trie = field("systemYomiTrie") as com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
        val bits = field("systemSuccinctBitVectorLBSYomi") as com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
        if (input !in trie.commonPrefixSearch(input, bits)) return emptySet()
        val leaves = field("systemSuccinctBitVectorIsLeafYomi") as com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
        val tokens = field("systemTokenArray") as com.kazumaproject.dictionary.TokenArray
        val tokenBits = field("systemSuccinctBitVectorTokenArray") as com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
        val tango = field("systemTangoTrie") as com.kazumaproject.Louds.LOUDS
        val tangoBits = field("systemSuccinctBitVectorTangoLBS") as com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
        val term = trie.getTermId(trie.getNodeIndex(input, bits), leaves)
        return tokens.getListDictionaryByYomiTermId(term, tokenBits).filter {
            tokens.leftIds[it.posTableIndex.toInt()].toInt() !in 2043..2055
        }.mapTo(hashSetOf()) { entry -> when (entry.nodeId) {
            -2 -> input
            -1 -> input.map { if (it in 'ぁ'..'ゖ') it + 0x60 else it }.joinToString("")
            else -> tango.getLetter(entry.nodeId, tangoBits)
        } }
    }

    private suspend fun KanaKanjiEngine.convertOriginal(
        input: String,
        repository: UserDictionaryRepository,
    ): List<Candidate> = getCandidatesOriginal(
        input = input,
        n = 8,
        mozcUtPersonName = false,
        mozcUTPlaces = false,
        mozcUTWiki = false,
        mozcUTNeologd = false,
        mozcUTWeb = false,
        userDictionaryRepository = repository,
        learnRepository = null,
        isOmissionSearchEnable = false,
        enableTypoCorrectionJapaneseFlick = false,
        enableTypoCorrectionQwertyEnglish = false,
        typoCorrectionOffsetScore = 3_000,
        omissionSearchOffsetScore = 3_000,
        beamWidth = 20,
        predictionConfig = com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig(japaneseNumberCandidatesEnabled = true),
    )

    private fun reportStage(stage: String) {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(2,
            android.os.Bundle().apply { putString("stream", "NUMBER_ENGINE_STAGE $stage\n") })
    }

    // API 24 ART misidentifies live registers in the long-lived suspend measurement loop
    // during GC. Keep the harness frame synchronous and scope each coroutine to one query.
    // Timings include the same runBlocking harness overhead for both measured paths.
    private fun measureConversions(
        label: String,
        corpus: List<String>,
        warmupIterations: Int,
        measuredIterations: Int,
        convert: suspend (String) -> List<Candidate>,
    ): BenchmarkResult {
        var blackHole = 0L
        repeat(warmupIterations) { index ->
            val candidates = runBlocking { convert(corpus[index % corpus.size]) }
            blackHole += candidates.size + (candidates.firstOrNull()?.string?.length ?: 0)
        }

        reportStage("$label warmup complete; collecting")
        forceGc()
        reportStage("$label collection complete; measuring")
        val baseline = memorySnapshot()
        val allocatedBefore = allocatedBytes()
        val samplesNs = LongArray(measuredIterations)

        repeat(measuredIterations) { index ->
            val startedNs = System.nanoTime()
            val candidates = runBlocking { convert(corpus[index % corpus.size]) }
            samplesNs[index] = System.nanoTime() - startedNs
            blackHole += candidates.size + (candidates.firstOrNull()?.string?.length ?: 0)
        }

        reportStage("$label measurement complete")
        val allocatedAfter = allocatedBytes()
        val immediate = memorySnapshot()
        forceGc()
        val settled = memorySnapshot()
        val sortedNs = samplesNs.sortedArray()

        return BenchmarkResult(
            label = label,
            iterations = measuredIterations,
            averageUs = samplesNs.average() / 1_000.0,
            p50Us = sortedNs.percentile(0.50) / 1_000.0,
            p95Us = sortedNs.percentile(0.95) / 1_000.0,
            allocatedBytesPerCall = allocatedBefore?.let { before ->
                allocatedAfter?.let { after -> (after - before).coerceAtLeast(0L).toDouble() / measuredIterations }
            },
            baseline = baseline,
            immediate = immediate,
            settled = settled,
            blackHole = blackHole,
        )
    }

    private fun LongArray.percentile(fraction: Double): Long {
        if (isEmpty()) return 0L
        val index = ((size - 1) * fraction).toInt().coerceIn(indices)
        return this[index]
    }

    private fun allocatedBytes(): Long? =
        Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()

    private fun memorySnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(
            javaHeapKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
            totalPssKb = Debug.getPss().toLong(),
        )
    }

    private fun forceGc() {
        repeat(2) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Thread.sleep(100)
        }
    }

    private data class MemorySnapshot(
        val javaHeapKb: Long,
        val totalPssKb: Long,
    )

    private data class BenchmarkResult(
        val label: String,
        val iterations: Int,
        val averageUs: Double,
        val p50Us: Double,
        val p95Us: Double,
        val allocatedBytesPerCall: Double?,
        val baseline: MemorySnapshot,
        val immediate: MemorySnapshot,
        val settled: MemorySnapshot,
        val blackHole: Long,
    ) {
        fun asReportLine(): String = String.format(
            Locale.US,
            "%s iterations=%d avgUs=%.3f p50Us=%.3f p95Us=%.3f " +
                "allocatedBytesPerCall=%s " +
                "heapBaselineKb=%d heapImmediateKb=%d heapSettledKb=%d " +
                "pssBaselineKb=%d pssImmediateKb=%d pssSettledKb=%d blackHole=%d",
            label,
            iterations,
            averageUs,
            p50Us,
            p95Us,
            allocatedBytesPerCall?.let { String.format(Locale.US, "%.1f", it) } ?: "unavailable",
            baseline.javaHeapKb,
            immediate.javaHeapKb,
            settled.javaHeapKb,
            baseline.totalPssKb,
            immediate.totalPssKb,
            settled.totalPssKb,
            blackHole,
        )
    }
}
