package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import kotlin.math.ceil
import kotlin.math.hypot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaHandwritingKanjiInstrumentedTest {
    @Test(timeout = 360_000)
    fun recognizesConsecutiveJapaneseKanjiInOneImage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        AppPreference.init(context)
        val originalPath = AppPreference.gemma_translation_model_path_preference
        val originalEnabled = AppPreference.enable_gemma_translation_preference
        val originalLanguage = preferences.getString(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY,
            null,
        )
        val languageWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY
        )
        val originalInstruction = preferences.getString(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY,
            null,
        )
        val instructionWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY
        )
        val manager = GemmaTranslationManager(context, AppPreference, GemmaRuntimeClient(context))
        val imageFile = File(context.cacheDir, "gemma_handwriting_test_kanji_nihon.png")
        try {
            val model = manager.installedModels()
                .firstOrNull { installed -> installed.descriptor.supports(GemmaMediaType.IMAGE) }
            assumeTrue("Install an image-capable .litertlm model before this test", model != null)
            AppPreference.gemma_translation_model_path_preference =
                requireNotNull(model).file.absolutePath
            AppPreference.enable_gemma_translation_preference = true

            val additionalInstruction = """
                画像には日本語の漢字が2文字、左から右へ続けて手書きされています。
                2文字とも省略せず、文字の形と順序を正確に読み取ってください。
            """.trimIndent()
            AppPreference.gemma_handwriting_recognition_language_preference = "ja"
            AppPreference.gemma_handwriting_additional_instruction_preference =
                additionalInstruction
            AppPreference.init(context)
            val settings = GemmaHandwritingSettings.normalized(
                autoRecognitionDelayMs =
                    AppPreference.gemma_handwriting_auto_recognition_delay_preference,
                recognitionLanguage =
                    AppPreference.gemma_handwriting_recognition_language_preference,
                additionalInstruction =
                    AppPreference.gemma_handwriting_additional_instruction_preference,
            )
            assertEquals(GemmaHandwritingLanguage.JAPANESE, settings.recognitionLanguage)
            assertTrue(settings.recognitionPrompt.contains(additionalInstruction))

            HandwritingBitmapExporter.writePng(
                strokes = captureThroughProductionView(
                    physicalStrokes = listOf(
                        // 日
                        pixelStroke(300f, 60f, 300f, 250f),
                        pixelStroke(300f, 60f, 440f, 60f, 440f, 250f),
                        pixelStroke(300f, 155f, 440f, 155f),
                        pixelStroke(300f, 250f, 440f, 250f),
                        // 本
                        pixelStroke(550f, 95f, 760f, 95f),
                        pixelStroke(655f, 35f, 655f, 270f),
                        pixelStroke(650f, 125f, 550f, 225f),
                        pixelStroke(660f, 125f, 760f, 225f),
                        pixelStroke(585f, 210f, 725f, 210f),
                    ),
                ),
                target = imageFile,
            )

            assertTrue(
                "Gemma image runtime did not initialize",
                manager.initializeIfEnabled(forceReload = true),
            )
            val raw = manager.runMediaPrompt(
                prompt = settings.recognitionPrompt,
                mediaPath = imageFile.absolutePath,
                mediaType = GemmaMediaType.IMAGE,
            )
            val candidates = GemmaHandwritingPrompt.parseCandidates(
                raw = raw,
                language = settings.recognitionLanguage,
            )
            println("Consecutive Japanese handwriting candidates: $candidates")

            assertTrue(
                "Expected the consecutive Japanese kanji 日本, raw result: $raw",
                candidates.contains("日本"),
            )
        } finally {
            manager.disable()
            imageFile.delete()
            AppPreference.gemma_translation_model_path_preference = originalPath
            AppPreference.enable_gemma_translation_preference = originalEnabled
            preferences.edit().apply {
                if (languageWasPresent) {
                    putString(
                        AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY,
                        originalLanguage,
                    )
                } else {
                    remove(AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY)
                }
                if (instructionWasPresent) {
                    putString(
                        AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY,
                        originalInstruction,
                    )
                } else {
                    remove(AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY)
                }
            }.commit()
        }
    }

    @Test(timeout = 360_000)
    fun recognizesJapaneseKanjiUsingPersistedHandwritingPrompt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        AppPreference.init(context)
        val originalPath = AppPreference.gemma_translation_model_path_preference
        val originalEnabled = AppPreference.enable_gemma_translation_preference
        val originalLanguage = preferences.getString(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY,
            null,
        )
        val languageWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY
        )
        val originalInstruction = preferences.getString(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY,
            null,
        )
        val instructionWasPresent = preferences.contains(
            AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY
        )
        val manager = GemmaTranslationManager(context, AppPreference, GemmaRuntimeClient(context))
        val imageFile = File(context.cacheDir, "gemma_handwriting_test_kanji_yama.png")
        try {
            val model = manager.installedModels()
                .firstOrNull { installed -> installed.descriptor.supports(GemmaMediaType.IMAGE) }
            assumeTrue("Install an image-capable .litertlm model before this test", model != null)
            AppPreference.gemma_translation_model_path_preference =
                requireNotNull(model).file.absolutePath
            AppPreference.enable_gemma_translation_preference = true

            val additionalInstruction = """
                画像には日本語の漢字がちょうど1文字だけ手書きされています。
                文字の形を正確に読み取ってください。
            """.trimIndent()
            AppPreference.gemma_handwriting_recognition_language_preference = "ja"
            AppPreference.gemma_handwriting_additional_instruction_preference =
                additionalInstruction
            AppPreference.init(context)
            val settings = GemmaHandwritingSettings.normalized(
                autoRecognitionDelayMs =
                    AppPreference.gemma_handwriting_auto_recognition_delay_preference,
                recognitionLanguage =
                    AppPreference.gemma_handwriting_recognition_language_preference,
                additionalInstruction =
                    AppPreference.gemma_handwriting_additional_instruction_preference,
            )
            assertEquals(GemmaHandwritingLanguage.JAPANESE, settings.recognitionLanguage)
            assertTrue(settings.recognitionPrompt.contains(additionalInstruction))

            HandwritingBitmapExporter.writePng(
                strokes = listOf(
                    stroke(0.50f, 0.12f, 0.50f, 0.80f),
                    stroke(0.25f, 0.30f, 0.25f, 0.80f, 0.76f, 0.80f),
                    stroke(0.76f, 0.30f, 0.76f, 0.80f),
                ),
                target = imageFile,
            )

            assertTrue(
                "Gemma image runtime did not initialize",
                manager.initializeIfEnabled(forceReload = true),
            )
            val raw = manager.runMediaPrompt(
                prompt = settings.recognitionPrompt,
                mediaPath = imageFile.absolutePath,
                mediaType = GemmaMediaType.IMAGE,
            )
            val candidates = GemmaHandwritingPrompt.parseCandidates(
                raw = raw,
                language = settings.recognitionLanguage,
            )

            assertTrue(
                "Expected the Japanese kanji 山, raw result: $raw",
                candidates.contains("山"),
            )
        } finally {
            manager.disable()
            imageFile.delete()
            AppPreference.gemma_translation_model_path_preference = originalPath
            AppPreference.enable_gemma_translation_preference = originalEnabled
            preferences.edit().apply {
                if (languageWasPresent) {
                    putString(
                        AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY,
                        originalLanguage,
                    )
                } else {
                    remove(AppPreference.GEMMA_HANDWRITING_RECOGNITION_LANGUAGE_KEY)
                }
                if (instructionWasPresent) {
                    putString(
                        AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY,
                        originalInstruction,
                    )
                } else {
                    remove(AppPreference.GEMMA_HANDWRITING_ADDITIONAL_INSTRUCTION_KEY)
                }
            }.commit()
        }
    }

    private fun stroke(vararg coordinates: Float): HandwritingStroke {
        return HandwritingStroke(
            coordinates.toList().chunked(2).map { (x, y) -> HandwritingPoint(x, y) },
        )
    }

    private fun pixelStroke(vararg coordinates: Float): List<PixelPoint> {
        return coordinates.toList().chunked(2).map { (x, y) -> PixelPoint(x, y) }
    }

    private fun captureThroughProductionView(
        physicalStrokes: List<List<PixelPoint>>,
    ): List<HandwritingStroke> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = HandwritingStrokeStore()
        instrumentation.runOnMainSync {
            val view = HandwritingCanvasView(instrumentation.targetContext).apply {
                bindStore(store)
                measure(
                    View.MeasureSpec.makeMeasureSpec(
                        PHYSICAL_VIEW_WIDTH,
                        View.MeasureSpec.EXACTLY,
                    ),
                    View.MeasureSpec.makeMeasureSpec(
                        PHYSICAL_VIEW_HEIGHT,
                        View.MeasureSpec.EXACTLY,
                    ),
                )
                layout(0, 0, PHYSICAL_VIEW_WIDTH, PHYSICAL_VIEW_HEIGHT)
            }
            var eventTime = SystemClock.uptimeMillis()
            physicalStrokes.forEach { points ->
                val downTime = eventTime
                dispatch(view, downTime, eventTime, MotionEvent.ACTION_DOWN, points.first())
                points.zipWithNext().forEach { (start, end) ->
                    val steps = ceil(
                        hypot(end.x - start.x, end.y - start.y) / MOTION_SAMPLE_DISTANCE_PX,
                    ).toInt().coerceAtLeast(1)
                    for (step in 1..steps) {
                        val progress = step.toFloat() / steps
                        eventTime += MOTION_EVENT_INTERVAL_MS
                        dispatch(
                            view = view,
                            downTime = downTime,
                            eventTime = eventTime,
                            action = MotionEvent.ACTION_MOVE,
                            point = PixelPoint(
                                x = start.x + (end.x - start.x) * progress,
                                y = start.y + (end.y - start.y) * progress,
                            ),
                        )
                    }
                }
                eventTime += MOTION_EVENT_INTERVAL_MS
                dispatch(
                    view = view,
                    downTime = downTime,
                    eventTime = eventTime,
                    action = MotionEvent.ACTION_UP,
                    point = points.last(),
                )
                eventTime += MOTION_EVENT_INTERVAL_MS
            }
        }
        return store.strokes
    }

    private fun dispatch(
        view: View,
        downTime: Long,
        eventTime: Long,
        action: Int,
        point: PixelPoint,
    ) {
        MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            point.x,
            point.y,
            0,
        ).also { event ->
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private data class PixelPoint(
        val x: Float,
        val y: Float,
    )

    private companion object {
        const val PHYSICAL_VIEW_WIDTH = 1_048
        const val PHYSICAL_VIEW_HEIGHT = 310
        const val MOTION_SAMPLE_DISTANCE_PX = 8f
        const val MOTION_EVENT_INTERVAL_MS = 4L
    }
}
