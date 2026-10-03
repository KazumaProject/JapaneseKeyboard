package com.kazumaproject.markdownhelperkeyboard.local_font

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.ui.input_mode_witch.InputModeSwitch
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class InputModeSwitchGeometryDeviceTest {
    @Test
    fun skinsPreserveDefaultIconGeometryWithStandardAndLocalFonts() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (font in listOf(null, localTypeface())) {
                for (gojuon in listOf(false, true)) for (threeState in listOf(false, true)) {
                    for (mode in listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)) for (size in listOf(240 to 120, 90 to 180, 70 to 45)) {
                        for (returnTarget in com.kazumaproject.core.domain.state.TwoStateNumberReturnTarget.values()) {
                            val view = newView().apply {
                                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                                setPadding(7, 9, 11, 13)
                                setKeyboardFont(com.kazumaproject.core.ui.font.KeyboardFontSnapshot(font, 1))
                                setInputMode(mode, gojuon, threeState, returnTarget)
                                measure(android.view.View.MeasureSpec.makeMeasureSpec(size.first, android.view.View.MeasureSpec.EXACTLY),
                                android.view.View.MeasureSpec.makeMeasureSpec(size.second, android.view.View.MeasureSpec.EXACTLY))
                                layout(0, 0, size.first, size.second)
                            }
                            val standard = render(view)
                            for (skin in listOf(KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK, KeyboardSkinId.CUPERTINO_CLASSIC)) {
                                val color = requireNotNull(KeyboardSkinRegistry.find(skin)).palette.specialText
                                view.setSkinModeLabelColors(color, ColorUtils.setAlphaComponent(color, 150))
                                val actual = render(view)
                                var mismatches = 0
                                for (y in 0 until actual.height) for (x in 0 until actual.width) {
                                    // Ignore faint anti-aliasing pixels affected by the idle-label alpha.
                                    fun hasInk(bitmap: Bitmap, threshold: Int): Boolean =
                                    ((y - 1).coerceAtLeast(0)..(y + 1).coerceAtMost(bitmap.height - 1)).any { py ->
                                        ((x - 1).coerceAtLeast(0)..(x + 1).coerceAtMost(bitmap.width - 1)).any { px ->
                                            Color.alpha(bitmap.getPixel(px, py)) >= threshold
                                        }
                                    }
                                    if (Color.alpha(actual.getPixel(x, y)) >= 80 && !hasInk(standard, 1) ||
                                    Color.alpha(standard.getPixel(x, y)) >= 128 && !hasInk(actual, 1)) mismatches++
                                }
                                assertTrue("$skin mode=$mode gojuon=$gojuon three=$threeState size=$size changed glyph geometry: $mismatches pixels", mismatches == 0)
                                view.setSkinModeLabelColors(null, null)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun localTypeface(): android.graphics.Typeface {
        val source = checkNotNull(java.io.File("/system/fonts").listFiles()).first {
            it.extension == "ttf" && it.name.startsWith("NotoSerif")
        }
        val target = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "mode-label-test.ttf")
        source.copyTo(target, overwrite = true)
        return try { android.graphics.Typeface.createFromFile(target) } finally { target.delete() }
    }

    private fun newView(): InputModeSwitch {
        val context = androidx.appcompat.view.ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().targetContext,
            com.kazumaproject.markdownhelperkeyboard.R.style.Theme_MarkdownKeyboard)
        val root = android.view.LayoutInflater.from(context).inflate(com.kazumaproject.markdownhelperkeyboard.R.layout.main_layout, null)
        return root.findViewById<InputModeSwitch>(com.kazumaproject.tenkey.R.id.key_switch_key_mode).apply { background = null }
    }
    private fun render(view: InputModeSwitch): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
}
