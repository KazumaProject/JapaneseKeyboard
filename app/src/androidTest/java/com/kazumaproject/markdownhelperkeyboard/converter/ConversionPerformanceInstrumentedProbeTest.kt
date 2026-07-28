package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.data.KeyCharacterCase
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
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

        val firstResults = LinkedHashMap<String, List<Candidate>>()
        val firstConversionNs = inputs.associateWith { input ->
            measureNanoTime {
                firstResults[input] = engine.convertForProbe(input, userDictionaryRepository)
            }
        }

        repeat(warmup) {
            inputs.forEach { input ->
                engine.convertForProbe(input, userDictionaryRepository)
            }
        }

        val warmResults = LinkedHashMap<String, List<Candidate>>()
        val warmConversionNs = inputs.associateWith { input ->
            measureNanoTime {
                repeat(iterations) {
                    warmResults[input] = engine.convertForProbe(input, userDictionaryRepository)
                }
            }
        }

        val continuousInput = "このあぷりのへんかんこうほをこうそくにしたい"
        var continuousResult: List<Candidate> = emptyList()
        val continuousElapsedNs = measureNanoTime {
            repeat(iterations) {
                continuousResult = engine.convertForProbe(continuousInput, userDictionaryRepository)
            }
        }

        val report = buildString {
            appendLine("label=$label")
            appendLine("warmup=$warmup")
            appendLine("iterations=$iterations")
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

    private suspend fun KanaKanjiEngine.convertForProbe(
        input: String,
        userDictionaryRepository: UserDictionaryRepository,
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
