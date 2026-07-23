package com.kazumaproject.markdownhelperkeyboard.learning

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.session.ConversionLearningSession
import com.kazumaproject.markdownhelperkeyboard.learning.session.LearningFragment
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class LearningDictionaryPerformanceInstrumentedTest {

    @Test
    fun verifyWholePhraseAndMeasureLargeDictionaryOnDevice() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("learningDictionaryPerfProbe") == "true")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val userDictionaryRepository = entryPoint.userDictionaryRepository()

        verifyWholePhraseTransaction(context, engine, userDictionaryRepository)

        // Initialize production conversion dictionaries before comparing learned-dictionary sizes.
        repeat(3) {
            engine.convertForLearningProbe(
                input = TARGET_READING,
                userDictionaryRepository = userDictionaryRepository,
                learnRepository = null,
            )
        }

        val scenarios = listOf(0, 1_000, 10_000, 50_000).map { entryCount ->
            measureScenario(
                context = context,
                engine = engine,
                userDictionaryRepository = userDictionaryRepository,
                entryCount = entryCount,
            )
        }

        val report = buildString {
            appendLine("LEARNING_DICTIONARY_PERFORMANCE_REPORT")
            appendLine(
                "device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
                    "api=${android.os.Build.VERSION.SDK_INT}"
            )
            appendLine("functionalWholePhrase=true")
            appendLine(
                "entries\tinsertMs\tfirstConversionMs\tconversionP50Ms\tconversionP95Ms\t" +
                    "conversionP99Ms\tcandidatePrepareP50Ms\tcandidatePrepareP95Ms\t" +
                    "candidatePrepareP99Ms\theapAfterInsertMiB\theapAfterSnapshotMiB\t" +
                    "pssAfterInsertMiB\tpssAfterSnapshotMiB\tcandidateCount"
            )
            scenarios.forEach { appendLine(it.asTsv()) }
        }

        File(context.filesDir, "conversion-perf").apply { mkdirs() }
            .resolve("learning-dictionary-performance.txt")
            .writeText(report)
        println(report)
    }

    private suspend fun verifyWholePhraseTransaction(
        context: Context,
        engine: KanaKanjiEngine,
        userDictionaryRepository: UserDictionaryRepository,
    ) {
        val database = temporaryDatabase(context)
        try {
            val repository = LearnRepository(database.learnDao())
            val session = ConversionLearningSession().apply {
                beginIfNeeded(TARGET_READING)
                record(fragment("しうん", "紫雲", 2))
                record(fragment("すみ", "清", 1))
                record(fragment("か", "夏", 3))
            }
            val entries = session.finish(learnFirstCandidate = false, timestamp = 100L)
            repository.upsertLearnedDataBatch(entries, allowJapaneseWithSymbolsAndNumbers = true)

            assertEquals("紫雲清夏", repository.findLearnDataByInputAndOutput(
                TARGET_READING,
                TARGET_OUTPUT,
            )?.out)
            assertEquals("紫雲清", repository.findLearnDataByInputAndOutput(
                "しうんすみ",
                "紫雲清",
            )?.out)

            repository.upsertLearnedDataBatch(entries, allowJapaneseWithSymbolsAndNumbers = true)
            val reinforced = repository.findLearnDataByInputAndOutput(TARGET_READING, TARGET_OUTPUT)
            assertEquals(2, reinforced?.usageCount)
            assertTrue((reinforced?.score ?: Int.MAX_VALUE) < entries.single {
                it.input == TARGET_READING && it.out == TARGET_OUTPUT
            }.score)

            val candidates = engine.convertForLearningProbe(
                input = TARGET_READING,
                userDictionaryRepository = userDictionaryRepository,
                learnRepository = repository,
            )
            assertTrue(candidates.any { it.string == TARGET_OUTPUT })
        } finally {
            database.close()
            context.deleteDatabase(PERFORMANCE_DATABASE_NAME)
        }
    }

    private suspend fun measureScenario(
        context: Context,
        engine: KanaKanjiEngine,
        userDictionaryRepository: UserDictionaryRepository,
        entryCount: Int,
    ): ScenarioResult {
        forceGc()
        val database = temporaryDatabase(context)
        try {
            val repository = LearnRepository(database.learnDao())
            val entries = generatedEntries(entryCount)
            val insertNs = measureNanoTime {
                repository.insertAll(entries)
            }
            forceGc()
            val afterInsert = memorySnapshot()

            lateinit var firstCandidates: List<Candidate>
            val firstConversionNs = measureNanoTime {
                firstCandidates = engine.convertForLearningProbe(
                    input = TARGET_READING,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = repository,
                )
            }
            if (entryCount > 0) {
                assertTrue("complete phrase missing at $entryCount entries", firstCandidates.any {
                    it.string == TARGET_OUTPUT
                })
            }
            forceGc()
            val afterSnapshot = memorySnapshot()

            repeat(5) {
                engine.convertForLearningProbe(
                    input = TARGET_READING,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = repository,
                )
            }
            val conversionSamples = LongArray(SAMPLE_COUNT) {
                measureNanoTime {
                    engine.convertForLearningProbe(
                        input = TARGET_READING,
                        userDictionaryRepository = userDictionaryRepository,
                        learnRepository = repository,
                    )
                }
            }

            repeat(5) { prepareLearnedCandidates(repository) }
            val candidatePreparationSamples = LongArray(SAMPLE_COUNT) {
                measureNanoTime { prepareLearnedCandidates(repository) }
            }

            return ScenarioResult(
                entries = entryCount,
                insertNs = insertNs,
                firstConversionNs = firstConversionNs,
                conversionSamples = conversionSamples,
                candidatePreparationSamples = candidatePreparationSamples,
                afterInsert = afterInsert,
                afterSnapshot = afterSnapshot,
                candidateCount = firstCandidates.size,
            )
        } finally {
            database.close()
            context.deleteDatabase(PERFORMANCE_DATABASE_NAME)
            forceGc()
        }
    }

    private suspend fun prepareLearnedCandidates(repository: LearnRepository): List<Candidate> =
        repository.predictiveSearchByInput("しうん", limit = 8).map { entry ->
            Candidate(
                string = entry.out,
                type = CANDIDATE_TYPE_LEARNED_DICTIONARY,
                length = entry.input.length.toUByte(),
                score = entry.score,
                yomi = entry.input,
                leftId = entry.leftId,
                rightId = entry.rightId,
            )
        }

    private fun generatedEntries(count: Int): List<LearnEntity> {
        if (count == 0) return emptyList()
        return buildList(count) {
            add(
                LearnEntity(
                    input = TARGET_READING,
                    out = TARGET_OUTPUT,
                    score = 10,
                    usageCount = 5,
                    lastUsedAt = 1L,
                    isPhrase = true,
                )
            )
            repeat(count - 1) { index ->
                add(
                    LearnEntity(
                        input = generatedReading(index),
                        out = "学習候補${index}",
                        score = 1000 + index % 10_000,
                        usageCount = 1 + index % 20,
                        lastUsedAt = index.toLong(),
                        isPhrase = index % 3 == 0,
                    )
                )
            }
        }
    }

    private fun generatedReading(value: Int): String {
        var remaining = value
        return buildString(7) {
            repeat(5) {
                append(KANA[remaining % KANA.size])
                remaining /= KANA.size
            }
            append("こうほ")
        }
    }

    private fun fragment(reading: String, output: String, index: Int) = LearningFragment(
        reading = reading,
        output = output,
        candidateScore = 3000,
        candidateIndex = index,
    )

    private fun temporaryDatabase(context: Context): AppDatabase {
        context.deleteDatabase(PERFORMANCE_DATABASE_NAME)
        return Room.databaseBuilder(context, AppDatabase::class.java, PERFORMANCE_DATABASE_NAME)
            .allowMainThreadQueries()
            .build()
    }

    private fun forceGc() {
        repeat(3) {
            Runtime.getRuntime().gc()
            SystemClock.sleep(60)
        }
    }

    private fun memorySnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return MemorySnapshot(
            heapBytes = runtime.totalMemory() - runtime.freeMemory(),
            totalPssBytes = memoryInfo.totalPss.toLong() * 1024L,
        )
    }

    private suspend fun KanaKanjiEngine.convertForLearningProbe(
        input: String,
        userDictionaryRepository: UserDictionaryRepository,
        learnRepository: LearnRepository?,
    ): List<Candidate> = getCandidatesWithBunsetsuSeparation(
        input = input,
        n = 4,
        mozcUtPersonName = false,
        mozcUTPlaces = false,
        mozcUTWiki = false,
        mozcUTNeologd = false,
        mozcUTWeb = false,
        userDictionaryRepository = userDictionaryRepository,
        learnRepository = learnRepository,
        isOmissionSearchEnable = false,
        enableTypoCorrectionJapaneseFlick = false,
        enableTypoCorrectionQwertyEnglish = false,
        typoCorrectionOffsetScore = 3000,
        omissionSearchOffsetScore = 1900,
        beamWidth = 20,
    ).candidates

    private data class ScenarioResult(
        val entries: Int,
        val insertNs: Long,
        val firstConversionNs: Long,
        val conversionSamples: LongArray,
        val candidatePreparationSamples: LongArray,
        val afterInsert: MemorySnapshot,
        val afterSnapshot: MemorySnapshot,
        val candidateCount: Int,
    ) {
        fun asTsv(): String = listOf(
            entries,
            insertNs.asMs(),
            firstConversionNs.asMs(),
            conversionSamples.valueAt(0.50).asMs(),
            conversionSamples.valueAt(0.95).asMs(),
            conversionSamples.valueAt(0.99).asMs(),
            candidatePreparationSamples.valueAt(0.50).asMs(),
            candidatePreparationSamples.valueAt(0.95).asMs(),
            candidatePreparationSamples.valueAt(0.99).asMs(),
            afterInsert.heapBytes.asMiB(),
            afterSnapshot.heapBytes.asMiB(),
            afterInsert.totalPssBytes.asMiB(),
            afterSnapshot.totalPssBytes.asMiB(),
            candidateCount,
        ).joinToString("\t")

        private fun Long.asMs(): String = "%.3f".format(this / 1_000_000.0)
        private fun Long.asMiB(): String = "%.2f".format(this / 1024.0 / 1024.0)
        private fun LongArray.valueAt(fraction: Double): Long {
            val sorted = sortedArray()
            val index = ((sorted.lastIndex) * fraction).toInt().coerceIn(sorted.indices)
            return sorted[index]
        }
    }

    private data class MemorySnapshot(val heapBytes: Long, val totalPssBytes: Long)

    companion object {
        private const val TARGET_READING = "しうんすみか"
        private const val TARGET_OUTPUT = "紫雲清夏"
        private const val PERFORMANCE_DATABASE_NAME = "learning-dictionary-performance.db"
        private const val SAMPLE_COUNT = 30
        private val KANA = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわ".toList()
    }
}
