package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramAssetLoader
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramRuntime
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.ceil
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class SystemNgramCandidatePerformanceInstrumentedTest {
    @Test
    fun conversionReranksAndRestoresWithoutChangingScore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entry.kanaKanjiEngine()
        val repository = entry.userDictionaryRepository()
        SystemNgramRuntime.disable()
        val without = convert(engine, repository, candidates = 32)
        val original = without.candidates.first { it.string == "服を着る" }
        assertEquals("服を切る", without.candidates.first().string)
        assertFalse("服を着る" in without.systemNgramMatchedCandidates)

        SystemNgramRuntime.install(SystemNgramAssetLoader.load(context))
        val with = convert(engine, repository, candidates = 32)
        assertEquals("服を着る", with.candidates.first().string)
        assertTrue("服を着る" in with.systemNgramMatchedCandidates)
        assertEquals(original.score, with.candidates.first().score)

        SystemNgramRuntime.disable()
        val restored = convert(engine, repository, candidates = 32)
        assertEquals(without.candidates.first().string, restored.candidates.first().string)
        assertEquals(original.score, restored.candidates.first { it.string == "服を着る" }.score)
        SystemNgramRuntime.install(SystemNgramAssetLoader.load(context))
    }

    @Test
    fun packedPrefixPrefilterMatchesConservativeSearchAcrossCorpus() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entry.kanaKanjiEngine()
        val repository = entry.userDictionaryRepository()
        val packed = SystemNgramAssetLoader.load(context)
        val conservative = object : SystemNgramDictionary {
            override val ruleCount: Int = packed.ruleCount
            override val storageBytes: Int = packed.storageBytes

            override fun matches(
                node0: com.kazumaproject.graph.Node,
                node1: com.kazumaproject.graph.Node,
                node2: com.kazumaproject.graph.Node?,
                node3: com.kazumaproject.graph.Node?,
                node4: com.kazumaproject.graph.Node?,
            ): Boolean = packed.matches(node0, node1, node2, node3, node4)
            // The default mayMatch methods return true, reproducing the old conservative search.
        }
        val corpus = listOf(
            "このあぷりのへんかんこうほをこうそくにしたい",
            "ふくをきる",
            "にほんごをにゅうりょくする",
            "きょうはいいてんきです",
            "せんたくきをつかう",
        )

        try {
            corpus.forEach { input ->
                SystemNgramRuntime.install(conservative)
                val expected = convertInput(engine, repository, input)
                SystemNgramRuntime.install(packed)
                val actual = convertInput(engine, repository, input)
                assertEquals("$input candidates", expected.candidates, actual.candidates)
                assertEquals("$input splits", expected.splitPatterns, actual.splitPatterns)
                val returnedCandidateStrings = actual.candidates.mapTo(HashSet()) { it.string }
                assertEquals(
                    "$input candidate splits",
                    expected.splitPatternByCandidateString.filterKeys(returnedCandidateStrings::contains),
                    actual.splitPatternByCandidateString.filterKeys(returnedCandidateStrings::contains),
                )
                assertEquals(
                    "$input matched system n-grams",
                    expected.systemNgramMatchedCandidates,
                    actual.systemNgramMatchedCandidates,
                )
            }
        } finally {
            SystemNgramRuntime.install(packed)
        }
    }

    @Test
    fun compareCandidateConversionWithAndWithoutSystemNgram() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("systemNgramPerf") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val iterations = arguments.getString("systemNgramIterations")?.toIntOrNull() ?: 100
        require(iterations >= 100) { "System n-gram performance measurement requires at least 100 iterations" }
        val report = StringBuilder(
            "enabled\trules\tstorageBytes\theapDeltaBytes\tnativeHeapDeltaBytes\tpssDeltaBytes\tallocatedBytesPerConversion\tgcCount\tp50Ms\tp95Ms\tp99Ms\tfirstCandidate\tfirstScore\tmatched\tfingerprint\tcandidates\tmatchedCandidates\n",
        )

        // Warm ART/JIT and converter caches for both paths before timing either path.
        prewarmBothConfigurations(
            context = context,
            engine = entry.kanaKanjiEngine(),
            repository = entry.userDictionaryRepository(),
        )

        val rawWithout = measureConfiguration(
            enabled = false,
            context = context,
            iterations = iterations,
            engine = entry.kanaKanjiEngine(),
            repository = entry.userDictionaryRepository(),
        )
        val rawWith = measureConfiguration(
            enabled = true,
            context = context,
            iterations = iterations,
            engine = entry.kanaKanjiEngine(),
            repository = entry.userDictionaryRepository(),
        )
        val pairedTimings = measurePairedTimings(
            context = context,
            iterations = iterations,
            engine = entry.kanaKanjiEngine(),
            repository = entry.userDictionaryRepository(),
        )
        val without = rawWithout.withTimings(pairedTimings.without)
        val with = rawWith.withTimings(pairedTimings.with)
        report.appendLine(without.toTsv())
        report.appendLine(with.toTsv())
        File(context.filesDir, "ngram-performance").apply { mkdirs() }
            .resolve("system-candidate-comparison.tsv")
            .writeText(report.toString())
        println(report)
        assertEquals("服を着る", with.firstCandidate)
        assertTrue(with.matched)
        // The system dictionary changes ordering only; it never mutates Candidate.score.
        val withoutSameCandidate = without.candidates.firstOrNull { it.first == with.firstCandidate }
        if (withoutSameCandidate != null) assertEquals(with.firstScore, withoutSameCandidate.second)
        SystemNgramRuntime.install(
            SystemNgramAssetLoader.load(context),
        )
    }

    @Test
    fun measureSystemAndCustomToggleMatrix() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("ngramTogglePerf") == "true")
        val iterations = arguments.getString("ngramToggleIterations")?.toIntOrNull() ?: 100
        require(iterations >= 100)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entry.kanaKanjiEngine()
        val repository = entry.userDictionaryRepository()
        val scorerManager = entry.ngramRuleScorerManager()
        SystemNgramRuntime.resetForTesting()
        SystemNgramRuntime.setEnabled(context, false)
        forceGc()
        val coldOffMemory = Triple(
            usedHeap(),
            Debug.getNativeHeapAllocatedSize(),
            usedPssBytes(),
        )
        SystemNgramRuntime.setEnabled(context, true)
        forceGc()
        val coldOnMemory = Triple(
            usedHeap(),
            Debug.getNativeHeapAllocatedSize(),
            usedPssBytes(),
        )
        val dictionary = SystemNgramRuntime.loadedDictionary()
        val configurations = listOf(
            ToggleConfiguration(system = false, custom = false),
            ToggleConfiguration(system = false, custom = true),
            ToggleConfiguration(system = true, custom = false),
            ToggleConfiguration(system = true, custom = true),
        )

        fun apply(configuration: ToggleConfiguration) {
            if (configuration.system) SystemNgramRuntime.install(dictionary) else SystemNgramRuntime.disable()
            scorerManager.setEnabled(configuration.custom)
        }

        repeat(30) { round ->
            repeat(configurations.size) { offset ->
                val configuration = configurations[(round + offset) % configurations.size]
                apply(configuration)
                convert(engine, repository)
            }
        }
        forceGc()

        val raw = configurations.associateWith { configuration ->
            apply(configuration)
            forceGc()
            val javaHeap = usedHeap()
            val nativeHeap = Debug.getNativeHeapAllocatedSize()
            val pss = usedPssBytes()
            val allocatedBefore = runtimeStat("art.gc.bytes-allocated")
            val gcBefore = runtimeStat("art.gc.gc-count")
            lateinit var result: BunsetsuCandidateResult
            repeat(iterations) { result = convert(engine, repository) }
            val allocatedAfter = runtimeStat("art.gc.bytes-allocated")
            val gcAfter = runtimeStat("art.gc.gc-count")
            ToggleMeasurement(
                configuration = configuration,
                rules = if (configuration.system) dictionary.ruleCount else 0,
                dictionaryStorageBytes = dictionary.storageBytes,
                javaHeapBytes = javaHeap,
                nativeHeapBytes = nativeHeap,
                pssBytes = pss,
                allocatedBytesPerConversion = (allocatedAfter - allocatedBefore) / iterations,
                gcCount = gcAfter - gcBefore,
                timings = DoubleArray(0),
                firstCandidate = result.candidates.first().string,
                firstScore = result.candidates.first().score,
                matched = result.candidates.first().string in result.systemNgramMatchedCandidates,
            )
        }
        val timings = configurations.associateWith { DoubleArray(iterations) }
        repeat(iterations) { round ->
            repeat(configurations.size) { offset ->
                val configuration = configurations[(round + offset) % configurations.size]
                apply(configuration)
                timings.getValue(configuration)[round] =
                    measureNanoTime { convert(engine, repository) } / 1_000_000.0
            }
        }
        val measurements = configurations.map { raw.getValue(it).copy(timings = timings.getValue(it)) }
        val report = buildString {
            appendLine("coldState\tjavaHeapBytes\tnativeHeapBytes\tpssBytes")
            appendLine("systemOff\t${coldOffMemory.first}\t${coldOffMemory.second}\t${coldOffMemory.third}")
            appendLine("systemOn\t${coldOnMemory.first}\t${coldOnMemory.second}\t${coldOnMemory.third}")
            appendLine("systemEnabled\tcustomEnabled\trules\tdictionaryStorageBytes\tjavaHeapBytes\tnativeHeapBytes\tpssBytes\tallocatedBytesPerConversion\tgcCount\tp50Ms\tp95Ms\tp99Ms\tfirstCandidate\tfirstScore\tmatched")
            measurements.forEach { appendLine(it.toTsv()) }
        }
        File(context.filesDir, "ngram-performance").apply { mkdirs() }
            .resolve("ngram-toggle-matrix.tsv")
            .writeText(report)
        println(report)

        measurements.forEach {
            assertEquals(if (it.configuration.system) "服を着る" else "服を切る", it.firstCandidate)
            assertEquals(it.configuration.system, it.matched)
        }
        SystemNgramRuntime.install(dictionary)
        scorerManager.setEnabled(true)
    }

    private suspend fun prewarmBothConfigurations(
        context: Context,
        engine: KanaKanjiEngine,
        repository: UserDictionaryRepository,
    ) {
        SystemNgramRuntime.disable()
        repeat(30) { convert(engine, repository) }
        SystemNgramRuntime.install(
            SystemNgramAssetLoader.load(context),
        )
        repeat(30) { convert(engine, repository) }
        SystemNgramRuntime.disable()
        forceGc()
    }

    private suspend fun measurePairedTimings(
        context: Context,
        iterations: Int,
        engine: KanaKanjiEngine,
        repository: UserDictionaryRepository,
    ): PairedTimings {
        SystemNgramRuntime.disable()
        val dictionary = SystemNgramAssetLoader.load(context)
        val without = DoubleArray(iterations)
        val with = DoubleArray(iterations)
        repeat(iterations) { index ->
            fun install(enabled: Boolean) {
                if (enabled) SystemNgramRuntime.install(dictionary) else SystemNgramRuntime.disable()
            }
            suspend fun sample(enabled: Boolean): Double {
                install(enabled)
                return measureNanoTime { convert(engine, repository) } / 1_000_000.0
            }
            if (index and 1 == 0) {
                without[index] = sample(false)
                with[index] = sample(true)
            } else {
                with[index] = sample(true)
                without[index] = sample(false)
            }
        }
        return PairedTimings(without = without, with = with)
    }

    private suspend fun measureConfiguration(
        enabled: Boolean,
        context: Context,
        iterations: Int,
        engine: KanaKanjiEngine,
        repository: UserDictionaryRepository,
    ): Measurement {
        SystemNgramRuntime.disable()
        forceGc()
        val heapWithout = usedHeap()
        val nativeHeapWithout = Debug.getNativeHeapAllocatedSize()
        val pssWithout = usedPssBytes()
        val dictionary = if (!enabled) {
            null
        } else {
            // Load exactly as production does. Keeping a separate ByteArray in the test would
            // hide the dictionary's retained Java-heap cost from this delta.
            SystemNgramAssetLoader.load(context)
        }
        if (dictionary != null) SystemNgramRuntime.install(dictionary)
        forceGc()
        val heapDelta = usedHeap() - heapWithout
        val nativeHeapDelta = Debug.getNativeHeapAllocatedSize() - nativeHeapWithout
        val pssDelta = usedPssBytes() - pssWithout
        repeat(20) { convert(engine, repository) }
        forceGc()
        val allocatedBefore = runtimeStat("art.gc.bytes-allocated")
        val gcBefore = runtimeStat("art.gc.gc-count")
        val samples = DoubleArray(iterations)
        lateinit var result: BunsetsuCandidateResult
        repeat(iterations) { index ->
            samples[index] = measureNanoTime { result = convert(engine, repository) } / 1_000_000.0
        }
        val allocatedAfter = runtimeStat("art.gc.bytes-allocated")
        val gcAfter = runtimeStat("art.gc.gc-count")
        val first = result.candidates.first()
        return Measurement(
            enabled = enabled,
            rules = dictionary?.ruleCount ?: 0,
            storageBytes = dictionary?.storageBytes ?: 0,
            heapDeltaBytes = heapDelta,
            nativeHeapDeltaBytes = nativeHeapDelta,
            pssDeltaBytes = pssDelta,
            allocatedBytesPerConversion = (allocatedAfter - allocatedBefore) / iterations,
            gcCount = gcAfter - gcBefore,
            p50Ms = percentile(samples, 0.50),
            p95Ms = percentile(samples, 0.95),
            p99Ms = percentile(samples, 0.99),
            firstCandidate = first.string,
            firstScore = first.score,
            matched = first.string in result.systemNgramMatchedCandidates,
            fingerprint = result.candidates.joinToString("|") { "${it.string}:${it.score}" }.hashCode(),
            candidates = result.candidates.map { it.string to it.score },
            matchedCandidates = result.systemNgramMatchedCandidates,
        )
    }

    private suspend fun convert(
        engine: KanaKanjiEngine,
        repository: UserDictionaryRepository,
        candidates: Int = 4,
    ): BunsetsuCandidateResult = engine.getCandidatesWithBunsetsuSeparation(
        input = "ふくをきる",
        n = candidates,
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
        typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900,
        beamWidth = 20,
    )

    private suspend fun convertInput(
        engine: KanaKanjiEngine,
        repository: UserDictionaryRepository,
        input: String,
    ): BunsetsuCandidateResult = engine.getCandidatesWithBunsetsuSeparation(
        input = input,
        n = 4,
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
        typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900,
        beamWidth = 20,
    )

    private fun forceGc() {
        repeat(3) {
            Runtime.getRuntime().gc()
            Thread.sleep(50)
        }
    }

    private fun usedHeap(): Long = Runtime.getRuntime().run { totalMemory() - freeMemory() }
    private fun usedPssBytes(): Long = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss.toLong() * 1024L
    private fun runtimeStat(name: String): Long = Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L
    private fun percentile(values: DoubleArray, fraction: Double): Double {
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)]
    }

    private data class Measurement(
        val enabled: Boolean,
        val rules: Int,
        val storageBytes: Int,
        val heapDeltaBytes: Long,
        val nativeHeapDeltaBytes: Long,
        val pssDeltaBytes: Long,
        val allocatedBytesPerConversion: Long,
        val gcCount: Long,
        val p50Ms: Double,
        val p95Ms: Double,
        val p99Ms: Double,
        val firstCandidate: String,
        val firstScore: Int,
        val matched: Boolean,
        val fingerprint: Int,
        val candidates: List<Pair<String, Int>>,
        val matchedCandidates: Set<String>,
    ) {
        fun withTimings(samples: DoubleArray): Measurement = copy(
            p50Ms = percentileOf(samples, 0.50),
            p95Ms = percentileOf(samples, 0.95),
            p99Ms = percentileOf(samples, 0.99),
        )

        fun toTsv(): String = listOf(
            enabled, rules, storageBytes, heapDeltaBytes, nativeHeapDeltaBytes, pssDeltaBytes,
            allocatedBytesPerConversion, gcCount,
            p50Ms, p95Ms, p99Ms, firstCandidate, firstScore, matched, fingerprint,
            candidates.joinToString("|"), matchedCandidates.joinToString("|"),
        ).joinToString("\t")

        private fun percentileOf(values: DoubleArray, fraction: Double): Double {
            val sorted = values.sorted()
            return sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)]
        }
    }

    private data class PairedTimings(val without: DoubleArray, val with: DoubleArray)

    private data class ToggleConfiguration(val system: Boolean, val custom: Boolean)

    private data class ToggleMeasurement(
        val configuration: ToggleConfiguration,
        val rules: Int,
        val dictionaryStorageBytes: Int,
        val javaHeapBytes: Long,
        val nativeHeapBytes: Long,
        val pssBytes: Long,
        val allocatedBytesPerConversion: Long,
        val gcCount: Long,
        val timings: DoubleArray,
        val firstCandidate: String,
        val firstScore: Int,
        val matched: Boolean,
    ) {
        fun toTsv(): String = listOf(
            configuration.system,
            configuration.custom,
            rules,
            dictionaryStorageBytes,
            javaHeapBytes,
            nativeHeapBytes,
            pssBytes,
            allocatedBytesPerConversion,
            gcCount,
            percentile(timings, 0.50),
            percentile(timings, 0.95),
            percentile(timings, 0.99),
            firstCandidate,
            firstScore,
            matched,
        ).joinToString("\t")

        private fun percentile(values: DoubleArray, fraction: Double): Double {
            val sorted = values.sorted()
            return sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)]
        }
    }
}
