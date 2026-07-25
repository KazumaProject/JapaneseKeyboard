package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HandwritingBitmapExporterTest {
    @Test
    fun configuredPenColorIsRenderedOnContrastingBackground() {
        val color = 0xFF1E88E5.toInt()
        val bitmap = HandwritingBitmapExporter.createBitmap(
            strokes = listOf(horizontalStroke()),
            width = 200,
            height = 100,
            strokeColor = color,
        )

        try {
            assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
            assertEquals(color, bitmap.getPixel(100, 50))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun configuredPenSizeChangesRenderedStrokeThickness() {
        val thin = HandwritingBitmapExporter.createBitmap(
            strokes = listOf(horizontalStroke()),
            width = 200,
            height = 100,
            penSizeDp = GemmaHandwritingSettings.MIN_PEN_SIZE_DP,
        )
        val thick = HandwritingBitmapExporter.createBitmap(
            strokes = listOf(horizontalStroke()),
            width = 200,
            height = 100,
            penSizeDp = GemmaHandwritingSettings.MAX_PEN_SIZE_DP,
        )

        try {
            assertTrue(countNonWhitePixels(thick) > countNonWhitePixels(thin))
        } finally {
            thin.recycle()
            thick.recycle()
        }
    }

    @Test
    fun wideSourceViewPreservesPhysicalStrokeAspectRatio() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = HandwritingStrokeStore()
        val view = HandwritingCanvasView(context).apply {
            bindStore(store)
            measure(exactly(1_048), exactly(310))
            layout(0, 0, 1_048, 310)
        }

        dispatch(view, MotionEvent.ACTION_DOWN, 100f, 50f)
        dispatch(view, MotionEvent.ACTION_MOVE, 300f, 50f)
        dispatch(view, MotionEvent.ACTION_MOVE, 300f, 250f)
        dispatch(view, MotionEvent.ACTION_MOVE, 100f, 250f)
        dispatch(view, MotionEvent.ACTION_UP, 100f, 50f)

        val bitmap = HandwritingBitmapExporter.createBitmap(
            strokes = store.strokes,
            width = 768,
            height = 384,
        )
        try {
            val bounds = findInkBounds(bitmap)
            val aspectRatio = bounds.width.toFloat() / bounds.height
            assertTrue("Expected a square, actual ratio=$aspectRatio", aspectRatio in 0.95f..1.05f)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun shortTextIsNotUpscaledToFillTheImageHeight() {
        val bitmap = HandwritingBitmapExporter.createBitmap(
            strokes = listOf(
                HandwritingStroke(
                    points = listOf(
                        HandwritingPoint(0.10f, 0.10f),
                        HandwritingPoint(0.10f, 0.90f),
                    ),
                ),
            ),
            width = 768,
            height = 384,
        )

        try {
            val bounds = findInkBounds(bitmap)
            assertTrue(
                "Short handwriting should retain document-like vertical margins: ${bounds.height}px",
                bounds.height <= 250,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun horizontalStroke(): HandwritingStroke {
        return HandwritingStroke(
            points = listOf(
                HandwritingPoint(0.10f, 0.50f),
                HandwritingPoint(0.90f, 0.50f),
            ),
        )
    }

    private fun countNonWhitePixels(bitmap: android.graphics.Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) != Color.WHITE) count++
            }
        }
        return count
    }

    private fun dispatch(view: View, action: Int, x: Float, y: Float) {
        MotionEvent.obtain(1L, 2L, action, x, y, 0).also { event ->
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun exactly(size: Int): Int {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    }

    private fun findInkBounds(bitmap: android.graphics.Bitmap): InkBounds {
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) != Color.WHITE) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        check(maxX >= minX && maxY >= minY) { "Expected rendered ink." }
        return InkBounds(
            width = maxX - minX + 1,
            height = maxY - minY + 1,
        )
    }

    private data class InkBounds(
        val width: Int,
        val height: Int,
    )
}
