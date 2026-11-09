package com.kazumaproject.core.ui.input_mode_witch

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View.MeasureSpec
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.R
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputModeSwitchSkinHighlightTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun cupertinoSkinsHighlightTheActiveModeForTenKeyAndGojuon() {
        val skinIds = listOf(
            KeyboardSkinId.CUPERTINO_LIGHT,
            KeyboardSkinId.CUPERTINO_DARK,
            KeyboardSkinId.CUPERTINO_CLASSIC,
        )
        val modes = listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)

        for (skinId in skinIds) {
            val palette = requireNotNull(KeyboardSkinRegistry.find(skinId)).palette
            val selectedColor = palette.specialText
            val idleColor = ColorUtils.setAlphaComponent(selectedColor, 150)
            for (isGojuon in listOf(false, true)) {
                for (mode in modes) {
                    val bitmap = render(mode, isGojuon, selectedColor, idleColor)
                    val regions = listOf(.28f, .5f, .72f).map { center ->
                        RectF(bitmap.width * (center - .11f), bitmap.height * .28f,
                            bitmap.width * (center + .11f), bitmap.height * .72f)
                    }
                    val selectedIndex = modes.indexOf(mode)
                    regions.forEachIndexed { index, region ->
                        val selected = index == selectedIndex
                        val pixels = if (selected) {
                            countRgb(bitmap, region, selectedColor, minAlpha = 240, maxAlpha = 255)
                        } else {
                            countRgb(bitmap, region, idleColor, minAlpha = 1, maxAlpha = 220)
                        }
                        assertTrue(
                            "$skinId $mode gojuon=$isGojuon has no " +
                                "${if (selected) "selected" else "idle"} label in region $index",
                            pixels > 0,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun skinHighlightOverridesTheUniformImageTintAndDefaultRestoresVectorIcon() {
        val view = newView().apply {
            imageTintList = ColorStateList.valueOf(Color.GREEN)
            setInputMode(InputMode.ModeEnglish, isGojuon = false, useThreeStateKeyboard = true)
        }
        val selectedColor = Color.WHITE
        val idleColor = ColorUtils.setAlphaComponent(selectedColor, 150)

        view.setSkinModeLabelColors(selectedColor, idleColor)
        val skinned = render(view)
        assertTrue(countRgb(skinned, labelRegion(skinned, .5f), selectedColor, 240, 255) > 0)
        assertTrue(countRgb(skinned, labelRegion(skinned, .28f), idleColor, 1, 220) > 0)
        assertTrue("skin palette must override the uniform image tint", countRgb(skinned, labelRegion(skinned, .5f), Color.GREEN, 240, 255) == 0)

        view.setSkinModeLabelColors(null, null)
        assertTrue("default mode should restore the vector drawable", view.drawable != null)
    }

    @Test
    fun standardFontSkinsPreserveDefaultIconGeometryAtEveryKeySize() {
        for (gojuon in listOf(false, true)) for (threeState in listOf(false, true)) {
            for (mode in listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)) for (size in listOf(240 to 120, 90 to 180, 70 to 45)) {
                for (returnTarget in com.kazumaproject.core.domain.state.TwoStateNumberReturnTarget.values()) {
                    val view = newView().apply {
                        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                        setPadding(7, 9, 11, 13)
                        setInputMode(mode, gojuon, threeState, returnTarget)
                        measure(android.view.View.MeasureSpec.makeMeasureSpec(size.first, 1073741824),
                            android.view.View.MeasureSpec.makeMeasureSpec(size.second, 1073741824))
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
                                        Color.alpha(bitmap.getPixel(px,py)) >= threshold
                                    }
                                }
                            if (Color.alpha(actual.getPixel(x,y)) >= 80 && !hasInk(standard,1) ||
                                Color.alpha(standard.getPixel(x,y)) >= 128 && !hasInk(actual,1)) mismatches++
                        }
                        assertTrue("$skin mode=$mode gojuon=$gojuon three=$threeState size=$size changed glyph geometry: $mismatches pixels", mismatches == 0)
                        view.setSkinModeLabelColors(null, null)
                    }
                }
            }
        }
    }

    @Test
    fun tenKeyKanaReturnLabelIsLargerThanTheOriginalAndSmallerThanTheJapaneseModeLabel() {
        val view = newView().apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        view.setImageResource(R.drawable.input_mode_japanese_select_custom)
        val originalHeight = inkBounds(view).height()
        view.setInputMode(InputMode.ModeJapanese, isGojuon = false, useThreeStateKeyboard = false)
        val japaneseHeight = inkBounds(view).height()
        view.setInputMode(InputMode.ModeNumber, isGojuon = false, useThreeStateKeyboard = false)
        val kanaReturnHeight = inkBounds(view).height()

        assertTrue(
            "Kana return label is ${kanaReturnHeight}px tall, original is ${originalHeight}px, Japanese mode label is ${japaneseHeight}px",
            kanaReturnHeight > originalHeight && kanaReturnHeight < japaneseHeight,
        )
    }

    @Test
    fun tenKeyScaleGrowsFittedLabelsAndKeepsTheirInkInsideNarrowKeys() {
        val view = newView().apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setInputMode(InputMode.ModeJapanese, isGojuon = false, useThreeStateKeyboard = true)
            setPadding(2, 2, 2, 2)
            measure(MeasureSpec.makeMeasureSpec(26, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(32, MeasureSpec.EXACTLY))
            layout(0, 0, 26, 32)
        }

        val initial = inkBounds(view)
        view.labelScale = .6f
        assertTrue(inkBounds(view).width() < initial.width())
        view.labelScale = 1.5f
        val large = inkBounds(view)
        assertTrue("The label must grow even if the base image already fills the key", large.width() > initial.width())
        assertTrue("The visible glyphs must fit inside the padding", Rect(3, 3, 23, 29).contains(large))
        view.labelScale = 1f
        val restored = inkBounds(view)
        assertEquals(initial.width(), restored.width())
        // Antialiasing can shift the threshold edge by a pixel after rasterizing at another scale.
        assertEquals(initial.height().toFloat(), restored.height().toFloat(), 1f)
    }

    private fun inkBounds(view: InputModeSwitch): Rect {
        val bitmap = render(view)
        try {
            val bounds = Rect()
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    if (Color.alpha(bitmap.getPixel(x, y)) >= 128) bounds.union(x, y, x + 1, y + 1)
                }
            }
            return bounds
        } finally {
            bitmap.recycle()
        }
    }

    private fun render(
        inputMode: InputMode,
        isGojuon: Boolean,
        selectedColor: Int,
        idleColor: Int,
    ): Bitmap {
        val view = newView().apply {
            setInputMode(inputMode, isGojuon, useThreeStateKeyboard = true)
            setKeyboardFont(KeyboardFontSnapshot(Typeface.create("serif", Typeface.NORMAL), 1))
            setSkinModeLabelColors(selectedColor, idleColor)
        }
        return render(view)
    }

    private fun newView() = InputModeSwitch(context, Robolectric.buildAttributeSet().build()).apply {
        background = null
        measure(
            android.view.View.MeasureSpec.makeMeasureSpec(240, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(120, android.view.View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, 240, 120)
    }

    private fun render(view: InputModeSwitch): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            view.draw(Canvas(bitmap))
        }

    private fun labelRegion(bitmap: Bitmap, center: Float): RectF = RectF(
        bitmap.width * (center - .11f), bitmap.height * .28f,
        bitmap.width * (center + .11f), bitmap.height * .72f,
    )

    private fun countRgb(bitmap: Bitmap, region: RectF, color: Int, minAlpha: Int, maxAlpha: Int): Int {
        var count = 0
        for (y in region.top.toInt() until region.bottom.toInt()) {
            for (x in region.left.toInt() until region.right.toInt()) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) == Color.red(color) &&
                    Color.green(pixel) == Color.green(color) &&
                    Color.blue(pixel) == Color.blue(color) &&
                    Color.alpha(pixel) in minAlpha..maxAlpha
                ) count++
            }
        }
        return count
    }
}
