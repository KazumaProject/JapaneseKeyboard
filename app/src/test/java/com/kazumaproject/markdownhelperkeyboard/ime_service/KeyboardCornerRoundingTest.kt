package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardCornerRoundingTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test fun classicBackgroundRoundingPreservesItsGradient() {
        val background = classicBackground()
        val colors = background.colors!!.clone()

        val result = applyClassicKeyboardCornerRounding(
            drawable = background,
            rounded = true,
            radiusDp = 12,
            density = 2f,
            topLeft = true,
            topRight = true,
            bottomRight = true,
            bottomLeft = true,
        )

        assertSame(background, result)
        assertArrayEquals(FloatArray(8) { 24f }, background.cornerRadii, 0f)
        assertArrayEquals(colors, background.colors)
    }

    @Test fun disablingRoundingClearsEveryCorner() {
        val background = classicBackground()
        background.cornerRadius = 18f

        applyClassicKeyboardCornerRounding(
            drawable = background,
            rounded = false,
            radiusDp = 32,
            density = 2f,
            topLeft = true,
            topRight = true,
            bottomRight = true,
            bottomLeft = true,
        )

        assertArrayEquals(FloatArray(8), background.cornerRadii, 0f)
    }

    @Test fun zeroRadiusRemainsSquareWhenRoundingIsEnabled() {
        val background = classicBackground()

        applyClassicKeyboardCornerRounding(
            drawable = background,
            rounded = true,
            radiusDp = 0,
            density = 2f,
            topLeft = true,
            topRight = true,
            bottomRight = true,
            bottomLeft = true,
        )

        assertArrayEquals(FloatArray(8), background.cornerRadii, 0f)
    }

    @Test fun onlySelectedCornersReceiveTheConfiguredRadius() {
        val background = classicBackground()

        applyClassicKeyboardCornerRounding(
            drawable = background,
            rounded = true,
            radiusDp = 10,
            density = 3f,
            topLeft = true,
            topRight = false,
            bottomRight = true,
            bottomLeft = false,
        )

        assertArrayEquals(
            floatArrayOf(30f, 30f, 0f, 0f, 30f, 30f, 0f, 0f),
            background.cornerRadii,
            0f,
        )
    }

    @Test fun nonGradientSkinBackgroundIsLeftUntouched() {
        val drawable = android.graphics.drawable.ColorDrawable(0xff000000.toInt())
        val result = applyClassicKeyboardCornerRounding(
            drawable = drawable,
            rounded = true,
            radiusDp = 10,
            density = 2f,
            topLeft = true,
            topRight = true,
            bottomRight = true,
            bottomLeft = true,
        )

        assertSame(drawable, result)
        assertTrue(result is android.graphics.drawable.ColorDrawable)
    }

    private fun classicBackground() =
        KeyboardSkinRegistry.find(KeyboardSkinId.CUPERTINO_CLASSIC)!!
            .keyboardDrawable(context.resources) as GradientDrawable
}
