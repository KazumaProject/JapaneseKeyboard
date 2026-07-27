package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.gemma.GemmaTranslationManager
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaMediaType
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil
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
        val artifactDirectory = File(
            requireNotNull(context.getExternalFilesDir(null)),
            ARTIFACT_DIRECTORY_NAME,
        ).apply(File::mkdirs)
        resultFile.writeText("language\texpected\tstatus\tcandidates\traw\n")
        val requestedLanguages = InstrumentationRegistry.getArguments()
            .getString(LANGUAGE_FILTER_ARGUMENT)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            .orEmpty()
        val selectedCases = if (requestedLanguages.isEmpty()) {
            LANGUAGE_CASES
        } else {
            LANGUAGE_CASES.filter { case ->
                case.language.preferenceValue in requestedLanguages
            }
        }
        require(selectedCases.isNotEmpty()) {
            "No configured language matched $requestedLanguages"
        }

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

            val results = selectedCases.map { case ->
                val imageFile = File(
                    artifactDirectory,
                    "gemma_handwriting_language_${case.language.preferenceValue}.png",
                )
                try {
                    HandwritingBitmapExporter.writePng(
                        strokes = captureHandwritingStyleSampleThroughProductionView(case.sample),
                        target = imageFile,
                    )
                    val raw = withTimeout(PER_LANGUAGE_TIMEOUT_MS) {
                        manager.runMediaPrompt(
                            prompt = GemmaHandwritingPrompt.build(case.language),
                            mediaPath = imageFile.absolutePath,
                            mediaType = GemmaMediaType.IMAGE,
                        )
                    }
                    val candidates = GemmaHandwritingPrompt.parseCandidates(
                        raw = raw,
                        language = case.language,
                    )
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
                }.also { result ->
                    val line = result.toTsv()
                    resultFile.appendText("$line\n")
                    println("$RESULT_LOG_PREFIX$line")
                }
            }

            assertEquals(selectedCases.size, results.size)
            val failures = results.filter { result -> result.status != "PASS" }
            val minimumPassCount = ceil(results.size * MINIMUM_PASS_RATE).toInt()
            assertTrue(
                "Gemma passed fewer than $minimumPassCount/${results.size} languages; failures: " +
                    failures.joinToString { result ->
                        "${result.case.language.preferenceValue}=${result.candidates}"
                    } +
                    "; see ${resultFile.absolutePath}",
                results.count { result -> result.status == "PASS" } >= minimumPassCount,
            )
        } finally {
            manager.disable()
            AppPreference.gemma_translation_model_path_preference = originalPath
            AppPreference.enable_gemma_translation_preference = originalEnabled
            println("20-language handwriting result file: ${resultFile.absolutePath}")
        }
    }

    /**
     * The cursive glyph is reduced to centerlines, traced into pen strokes, sent to the real
     * handwriting View as MotionEvents, and then exported by the production bitmap exporter.
     */
    private fun captureHandwritingStyleSampleThroughProductionView(
        text: String,
    ): List<HandwritingStroke> {
        val bitmap = createHandwritingStyleSample(text)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()

        val skeleton = thinToCenterlines(
            pixels = pixels,
            width = PHYSICAL_VIEW_WIDTH,
            height = PHYSICAL_VIEW_HEIGHT,
        )
        val rasterStrokes = traceCenterlines(
            skeleton = skeleton,
            width = PHYSICAL_VIEW_WIDTH,
            height = PHYSICAL_VIEW_HEIGHT,
        )
        check(rasterStrokes.isNotEmpty()) { "No rendered ink for sample: $text" }

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = HandwritingStrokeStore()
        instrumentation.runOnMainSync {
            val view = HandwritingCanvasView(instrumentation.targetContext).apply {
                bindStore(store)
                measure(exactly(PHYSICAL_VIEW_WIDTH), exactly(PHYSICAL_VIEW_HEIGHT))
                layout(0, 0, PHYSICAL_VIEW_WIDTH, PHYSICAL_VIEW_HEIGHT)
            }
            var eventTime = SystemClock.uptimeMillis()
            rasterStrokes.forEach { points ->
                val downTime = eventTime
                val first = points.first()
                dispatch(view, downTime, eventTime, MotionEvent.ACTION_DOWN, first.x, first.y)
                points.drop(1).forEach { point ->
                    eventTime += MOTION_EVENT_INTERVAL_MS
                    dispatch(
                        view,
                        downTime,
                        eventTime,
                        MotionEvent.ACTION_MOVE,
                        point.x,
                        point.y,
                    )
                }
                eventTime += MOTION_EVENT_INTERVAL_MS
                val last = points.last()
                dispatch(view, downTime, eventTime, MotionEvent.ACTION_UP, last.x, last.y)
                eventTime += MOTION_EVENT_INTERVAL_MS
            }
        }
        return store.strokes
    }

    private fun thinToCenterlines(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): BooleanArray {
        val ink = BooleanArray(pixels.size) { index ->
            Color.red(pixels[index]) < INK_THRESHOLD
        }
        var changed: Boolean
        var iteration = 0
        do {
            changed = false
            for (phase in 0..1) {
                val removals = ArrayList<Int>()
                for (y in 1 until height - 1) {
                    for (x in 1 until width - 1) {
                        val index = y * width + x
                        if (!ink[index]) continue
                        val north = ink[index - width]
                        val northEast = ink[index - width + 1]
                        val east = ink[index + 1]
                        val southEast = ink[index + width + 1]
                        val south = ink[index + width]
                        val southWest = ink[index + width - 1]
                        val west = ink[index - 1]
                        val northWest = ink[index - width - 1]
                        val neighborCount =
                            north.toInt() +
                                northEast.toInt() +
                                east.toInt() +
                                southEast.toInt() +
                                south.toInt() +
                                southWest.toInt() +
                                west.toInt() +
                                northWest.toInt()
                        if (neighborCount !in 2..6) continue
                        val transitionCount =
                            (!north && northEast).toInt() +
                                (!northEast && east).toInt() +
                                (!east && southEast).toInt() +
                                (!southEast && south).toInt() +
                                (!south && southWest).toInt() +
                                (!southWest && west).toInt() +
                                (!west && northWest).toInt() +
                                (!northWest && north).toInt()
                        if (transitionCount != 1) continue
                        val keepForPhase = if (phase == 0) {
                            north && east && south || east && south && west
                        } else {
                            north && east && west || north && south && west
                        }
                        if (!keepForPhase) removals += index
                    }
                }
                if (removals.isNotEmpty()) {
                    removals.forEach { index -> ink[index] = false }
                    changed = true
                }
            }
            iteration += 1
        } while (changed && iteration < MAX_THINNING_ITERATIONS)
        return ink
    }

    private fun traceCenterlines(
        skeleton: BooleanArray,
        width: Int,
        height: Int,
    ): List<List<RasterPoint>> {
        val visitedEdges = HashSet<Long>()
        val result = mutableListOf<List<RasterPoint>>()

        fun neighbors(index: Int): List<Int> {
            val x = index % width
            val y = index / width
            return buildList(8) {
                for (deltaY in -1..1) {
                    for (deltaX in -1..1) {
                        if (deltaX == 0 && deltaY == 0) continue
                        val neighborX = x + deltaX
                        val neighborY = y + deltaY
                        if (
                            neighborX in 0 until width &&
                            neighborY in 0 until height
                        ) {
                            val neighbor = neighborY * width + neighborX
                            if (skeleton[neighbor]) add(neighbor)
                        }
                    }
                }
            }
        }

        fun edgeKey(first: Int, second: Int): Long {
            val lower = minOf(first, second)
            val upper = maxOf(first, second)
            return (lower.toLong() shl 32) or (upper.toLong() and 0xFFFF_FFFFL)
        }

        fun trace(start: Int, firstNeighbor: Int): List<Int> {
            val path = mutableListOf(start)
            var previous = start
            var current = firstNeighbor
            visitedEdges += edgeKey(previous, current)
            while (true) {
                path += current
                if (current == start) break
                val currentNeighbors = neighbors(current)
                if (currentNeighbors.size != 2) break
                val next = currentNeighbors.first { neighbor -> neighbor != previous }
                if (!visitedEdges.add(edgeKey(current, next))) break
                previous = current
                current = next
            }
            return path
        }

        skeleton.indices.forEach { index ->
            if (!skeleton[index]) return@forEach
            val currentNeighbors = neighbors(index)
            if (currentNeighbors.isEmpty()) {
                result += listOf(RasterPoint((index % width).toFloat(), (index / width).toFloat()))
            } else if (currentNeighbors.size != 2) {
                currentNeighbors.forEach { neighbor ->
                    if (edgeKey(index, neighbor) !in visitedEdges) {
                        result += trace(index, neighbor).toRasterPoints(width)
                    }
                }
            }
        }
        skeleton.indices.forEach { index ->
            if (!skeleton[index]) return@forEach
            neighbors(index).forEach { neighbor ->
                if (edgeKey(index, neighbor) !in visitedEdges) {
                    result += trace(index, neighbor).toRasterPoints(width)
                }
            }
        }
        return result
    }

    private fun List<Int>.toRasterPoints(width: Int): List<RasterPoint> {
        return map { index ->
            RasterPoint(
                x = (index % width).toFloat(),
                y = (index / width).toFloat(),
            )
        }
    }

    private fun Boolean.toInt(): Int = if (this) 1 else 0

    private fun createHandwritingStyleSample(text: String): Bitmap {
        val bitmap = Bitmap.createBitmap(
            PHYSICAL_VIEW_WIDTH,
            PHYSICAL_VIEW_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = INITIAL_TEXT_SIZE
            typeface = Typeface.create("cursive", Typeface.NORMAL)
            strokeWidth = 2f
        }
        val availableWidth = PHYSICAL_VIEW_WIDTH - HORIZONTAL_PADDING * 2f
        val measuredWidth = paint.measureText(text).coerceAtLeast(1f)
        if (measuredWidth > availableWidth) {
            paint.textSize *= availableWidth / measuredWidth
        }
        val metrics = paint.fontMetrics
        val baseline = PHYSICAL_VIEW_HEIGHT / 2f - (metrics.ascent + metrics.descent) / 2f
        val startX = (PHYSICAL_VIEW_WIDTH - paint.measureText(text)) / 2f
        canvas.save()
        canvas.rotate(-1.25f, PHYSICAL_VIEW_WIDTH / 2f, PHYSICAL_VIEW_HEIGHT / 2f)
        canvas.drawText(text, startX, baseline, paint)
        canvas.restore()
        return bitmap
    }

    private fun dispatch(
        view: View,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun exactly(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
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

    private data class RasterPoint(
        val x: Float,
        val y: Float,
    )

    private companion object {
        const val RESULT_FILE_NAME = "gemma_handwriting_20_language_results.tsv"
        const val ARTIFACT_DIRECTORY_NAME = "gemma_handwriting_20_language_images"
        const val RESULT_LOG_PREFIX = "HANDWRITING_20_LANGUAGE_RESULT\t"
        const val LANGUAGE_FILTER_ARGUMENT = "handwritingLanguages"
        const val PER_LANGUAGE_TIMEOUT_MS = 120_000L
        const val PHYSICAL_VIEW_WIDTH = 1_048
        const val PHYSICAL_VIEW_HEIGHT = 310
        const val HORIZONTAL_PADDING = 56
        const val INITIAL_TEXT_SIZE = 150f
        const val INK_THRESHOLD = 224
        const val MAX_THINNING_ITERATIONS = 64
        const val MOTION_EVENT_INTERVAL_MS = 1L
        const val MINIMUM_PASS_RATE = 0.90
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
