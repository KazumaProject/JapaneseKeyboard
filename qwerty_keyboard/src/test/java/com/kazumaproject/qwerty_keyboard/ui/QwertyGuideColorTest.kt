package com.kazumaproject.qwerty_keyboard.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.ContextThemeWrapper
import android.view.View
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.qwerty_keyboard.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "notnight")
class QwertyGuideColorTest {
    @Test
    fun feverColorsLetterAndNumberGuidesAndSwitchingSkinsRestoresTheirDefaultColor() {
        val defaultColor = defaultGuideColor()
        val feverColor = requireNotNull(KeyboardSkinRegistry.find(KeyboardSkinId.FEVER)).palette.text
        assertRenderedGuideColors(
            KeyboardSkinId.FEVER to feverColor,
            KeyboardSkinId.CUPERTINO_LIGHT to defaultColor,
            KeyboardSkinId.FEVER to feverColor,
            KeyboardSkinId.DEFAULT to defaultColor,
        )
    }

    @Test
    @Config(qualifiers = "night")
    fun eInkKeepsGuidesBlackInDarkModeAndRestoresDefaultColorsWhenDeselected() {
        assertRenderedGuideColors(
            KeyboardSkinId.E_INK to Color.BLACK,
            KeyboardSkinId.DEFAULT to defaultGuideColor(),
        )
    }

    private fun defaultGuideColor() =
        ContextCompat.getColor(RuntimeEnvironment.getApplication(), com.kazumaproject.core.R.color.keyboard_icon_color)

    private fun assertRenderedGuideColors(vararg expectedColors: Pair<KeyboardSkinId, Int>) {
        val context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        val keyboard = QWERTYKeyboardView(context).apply {
            updateNumberKeyState(true)
            updateSymbolKeymapState(true)
            setNumberKeyFlickUpChars(mapOf("1" to "#"))
            setNumberKeyFlickDownChars(mapOf("1" to "+"))
            setFlickUpDetectionEnabled(true)
            setFlickDownDetectionEnabled(true)
        }
        val letter = keyboard.findViewById<QWERTYButton>(R.id.key_a)
        val number = keyboard.findViewById<QWERTYButton>(R.id.key_1)

        for ((skin, expectedColor) in expectedColors) {
            val palette = KeyboardSkinRegistry.find(skin)?.palette
            keyboard.applyKeyboardTheme(
                themeMode = if (palette == null) "default" else "custom",
                currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK,
                isDynamicColorEnabled = false,
                customBgColor = palette?.background ?: Color.WHITE,
                customKeyColor = palette?.key ?: Color.WHITE,
                customSpecialKeyColor = palette?.specialKey ?: Color.WHITE,
                customKeyTextColor = palette?.text ?: Color.BLACK,
                customSpecialKeyTextColor = palette?.specialText ?: Color.BLACK,
                liquidGlassEnable = false,
                customBorderEnable = false,
                customBorderColor = Color.BLACK,
                liquidGlassKeyAlphaEnable = 255,
                borderWidth = 1,
                skinId = skin
            )
            keyboard.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
            )
            keyboard.layout(0, 0, 1080, 500)

            assertEquals("$skin should color the letter guide", expectedColor, drawnTextColors(letter)["@"])
            val numberColors = drawnTextColors(number)
            assertEquals("$skin should color the flick-up guide", expectedColor, numberColors["#"])
            assertEquals("$skin should color the flick-down guide", expectedColor, numberColors["+"])
        }
    }

    private fun drawnTextColors(button: QWERTYButton): Map<String, Int> {
        val colors = mutableMapOf<String, Int>()
        button.draw(object : Canvas() {
            override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                colors[text] = paint.color
            }
        })
        return colors
    }
}
