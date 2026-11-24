package com.kazumaproject.core.ui.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
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
class EInkSkinTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val skin = requireNotNull(KeyboardSkinRegistry.find(KeyboardSkinId.E_INK))

    @Test
    fun keysKeepTheirOutlineAndLegibleLabelsWhenPressed() {
        val character = skin.keyDrawable(resources, qwerty = true)
        val modifier = skin.keyDrawable(resources, role = SkinKeyRole.MODIFIER)

        assertEquals(Color.WHITE, drawn(character).getPixel(40, 20))
        assertEquals(Color.BLACK, drawn(character).getPixel(40, 0))
        assertEquals(skin.palette.specialKey, drawn(modifier).getPixel(40, 20))

        character.state = intArrayOf(android.R.attr.state_pressed)
        assertEquals(skin.palette.pressed, drawn(character).getPixel(40, 20))
        assertTrue(ColorUtils.calculateContrast(skin.palette.text, skin.palette.pressed) >= 7)

        character.state = intArrayOf()
        assertEquals(Color.WHITE, drawn(character).getPixel(40, 20))
    }

    @Test
    fun guideSelectionInvertsTheLabelAndSurface() {
        val idle = drawn(skin.guideDrawable(resources, PopupDirection.LEFT, selected = false))
        val selected = drawn(skin.guideDrawable(resources, PopupDirection.CENTER, selected = true))

        assertEquals(Color.WHITE, idle.getPixel(40, 20))
        assertEquals(Color.BLACK, idle.getPixel(40, 0))
        assertEquals(Color.BLACK, selected.getPixel(40, 20))
        assertEquals(Color.WHITE, skin.palette.selectionText)
        assertEquals(0L, skin.popupReleaseDelayMillis)
        assertEquals(0L, skin.popupReleaseAnimationMillis)
    }

    @Test
    fun qwertyPreviewsFitAboveKeysAtBothScreenEdges() {
        for (keyLeft in listOf(0, 300)) {
            val preview = requireNotNull(skin.keyPreview(resources, 20, 36, keyLeft, 320, false))
            val popupLeft = keyLeft + preview.xOffset
            assertTrue(popupLeft >= 0)
            assertTrue(popupLeft + preview.width <= 320)
            assertTrue(preview.yOffset + 36 + preview.height < 0)
        }
    }

    private fun drawn(drawable: Drawable): Bitmap =
        Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, it.width, it.height)
            drawable.draw(Canvas(it))
        }
}
