package com.kazumaproject.custom_keyboard.view

import android.content.Intent
import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.data.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomKeyboardNearestKeyInstrumentedTest {

    @Test
    fun spacerIsUntouchableByDefaultAndUsesNearestKeyWhenEnabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val activity = instrumentation.startActivitySync(Intent(
            instrumentation.context,
            SumireNearestKeyTestActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SumireNearestKeyTestActivity
        try {
            instrumentation.runOnMainSync {
                activity.configureCustomLayout(KeyHitTestMode.NEAREST_KEY_IN_KEY_CELLS)
            }
            instrumentation.waitForIdleSync()

            lateinit var key: Rect
            lateinit var spacer: Rect
            instrumentation.runOnMainSync {
                key = activity.keyBounds("a")
                val spacerView = activity.keyboard.getChildAt(1)
                val origin = IntArray(2).also(spacerView::getLocationOnScreen)
                spacer = Rect(
                    origin[0],
                    origin[1],
                    origin[0] + spacerView.width,
                    origin[1] + spacerView.height
                )
            }
            assertTrue("The test layout must have a visible key-to-spacer gap", key.right < spacer.left)

            tap(automation, (key.right + spacer.left) / 2f, key.exactCenterY())
            assertEquals(listOf(KeyAction.Text("a")), activity.actions.toList())

            activity.actions.clear()
            tap(automation, spacer.exactCenterX(), spacer.exactCenterY())
            assertTrue("The default setting must leave the spacer empty", activity.actions.isEmpty())

            instrumentation.runOnMainSync {
                activity.keyboard.setKeyHitTestMode(KeyHitTestMode.NEAREST_KEY)
            }
            tap(automation, spacer.exactCenterX(), spacer.exactCenterY())
            assertEquals(listOf(KeyAction.Text("a")), activity.actions.toList())
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun tap(
        automation: UiAutomation,
        x: Float,
        y: Float
    ) {
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(40)
        }
    }
}
