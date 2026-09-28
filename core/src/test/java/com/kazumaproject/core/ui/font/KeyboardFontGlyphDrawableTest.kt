package com.kazumaproject.core.ui.font

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.core.graphics.PathParser
import com.kazumaproject.core.R
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.ui.input_mode_witch.InputModeSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardFontGlyphDrawableTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val expectedLabels = mapOf(
        R.drawable.henkan to listOf("変換"),
        R.drawable.katakana to listOf("カタ"),
        R.drawable.input_mode_english_custom to listOf("ABC"),
        R.drawable.input_mode_japanese_select_custom to listOf("あいう"),
        R.drawable.input_mode_number_select_custom to listOf("123"),
        R.drawable.language_japanese_kana_24px to listOf("あ", "a"),
        R.drawable.language_japanese_kana_left_24px to listOf("あ"),
        R.drawable.language_japanese_kana_left_bold_24px to listOf("あ", "a"),
        R.drawable.language_japanese_kana_right_24px to listOf("a"),
        R.drawable.language_japanese_kana_right_bold_24px to listOf("あ", "a"),
        R.drawable.english_small to listOf("a/A"),
        R.drawable.kana_small to listOf("゛", "゜", "小", "大"),
        R.drawable.kana_small_custom to listOf("゛", "゜", "小", "大"),
        R.drawable.number_small to listOf("()", "[]"),
        R.drawable.number_small_flick_guide to listOf("(", ")", "[", "]"),
        R.drawable.custom_key_english_case_24 to listOf("a/A"),
        R.drawable.custom_key_kana_case_24 to listOf("゛", "゜", "小", "大"),
        R.drawable.symbol to listOf("§"),
        R.drawable.open_bracket to listOf("("),
        R.drawable.qwerty_number to listOf("123"),
        R.drawable.qwerty_symbol to listOf("#+="),
        R.drawable.live_conversion_24px to listOf("あ"),
        R.drawable.live_conversion_on_24px to listOf("あ"),
        R.drawable.emoji_symbols to listOf("〒", "♪", "&", "%"),
        R.drawable.question_mark_24dp to listOf("?"),
    )

    @Test
    fun allAffectedResourcesHaveFontAwareWrappersWithoutChangingIntrinsicSize() {
        assertEquals(25, expectedLabels.size)
        val custom = KeyboardFontSnapshot(Typeface.create("monospace", Typeface.NORMAL), 1)

        expectedLabels.forEach { (resourceId, expectedText) ->
            assertTrue("resource $resourceId is missing a glyph spec", KeyboardFontGlyphDrawable.isSupported(resourceId))
            val spec = checkNotNull(KeyboardFontGlyphDrawable.specs[resourceId])
            assertEquals("wrong labels for $resourceId", expectedText, spec.glyphs.map { it.text })
            val original = checkNotNull(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, resourceId))
            val wrapped = checkNotNull(KeyboardFontGlyphDrawable.create(context, resourceId, custom))
            assertEquals("width changed for $resourceId", original.intrinsicWidth, wrapped.intrinsicWidth)
            assertEquals("height changed for $resourceId", original.intrinsicHeight, wrapped.intrinsicHeight)

            val standard = checkNotNull(
                KeyboardFontGlyphDrawable.create(context, resourceId, KeyboardFontSnapshot(null, 2))
            )
            val standardBitmap = render(standard, original.intrinsicWidth, original.intrinsicHeight)
            val originalBitmap = render(original, original.intrinsicWidth, original.intrinsicHeight)
            assertTrue("standard artwork changed for $resourceId", standardBitmap.sameAs(originalBitmap))

            wrapped.setTint(Color.BLACK)
            val scale = 256f / maxOf(spec.viewportWidth, spec.viewportHeight)
            val bitmap = render(
                wrapped,
                (spec.viewportWidth * scale).toInt().coerceAtLeast(1),
                (spec.viewportHeight * scale).toInt().coerceAtLeast(1),
            )
            spec.glyphs.forEach { glyph ->
                val region = RectF(
                    glyph.bounds.left * bitmap.width,
                    glyph.bounds.top * bitmap.height,
                    glyph.bounds.right * bitmap.width,
                    glyph.bounds.bottom * bitmap.height,
                )
                val glyphInk = inkBounds(bitmap, region)
                assertTrue("glyph '${glyph.text}' was not rendered for $resourceId", glyphInk != null)
                val ink = checkNotNull(glyphInk)
                val relativeWidth = ink.width() / region.width()
                val relativeHeight = ink.height() / region.height()
                assertTrue(
                    "glyph '${glyph.text}' is too small in $resourceId: " +
                        "${relativeWidth}x$relativeHeight of its assigned region",
                    maxOf(relativeWidth, relativeHeight) >= 0.25f,
                )
                val antialiasAllowance = RectF(region).apply { inset(-2f, -2f) }
                assertTrue("glyph '${glyph.text}' exceeds its assigned box in $resourceId", antialiasAllowance.contains(ink))
            }
        }
    }

    @Test
    fun numberModeGlyphUsesTheOriginalKeyboardIconColor() {
        val spec = checkNotNull(
            KeyboardFontGlyphDrawable.specs[R.drawable.input_mode_number_select_custom]
        )

        assertEquals(
            androidx.core.content.ContextCompat.getColor(context, R.color.keyboard_icon_color),
            spec.defaultColor(context),
        )
    }

    @Test
    fun customKanaSmallArrowStaysBetweenTheSmallAndLargeLabels() {
        val spec = checkNotNull(KeyboardFontGlyphDrawable.specs[R.drawable.kana_small_custom])
        assertEquals(100f, spec.viewportWidth, 0f)
        assertEquals(100f, spec.viewportHeight, 0f)

        val decoration = spec.decorations.single()
        val path = checkNotNull(PathParser.createPathFromPathData(decoration.pathData))
        if (decoration.scaleX != 1f || decoration.scaleY != 1f ||
            decoration.translateX != 0f || decoration.translateY != 0f ||
            decoration.pivotX != 0f || decoration.pivotY != 0f
        ) {
            val tx = decoration.translateX + decoration.pivotX - decoration.scaleX * decoration.pivotX
            val ty = decoration.translateY + decoration.pivotY - decoration.scaleY * decoration.pivotY
            val matrix = Matrix().apply {
                setValues(
                    floatArrayOf(
                        decoration.scaleX, 0f, tx,
                        0f, decoration.scaleY, ty,
                        0f, 0f, 1f,
                    )
                )
            }
            path.transform(matrix)
        }
        val bounds = RectF().also { path.computeBounds(it, true) }

        assertTrue("custom kana arrow left its original central label area: $bounds", RectF(34f, 51f, 68f, 68f).contains(bounds))
    }

    @Test
    fun imageViewsReceiveIndependentDrawablesAndTintAlphaSurviveFontSwitches() {
        val localFont = KeyboardFontSnapshot(Typeface.create("monospace", Typeface.NORMAL), 4)
        val first = ImageView(context)
        val second = ImageView(context)
        KeyboardFontGlyphDrawable.setImageResource(first, com.kazumaproject.core.R.drawable.qwerty_symbol, localFont)
        KeyboardFontGlyphDrawable.setImageResource(second, com.kazumaproject.core.R.drawable.qwerty_symbol, localFont)
        assertNotSame(first.drawable, second.drawable)

        val drawable = first.drawable as KeyboardFontGlyphDrawable
        drawable.setTint(Color.MAGENTA)
        drawable.alpha = 128
        val custom = render(drawable, 290, 120)
        val coloredPixel = pixels(custom).firstOrNull { Color.alpha(it) > 0 }
        assertTrue("expected custom glyph pixels", coloredPixel != null)
        assertEquals(255, Color.red(checkNotNull(coloredPixel)))
        assertEquals(0, Color.green(checkNotNull(coloredPixel)))
        assertEquals(255, Color.blue(checkNotNull(coloredPixel)))
        assertTrue(Color.alpha(checkNotNull(coloredPixel)) <= 128)

        drawable.setTintList(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                intArrayOf(Color.RED, Color.BLUE),
            )
        )
        drawable.state = intArrayOf(android.R.attr.state_pressed)
        val pressedPixel = pixels(render(drawable, 290, 120)).firstOrNull { Color.alpha(it) > 0 }
        assertTrue("pressed tint did not render", pressedPixel != null)
        assertEquals(255, Color.red(checkNotNull(pressedPixel)))
        assertEquals(0, Color.green(checkNotNull(pressedPixel)))
        assertEquals(0, Color.blue(checkNotNull(pressedPixel)))

        (drawable as KeyboardFontAware).setKeyboardFont(KeyboardFontSnapshot(null, 5))
        val standard = render(drawable, 290, 120)
        assertTrue("standard vector should still draw", bitmapHasInk(standard))
        assertTrue("custom text and standard vector should differ", !custom.sameAs(standard))
    }

    @Test
    fun twoStateInputModeSwitchPreservesSelectedBoldLabel() {
        val japanese = renderInputModeSwitch(InputMode.ModeJapanese)
        val english = renderInputModeSwitch(InputMode.ModeEnglish)
        val leftKana = RectF(0.27f * japanese.width, 0.28f * japanese.height, 0.49f * japanese.width, 0.72f * japanese.height)
        val rightLatin = RectF(0.51f * japanese.width, 0.28f * japanese.height, 0.73f * japanese.width, 0.72f * japanese.height)

        assertTrue("selected kana label should be bold", countInk(japanese, leftKana) > countInk(english, leftKana))
        assertTrue("selected Latin label should be bold", countInk(english, rightLatin) > countInk(japanese, rightLatin))
    }

    private fun renderInputModeSwitch(inputMode: InputMode): Bitmap {
        val size = 120
        val view = InputModeSwitch(context, Robolectric.buildAttributeSet().build()).apply {
            background = null
            imageTintList = ColorStateList.valueOf(Color.BLACK)
            setInputMode(inputMode, isGojuon = false, useThreeStateKeyboard = false)
            setKeyboardFont(KeyboardFontSnapshot(Typeface.create("serif", Typeface.NORMAL), 10))
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, size, size)
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun countInk(bitmap: Bitmap, region: RectF): Int {
        var count = 0
        for (y in region.top.toInt() until region.bottom.toInt()) {
            for (x in region.left.toInt() until region.right.toInt()) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) count++
            }
        }
        return count
    }

    private fun render(drawable: android.graphics.drawable.Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun bitmapHasInk(bitmap: Bitmap): Boolean = pixels(bitmap).any { Color.alpha(it) > 0 }

    private fun inkBounds(bitmap: Bitmap, region: RectF): RectF? {
        val left = region.left.toInt().coerceIn(0, bitmap.width)
        val top = region.top.toInt().coerceIn(0, bitmap.height)
        val right = region.right.toInt().coerceIn(left, bitmap.width)
        val bottom = region.bottom.toInt().coerceIn(top, bitmap.height)
        var minX = right
        var minY = bottom
        var maxX = left - 1
        var maxY = top - 1
        for (y in top until bottom) {
            for (x in left until right) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
        return if (maxX < minX || maxY < minY) null else RectF(minX.toFloat(), minY.toFloat(), maxX + 1f, maxY + 1f)
    }

    private fun pixels(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }
}
