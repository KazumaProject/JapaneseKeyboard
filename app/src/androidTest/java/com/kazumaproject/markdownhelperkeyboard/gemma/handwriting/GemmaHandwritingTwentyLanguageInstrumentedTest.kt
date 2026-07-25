package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaHandwritingTwentyLanguageInstrumentedTest {
    @Test(timeout = 1_800_000)
    fun evaluatesTwentyConfiguredLanguagesOnPhysicalDevice() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AppPreference.init(context)
        val originalPath = AppPreference.gemma_translation_model_path_preference
        val originalEnabled = AppPreference.enable_gemma_translation_preference
        val manager = GemmaTranslationManager(context, AppPreference, GemmaRuntimeClient(context))
        val resultFile = File(
            requireNotNull(context.getExternalFilesDir(null)),
            RESULT_FILE_NAME,
        )
        resultFile.writeText("language\texpected\tstatus\tcandidates\traw\n")

        try {
            val model = manager.installedModels()
                .firstOrNull { installed -> installed.descriptor.supports(GemmaMediaType.IMAGE) }
            assumeTrue("Install an image-capable .litertlm model before this test", model != null)
            AppPreference.gemma_translation_model_path_preference =
                requireNotNull(model).file.absolutePath
            AppPreference.enable_gemma_translation_preference = true
            assertTrue(
                "Gemma image runtime did not initialize",
                manager.initializeIfEnabled(forceReload = true),
            )

            val results = LANGUAGE_CASES.map { case ->
                val imageFile = File(
                    context.cacheDir,
                    "gemma_handwriting_language_${case.language.preferenceValue}.png",
                )
                try {
                    writeHandwritingStyleSample(case.sample, imageFile)
                    val raw = withTimeout(PER_LANGUAGE_TIMEOUT_MS) {
                        manager.runMediaPrompt(
                            prompt = GemmaHandwritingPrompt.build(case.language),
                            mediaPath = imageFile.absolutePath,
                            mediaType = GemmaMediaType.IMAGE,
                        )
                    }
                    val candidates = GemmaHandwritingPrompt.parseCandidates(raw)
                    val matched = candidates.any { candidate ->
                        normalize(candidate) == normalize(case.expected)
                    }
                    EvaluationResult(
                        case = case,
                        status = if (matched) "PASS" else "FAIL",
                        candidates = candidates,
                        raw = raw,
                    )
                } catch (error: TimeoutCancellationException) {
                    manager.cancelActiveTranslation()
                    EvaluationResult(case, "TIMEOUT", emptyList(), error.toString())
                } catch (error: Throwable) {
                    EvaluationResult(case, "ERROR", emptyList(), error.toString())
                } finally {
                    imageFile.delete()
                }.also { result ->
                    val line = result.toTsv()
                    resultFile.appendText("$line\n")
                    println("$RESULT_LOG_PREFIX$line")
                }
            }

            assertEquals(20, results.size)
            assertTrue(
                "Gemma did not recognize any language sample; see ${resultFile.absolutePath}",
                results.any { result -> result.status == "PASS" },
            )
        } finally {
            manager.disable()
            AppPreference.gemma_translation_model_path_preference = originalPath
            AppPreference.enable_gemma_translation_preference = originalEnabled
            println("20-language handwriting result file: ${resultFile.absolutePath}")
        }
    }

    private fun writeHandwritingStyleSample(text: String, target: File) {
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = INITIAL_TEXT_SIZE
            typeface = Typeface.create("cursive", Typeface.NORMAL)
            strokeWidth = 2f
        }
        val availableWidth = IMAGE_WIDTH - HORIZONTAL_PADDING * 2f
        val measuredWidth = paint.measureText(text).coerceAtLeast(1f)
        if (measuredWidth > availableWidth) {
            paint.textSize *= availableWidth / measuredWidth
        }
        val metrics = paint.fontMetrics
        val baseline = IMAGE_HEIGHT / 2f - (metrics.ascent + metrics.descent) / 2f
        val startX = (IMAGE_WIDTH - paint.measureText(text)) / 2f
        canvas.save()
        canvas.rotate(-1.25f, IMAGE_WIDTH / 2f, IMAGE_HEIGHT / 2f)
        canvas.drawText(text, startX, baseline, paint)
        canvas.restore()
        target.parentFile?.mkdirs()
        target.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, "")
            .trim('"', '\'', '`', '.', '。')
    }

    private data class LanguageCase(
        val language: GemmaHandwritingLanguage,
        val sample: String,
        val expected: String = sample,
    )

    private data class EvaluationResult(
        val case: LanguageCase,
        val status: String,
        val candidates: List<String>,
        val raw: String,
    ) {
        fun toTsv(): String {
            return listOf(
                case.language.preferenceValue,
                case.expected,
                status,
                candidates.joinToString(" | "),
                raw.replace('\t', ' ').replace('\n', ' '),
            ).joinToString("\t")
        }
    }

    private companion object {
        const val RESULT_FILE_NAME = "gemma_handwriting_20_language_results.tsv"
        const val RESULT_LOG_PREFIX = "HANDWRITING_20_LANGUAGE_RESULT\t"
        const val PER_LANGUAGE_TIMEOUT_MS = 120_000L
        const val IMAGE_WIDTH = 960
        const val IMAGE_HEIGHT = 320
        const val HORIZONTAL_PADDING = 56
        const val INITIAL_TEXT_SIZE = 150f
        val WHITESPACE = Regex("\\s+")

        val LANGUAGE_CASES = listOf(
            LanguageCase(GemmaHandwritingLanguage.ENGLISH, "hello"),
            LanguageCase(GemmaHandwritingLanguage.JAPANESE, "日本"),
            LanguageCase(GemmaHandwritingLanguage.KOREAN, "한국"),
            LanguageCase(GemmaHandwritingLanguage.CHINESE_SIMPLIFIED, "中国"),
            LanguageCase(GemmaHandwritingLanguage.CHINESE_TRADITIONAL, "臺灣"),
            LanguageCase(GemmaHandwritingLanguage.SPANISH, "hola"),
            LanguageCase(GemmaHandwritingLanguage.FRENCH, "été"),
            LanguageCase(GemmaHandwritingLanguage.GERMAN, "groß"),
            LanguageCase(GemmaHandwritingLanguage.ITALIAN, "ciao"),
            LanguageCase(GemmaHandwritingLanguage.PORTUGUESE, "olá"),
            LanguageCase(GemmaHandwritingLanguage.RUSSIAN, "привет"),
            LanguageCase(GemmaHandwritingLanguage.ARABIC, "مرحبا"),
            LanguageCase(GemmaHandwritingLanguage.HINDI, "नमस्ते"),
            LanguageCase(GemmaHandwritingLanguage.INDONESIAN, "halo"),
            LanguageCase(GemmaHandwritingLanguage.THAI, "ไทย"),
            LanguageCase(GemmaHandwritingLanguage.VIETNAMESE, "Việt"),
            LanguageCase(GemmaHandwritingLanguage.TURKISH, "Türk"),
            LanguageCase(GemmaHandwritingLanguage.POLISH, "Łódź"),
            LanguageCase(GemmaHandwritingLanguage.DUTCH, "hallo"),
            LanguageCase(GemmaHandwritingLanguage.UKRAINIAN, "привіт"),
        )
    }
}
