package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import android.graphics.Color
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
}
