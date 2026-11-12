package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.core.domain.flick.FixedGestureSessionConfigSource
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.TfbiDiagonalRecognitionMode
import com.kazumaproject.custom_keyboard.data.BuiltInTwoStepKey
import com.kazumaproject.custom_keyboard.data.TfbiFlickRotation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BuiltInTwoStepControllerTest {
    @Test fun kanaVoicingAndRotationWorkInBothGuideModes() {
        for (popup in TfbiPopupPresentationMode.entries)
        for (mode in TfbiDiagonalRecognitionMode.entries)
        for (rotation in TfbiFlickRotation.entries) {
            Fixture(BuiltInTwoStepKey.HA, rotation, popup, mode).use { f ->
                val voicedY = if (rotation == TfbiFlickRotation.CLOCKWISE) 100f else 500f
                val semiVoicedY = if (rotation == TfbiFlickRotation.CLOCKWISE) 500f else 100f
                assertEquals('は', f.gesture())
                assertEquals('ひ', f.gesture(100f to 300f))
                assertEquals('び', f.gesture(100f to 300f, 100f to voicedY))
                assertEquals('ぴ', f.gesture(100f to 300f, 100f to semiVoicedY))
                assertTrue(f.outputs.all { it in listOf('は', 'ひ', 'び', 'ぴ') })
            }
        }
    }

    @Test fun holdingDoesNotChangeTheFixedVocabularyAndCancelDoesNotLeakToTheNextTap() {
        for (popup in TfbiPopupPresentationMode.entries) {
            Fixture(BuiltInTwoStepKey.KA, TfbiFlickRotation.DEFAULT, popup,
                TfbiDiagonalRecognitionMode.STABLE).use { f ->
                assertEquals('か', f.gesture(hold = true))
                assertEquals('ぎ', f.gesture(100f to 300f, 100f to 500f, hold = true))
                assertNull(f.gesture(100f to 300f, 100f to 500f, cancel = true))
                assertEquals('か', f.gesture())
            }
        }
    }

    private class Fixture(key: BuiltInTwoStepKey, rotation: TfbiFlickRotation,
                          popup: TfbiPopupPresentationMode, mode: TfbiDiagonalRecognitionMode) : AutoCloseable {
        private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        private val target = View(ContextThemeWrapper(activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar))
        val outputs = mutableListOf<Char>()
        private val controller = BuiltInTwoStepController(target.context,
            FixedGestureSessionConfigSource(GestureSessionConfig(settingsRevision = 0,
                flickSensitivity = 100, flickThresholdPx = 60f, longPressTimeoutMillis = 100,
                tfbiDiagonalRecognitionMode = mode)), key, rotation)
        init {
            activity.setContentView(target)
            target.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            target.layout(0, 0, 600, 600)
            controller.setPopupPresentationMode(popup)
            controller.listener = object : BuiltInTwoStepController.Listener {
                override fun onPress(character: Char) = Unit
                override fun onSelection(character: Char, isFlick: Boolean) = Unit
                override fun onCommit(character: Char, isFlick: Boolean) { outputs += character }
                override fun onCanceled() = Unit
            }
            controller.attach(target)
        }
        fun gesture(vararg moves: Pair<Float, Float>, hold: Boolean = false, cancel: Boolean = false): Char? {
            val before = outputs.size
            send(MotionEvent.ACTION_DOWN, 300f, 300f)
            if (hold) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            for ((x, y) in moves) send(MotionEvent.ACTION_MOVE, x, y)
            assertEquals("Input before release", before, outputs.size)
            val last = moves.lastOrNull() ?: (300f to 300f)
            send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, last.first, last.second)
            assertTrue(outputs.size - before <= 1)
            return outputs.drop(before).singleOrNull()
        }
        private fun send(action: Int, x: Float, y: Float) {
            val now = android.os.SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, action, x, y, 0)
            try { target.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        override fun close() { controller.cancel(); activity.finish() }
    }
}
