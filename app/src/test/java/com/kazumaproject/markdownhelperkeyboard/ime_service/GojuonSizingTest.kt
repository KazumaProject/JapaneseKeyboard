package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.gojuon_keyboard.GojuonKeyboardView
import com.kazumaproject.gojuon_keyboard.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GojuonSizingTest {
    private class Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val keyboard = GojuonKeyboardView(ContextThemeWrapper(activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar))
        init { activity.setContentView(keyboard); layout() }
        fun layout() {
            keyboard.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            keyboard.layout(0, 0, 1200, 600)
        }
        fun mode(mode: InputMode) {
            keyboard.currentInputMode.set(mode)
            keyboard.setInputModeSwitchState()
            shadowOf(Looper.getMainLooper()).idle()
            layout()
        }
        fun tap(id: Int, independent: Boolean = false) {
            keyboard.setIndependentMultiTouchEnabled(independent)
            val key = keyboard.findViewById<View>(id)
            val x = key.left + key.width / 2f
            val y = key.top + key.height / 2f
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 10, action, x, y, 0)
                keyboard.dispatchTouchEvent(event)
                event.recycle()
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
        fun textSize(id: Int): Float = keyboard.findViewById<TextView>(id).textSize /
            keyboard.resources.displayMetrics.scaledDensity
        fun margins(id: Int): List<Int> =
            (keyboard.findViewById<View>(id).layoutParams as ConstraintLayout.LayoutParams).let {
                listOf(it.marginStart, it.topMargin, it.marginEnd, it.bottomMargin)
            }
    }

    @Test fun modeSizesSurviveTapsShiftWidthChangesAndReset() {
        for (independent in listOf(false, true)) {
            val f = Fixture()
            f.keyboard.setModeLetterSizes(mapOf(InputMode.ModeJapanese to 22f,
                InputMode.ModeEnglish to 24f, InputMode.ModeNumber to 26f))
            for ((mode, size) in listOf(InputMode.ModeJapanese to 22f,
                InputMode.ModeEnglish to 24f, InputMode.ModeNumber to 26f)) {
                f.mode(mode)
                assertEquals(size, f.textSize(R.id.key_51), .01f)
                f.tap(R.id.key_51, independent)
                f.keyboard.resetLayout()
                assertEquals(size, f.textSize(R.id.key_51), .01f)
                f.tap(R.id.key_55, independent) // Full-width toggle in English / numbers.
                assertEquals(size, f.textSize(R.id.key_51), .01f)
            }
            f.mode(InputMode.ModeEnglish)
            f.tap(R.id.key_5, independent) // Shift / caps label restoration.
            assertEquals(24f, f.textSize(R.id.key_51), .01f)
            f.keyboard.setModeLetterSizes(emptyMap())
            assertEquals(18f, f.textSize(R.id.key_51), .01f)
            f.activity.finish()
        }
    }

    @Test fun scalingDoesNotAccumulateAcrossModeCyclesAndRetainsCursorGap() {
        val f = Fixture()
        val baseline = listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber).associateWith { mode ->
            f.mode(mode); f.margins(R.id.key_51)
        }
        for (scale in listOf(60, 140, 80, 100)) {
            f.keyboard.setKeySizeScale(scale, scale)
            val once = listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber).associateWith { mode ->
                f.mode(mode); f.margins(R.id.key_51)
            }
            repeat(3) {
                for (mode in listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)) {
                    f.mode(mode)
                    f.keyboard.resetLayout()
                    assertEquals(once.getValue(mode), f.margins(R.id.key_51))
                    val left = f.keyboard.findViewById<View>(R.id.key_left_cursor)
                    val right = f.keyboard.findViewById<View>(R.id.key_right_cursor)
                    assertEquals((4 * f.keyboard.resources.displayMetrics.density).toInt(),
                        right.left - left.right)
                    if (scale == 100) assertEquals(baseline.getValue(mode), f.margins(R.id.key_51))
                }
            }
        }
        f.activity.finish()
    }

    @Test fun normalAndSpecialKeysHaveIndependentSizesAndIcons() = verifyIndependentSpecialSizes()

    @Test @Config(qualifiers = "land")
    fun independentSpecialSizesAlsoApplyInLandscape() = verifyIndependentSpecialSizes()

    @Test @Config(qualifiers = "sw600dp")
    fun independentSpecialSizesAlsoApplyOnTablet() = verifyIndependentSpecialSizes()

    private fun verifyIndependentSpecialSizes() {
        val f = Fixture()
        val side = f.keyboard.findViewById<android.widget.ImageView>(R.id.key_delete)
        val originalWidth = side.width
        f.keyboard.setModeLetterSizes(mapOf(InputMode.ModeJapanese to 22f, InputMode.ModeEnglish to 24f))
        f.keyboard.setKeySizeScale(80, 75)
        f.layout()
        val normalMargins = f.margins(R.id.key_51)
        assertEquals(originalWidth, side.width)
        f.keyboard.setSpecialKeySizing(60, 70, 70, 14f)
        f.layout()
        assertTrue(kotlin.math.abs((originalWidth * .6f).toInt() - side.width) <= 1)
        assertEquals(normalMargins, f.margins(R.id.key_51))
        assertEquals(22f, f.textSize(R.id.key_51), .01f)
        assertEquals(14f, f.textSize(R.id.key_10), .01f)
        val bitmap = android.graphics.Bitmap.createBitmap(1200, 600, android.graphics.Bitmap.Config.ARGB_8888)
        f.keyboard.draw(android.graphics.Canvas(bitmap))
        assertEquals(android.widget.ImageView.ScaleType.MATRIX, side.scaleType)
        val values = FloatArray(9).also(side.imageMatrix::getValues)
        assertTrue(values[android.graphics.Matrix.MSCALE_X] > 0)
        assertTrue(values[android.graphics.Matrix.MSCALE_X] < 1f)
        f.mode(InputMode.ModeEnglish)
        assertEquals(24f, f.textSize(R.id.key_51), .01f)
        assertEquals(14f, f.textSize(R.id.key_55), .01f)
        f.keyboard.setSpecialKeySizing(100, 100, 100, 18f)
        f.layout()
        f.keyboard.draw(android.graphics.Canvas(bitmap))
        assertEquals(originalWidth, side.width)
        assertEquals(android.widget.ImageView.ScaleType.CENTER_INSIDE, side.scaleType)
        bitmap.recycle()
        f.activity.finish()
    }
}
