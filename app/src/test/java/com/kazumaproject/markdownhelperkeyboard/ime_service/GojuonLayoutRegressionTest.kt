package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.state.GestureType
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
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GojuonLayoutRegressionTest {
    private class Fixture(independent: Boolean = false) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val keyboard = GojuonKeyboardView(ContextThemeWrapper(activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar))
        val taps = mutableListOf<Pair<Key, Char?>>()
        val longPresses = mutableListOf<Key>()

        init {
            activity.setContentView(keyboard)
            keyboard.setIndependentMultiTouchEnabled(independent)
            keyboard.setLongPressTimeout(300)
            keyboard.setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                    if (gestureType == GestureType.Tap) taps += key to char
                }
            })
            keyboard.setOnLongPressListener(object : LongPressListener {
                override fun onLongPress(key: Key) { longPresses += key }
            })
            layout()
        }

        fun key(id: Int): View = keyboard.findViewById(id)
        fun layout() {
            shadowOf(Looper.getMainLooper()).idle()
            keyboard.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
            keyboard.layout(0, 0, 1080, 500)
        }
        fun mode(mode: InputMode) {
            keyboard.currentInputMode.set(mode)
            keyboard.setInputModeSwitchState()
            layout()
        }
        fun send(action: Int, vararg ids: Int, actionIndex: Int = 0) {
            val origin = IntArray(2).also(keyboard::getLocationOnScreen)
            val properties = Array(ids.size) { index -> MotionEvent.PointerProperties().apply {
                id = index; toolType = MotionEvent.TOOL_TYPE_FINGER
            } }
            val coordinates = ids.map { id ->
                val view = key(id)
                val point = IntArray(2).also(view::getLocationOnScreen)
                MotionEvent.PointerCoords().apply {
                    x = point[0] - origin[0] + view.width / 2f
                    y = point[1] - origin[1] + view.height / 2f
                    pressure = 1f; size = 1f
                }
            }.toTypedArray()
            val event = MotionEvent.obtain(100L, 120L,
                action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), ids.size,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { keyboard.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        fun tap(id: Int) {
            send(MotionEvent.ACTION_DOWN, id)
            send(MotionEvent.ACTION_UP, id)
            layout()
        }
        fun close() { activity.finish(); shadowOf(Looper.getMainLooper()).idle() }
    }

    private fun assertLayout() {
        val f = Fixture()
        try {
            for (mode in listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)) {
                f.mode(mode)
                val left = f.key(R.id.key_left_cursor)
                val right = f.key(R.id.key_right_cursor)
                val group = f.key(R.id.cursor_keys_container)
                val modeKey = f.key(R.id.key_switch_key_mode)
                val ime = f.key(R.id.key_ime_switch)
                assertTrue("$mode cursors: left=${left.left}..${left.right}, right=${right.left}..${right.right}, group=${group.width}", abs(left.width - right.width) <= 1)
                assertEquals(left.top, right.top)
                assertEquals(left.bottom, right.bottom)
                assertTrue(abs(right.left - left.right - 4f * left.resources.displayMetrics.density) <= 1f)
                assertEquals(modeKey.width, group.width)
                assertEquals(group.width, left.width + right.width + right.left - left.right)
                val rows = listOf(f.key(R.id.key_previous), group, f.key(R.id.key_kigou), modeKey, ime)
                rows.forEach { assertTrue(it.width > 0 && it.height > 0) }
                rows.zipWithNext().forEach { (above, below) -> assertTrue(above.bottom <= below.top) }
                assertEquals(modeKey.left, ime.left)
                val firstColumn = listOf(R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4, R.id.key_5).map(f::key)
                firstColumn.forEach { assertEquals(firstColumn.first().left, it.left) }
                if (mode == InputMode.ModeNumber) assertBrackets(f)
            }
        } finally { f.close() }
    }

    private fun assertBrackets(f: Fixture) {
        val left = f.key(R.id.key_5) as TextView
        val right = f.key(R.id.key_15) as TextView
        assertEquals("[", left.text.toString())
        assertEquals("]", right.text.toString())
        assertEquals(View.VISIBLE, right.visibility)
        assertTrue(left.width > 0 && right.width > 0)
        assertTrue(left.right <= right.left)
        assertTrue(abs(left.width - right.width) <= 4 * left.resources.displayMetrics.density)
    }

    @Test @Config(qualifiers = "port") fun portraitGeometry() = assertLayout()
    @Test @Config(qualifiers = "land") fun landscapeGeometry() = assertLayout()
    @Test @Config(qualifiers = "sw600dp") fun tabletGeometry() = assertLayout()

    @Test fun numberBracketsUseActualTouchTargetsAndWidthToggle() {
        for (independent in listOf(false, true)) {
            val f = Fixture(independent)
            try {
                f.mode(InputMode.ModeNumber)
                f.tap(R.id.key_5); f.tap(R.id.key_15)
                assertEquals(listOf(Key.KeyKuten to '[', Key.KeyRO to ']'), f.taps)
                f.taps.clear()
                f.tap(R.id.key_55)
                f.taps.clear()
                f.tap(R.id.key_5); f.tap(R.id.key_15)
                assertEquals(listOf(Key.KeyKuten to '［', Key.KeyRO to '］'), f.taps)
            } finally { f.close() }
        }
    }

    @Test fun numberBracketsRemainIndependentWhenBothFingersAreDown() {
        val f = Fixture(independent = true)
        try {
            f.mode(InputMode.ModeNumber)
            f.send(MotionEvent.ACTION_DOWN, R.id.key_5)
            f.send(MotionEvent.ACTION_POINTER_DOWN, R.id.key_5, R.id.key_15, actionIndex = 1)
            assertTrue(f.taps.isEmpty())
            f.send(MotionEvent.ACTION_POINTER_UP, R.id.key_5, R.id.key_15, actionIndex = 1)
            f.send(MotionEvent.ACTION_UP, R.id.key_5)
            assertEquals(listOf(Key.KeyRO to ']', Key.KeyKuten to '['), f.taps)
        } finally { f.close() }
    }

    @Test fun cursorAndImeKeysDispatchSeparatelyInEveryMode() {
        for (independent in listOf(false, true)) {
            val f = Fixture(independent)
            try {
                for (mode in listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)) {
                    f.mode(mode); f.taps.clear()
                    f.tap(R.id.key_left_cursor); f.tap(R.id.key_right_cursor); f.tap(R.id.key_ime_switch)
                    assertEquals(listOf(Key.SideKeyCursorLeft, Key.SideKeyCursorRight, Key.SideKeyImeSwitch),
                        f.taps.map { it.first })
                }
                for (id in listOf(R.id.key_left_cursor, R.id.key_right_cursor)) {
                    f.send(MotionEvent.ACTION_DOWN, id)
                    shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
                    f.send(MotionEvent.ACTION_UP, id)
                }
                assertEquals(listOf(Key.SideKeyCursorLeft, Key.SideKeyCursorRight), f.longPresses)
            } finally { f.close() }
        }
    }

    @Test fun imeSwitchLongPressDoesNotDispatchTapInEveryMode() {
        for (independent in listOf(false, true)) {
            val f = Fixture(independent)
            try {
                for (mode in listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)) {
                    f.mode(mode)
                    f.taps.clear()
                    f.longPresses.clear()
                    f.send(MotionEvent.ACTION_DOWN, R.id.key_ime_switch)
                    shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
                    assertEquals(listOf(Key.SideKeyImeSwitch), f.longPresses)
                    f.send(MotionEvent.ACTION_UP, R.id.key_ime_switch)
                    assertTrue("$mode independent=$independent", f.taps.isEmpty())
                    f.tap(R.id.key_ime_switch)
                    assertEquals(listOf(Key.SideKeyImeSwitch), f.taps.map { it.first })
                }
            } finally { f.close() }
        }
    }

    @Test fun imeSwitchLongPressDoesNotTapWhenAnotherFingerArrives() {
        for (independent in listOf(false, true)) {
            val f = Fixture(independent)
            try {
                f.send(MotionEvent.ACTION_DOWN, R.id.key_ime_switch)
                shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
                f.send(MotionEvent.ACTION_POINTER_DOWN, R.id.key_ime_switch, R.id.key_1, actionIndex = 1)
                f.send(MotionEvent.ACTION_POINTER_UP, R.id.key_ime_switch, R.id.key_1, actionIndex = 1)
                f.send(MotionEvent.ACTION_UP, R.id.key_ime_switch)
                assertEquals(listOf(Key.SideKeyImeSwitch), f.longPresses)
                assertFalse(f.taps.any { it.first == Key.SideKeyImeSwitch })
            } finally { f.close() }
        }
    }

    @Test fun cancelledImeSwitchDoesNotTapOrLongPress() {
        for (independent in listOf(false, true)) {
            val f = Fixture(independent)
            try {
                f.send(MotionEvent.ACTION_DOWN, R.id.key_ime_switch)
                f.send(MotionEvent.ACTION_CANCEL, R.id.key_ime_switch)
                shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
                assertTrue(f.longPresses.isEmpty())
                assertTrue(f.taps.isEmpty())
                f.tap(R.id.key_ime_switch)
                assertEquals(listOf(Key.SideKeyImeSwitch), f.taps.map { it.first })
            } finally { f.close() }
        }
    }

    @Test fun repeatedModeCyclesRestoreBothBracketCellsAndKana() {
        val f = Fixture()
        try {
            repeat(3) {
                f.tap(R.id.key_switch_key_mode)
                assertEquals(InputMode.ModeEnglish, f.keyboard.currentInputMode.get())
                assertEquals(View.GONE, f.key(R.id.key_15).visibility)
                f.tap(R.id.key_switch_key_mode)
                assertEquals(InputMode.ModeNumber, f.keyboard.currentInputMode.get())
                assertBrackets(f)
                f.tap(R.id.key_switch_key_mode)
                assertEquals(InputMode.ModeJapanese, f.keyboard.currentInputMode.get())
                assertEquals("。", (f.key(R.id.key_5) as TextView).text.toString())
                assertEquals("ろ", (f.key(R.id.key_15) as TextView).text.toString())
                assertTrue(f.key(R.id.key_5).right <= f.key(R.id.key_10).left)
                assertTrue(f.key(R.id.key_10).right <= f.key(R.id.key_15).left)
            }
        } finally { f.close() }
    }
}
