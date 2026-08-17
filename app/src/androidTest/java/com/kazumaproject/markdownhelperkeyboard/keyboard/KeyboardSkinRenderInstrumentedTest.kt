package com.kazumaproject.markdownhelperkeyboard.keyboard

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.data.keyboard.KeyboardElementRole
import com.kazumaproject.core.data.keyboard.KeyboardSkinId
import com.kazumaproject.core.data.keyboard.KeyboardSkinMotionMode
import com.kazumaproject.core.data.keyboard.KeyboardSkinPreviewView
import com.kazumaproject.core.data.keyboard.KeyboardSkinViewStyler
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class KeyboardSkinRenderInstrumentedTest {

    @Test
    fun allSkinsRenderAsDistinctPixel6Previews() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val outputDirectory = File(context.filesDir, OUTPUT_DIRECTORY).apply { mkdirs() }
        val hashes = linkedSetOf<Int>()

        KeyboardSkinId.entries.forEach { skin ->
            var bitmap: Bitmap? = null
            instrumentation.runOnMainSync {
                val preview = KeyboardSkinPreviewView(context).apply {
                    setSkin(skin, KeyboardSkinMotionMode.OFF)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
                    )
                    layout(0, 0, WIDTH_PX, HEIGHT_PX)
                }
                bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888)
                    .also { preview.draw(Canvas(it)) }
            }

            val rendered = checkNotNull(bitmap)
            val pixels = IntArray(WIDTH_PX * HEIGHT_PX)
            rendered.getPixels(pixels, 0, WIDTH_PX, 0, 0, WIDTH_PX, HEIGHT_PX)
            hashes += pixels.contentHashCode()
            val output = File(outputDirectory, "skin-${skin.preferenceValue}.png")
            FileOutputStream(output).use { stream ->
                assertTrue(rendered.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            assertTrue(output.length() > 8_000L)
            rendered.recycle()
        }

        assertEquals(KeyboardSkinId.entries.size, hashes.size)
        Log.i(TAG, "Rendered distinct skin previews to ${outputDirectory.absolutePath}")
    }

    @Test
    fun allSkinsRenderAsDistinctPixel6TenKeyViews() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val themedContext = ContextThemeWrapper(context, R.style.Theme_MarkdownKeyboard)
        val outputDirectory = File(context.filesDir, OUTPUT_DIRECTORY).apply { mkdirs() }
        val hashes = linkedSetOf<Int>()

        KeyboardSkinId.entries.forEach { skin ->
            var bitmap: Bitmap? = null
            instrumentation.runOnMainSync {
                val root = LayoutInflater.from(themedContext).inflate(R.layout.keyboard_settings, null)
                val tenKey = root.findViewById<TenKey>(R.id.keyboard_view).apply {
                    applyKeyboardTheme(
                        themeMode = "custom",
                        currentNightMode = Configuration.UI_MODE_NIGHT_NO,
                        isDynamicColorEnabled = false,
                        customBgColor = Color.rgb(225, 228, 234),
                        customKeyColor = Color.rgb(248, 249, 251),
                        customSpecialKeyColor = Color.rgb(207, 212, 220),
                        customKeyTextColor = Color.rgb(25, 28, 34),
                        customSpecialKeyTextColor = Color.rgb(25, 28, 34),
                        liquidGlassEnable = true,
                        customBorderEnable = true,
                        customBorderColor = Color.MAGENTA,
                        liquidGlassKeyAlphaEnable = 35,
                        borderWidth = 12,
                        keyboardSkin = skin.preferenceValue,
                        keyboardSkinMotion = KeyboardSkinMotionMode.OFF.preferenceValue,
                    )
                    measure(
                        View.MeasureSpec.makeMeasureSpec(TENKEY_WIDTH_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(TENKEY_HEIGHT_PX, View.MeasureSpec.EXACTLY),
                    )
                    layout(0, 0, TENKEY_WIDTH_PX, TENKEY_HEIGHT_PX)
                }
                bitmap = Bitmap.createBitmap(
                    TENKEY_WIDTH_PX,
                    TENKEY_HEIGHT_PX,
                    Bitmap.Config.ARGB_8888,
                ).also { tenKey.draw(Canvas(it)) }
            }

            val rendered = checkNotNull(bitmap)
            val pixels = IntArray(TENKEY_WIDTH_PX * TENKEY_HEIGHT_PX)
            rendered.getPixels(
                pixels,
                0,
                TENKEY_WIDTH_PX,
                0,
                0,
                TENKEY_WIDTH_PX,
                TENKEY_HEIGHT_PX,
            )
            hashes += pixels.contentHashCode()
            val output = File(outputDirectory, "tenkey-${skin.preferenceValue}.png")
            FileOutputStream(output).use { stream ->
                assertTrue(rendered.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            assertTrue(output.length() > 8_000L)
            rendered.recycle()
        }

        assertEquals(KeyboardSkinId.entries.size, hashes.size)
        Log.i(TAG, "Rendered distinct TenKey skins to ${outputDirectory.absolutePath}")
    }

    @Test
    fun tactileConceptSkinsHaveStrongPairwiseVisualDifferences() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val skins = listOf(
            KeyboardSkinId.SUMI_HANSHI,
            KeyboardSkinId.LETTERPRESS,
            KeyboardSkinId.PORCELAIN,
            KeyboardSkinId.URUSHI,
            KeyboardSkinId.CHALKBOARD,
            KeyboardSkinId.LINEN,
            KeyboardSkinId.MONOCHROME_LCD,
        )
        val rendered = linkedMapOf<KeyboardSkinId, IntArray>()

        skins.forEach { skin ->
            var bitmap: Bitmap? = null
            instrumentation.runOnMainSync {
                val preview = KeyboardSkinPreviewView(context).apply {
                    setSkin(skin, KeyboardSkinMotionMode.OFF)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
                    )
                    layout(0, 0, WIDTH_PX, HEIGHT_PX)
                }
                bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.ARGB_8888)
                    .also { preview.draw(Canvas(it)) }
            }
            val image = checkNotNull(bitmap)
            rendered[skin] = IntArray(WIDTH_PX * HEIGHT_PX).also { pixels ->
                image.getPixels(pixels, 0, WIDTH_PX, 0, 0, WIDTH_PX, HEIGHT_PX)
            }
            image.recycle()
        }

        skins.forEachIndexed { firstIndex, first ->
            for (secondIndex in firstIndex + 1 until skins.size) {
                val second = skins[secondIndex]
                val firstPixels = checkNotNull(rendered[first])
                val secondPixels = checkNotNull(rendered[second])
                var stronglyChanged = 0
                firstPixels.indices.forEach { pixelIndex ->
                    val firstColor = firstPixels[pixelIndex]
                    val secondColor = secondPixels[pixelIndex]
                    val rgbDistance =
                        abs(Color.red(firstColor) - Color.red(secondColor)) +
                            abs(Color.green(firstColor) - Color.green(secondColor)) +
                            abs(Color.blue(firstColor) - Color.blue(secondColor))
                    if (rgbDistance >= MIN_STRONG_RGB_DISTANCE) stronglyChanged += 1
                }
                val changedRatio = stronglyChanged.toFloat() / firstPixels.size
                assertTrue(
                    "$first and $second only differ strongly across $changedRatio of the preview",
                    changedRatio >= MIN_STRONGLY_CHANGED_RATIO,
                )
            }
        }
    }

    @Test
    fun reducedMotionKeepsAStateTransitionWhileOffRemovesAnimations() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var reducedHasAnimator = false
        var offHasAnimator = true
        var fullHasAnimator = false

        instrumentation.runOnMainSync {
            val key = View(context)
            KeyboardSkinViewStyler.applyKey(
                key,
                KeyboardSkinId.CUPERTINO,
                KeyboardElementRole.CHARACTER,
                KeyboardSkinMotionMode.REDUCED,
            )
            reducedHasAnimator = key.stateListAnimator != null

            KeyboardSkinViewStyler.applyKey(
                key,
                KeyboardSkinId.CUPERTINO,
                KeyboardElementRole.CHARACTER,
                KeyboardSkinMotionMode.OFF,
            )
            offHasAnimator = key.stateListAnimator != null

            KeyboardSkinViewStyler.applyKey(
                key,
                KeyboardSkinId.CUPERTINO,
                KeyboardElementRole.CHARACTER,
                KeyboardSkinMotionMode.FULL,
            )
            fullHasAnimator = key.stateListAnimator != null
        }

        assertTrue(reducedHasAnimator)
        assertTrue(!offHasAnimator)
        assertTrue(fullHasAnimator)
    }

    @Test
    fun flatChromeControlsNeverDrawKeycapGeometry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        KeyboardSkinId.entries.filterNot { it == KeyboardSkinId.DEFAULT }.forEach { skin ->
            var idlePixels = IntArray(0)
            var pressedPixels = IntArray(0)
            var hasStateAnimator = false
            var pressedAlpha = 1f
            var releasedAlpha = 0f
            var backgroundIsNull = false
            var geometryStayedFlat = false
            instrumentation.runOnMainSync {
                val control = View(context).apply {
                    isEnabled = true
                    KeyboardSkinViewStyler.applyFlatControl(
                        this,
                        skin,
                        KeyboardElementRole.TOOLBAR,
                    )
                    measure(
                        View.MeasureSpec.makeMeasureSpec(
                            FLAT_CONTROL_WIDTH_PX,
                            View.MeasureSpec.EXACTLY,
                        ),
                        View.MeasureSpec.makeMeasureSpec(
                            FLAT_CONTROL_HEIGHT_PX,
                            View.MeasureSpec.EXACTLY,
                        ),
                    )
                    layout(0, 0, FLAT_CONTROL_WIDTH_PX, FLAT_CONTROL_HEIGHT_PX)
                }
                hasStateAnimator = control.stateListAnimator != null
                backgroundIsNull = control.background == null
                idlePixels = renderPixels(control, FLAT_CONTROL_WIDTH_PX, FLAT_CONTROL_HEIGHT_PX)
                control.isPressed = true
                control.refreshDrawableState()
                control.stateListAnimator?.jumpToCurrentState()
                pressedAlpha = control.alpha
                pressedPixels = renderPixels(
                    control,
                    FLAT_CONTROL_WIDTH_PX,
                    FLAT_CONTROL_HEIGHT_PX,
                )
                geometryStayedFlat =
                    control.translationX == 0f &&
                        control.translationY == 0f &&
                        control.scaleX == 1f &&
                        control.scaleY == 1f &&
                        control.elevation == 0f
                control.isPressed = false
                control.refreshDrawableState()
                control.stateListAnimator?.jumpToCurrentState()
                releasedAlpha = control.alpha
            }

            assertTrue(
                "$skin must not draw a resting keycap",
                idlePixels.all { Color.alpha(it) == 0 },
            )
            assertTrue(
                "$skin must not draw pressed keycap geometry",
                pressedPixels.all { Color.alpha(it) == 0 },
            )
            assertTrue("$skin flat control background must stay absent", backgroundIsNull)
            assertTrue("$skin needs content-only pressed feedback", hasStateAnimator)
            assertEquals(FLAT_CONTROL_PRESSED_ALPHA, pressedAlpha, ALPHA_TOLERANCE)
            assertEquals(1f, releasedAlpha, ALPHA_TOLERANCE)
            assertTrue("$skin flat control must not move or lift", geometryStayedFlat)
        }
    }

    private fun renderPixels(view: View, width: Int, height: Int): IntArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return IntArray(width * height).also { pixels ->
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.recycle()
        }
    }

    companion object {
        private const val TAG = "KeyboardSkinRender"
        private const val OUTPUT_DIRECTORY = "keyboard-skin-render-report"
        private const val WIDTH_PX = 720
        private const val HEIGHT_PX = 420
        private const val TENKEY_WIDTH_PX = 1080
        private const val TENKEY_HEIGHT_PX = 760
        private const val MIN_STRONG_RGB_DISTANCE = 45
        private const val MIN_STRONGLY_CHANGED_RATIO = 0.60f
        private const val FLAT_CONTROL_WIDTH_PX = 240
        private const val FLAT_CONTROL_HEIGHT_PX = 72
        private const val FLAT_CONTROL_PRESSED_ALPHA = 0.72f
        private const val ALPHA_TOLERANCE = 0.01f
    }
}
