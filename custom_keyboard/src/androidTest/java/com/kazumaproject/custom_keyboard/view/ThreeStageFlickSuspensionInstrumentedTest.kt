package com.kazumaproject.custom_keyboard.view

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.core.view.doOnLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.custom_keyboard.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ThreeStageFlickSuspensionInstrumentedTest {
    @Test fun savedKeysStayBlockedInBothPopupModesAndEveryHitTestMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(SumireFlickRotationTestActivity::class.java).use { scenario ->
            for (popup in TfbiPopupPresentationMode.entries) for (hitMode in KeyHitTestMode.entries) {
                val laidOut = CountDownLatch(1)
                scenario.onActivity { activity ->
                    activity.configure("third-flick", TfbiFlickRotation.DEFAULT, popup)
                    val blocked = KeyData("saved", 0, 0, true, action = KeyAction.Text("blocked"),
                        keyId = "saved", keyType = KeyType.HIERARCHICAL_FLICK)
                    val ordinary = KeyData("ok", 0, 1, false, action = KeyAction.Text("ok"), keyId = "ordinary")
                    val saved = TfbiFlickNode.StatefulKey(
                        mapOf(TfbiFlickDirection.TAP to TfbiFlickNode.Input("preserved")), label = "saved")
                    activity.keyboard.setLongPressTimeout(100)
                    activity.keyboard.setKeyboard(KeyboardLayout(listOf(blocked, ordinary), emptyMap(), 2, 1,
                        hierarchicalFlickMaps = mapOf("saved" to saved)), hitMode)
                    activity.keyboard.doOnLayout { laidOut.countDown() }
                }
                assertTrue("Keyboard layout did not finish", laidOut.await(5, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                fun tap(index: Int, hold: Boolean = false) {
                    var x = 0f; var y = 0f
                    scenario.onActivity { activity ->
                        val key = activity.keyboard.getChildAt(index)
                        assertEquals(index != 0, key.isEnabled)
                        assertTrue("Unmeasured key: $index ${key.width}x${key.height}", key.width > 0 && key.height > 0)
                        val position = IntArray(2).also(key::getLocationOnScreen)
                        x = position[0] + key.width / 2f; y = position[1] + key.height / 2f
                    }
                    val down = SystemClock.uptimeMillis()
                    fun send(action: Int) {
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
                        finally { event.recycle() }
                    }
                    send(MotionEvent.ACTION_DOWN)
                    SystemClock.sleep(if (hold) 200 else 40)
                    send(MotionEvent.ACTION_UP)
                    instrumentation.waitForIdleSync()
                }
                tap(0); tap(0); tap(0, hold = true)
                scenario.onActivity { activity ->
                    assertTrue("$popup/$hitMode: ${activity.actions}", activity.actions.isEmpty())
                    activity.keyboard.setVisibleKeyLabels(null)
                    assertFalse(activity.keyboard.getChildAt(0).isEnabled)
                    activity.keyboard.getChildAt(0).performClick()
                    activity.keyboard.getChildAt(0).performLongClick()
                    assertTrue(activity.actions.isEmpty())
                }
                tap(1)
                scenario.onActivity { assertEquals("$popup/$hitMode", listOf(KeyAction.Text("ok")), it.actions) }
            }
        }
    }
}
