package com.kazumaproject.qwerty_keyboard.ui

import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.qwerty_keyboard.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35], application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class QwertyCursorPointerTest {
    @Test fun secondPointerSpaceOwnsMovementAndItsReleaseSuppressesRemainingFingers() {
        for (independent in listOf(false, true)) {
            val fixture = Fixture(independent)
            fixture.enterCursorModeWithSecondPointer()
            val tapsBeforeCursor = fixture.taps.toList()
            fixture.released.clear()

            fixture.send(MotionEvent.ACTION_MOVE, 0, fixture.first,
                fixture.space.copy(y = fixture.space.y - 100))
            assertEquals(listOf(QWERTYKey.QWERTYKeyCursorUp), fixture.released)

            fixture.send(MotionEvent.ACTION_POINTER_UP, 1, fixture.first, fixture.space)
            assertFalse("Space POINTER_UP left cursor mode active", fixture.cursorMode())
            fixture.send(MotionEvent.ACTION_MOVE, 0, fixture.first.copy(x = fixture.first.x + 40))
            fixture.send(MotionEvent.ACTION_UP, 0, fixture.first)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(650))
            assertEquals("Remaining finger added a cursor move or key release",
                listOf(QWERTYKey.QWERTYKeyCursorUp), fixture.released)
            assertEquals("Remaining finger added text", tapsBeforeCursor, fixture.taps)
        }
    }

    @Test fun liftingTheNonOwnerDoesNotEndSpaceCursorMode() {
        for (independent in listOf(false, true)) {
            val fixture = Fixture(independent)
            fixture.enterCursorModeWithSecondPointer()
            fixture.released.clear()
            fixture.send(MotionEvent.ACTION_POINTER_UP, 0, fixture.first, fixture.space)
            assertTrue(fixture.cursorMode())
            fixture.send(MotionEvent.ACTION_MOVE, 0, fixture.space.copy(y = fixture.space.y + 100))
            assertEquals(listOf(QWERTYKey.QWERTYKeyCursorDown), fixture.released)
            fixture.send(MotionEvent.ACTION_UP, 0, fixture.space)
            assertFalse(fixture.cursorMode())
            assertEquals(listOf(QWERTYKey.QWERTYKeyCursorDown), fixture.released)
        }
    }

    private data class Pointer(val id: Int, val x: Float, val y: Float)

    private class Fixture(private val independent: Boolean) {
        val released = mutableListOf<QWERTYKey>()
        val taps = mutableListOf<Char>()
        private val held = mutableListOf<QWERTYKey>()
        private val downTime = SystemClock.uptimeMillis()
        private val keyboard = QWERTYKeyboardView(ContextThemeWrapper(
            RuntimeEnvironment.getApplication(), com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )).apply {
            setPopUpViewState(false)
            setIndependentMultiTouchEnabled(independent)
            setRomajiMode(false)
            setLongPressTimeout(300)
            setOnQWERTYKeyListener(object : QWERTYKeyListener {
                override fun onPressedQWERTYKey(qwertyKey: QWERTYKey) = Unit
                override fun onReleasedQWERTYKey(qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?) {
                    released += qwertyKey
                    tap?.let { taps += it }
                }
                override fun onLongPressQWERTYKey(qwertyKey: QWERTYKey) {
                    held += qwertyKey
                    if (qwertyKey == QWERTYKey.QWERTYKeySpace) setCursorMode(true)
                }
                override fun onFlickUPQWERTYKey(qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?) = Unit
                override fun onFlickDownQWERTYKey(qwertyKey: QWERTYKey, character: Char) = Unit
            })
            measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
            layout(0, 0, 1080, 500)
        }
        val first = center(R.id.key_shift, 3)
        val space = center(R.id.key_space, 19)

        fun enterCursorModeWithSecondPointer() {
            send(MotionEvent.ACTION_DOWN, 0, first)
            send(MotionEvent.ACTION_POINTER_DOWN, 1, first, space)
            @Suppress("UNCHECKED_CAST")
            val buttons = field("pointerButtonMap") as SparseArray<View>
            if (!independent) assertNull("Legacy second DOWN did not release the first key", buttons[3])
            assertEquals(R.id.key_space, buttons[19].id)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(650))
            assertTrue("Actual space long press did not run", QWERTYKey.QWERTYKeySpace in held)
            assertTrue(cursorMode())
            assertNull("Cursor entry did not suppress the non-owner", buttons[3])
        }

        fun cursorMode() = field("isCursorMode") as Boolean
        private fun field(name: String) = keyboard.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(keyboard)
        private fun center(id: Int, pointerId: Int): Pointer {
            val key = keyboard.findViewById<View>(id)
            assertTrue(key.width > 0 && key.height > 0)
            return Pointer(pointerId, key.left + key.width / 2f, key.top + key.height / 2f)
        }

        fun send(action: Int, index: Int, vararg pointers: Pointer) {
            val properties = Array(pointers.size) { n -> MotionEvent.PointerProperties().apply {
                id = pointers[n].id; toolType = MotionEvent.TOOL_TYPE_FINGER
            } }
            val coordinates = Array(pointers.size) { n -> MotionEvent.PointerCoords().apply {
                x = pointers[n].x; y = pointers[n].y; pressure = 1f; size = 1f
            } }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), pointers.size,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { keyboard.onTouchEvent(event) } finally { event.recycle() }
        }
    }
}
