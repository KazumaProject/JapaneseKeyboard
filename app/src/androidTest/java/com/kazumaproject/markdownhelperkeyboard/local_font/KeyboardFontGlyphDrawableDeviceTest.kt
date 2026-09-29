package com.kazumaproject.markdownhelperkeyboard.local_font

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.ui.font.KeyboardFontGlyphDrawable
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardFontGlyphDrawableDeviceTest {
    @Test
    fun customKanaSmallArrowRemainsCenteredOnDeviceCanvas() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val drawable = checkNotNull(
            KeyboardFontGlyphDrawable.create(
                context,
                com.kazumaproject.core.R.drawable.kana_small_custom,
                KeyboardFontSnapshot(Typeface.DEFAULT, 1L),
            )
        )
        drawable.setTint(Color.BLACK)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, SIZE, SIZE)
        drawable.draw(Canvas(bitmap))

        assertTrue("arrow did not render in the original label gap", countInk(bitmap, 176, 220, 224, 252) > 0)
        assertTrue("arrow was displaced to the upper-left", countInk(bitmap, 24, 64, 68, 92) == 0)
    }

    private fun countInk(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) count++
            }
        }
        return count
    }

    private companion object {
        const val SIZE = 400
    }
}
