package com.kazumaproject.qwerty_keyboard.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.qwerty_keyboard.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QwertyGuideTextSizeInstrumentedTest {

    @Test
    fun guideTextSizeAppliesToLetterAndNumberKeyGuides() {
        runOnMain {
            val keyboard = createKeyboard()

            keyboard.setKeyMargins(symbolKeymapTextSizeSp = 15f)

            assertEquals(15f, keyboard.findViewById<QWERTYButton>(R.id.key_a).guideTextSizeSp)
            assertEquals(15f, keyboard.findViewById<QWERTYButton>(R.id.key_1).guideTextSizeSp)
            assertEquals(15f, keyboard.findViewById<QWERTYButton>(R.id.key_0).guideTextSizeSp)
        }
    }

    @Test
    fun guideTextSizeUsesQwertyButtonBounds() {
        runOnMain {
            val keyboard = createKeyboard()
            val numberKey = keyboard.findViewById<QWERTYButton>(R.id.key_1)

            keyboard.setKeyMargins(symbolKeymapTextSizeSp = 100f)
            assertEquals(24f, numberKey.guideTextSizeSp)

            keyboard.setKeyMargins(symbolKeymapTextSizeSp = 1f)
            assertEquals(4f, numberKey.guideTextSizeSp)
        }
    }

    private fun createKeyboard(): QWERTYKeyboardView {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val themedContext: Context = ContextThemeWrapper(
            targetContext,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        return QWERTYKeyboardView(themedContext).apply {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
            measure(widthSpec, heightSpec)
            layout(0, 0, 1080, 500)
        }
    }

    private fun runOnMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
