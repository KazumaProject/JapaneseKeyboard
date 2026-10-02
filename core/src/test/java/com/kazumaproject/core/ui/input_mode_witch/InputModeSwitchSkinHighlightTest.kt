package com.kazumaproject.core.ui.input_mode_witch

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
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
        assertTrue("skin mode should draw labels instead of the uniformly tinted vector", view.drawable == null)

        view.setSkinModeLabelColors(null, null)
        assertTrue("default mode should restore the vector drawable", view.drawable != null)
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
