package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.view.ContextThemeWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.data.KeyCharacterCase
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderScope
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class ConversionPerformanceInstrumentedProbeTest {
    @Test
    fun compareConversionWithDefinedAndUppercaseKeyPresentation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("keyCaseConversionProbe") == "true")
        val iterations = arguments.getString("keyCaseConversionIterations")?.toIntOrNull() ?: 30
        val input = "わたしはきのうともだちとえきまえであいました"
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KanaKanjiEngineEntryPoint::class.java,
        )
        val engine = entryPoint.kanaKanjiEngine()
        val repository = entryPoint.userDictionaryRepository()
        lateinit var keyboard: FlickKeyboardView

        instrumentation.runOnMainSync {
            keyboard = FlickKeyboardView(
                ContextThemeWrapper(
                    context,
                    com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar,
                ),
            ).apply {
                setKeyboard(KeyboardDefaultLayouts.createQwertyTemplateLayout())
            }
        }

        repeat(30) {
            engine.convertForProbe(input, repository)
        }

        val asDefinedNanos = LongArray(iterations)
        val uppercaseNanos = LongArray(iterations)
        var asDefinedCandidates: List<Candidate> = emptyList()
        var uppercaseCandidates: List<Candidate> = emptyList()

        suspend fun measure(
            characterCase: KeyCharacterCase,
            samples: LongArray,
            index: Int,
            capture: (List<Candidate>) -> Unit,
        ) {
            instrumentation.runOnMainSync {
                keyboard.setKeyCharacterCase(characterCase)
            }
            samples[index] = measureNanoTime {
                capture(engine.convertForProbe(input, repository))
            }
        }

        repeat(iterations) { index ->
            if (index % 2 == 0) {
                measure(KeyCharacterCase.AS_DEFINED, asDefinedNanos, index) {
                    asDefinedCandidates = it
                }
                measure(KeyCharacterCase.UPPERCASE, uppercaseNanos, index) {
                    uppercaseCandidates = it
                }
            } else {
                measure(KeyCharacterCase.UPPERCASE, uppercaseNanos, index) {
                    uppercaseCandidates = it
                }
                measure(KeyCharacterCase.AS_DEFINED, asDefinedNanos, index) {
                    asDefinedCandidates = it
                }
            }
        }

        assertEquals(asDefinedCandidates, uppercaseCandidates)
        val asDefinedAverageUs = asDefinedNanos.average() / 1_000.0
        val uppercaseAverageUs = uppercaseNanos.average() / 1_000.0
        println(
            "KEY_CASE_CONVERSION_ISOLATION iterations=$iterations " +
                    "asDefinedAverageUs=$asDefinedAverageUs " +
                    "uppercaseAverageUs=$uppercaseAverageUs " +
                    "ratio=${uppercaseAverageUs / asDefinedAverageUs} " +
                    "candidateCount=${uppercaseCandidates.size}",
        )
    }

    @Test
    fun measureProductionDiConversionPerformance() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("conversionPerfProbe") == "true")

        val label = arguments.getString("conversionPerfLabel") ?: "instrumented"
        val warmup = arguments.getString("conversionPerfWarmup")?.toIntOrNull() ?: 2
        val iterations = arguments.getString("conversionPerfIterations")?.toIntOrNull() ?: 10
        val collectCandidateSegments =
            arguments.getString("conversionPerfCollectCandidateSegments") == "true"
        val inputs = listOf(
            "きょう",
            "きょうはいいてんきですね",
            "とうきょうとちよだく",
            "けいたいでにほんごをへんかんする",
            "わたしはきのうともだちとえきまえであいました",
            "このあぷりのへんかんこうほをこうそくにしたい",
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        lateinit var entryPoint: KanaKanjiEngineEntryPoint
        val entryPointResolveNs = measureNanoTime {
            entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                KanaKanjiEngineEntryPoint::class.java,
            )
        }
        lateinit var engine: KanaKanjiEngine
        lateinit var userDictionaryRepository: UserDictionaryRepository
        val productionSingletonResolveNs = measureNanoTime {
            engine = entryPoint.kanaKanjiEngine()
            userDictionaryRepository = entryPoint.userDictionaryRepository()
        }

        var lastCandidateSegments: Map<String, List<CandidateConversionSegment>> = emptyMap()
        suspend fun convert(input: String): List<Candidate> {
            val segmentCollector: MutableMap<String, List<CandidateConversionSegment>>? =
                if (collectCandidateSegments) LinkedHashMap() else null
            return engine.convertForProbe(
                input = input,
                userDictionaryRepository = userDictionaryRepository,
                candidateSegmentCollector = segmentCollector,
            ).also {
                if (segmentCollector != null) lastCandidateSegments = segmentCollector
            }
        }

        val firstResults = LinkedHashMap<String, List<Candidate>>()
        val firstConversionNs = inputs.associateWith { input ->
            measureNanoTime {
                firstResults[input] = convert(input)
            }
        }

        repeat(warmup) {
            inputs.forEach { input ->
                convert(input)
            }
        }
        Runtime.getRuntime().gc()
        SystemClock.sleep(500)
        val pssBeforeKb = Debug.getPss()

        val warmResults = LinkedHashMap<String, List<Candidate>>()
        val warmConversionNs = inputs.associateWith { input ->
            measureNanoTime {
                repeat(iterations) {
                    warmResults[input] = convert(input)
                }
            }
        }

        val continuousInput = "このあぷりのへんかんこうほをこうそくにしたい"
        var continuousResult: List<Candidate> = emptyList()
        val continuousElapsedNs = measureNanoTime {
            repeat(iterations) {
                continuousResult = convert(continuousInput)
            }
        }
        Runtime.getRuntime().gc()
        SystemClock.sleep(500)
        val pssAfterKb = Debug.getPss()

        val report = buildString {
            appendLine("label=$label")
            appendLine("warmup=$warmup")
            appendLine("iterations=$iterations")
            appendLine("collectCandidateSegments=$collectCandidateSegments")
            appendLine("segmentCandidateCount=${lastCandidateSegments.size}")
            appendLine("segmentNodeCount=${lastCandidateSegments.values.sumOf { it.size }}")
            appendLine("pssBeforeKb=$pssBeforeKb")
            appendLine("pssAfterKb=$pssAfterKb")
            appendLine("pssDeltaKb=${pssAfterKb - pssBeforeKb}")
            appendLine("entryPointResolveNs=$entryPointResolveNs")
            appendLine("productionSingletonResolveNs=$productionSingletonResolveNs")
            appendLine("fingerprint=${warmResults.fingerprint()}")
            appendLine("firstConversionUs")
            firstConversionNs.forEach { (input, elapsedNs) ->
                appendLine("$input\t${elapsedNs / 1_000.0}")
            }
            appendLine("warmAvgConversionUs")
            warmConversionNs.forEach { (input, elapsedNs) ->
                appendLine("$input\t${elapsedNs / iterations / 1_000.0}")
            }
            appendLine("sameInputContinuousAvgUs")
            appendLine("$continuousInput\t${continuousElapsedNs / iterations / 1_000.0}")
            appendLine("firstCandidates")
            firstResults.forEach { (input, candidates) ->
                appendLine("$input\t${candidates.take(12).joinToString(" / ") { "${it.string}:${it.score}" }}")
            }
            appendLine("warmCandidates")
            warmResults.forEach { (input, candidates) ->
                appendLine("$input\t${candidates.take(12).joinToString(" / ") { "${it.string}:${it.score}" }}")
            }
            appendLine("sameInputContinuousCandidates")
            appendLine("$continuousInput\t${continuousResult.take(12).joinToString(" / ") { "${it.string}:${it.score}" }}")
        }

        val outputDir = File(context.filesDir, "conversion-perf").apply { mkdirs() }
        File(outputDir, "$label.txt").writeText(report)
        println(report)
    }

    @Test
    fun measureCandidateOrderScalePerformance() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("candidateOrderScaleProbe") == "true")

        val scale = arguments.getString("candidateOrderScale")?.toIntOrNull() ?: 100
        val conversionWarmup =
            arguments.getString("candidateOrderConversionWarmup")?.toIntOrNull() ?: 10
        val conversionIterations =
            arguments.getString("candidateOrderConversionIterations")?.toIntOrNull() ?: 100
        val sortIterations =
            arguments.getString("candidateOrderSortIterations")?.toIntOrNull() ?: 10_000
        val candidatesPerGroup =
            arguments.getString("candidateOrderCandidatesPerGroup")?.toIntOrNull() ?: 3
        require(scale in 1..10_000)
        require(candidatesPerGroup in 3..200)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val input = "ひを"
        lateinit var engine: KanaKanjiEngine
        lateinit var userDictionaryRepository: UserDictionaryRepository

        suspend fun convertWithSegments(): Pair<
            List<Candidate>,
            Map<String, List<CandidateConversionSegment>>,
        > {
            val segments = LinkedHashMap<String, List<CandidateConversionSegment>>()
            val candidates = engine.convertForProbe(
                input = input,
                userDictionaryRepository = userDictionaryRepository,
                candidateSegmentCollector = segments,
            )
            return candidates to segments
        }

        val memoryProbeCandidates = listOf(
            Candidate("日を", 1, 2u, 0),
            Candidate("火を", 1, 2u, 1),
            Candidate("陽を", 1, 2u, 2),
        )
        val memoryProbeSegments = mapOf(
            "日を" to listOf(
                CandidateConversionSegment(0, 1, "日"),
                CandidateConversionSegment(1, 2, "を"),
            ),
            "火を" to listOf(
                CandidateConversionSegment(0, 1, "火"),
                CandidateConversionSegment(1, 2, "を"),
            ),
            "陽を" to listOf(
                CandidateConversionSegment(0, 1, "陽"),
                CandidateConversionSegment(1, 2, "を"),
            ),
        )

        val databaseName = "candidate-order-scale-${scale}-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName,
        ).build()
        try {
            val dao = database.candidateOrderOverrideDao()
            val repository = CandidateOrderOverrideRepository(dao)

            val now = System.currentTimeMillis()
            val entityCount = scale * candidatesPerGroup
            val insertNs = measureNanoTime {
                val batch = ArrayList<CandidateOrderOverrideEntity>(10_000)
                suspend fun flushBatch() {
                    if (batch.isEmpty()) return
                    dao.insertAll(batch)
                    batch.clear()
                }
                for (groupIndex in 0 until scale) {
                    val suffix = groupIndex.toString().padStart(5, '0')
                    val groupInput = if (groupIndex == 0) "ひ" else "べんちまっぷ$suffix"
                    for (candidateIndex in 0 until candidatesPerGroup) {
                        val output = when {
                            groupIndex == 0 && candidateIndex == 0 -> "火"
                            groupIndex == 0 && candidateIndex == 1 -> "日"
                            groupIndex == 0 && candidateIndex == 2 -> "陽"
                            else -> "候補${candidateIndex.toString().padStart(3, '0')}-$suffix"
                        }
                        batch += CandidateOrderOverrideEntity(
                            input = groupInput,
                            scope = CandidateOrderScope.LEXICAL_UNIT.name,
                            candidate = output,
                            rank = candidateIndex + 1,
                            createdAt = now,
                            updatedAt = now,
                        )
                        if (batch.size >= 10_000) flushBatch()
                    }
                }
                flushBatch()
            }

            // Measure only the conversion-time lookup/cache allocation. The test database is
            // file-backed like production, and insertion pages are already present at baseline.
            dao.findByInputs(listOf("__measurement_warmup__"))
            forceGcForMeasurement()
            val baselinePssKb = Debug.getPss()
            val baselineHeapKb = usedJavaHeapKb()

            lateinit var firstOrdered: List<Candidate>
            val firstSnapshotAndSortNs = measureNanoTime {
                firstOrdered = repository.applyOrderFromSnapshot(
                    input = input,
                    candidates = memoryProbeCandidates,
                    candidateSegmentsByString = memoryProbeSegments,
                )
            }
            assertEquals("火を", firstOrdered.first().string)

            val ambiguousLongInputCandidates = listOf(
                Candidate("人多すぎ", 1, 6u, 0),
                Candidate("火と多すぎ", 1, 6u, 1),
            )
            val ambiguousLongInputSegments = mapOf(
                "人多すぎ" to listOf(
                    CandidateConversionSegment(0, 2, "人"),
                    CandidateConversionSegment(2, 6, "多すぎ"),
                ),
                "火と多すぎ" to listOf(
                    CandidateConversionSegment(0, 1, "火"),
                    CandidateConversionSegment(1, 2, "と"),
                    CandidateConversionSegment(2, 6, "多すぎ"),
                ),
            )
            val safelyOrdered = repository.applyOrderFromSnapshot(
                input = "ひとおおすぎ",
                candidates = ambiguousLongInputCandidates,
                candidateSegmentsByString = ambiguousLongInputSegments,
            )
            assertEquals("人多すぎ", safelyOrdered.first().string)

            forceGcForMeasurement()
            val snapshotPssKb = Debug.getPss()
            val snapshotHeapKb = usedJavaHeapKb()

            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                KanaKanjiEngineEntryPoint::class.java,
            )
            engine = entryPoint.kanaKanjiEngine()
            userDictionaryRepository = entryPoint.userDictionaryRepository()
            repeat(conversionWarmup) { convertWithSegments() }
            val (referenceCandidates, referenceSegments) = convertWithSegments()

            var lastSorted: List<Candidate> = emptyList()
            val sortElapsedNs = measureNanoTime {
                repeat(sortIterations) {
                    lastSorted = repository.applyOrderFromSnapshot(
                        input = input,
                        candidates = referenceCandidates,
                        candidateSegmentsByString = referenceSegments,
                    )
                }
            }
            assertEquals("火を", lastSorted.first().string)

            suspend fun convertAndOrder(): List<Candidate> {
                val (candidates, segments) = convertWithSegments()
                return repository.applyOrderFromSnapshot(
                    input = input,
                    candidates = candidates,
                    candidateSegmentsByString = segments,
                )
            }

            repeat(conversionWarmup) {
                convertWithSegments()
                convertAndOrder()
            }
            val conversionOnlyNs = LongArray(conversionIterations)
            val conversionAndOrderNs = LongArray(conversionIterations)
            repeat(conversionIterations) { index ->
                if (index % 2 == 0) {
                    conversionOnlyNs[index] = measureNanoTime { convertWithSegments() }
                    conversionAndOrderNs[index] = measureNanoTime { convertAndOrder() }
                } else {
                    conversionAndOrderNs[index] = measureNanoTime { convertAndOrder() }
                    conversionOnlyNs[index] = measureNanoTime { convertWithSegments() }
                }
            }

            val conversionOnlyAvgUs = conversionOnlyNs.average() / 1_000.0
            val conversionAndOrderAvgUs = conversionAndOrderNs.average() / 1_000.0
            val report = buildString {
                appendLine("scale=$scale")
                appendLine("overrideGroupCount=$scale")
                appendLine("overrideEntityCount=$entityCount")
                appendLine("scope=${CandidateOrderScope.LEXICAL_UNIT.name}")
                appendLine("ambiguousLongInputFirst=${safelyOrdered.first().string}")
                appendLine("candidatesPerGroup=$candidatesPerGroup")
                appendLine("databaseMode=file")
                appendLine("conversionWarmup=$conversionWarmup")
                appendLine("conversionIterations=$conversionIterations")
                appendLine("sortIterations=$sortIterations")
                appendLine("insertMs=${insertNs / 1_000_000.0}")
                appendLine("firstSnapshotAndSortMs=${firstSnapshotAndSortNs / 1_000_000.0}")
                appendLine("sortOnlyAvgUs=${sortElapsedNs / sortIterations / 1_000.0}")
                appendLine("conversionOnlyAvgUs=$conversionOnlyAvgUs")
                appendLine("conversionAndOrderAvgUs=$conversionAndOrderAvgUs")
                appendLine(
                    "conversionOrderDeltaUs=${conversionAndOrderAvgUs - conversionOnlyAvgUs}",
                )
                appendLine("baselinePssKb=$baselinePssKb")
                appendLine("snapshotPssKb=$snapshotPssKb")
                appendLine("snapshotPssDeltaKb=${snapshotPssKb - baselinePssKb}")
                appendLine("baselineHeapKb=$baselineHeapKb")
                appendLine("snapshotHeapKb=$snapshotHeapKb")
                appendLine("snapshotHeapDeltaKb=${snapshotHeapKb - baselineHeapKb}")
                appendLine("candidateCount=${referenceCandidates.size}")
                appendLine(
                    "segmentCandidateCount=${referenceSegments.size}",
                )
            }

            val outputDir = File(context.filesDir, "conversion-perf").apply { mkdirs() }
            File(outputDir, "candidate-order-scale-$scale.txt").writeText(report)
            println(report)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun forceGcForMeasurement() {
        repeat(3) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            SystemClock.sleep(300)
        }
    }

    private fun usedJavaHeapKb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024L
    }

    private suspend fun KanaKanjiEngine.convertForProbe(
        input: String,
        userDictionaryRepository: UserDictionaryRepository,
        candidateSegmentCollector: MutableMap<String, List<CandidateConversionSegment>>? = null,
    ): List<Candidate> =
        getCandidatesWithBunsetsuSeparation(
            input = input,
            n = 4,
            mozcUtPersonName = false,
            mozcUTPlaces = false,
            mozcUTWiki = false,
            mozcUTNeologd = false,
            mozcUTWeb = false,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = null,
            isOmissionSearchEnable = false,
            enableTypoCorrectionJapaneseFlick = false,
            enableTypoCorrectionQwertyEnglish = false,
            typoCorrectionOffsetScore = 3000,
            omissionSearchOffsetScore = 1900,
            beamWidth = 20,
            candidateSegmentCollector = candidateSegmentCollector,
        ).candidates

    private fun Map<String, List<Candidate>>.fingerprint(): String {
        val payload = entries.joinToString("\n") { (input, candidates) ->
            input + "\t" + candidates.joinToString("\u001f") {
                listOf(
                    it.string,
                    it.type.toString(),
                    it.length.toString(),
                    it.score.toString(),
                    it.yomi.orEmpty(),
                    it.leftId?.toString().orEmpty(),
                    it.rightId?.toString().orEmpty(),
                ).joinToString("\u001e")
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
