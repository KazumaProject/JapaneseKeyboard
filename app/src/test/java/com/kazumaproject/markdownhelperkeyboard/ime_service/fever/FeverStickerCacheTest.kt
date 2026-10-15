package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeverStickerCacheTest {
    @Test fun sixSourcesAreReusedAcrossAllAnimatedSizesAndReleasedOnClear() {
        val cache = FeverStickerCache(96f)
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { translate(150f, 150f) }
        try {
            for (kind in 0..5) cache.draw(canvas, kind, 40f, 255)
            val allocated = cache.bytes
            assertTrue("Only glyph ink and filter margin should be retained", allocated in 1 until 6 * 192 * 192 * 4)
            repeat(500) { frame ->
                cache.draw(canvas, frame % 6, 2f + frame * .13f, frame % 256)
            }
            assertEquals("Animation must not rasterize new font sizes or grow the cache", allocated, cache.bytes)
            cache.clear()
            assertEquals(0, cache.bytes)
        } finally { cache.clear(); bitmap.recycle() }
    }

    @Test fun replayingASourcePreservesAlphaWithoutChangingLaterDraws() {
        val cache = FeverStickerCache(96f)
        fun render(alpha: Int): Bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it); canvas.translate(100f, 100f)
            cache.draw(canvas, 1, 70f, alpha)
        }
        val full = render(255); val faded = render(64); val fullAgain = render(255)
        try {
            assertTrue(full.sameAs(fullAgain))
            assertFalse(full.sameAs(faded))
            var max = 0
            for (y in 0 until faded.height) for (x in 0 until faded.width)
                max = maxOf(max, android.graphics.Color.alpha(faded.getPixel(x, y)))
            assertTrue(max in 1..64)
        } finally { full.recycle(); faded.recycle(); fullAgain.recycle(); cache.clear() }
    }
}
