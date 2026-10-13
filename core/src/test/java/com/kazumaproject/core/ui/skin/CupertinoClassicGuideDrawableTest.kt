package com.kazumaproject.core.ui.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CupertinoClassicGuideDrawableTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun guideCellsJoinAsACrossAndKeepASelectedCenter() {
        val width = 64
        val height = 48
        val bitmap = Bitmap.createBitmap(3 * width, 3 * height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val skin = checkNotNull(KeyboardSkinRegistry.find(KeyboardSkinId.CUPERTINO_CLASSIC))
        val cells = listOf(
            PopupDirection.CENTER to Rect(width, height, 2 * width, 2 * height),
            PopupDirection.LEFT to Rect(0, height, width, 2 * height),
            PopupDirection.TOP to Rect(width, 0, 2 * width, height),
            PopupDirection.RIGHT to Rect(2 * width, height, 3 * width, 2 * height),
            PopupDirection.BOTTOM to Rect(width, 2 * height, 2 * width, 3 * height),
        )

        cells.forEach { (direction, bounds) ->
            skin.guideDrawable(context.resources, direction, direction == PopupDirection.CENTER).apply {
                setBounds(bounds)
                draw(canvas)
            }
        }

        assertEquals(Color.TRANSPARENT, bitmap.getPixel(10, 10))
        assertTrue(Color.alpha(bitmap.getPixel(width - 1, height + height / 2)) > 0)
        assertTrue(Color.alpha(bitmap.getPixel(width, height + height / 2)) > 0)
        val selectedCenter = bitmap.getPixel(width + width / 2, height + height / 2)
        assertTrue(Color.blue(selectedCenter) > Color.red(selectedCenter))
    }
}
