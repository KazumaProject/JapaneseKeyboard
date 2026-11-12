package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
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
class CustomThreeStageFlickTest {
    @Test fun restoredBuiltInStylesUseNormalFlickForEveryLanguageAndOldInputMethod() {
        for (mode in KeyboardInputMode.entries) for (method in listOf("flick", "toggle", "romaji")) {
            val expected = KeyboardDefaultLayouts.createFinalLayout(mode, emptyMap(), "flick", "default")
            val restored = KeyboardDefaultLayouts.createFinalLayout(mode, emptyMap(), method, "third-flick")
            assertEquals(expected, restored)
            assertTrue(restored.preservedFlickDefinitions.isEmpty())
            assertFalse(restored.keys.any { it.keyType == KeyType.HIERARCHICAL_FLICK })
        }
    }

    @Test fun suspendedKeysCannotDispatchTapHoldDoubleTapOrNearestKeyInputAfterRebuild() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val keyboard = FlickKeyboardView(ContextThemeWrapper(activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar))
        val actions = mutableListOf<KeyAction>()
        keyboard.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
            override fun onPress(action: KeyAction) = Unit
            override fun onAction(action: KeyAction, isFlick: Boolean) { actions += action }
            override fun onActionLongPress(action: KeyAction) { actions += action }
            override fun onActionUpAfterLongPress(action: KeyAction) { actions += action }
            override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
            override fun onFlickActionLongPress(action: KeyAction) { actions += action }
            override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) { actions += action }
        })
        val blocked = KeyData("saved", 0, 0, true, action = KeyAction.Text("blocked"),
            keyType = KeyType.HIERARCHICAL_FLICK, keyId = "saved")
        val ordinary = KeyData("ok", 0, 1, false, action = KeyAction.Text("ok"), keyId = "ordinary")
        val saved = """{"normal":{"TAP":{"type":"input","output":"saved"}}}"""
        val layout = KeyboardLayout(listOf(blocked, ordinary), emptyMap(), 2, 1,
            preservedFlickDefinitions = mapOf("saved" to saved))
        activity.setContentView(keyboard)
        fun tap(index: Int, hold: Boolean = false) {
            val key = keyboard.getChildAt(index)
            assertTrue("Key $index has no bounds", key.width > 0 && key.height > 0)
            val position = IntArray(2).also(key::getLocationOnScreen)
            val owner = IntArray(2).also(keyboard::getLocationOnScreen)
            val x = position[0] + key.width / 2f
            val y = position[1] + key.height / 2f
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val now = android.os.SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, action, x, y, 0)
                event.offsetLocation(-owner[0].toFloat(), -owner[1].toFloat())
                keyboard.dispatchTouchEvent(event)
                event.recycle()
                if (action == MotionEvent.ACTION_DOWN && hold) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            }
        }
        for (hitMode in KeyHitTestMode.entries) {
            keyboard.setKeyboard(layout, hitMode)
            keyboard.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            keyboard.layout(0, 0, 800, 400)
            assertFalse(keyboard.getChildAt(0).isEnabled)
            tap(0); tap(0); tap(0, hold = true)
            assertTrue("$hitMode: $actions", actions.isEmpty())
            keyboard.setVisibleKeyLabels(null)
            keyboard.setKeyboard(layout, hitMode)
            assertFalse(keyboard.getChildAt(0).isEnabled)
            tap(0)
            assertTrue(actions.isEmpty())
            tap(1)
            assertEquals(listOf(KeyAction.Text("ok")), actions)
            actions.clear()
            assertEquals(saved, layout.preservedFlickDefinitions["saved"])
        }
        activity.finish()
    }
}
