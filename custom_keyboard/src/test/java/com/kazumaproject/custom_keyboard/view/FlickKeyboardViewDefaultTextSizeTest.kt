package com.kazumaproject.custom_keyboard.view

import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewDefaultTextSizeTest {

    @Test
    fun defaultLayoutRendersStandardKeysAtTheReadableDefault() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar,
        )
        val keyboard = FlickKeyboardView(context)
        val layout = KeyboardDefaultLayouts.defaultLayout()
        val standardKeyLabel = layout.keys.first { !it.isSpecialKey }.label
        keyboard.setKeyboard(layout)
        keyboard.measure(exactly(1080), exactly(1000))
        keyboard.layout(0, 0, 1080, 1000)

        val standardKey = (0 until keyboard.childCount)
            .map(keyboard::getChildAt)
            .filterIsInstance<AutoSizeButton>()
            .first { it.text.toString() == standardKeyLabel }
        val expectedSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            20f,
            context.resources.displayMetrics,
        )

        assertEquals(expectedSizePx, standardKey.textSize, 0.01f)
    }

    private fun exactly(size: Int): Int =
        View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
