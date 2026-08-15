package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.ceil
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class EnglishReadingDictionaryPerformanceInstrumentedTest {

    @Test
    fun measureEnabledAndDisabledConversion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val hadPreviousValue = preferences.contains(
            DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE
        )
        val previousValue = preferences.getBoolean(
            DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE,
            true,
        )
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()

        try {
            val enabled = measureState(
                context = context,
                preferences = preferences,
                engine = engine,
                repository = repository,
                enabled = true,
            )
            val disabled = measureState(
                context = context,
                preferences = preferences,
                engine = engine,
                repository = repository,
                enabled = false,
            )
            val report = buildString {
                appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT}")
                appendLine("input=かー")
                appendLine("warmups=$WARMUPS")
                appendLine("iterations=$ITERATIONS")
                appendLine(enabled.toReport("enabled"))
                appendLine(disabled.toReport("disabled"))
                appendLine("warmP50DeltaMs=${enabled.p50Ms - disabled.p50Ms}")
                appendLine("warmP95DeltaMs=${enabled.p95Ms - disabled.p95Ms}")
                appendLine("retainedHeapDeltaBytes=${enabled.retainedHeapBytes - disabled.retainedHeapBytes}")
                appendLine("nativeRetainedDeltaBytes=${enabled.nativeRetainedBytes - disabled.nativeRetainedBytes}")
                appendLine("enabledEnglishCandidateIndexes=${ENGLISH_CASES.associateWith { word -> enabled.candidates.indexOfFirst { it.string == word } }}")
                appendLine("enabledEnglishCandidateScores=${ENGLISH_CASES.associateWith { word -> enabled.candidates.firstOrNull { it.string == word }?.score }}")
                appendLine("enabledNBest4Candidates=${enabled.nBestCandidates.joinToString("|") { it.string }}")
                appendLine("enabledCandidates=${enabled.candidates.take(16).joinToString("|") { it.string }}")
                appendLine("disabledCandidates=${disabled.candidates.take(16).joinToString("|") { it.string }}")
            }
            File(context.filesDir, "conversion-perf").apply { mkdirs() }
                .resolve("english-reading.txt")
                .writeText(report)
            println(report)
        } finally {
            if (hadPreviousValue) {
                setEnabled(preferences, previousValue)
            } else {
                preferences.edit()
                    .remove(DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE)
                    .commit()
            }
            engine.applyDictionaryOverrideState(context)
        }
    }

    private suspend fun measureState(
        context: Context,
        preferences: android.content.SharedPreferences,
        engine: KanaKanjiEngine,
        repository: com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository,
        enabled: Boolean,
    ): Measurement {
        setEnabled(preferences, enabled)
        val applyMs = measureNanoTime { engine.applyDictionaryOverrideState(context) } / 1_000_000.0
        repeat(WARMUPS) { convert(engine, repository, nBest = 4) }
        forceGc()

        val heapBefore = usedHeapBytes()
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        val allocatedBefore = runtimeStat("art.gc.bytes-allocated")
        val gcBefore = runtimeStat("art.gc.gc-count")
        val samples = DoubleArray(ITERATIONS)
        repeat(ITERATIONS) { index ->
            samples[index] = measureNanoTime {
                convert(engine, repository, nBest = 4)
            } / 1_000_000.0
        }
        val allocatedAfter = runtimeStat("art.gc.bytes-allocated")
        val gcAfter = runtimeStat("art.gc.gc-count")
        val heapAfter = usedHeapBytes()
        val nativeAfter = Debug.getNativeHeapAllocatedSize()
        forceGc()
        val heapAfterGc = usedHeapBytes()
        val nativeAfterGc = Debug.getNativeHeapAllocatedSize()

        val validationCandidates = convert(engine, repository, nBest = 64)
        val nBestCandidates = convert(engine, repository, nBest = 4)
        if (enabled) {
            check(ENGLISH_CASES.all { word -> validationCandidates.any { it.string == word } }) {
                "Enabled dictionary did not produce all case candidates: $validationCandidates"
            }
            check(ENGLISH_CASES.all { word -> nBestCandidates.any { it.string == word } }) {
                "Enabled dictionary did not produce all case candidates in n-best candidates: $nBestCandidates"
            }
        } else {
            check(validationCandidates.none { it.string in ENGLISH_CASES }) {
                "Disabled dictionary still produced English case candidates: $validationCandidates"
            }
            check(nBestCandidates.none { it.string in ENGLISH_CASES }) {
                "Disabled dictionary still produced English case candidates in n-best candidates: $nBestCandidates"
            }
        }
        return Measurement(
            enabled = enabled,
            applyMs = applyMs,
            avgMs = samples.average(),
            p50Ms = percentile(samples, 0.50),
            p95Ms = percentile(samples, 0.95),
            maxMs = samples.maxOrNull() ?: 0.0,
            allocatedBytesPerConversion = (allocatedAfter - allocatedBefore) / ITERATIONS,
            gcCount = gcAfter - gcBefore,
            heapBeforeBytes = heapBefore,
            heapAfterBytes = heapAfter,
            retainedHeapBytes = heapAfterGc - heapBefore,
            nativeBeforeBytes = nativeBefore,
            nativeAfterBytes = nativeAfter,
            nativeRetainedBytes = nativeAfterGc - nativeBefore,
            candidates = validationCandidates,
            nBestCandidates = nBestCandidates,
        )
    }

    private suspend fun convert(
        engine: KanaKanjiEngine,
        repository: com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository,
        nBest: Int,
    ): List<Candidate> = engine.getCandidatesWithBunsetsuSeparation(
        input = "かー",
        n = nBest,
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
    ).candidates

    private fun setEnabled(
        preferences: android.content.SharedPreferences,
        enabled: Boolean,
    ) {
        preferences.edit()
            .putBoolean(DictionaryOverrideStore.ENGLISH_READING_ENABLED_PREFERENCE, enabled)
            .commit()
    }

    private fun forceGc() {
        repeat(3) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Thread.sleep(100)
        }
    }

    private fun usedHeapBytes(): Long = Runtime.getRuntime().run {
        totalMemory() - freeMemory()
    }

    private fun runtimeStat(name: String): Long =
        Debug.getRuntimeStat(name)?.toLongOrNull() ?: 0L

    private fun percentile(values: DoubleArray, fraction: Double): Double {
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)]
    }

    private data class Measurement(
        val enabled: Boolean,
        val applyMs: Double,
        val avgMs: Double,
        val p50Ms: Double,
        val p95Ms: Double,
        val maxMs: Double,
        val allocatedBytesPerConversion: Long,
        val gcCount: Long,
        val heapBeforeBytes: Long,
        val heapAfterBytes: Long,
        val retainedHeapBytes: Long,
        val nativeBeforeBytes: Long,
        val nativeAfterBytes: Long,
        val nativeRetainedBytes: Long,
        val candidates: List<Candidate>,
        val nBestCandidates: List<Candidate>,
    ) {
        fun toReport(label: String): String = buildString {
            appendLine("[$label]")
            appendLine("enabled=$enabled")
            appendLine("applyMs=$applyMs")
            appendLine("avgMs=$avgMs")
            appendLine("p50Ms=$p50Ms")
            appendLine("p95Ms=$p95Ms")
            appendLine("maxMs=$maxMs")
            appendLine("allocatedBytesPerConversion=$allocatedBytesPerConversion")
            appendLine("gcCount=$gcCount")
            appendLine("heapBeforeBytes=$heapBeforeBytes")
            appendLine("heapAfterBytes=$heapAfterBytes")
            appendLine("retainedHeapBytes=$retainedHeapBytes")
            appendLine("nativeBeforeBytes=$nativeBeforeBytes")
            appendLine("nativeAfterBytes=$nativeAfterBytes")
            appendLine("nativeRetainedBytes=$nativeRetainedBytes")
        }.trimEnd()
    }

    private companion object {
        const val WARMUPS = 10
        const val ITERATIONS = 50
        val ENGLISH_CASES = setOf("car", "Car", "CAR")
    }
}
