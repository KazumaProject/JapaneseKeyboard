package com.kazumaproject.custom_keyboard.view

import android.content.Intent
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

/** Software rendering keeps input injection independent of emulator GPU availability. */
class SumireIndependentMultiTouchTestActivity : SumireNearestKeyTestActivity()

@RunWith(AndroidJUnit4::class)
class SumireIndependentMultiTouchInstrumentedTest {
    @Test fun generatedSumireLayoutCommitsInReleaseOrderAtBothSizesAndWindowPositions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.context,
            SumireIndependentMultiTouchTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SumireIndependentMultiTouchTestActivity
        try {
            for (floating in listOf(false, true)) for (scale in listOf(100, 0)) for (enabled in listOf(false, true)) {
                instrumentation.runOnMainSync {
                    activity.configure(scale, floating)
                    activity.keyboard.setIndependentMultiTouchEnabled(enabled)
                    activity.keyboard.setLongPressTimeout(2000)
                }
                instrumentation.waitForIdleSync()
                val deadline = SystemClock.uptimeMillis() + 3000
                var ready = false
                while (!ready && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        ready = activity.hasWindowFocus() && activity.keyboard.width > 0 &&
                            activity.keyboard.getChildAt(0).width > 0
                    }
                    if (!ready) SystemClock.sleep(25)
                }
                assertTrue("Test window must be focused and laid out", ready)
                lateinit var a: Rect
                lateinit var ka: Rect
                instrumentation.runOnMainSync {
                    a = activity.keyBounds("あ")
                    ka = activity.keyBounds("か")
                }
                val downTime = SystemClock.uptimeMillis()
                fun send(action: Int, index: Int, ids: IntArray, bounds: List<Rect>) {
                    val properties = Array(ids.size) { i -> MotionEvent.PointerProperties().apply {
                        id = ids[i]; toolType = MotionEvent.TOOL_TYPE_FINGER
                    } }
                    val coordinates = Array(ids.size) { i -> MotionEvent.PointerCoords().apply {
                        x = bounds[i].exactCenterX(); y = bounds[i].exactCenterY(); pressure = 1f; size = 1f
                    } }
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                        action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), ids.size,
                        properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                    try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
                    finally { event.recycle() }
                    instrumentation.waitForIdleSync()
                }
                send(MotionEvent.ACTION_DOWN, 0, intArrayOf(3), listOf(a))
                send(MotionEvent.ACTION_POINTER_DOWN, 1, intArrayOf(3, 19), listOf(a, ka))
                val beforeRelease = if (enabled) emptyList() else listOf(KeyAction.Text("あ"))
                assertEquals("scale=$scale floating=$floating enabled=$enabled before release", beforeRelease, activity.actions.toList())
                send(MotionEvent.ACTION_POINTER_UP, 1, intArrayOf(3, 19), listOf(a, ka))
                send(MotionEvent.ACTION_UP, 0, intArrayOf(3), listOf(a))
                val expected = if (enabled) listOf(KeyAction.Text("か"), KeyAction.Text("あ"))
                    else listOf(KeyAction.Text("あ"), KeyAction.Text("か"))
                assertEquals("scale=$scale floating=$floating enabled=$enabled", expected, activity.actions.toList())
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
