package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SumireModeKeyHighlightTest {
    @Test
    fun disabledSettingDoesNotLeaveAnyModeHighlighted() {
        for (style in styles) for (mode in KeyboardInputMode.entries) {
            assertFalse(layout(mode, style).keys.any { it.isHiLighted })
        }
    }

    @Test
    fun selectedModeSurvivesRetapCancelReuseAndControllerRebind() {
        for (style in styles) for (independent in listOf(false, true)) {
            val keyboard = keyboard(independent)
            for (mode in KeyboardInputMode.entries) {
                val layout = layout(mode, style, true)
                keyboard.setKeyboard(layout)
                measure(keyboard)
                val index = layout.keys.indexOfFirst { it.action == mode.action() }
                assertEquals(listOf(mode.action()), layout.keys.filter { it.isHiLighted }.map { it.action })
                repeat(2) {
                    touch(keyboard, index)
                    assertSelected(keyboard, index)
                }
                touch(keyboard, index, cancel = true)
                assertSelected(keyboard, index)
                keyboard.setKeyboard(layout)
                assertSelected(keyboard, index)
                keyboard.visibility = View.GONE
                keyboard.visibility = View.VISIBLE
                assertSelected(keyboard, index)
            }
        }
    }

    @Test
    fun modeSwitchesHonorBothSettingsAndReleaseOrdinaryKeys() {
        for (enabled in listOf(false, true)) for (independent in listOf(false, true)) {
            val keyboard = keyboard(independent)
            var mode = KeyboardInputMode.HIRAGANA
            var currentLayout = layout(mode, "third-flick", enabled)
            val actions = mutableListOf<KeyAction>()
            keyboard.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
                override fun onPress(action: KeyAction) = Unit
                override fun onAction(action: KeyAction, isFlick: Boolean) {
                    actions += action
                    val next = KeyboardInputMode.entries.firstOrNull { it.action() == action } ?: return
                    mode = next
                    currentLayout = layout(mode, "third-flick", enabled)
                    keyboard.setKeyboard(currentLayout)
                }
                override fun onActionLongPress(action: KeyAction) = Unit
                override fun onActionUpAfterLongPress(action: KeyAction) = Unit
                override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
                override fun onFlickActionLongPress(action: KeyAction) = Unit
                override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
            })
            keyboard.setKeyboard(currentLayout)
            for (next in listOf(KeyboardInputMode.ENGLISH, KeyboardInputMode.SYMBOLS, KeyboardInputMode.HIRAGANA)) {
                repeat(2) {
                    measure(keyboard)
                    val index = currentLayout.keys.indexOfFirst { it.action == next.action() }
                    touch(keyboard, index)
                    assertEquals(next, mode)
                    assertEquals(next.action(), actions.last())
                    if (enabled) assertSelected(keyboard, index)
                    else assertTrue((0 until keyboard.childCount).none { keyboard.getChildAt(it).isPressed })
                }
                val ordinary = currentLayout.keys.indexOfFirst { !it.isSpecialKey }
                touch(keyboard, ordinary)
                assertFalse(keyboard.getChildAt(ordinary).isPressed)
            }
        }
    }

    private fun assertSelected(keyboard: FlickKeyboardView, index: Int) {
        assertEquals(listOf(index), (0 until keyboard.childCount).filter { keyboard.getChildAt(it).isPressed })
    }

    private fun touch(keyboard: FlickKeyboardView, index: Int, cancel: Boolean = false) {
        val child = keyboard.getChildAt(index)
        val x = (child.left + child.right) / 2f
        val y = (child.top + child.bottom) / 2f
        val time = SystemClock.uptimeMillis()
        for ((action, offset) in listOf(MotionEvent.ACTION_DOWN to 0L,
            (if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP) to 10L)) {
            val origin = IntArray(2).also(keyboard::getLocationOnScreen)
            val event = MotionEvent.obtain(time, time + offset, action,
                x + origin[0], y + origin[1], 0).apply { offsetLocation(-origin[0].toFloat(), -origin[1].toFloat()) }
            try { keyboard.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    private fun measure(keyboard: FlickKeyboardView) {
        keyboard.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        keyboard.layout(0, 0, 1080, 600)
    }

    private fun keyboard(independent: Boolean): FlickKeyboardView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return FlickKeyboardView(ContextThemeWrapper(activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )).apply {
            setIndependentMultiTouchEnabled(independent)
            activity.setContentView(this)
        }
    }

    private fun layout(mode: KeyboardInputMode, style: String, highlight: Boolean = false): KeyboardLayout =
        KeyboardDefaultLayouts.createFinalLayout(mode, emptyMap(), "flick", style,
            highlightSelectedMode = highlight)

    private fun KeyboardInputMode.action(): KeyAction = when (this) {
        KeyboardInputMode.HIRAGANA -> KeyAction.SwitchToKanaLayout
        KeyboardInputMode.ENGLISH -> KeyAction.SwitchToEnglishLayout
        KeyboardInputMode.SYMBOLS -> KeyAction.SwitchToNumberLayout
    }

    private val styles = listOf("default", "circle", "sumire", "second-flick", "third-flick")
}
