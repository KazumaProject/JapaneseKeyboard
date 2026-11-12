package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.custom_keyboard.data.*
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
class CustomFlickSuspensionTest {
    @Test fun everySuspendedTypeBlocksAllHitModesAndDirectDispatchWhileOtherKeysWork() {
        for (type in listOf(KeyType.TWO_STEP_FLICK, KeyType.STICKY_TWO_STEP_FLICK, KeyType.HIERARCHICAL_FLICK)) {
            Fixture(type).use { f ->
                for (mode in KeyHitTestMode.entries) {
                    f.keyboard.setKeyboard(f.layout, mode)
                    f.measure()
                    assertFalse(f.keyboard.getChildAt(0).isEnabled)
                    assertTrue((f.keyboard.getChildAt(0) as AutoSizeButton).text.contains(f.keyboard.context.getString(com.kazumaproject.core.R.string.custom_flick_paused_label)))
                    f.tap(0); f.tap(0); f.tap(0, hold = true)
                    f.directDispatch()
                    assertTrue("$type/$mode: ${f.actions}", f.actions.isEmpty())
                    f.keyboard.setVisibleKeyLabels(null)
                    f.keyboard.setKeyboard(f.layout, mode)
                    assertFalse(f.keyboard.getChildAt(0).isEnabled)
                    f.tap(1)
                    assertEquals(listOf(KeyAction.Text("ok")), f.actions)
                    f.actions.clear()
                }
                assertEquals("saved", f.layout.preservedFlickDefinitions["saved"])
            }
        }
    }

    @Test fun originChangesRebuildTheSameLayoutAndCancelPendingBuiltInInput() {
        Fixture(KeyType.TWO_STEP_FLICK).use { f ->
            f.keyboard.setKeyboard(f.layout, origin = KeyboardLayoutOrigin.BUILT_IN)
            f.measure()
            assertTrue(f.keyboard.getChildAt(0).isEnabled)
            f.tap(0)
            assertEquals(listOf(KeyAction.Text("blocked")), f.actions)
            f.actions.clear()
            f.send(MotionEvent.ACTION_DOWN, 0)
            f.keyboard.setKeyboard(f.layout) // A custom caller cannot inherit built-in permission.
            f.measure()
            f.send(MotionEvent.ACTION_UP, 0)
            assertFalse(f.keyboard.getChildAt(0).isEnabled)
            assertTrue(f.actions.isEmpty())
            f.tap(0, hold = true)
            assertTrue(f.actions.isEmpty())
            f.keyboard.setKeyboard(f.layout, origin = KeyboardLayoutOrigin.BUILT_IN)
            f.measure()
            assertTrue(f.keyboard.getChildAt(0).isEnabled)
            f.tap(0)
            assertEquals(listOf(KeyAction.Text("blocked")), f.actions)
        }
    }

    private class Fixture(type: KeyType) : AutoCloseable {
        private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val actions = mutableListOf<KeyAction>()
        private val blocked = KeyData("saved", 0, 0, true, action = KeyAction.Text("blocked"),
            keyType = type, keyId = "saved", drawableResId = android.R.drawable.ic_menu_add)
        val layout = KeyboardLayout(listOf(blocked,
            KeyData("ok", 0, 1, false, action = KeyAction.Text("ok"), keyId = "ok")),
            emptyMap(), 2, 1, preservedFlickDefinitions = mapOf("saved" to "saved"),
            twoStepFlickKeyMaps = mapOf("saved" to mapOf(TfbiFlickDirection.TAP to
                mapOf(TfbiFlickDirection.TAP to "blocked"))))
        val keyboard = FlickKeyboardView(ContextThemeWrapper(activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar))
        init {
            keyboard.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
                override fun onPress(action: KeyAction) = Unit
                override fun onAction(action: KeyAction, isFlick: Boolean) { actions += action }
                override fun onActionLongPress(action: KeyAction) { actions += action }
                override fun onActionUpAfterLongPress(action: KeyAction) { actions += action }
                override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
                override fun onFlickActionLongPress(action: KeyAction) { actions += action }
                override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) { actions += action }
            })
            activity.setContentView(keyboard)
        }
        fun measure() {
            keyboard.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            keyboard.layout(0, 0, 800, 400)
        }
        fun tap(index: Int, hold: Boolean = false) {
            send(MotionEvent.ACTION_DOWN, index)
            if (hold) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            send(MotionEvent.ACTION_UP, index)
        }
        fun send(action: Int, index: Int) {
            val key = keyboard.getChildAt(index)
            val now = android.os.SystemClock.uptimeMillis()
            val e = MotionEvent.obtain(now, now, action, key.x + key.width / 2f, key.y + key.height / 2f, 0)
            try { keyboard.dispatchTouchEvent(e) } finally { e.recycle() }
        }
        fun directDispatch() {
            keyboard.javaClass.getDeclaredMethod("dispatchCommittedKeyAction", KeyData::class.java,
                KeyAction::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(keyboard, blocked, KeyAction.Text("blocked"), false, false)
        }
        override fun close() { activity.finish() }
    }
}
