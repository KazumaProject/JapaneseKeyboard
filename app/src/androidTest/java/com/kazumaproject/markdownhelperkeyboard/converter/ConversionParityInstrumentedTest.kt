package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryRequest
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateDisplayMerger
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class ConversionParityInstrumentedTest {
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val args get() = InstrumentationRegistry.getArguments()

    @Test
    fun captureConversionResults() = runBlocking {
        assumeTrue(args.getString("conversionParity") == "snapshot")
        withFixture { fixture ->
            val rows = mutableListOf<JsonObject>()
            for (backend in ConversionBackend.entries) {
                for (case in cases) {
                    val session = KanaKanjiConversionSession(fixture.engine, backend)
                    case.inputs.forEachIndexed { step, input ->
                        val request = fixture.request(case, input, collectSegments = true)
                        val result = session.query(request)
                        rows += json(
                            "id" to "${backend.name}/${case.id}/$step",
                            "input" to input,
                            "mode" to case.mode.name,
                            "bunsetsu" to case.bunsetsu,
                            "correction" to case.correction,
                            "prediction" to case.prediction,
                            "personalDictionary" to case.personalDictionary,
                            "result" to snapshot(result),
                        )
                    }
                }
            }
            for (context in listOf("good", "Android", "New York", "two-factor", "qzxjvk")) {
                rows += json(
                    "id" to "ENGLISH_CONTINUATION/$context",
                    "input" to context,
                    "result" to fixture.english.getPhraseContinuations(context),
                )
            }
            check(rows.isNotEmpty())
            write("snapshot", json("environment" to environment(), "cases" to rows))
        }
    }

    @Test
    fun measureConversion() = runBlocking {
        val mode = args.getString("conversionParity")
        assumeTrue(mode == "benchmark" || mode == "stages" || mode == "allocations")
        val case = cases.single { it.id == args.getString("conversionCase") }
        val backend = ConversionBackend.valueOf(
            args.getString("conversionBackend") ?: ConversionBackend.INCREMENTAL_SESSION.name,
        )
        val warmup = args.getString("conversionWarmup")?.toInt() ?: 20
        val iterations = args.getString("conversionIterations")?.toInt() ?: 100
        require(warmup in 0..1000 && iterations in 1..1000)
        require(mode != "stages" || backend == ConversionBackend.INCREMENTAL_SESSION)

        withFixture { fixture ->
            val session = KanaKanjiConversionSession(fixture.engine, backend)
            if (mode == "stages") session.enablePerformanceProbe()
            val requests = case.inputs.map { fixture.request(case, it, collectSegments = case.bunsetsu) }
            val durations = Array(iterations + 1) { LongArray(requests.size) }
            val stages = mutableListOf<JsonObject>()

            suspend fun measureStep(request: KanaKanjiQueryRequest, row: Int, step: Int) {
                val start = SystemClock.elapsedRealtimeNanos()
                val result = session.query(request)
                durations[row][step] = SystemClock.elapsedRealtimeNanos() - start
                if (mode == "stages") {
                    val stage = checkNotNull(session.performanceSnapshot())
                    stages += json(
                        "round" to row, "step" to step, "graphNs" to stage.graphNs,
                        "penaltyNs" to stage.penaltyNs, "forwardDpNs" to stage.forwardDpNs,
                        "backwardSearchNs" to stage.backwardSearchNs,
                        "nBestSelectionNs" to result.nBestSelectionNs,
                        "graphAppendReused" to stage.graphAppendReused,
                        "forwardDpReused" to stage.forwardDpReused,
                    )
                }
            }

            measureStep(requests.first(), 0, 0)
            for (step in 1 until requests.size) measureStep(requests[step], 0, step)
            repeat(warmup) {
                for (request in requests) session.query(request)
            }
            val before = counters()
            val allocationObjects = countAllocatedObjects(enabled = mode == "allocations") {
                for (round in 1..iterations) {
                    requests.forEachIndexed { step, request -> measureStep(request, round, step) }
                }
            }
            val after = counters()
            write("benchmark", json(
                "environment" to environment(),
                "case" to case.id, "language" to case.language, "inputs" to case.inputs,
                "backend" to backend.name, "mode" to mode, "warmup" to warmup,
                "queryMode" to case.mode.name, "bunsetsu" to case.bunsetsu,
                "correction" to case.correction, "prediction" to case.prediction,
                "personalDictionary" to case.personalDictionary,
                "resolveEngineNs" to fixture.resolveEngineNs,
                "firstNs" to durations.first(), "warmNs" to durations.drop(1),
                "before" to before, "after" to after,
                "allocationObjects" to allocationObjects,
                "stages" to stages,
            ))
        }
    }

    @Suppress("DEPRECATION")
    private suspend inline fun countAllocatedObjects(enabled: Boolean, block: () -> Unit): Int? {
        if (!enabled) {
            block()
            return null
        }
        // Allocation counting affects ART and is used only in the separate diagnostic run.
        Debug.startAllocCounting()
        val before = Debug.getGlobalAllocCount()
        try {
            block()
            val delta = Debug.getGlobalAllocCount() - before
            return delta.takeIf { it > 0 }
        } finally {
            Debug.stopAllocCounting()
        }
    }

    private fun counters() = json(
        "pssKiB" to Debug.getPss(),
        "allocatedBytes" to Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull(),
        "gcCount" to Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull(),
        "blockingGcMs" to Debug.getRuntimeStat("art.gc.blocking-gc-time")?.toLongOrNull(),
        "processCpuMs" to Process.getElapsedCpuTime(),
    )

    private fun snapshot(result: KanaKanjiQueryResult): JsonObject {
        val bunsetsu = result.bunsetsuResult
        return json(
            "candidates" to result.candidates.map(::candidate),
            "displayMerge" to CandidateDisplayMerger.merge(result.candidates).map(::candidate),
            "bestNBestCandidate" to result.bestNBestCandidate?.let(::candidate),
            "candidateSegmentsByString" to result.candidateSegmentsByString,
            "bunsetsu" to bunsetsu?.let {
                json(
                    "candidates" to it.candidates.map(::candidate),
                    "splitPatterns" to it.splitPatterns,
                    "splitPatternByCandidateString" to it.splitPatternByCandidateString,
                    "systemNgramMatchedCandidates" to it.systemNgramMatchedCandidates.sorted(),
                )
            },
        )
    }

    private fun candidate(candidate: Candidate): JsonObject = gson.toJsonTree(candidate).asJsonObject.apply {
        add("length", gson.toJsonTree(candidate.length.toInt()))
        add("dictionaryCategories", gson.toJsonTree(candidate.dictionaryCategories.map { it.name }.sorted()))
        add("dictionaryOrigins", gson.toJsonTree(candidate.dictionaryOrigins.map { it.name }.sorted()))
        candidate.presentation?.let { presentation ->
            // Preserve the concrete formula node types as well as their Gson fields.
            getAsJsonObject("presentation").addProperty("astRepresentation", presentation.ast.toString())
        }
    }

    private fun environment() = json(
        "schema" to 1, "android" to Build.FINGERPRINT, "sdk" to Build.VERSION.SDK_INT,
        "locale" to Locale.getDefault().toLanguageTag(), "timeZone" to TimeZone.getDefault().id,
        "flavor" to BuildConfig.FLAVOR, "buildType" to BuildConfig.BUILD_TYPE,
    )

    private fun json(vararg fields: Pair<String, Any?>) = JsonObject().apply {
        for ((key, value) in fields) add(key, gson.toJsonTree(value))
    }

    private fun write(name: String, document: JsonElement) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "conversion-$name.json")
        file.writeText(gson.toJson(document))
        InstrumentationRegistry.getInstrumentation().sendStatus(
            2, android.os.Bundle().apply { putString("stream", "\nCONVERSION_PARITY_REPORT ${file.name}\n") },
        )
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        check(!BuildConfig.HAS_ZENZ && !BuildConfig.HAS_GEMMA) { "Use the offline Lite build" }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val users = UserDictionaryRepository(database.userWordDao())
            val learning = LearnRepository(database.learnDao())
            users.insert(UserWord(word = "菫計測", reading = "すみれけいそく", posIndex = 0, posScore = 2000))
            learning.insertLearnedData(LearnEntity(input = "すみれけいそく", out = "すみれ計測", score = 1800))
            val entryPoint = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
            block(Fixture(entryPoint, users, learning))
        } finally {
            database.close()
        }
    }

    private class Fixture(
        entryPoint: KanaKanjiEngineEntryPoint,
        private val users: UserDictionaryRepository,
        private val learning: LearnRepository,
    ) {
        private val resolveEngineStart = SystemClock.elapsedRealtimeNanos()
        val engine = entryPoint.kanaKanjiEngine()
        val resolveEngineNs = SystemClock.elapsedRealtimeNanos() - resolveEngineStart
        val english by lazy { EnglishEngine().apply { configureLazyDictionaryLoading(entryPoint.dictionaryBinaryReader()) } }

        fun request(case: Case, input: String, collectSegments: Boolean) = KanaKanjiQueryRequest(
            input = input,
            mode = case.mode,
            bunsetsuSeparation = case.bunsetsu,
            n = 4,
            userDictionaryRepository = users,
            learnRepository = learning.takeIf { case.personalDictionary },
            omissionSearchEnabled = case.correction && case.language == "ja",
            typoCorrectionJapaneseFlickEnabled = case.correction && case.language == "ja",
            typoCorrectionQwertyEnglishEnabled = case.correction && case.language == "en",
            typoCorrectionOffsetScore = 3000,
            omissionSearchOffsetScore = 1900,
            beamWidth = 20,
            predictionConfig = PredictionConfig(
                japanesePredictionEnabled = case.prediction,
                englishPredictionEnabled = case.prediction,
                systemUserDictionaryEnabled = false,
            ),
            collectCandidateSegments = collectSegments,
        )
    }

    private data class Case(
        val id: String,
        val language: String,
        val inputs: List<String>,
        val mode: CandidateQueryMode = CandidateQueryMode.NO_TAB_DEFAULT,
        val bunsetsu: Boolean = false,
        val correction: Boolean = false,
        val prediction: Boolean = true,
        val personalDictionary: Boolean = false,
    )

    private val cases = listOf(
        Case("ja-short", "ja", listOf("あさ")),
        Case("ja-ambiguous", "ja", listOf("はし", "こうしょう", "きしゃ")),
        Case("ja-long", "ja", listOf("わたしはきのうともだちとえきまえであいました")),
        Case("ja-bunsetsu", "ja", listOf("とうきょうとちよだく"), CandidateQueryMode.CONVERSION, bunsetsu = true),
        Case("ja-edit", "ja", listOf("に", "にほ", "にほん", "にほんご", "にほん", "にほんじん"), CandidateQueryMode.PREDICTION),
        Case("ja-name", "ja", listOf("とうきょう", "さいとう"), CandidateQueryMode.PREDICTION),
        Case("ja-symbol-number", "ja", listOf("かっこ", "１２３", "3ばんめ", "きごう", "ぶんすう")),
        Case("ja-correction", "ja", listOf("おはようござます", "こにちは"), correction = true),
        Case("ja-personal", "ja", listOf("すみれけ", "すみれけいそく"), personalDictionary = true),
        Case("ja-prediction-off", "ja", listOf("けいたいでにほんごをへんかんする"), prediction = false),
        Case("en-prefix", "en", listOf("key")),
        Case("en-edit", "en", listOf("k", "ke", "key", "keyb", "key", "keys"), CandidateQueryMode.PREDICTION),
        Case("en-phrase", "en", listOf("he can tak", "hecantake", "New Yo", "twofactorauthentication")),
        Case("en-exact", "en", listOf("androidstudio", "taylor swift", "key"), CandidateQueryMode.CONVERSION),
        Case("en-case-symbol", "en", listOf("GitH", "don'", "can’t", "h8", "1.5", "a/b")),
        Case("en-typo", "en", listOf("teh", "tset", "keybaord"), correction = true),
        Case("en-prediction-off", "en", listOf("h8", "key"), prediction = false),
        Case("en-eisukana", "en", listOf("Sumire", "ねこ"), CandidateQueryMode.EISUKANA),
    )
}
