package com.kazumaproject.markdownhelperkeyboard.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.ui.skin.PopupDirection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardSkinRendererTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private fun bitmap(drawable: Drawable, width: Int = 240, height: Int = 147): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(it))
        }
    }

    @Test fun defaultDelegatesToLegacyRenderer() { assertNull(KeyboardSkinRegistry.find(KeyboardSkinId.DEFAULT)) }

    @Test fun measuredOpaqueColorsAndPressedStatesAreIndependentAcrossKeys() {
        val expected = mapOf(KeyboardSkinId.CUPERTINO_LIGHT to 0xffffffff.toInt(),
            KeyboardSkinId.CUPERTINO_DARK to 0xff3d3d3d.toInt())
        expected.forEach { (id, color) ->
            val skin = requireNotNull(KeyboardSkinRegistry.find(id))
            val first = skin.keyDrawable(resources)
            val second = requireNotNull(first.constantState).newDrawable().mutate()
            assertEquals(color, bitmap(first).getPixel(120, 73))
            assertEquals(0, bitmap(first).getPixel(0, 0))
            first.state = intArrayOf(android.R.attr.state_pressed)
            assertEquals(skin.palette.pressed, bitmap(first).getPixel(120, 73))
            assertEquals(color, bitmap(second).getPixel(120, 73))
        }
    }

    @Test fun longPressUsesMeasuredSelectionColorAndDirectionalPointerFacesAnchor() {
        for (id in listOf(KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK)) {
            val skin = requireNotNull(KeyboardSkinRegistry.find(id))
            val selected = bitmap(skin.popupDrawable(resources, PopupDirection.CENTER, true))
            assertEquals(if (id == KeyboardSkinId.CUPERTINO_LIGHT) 0xff0088ff.toInt() else 0xff0091ff.toInt(), selected.getPixel(120, 73))
            val up = bitmap(skin.popupDrawable(resources, PopupDirection.TOP), 240, 210)
            assertEquals(0, up.getPixel(10, 190))
            assertTrue(android.graphics.Color.alpha(up.getPixel(120, 190)) > 250)
            val down = bitmap(skin.popupDrawable(resources, PopupDirection.BOTTOM), 240, 210)
            assertEquals(0, down.getPixel(10, 20))
            assertTrue(android.graphics.Color.alpha(down.getPixel(120, 20)) > 250)
        }
    }
}
