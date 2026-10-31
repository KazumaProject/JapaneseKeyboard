package com.kazumaproject.qwerty_keyboard.ui

import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.qwerty_keyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class QwertyCursorFlickTest {
    @Test
    fun verticalFlicksKeepTheirOriginRegardlessOfLetterFlickAndGlideSettings() {
        for (independent in listOf(false, true)) {
            for (letterFlick in listOf(false, true)) {
                for (glide in listOf(false, true)) {
                    for (romaji in listOf(false, true)) {
                        for ((id, key) in cursorKeys) {
                            for ((delta, direction) in verticalDirections) {
                                val fixture = Fixture(independent, letterFlick, glide, romaji)
                                val start = fixture.center(id, 3)
                                val end = start.copy(y = start.y + delta)
                                fixture.send(MotionEvent.ACTION_DOWN, 0, start)
                                // A small move followed by a move well beyond the key's bounds.
                                fixture.send(MotionEvent.ACTION_MOVE, 0, start.copy(y = start.y + delta / 100))
                                fixture.send(MotionEvent.ACTION_MOVE, 0, end)
                                assertTrue(fixture.flicks.isEmpty())
                                fixture.send(MotionEvent.ACTION_UP, 0, end)
                                val message = "key=$key direction=$direction independent=$independent " +
                                    "letterFlick=$letterFlick glide=$glide romaji=$romaji"
                                assertEquals(message, listOf(key to direction), fixture.flicks)
                                assertTrue(message, fixture.released.isEmpty())
                                assertTrue(message, fixture.characterFlicks.isEmpty())
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun finalDirectionAndThresholdDetermineFlickOrTap() {
        for (independent in listOf(false, true)) {
            for (shape in FlickThresholdShape.entries) {
                val fixture = Fixture(independent).apply { keyboard.setFlickThresholdShape(shape) }
                val start = fixture.center(R.id.cursor_right, 3)
                fixture.send(MotionEvent.ACTION_DOWN, 0, start)
                fixture.send(MotionEvent.ACTION_MOVE, 0, start.copy(y = start.y - 100f))
                fixture.send(MotionEvent.ACTION_MOVE, 0, start.copy(y = start.y + 100f))
                fixture.send(MotionEvent.ACTION_UP, 0, start.copy(y = start.y + 100f))
                assertEquals(listOf(QWERTYKey.QWERTYKeyCursorRight to GestureType.FlickBottom), fixture.flicks)
                assertTrue(fixture.released.isEmpty())

                fixture.flicks.clear()
                fixture.send(MotionEvent.ACTION_DOWN, 0, start)
                fixture.send(MotionEvent.ACTION_MOVE, 0, start.copy(y = start.y - 1f))
                fixture.send(MotionEvent.ACTION_UP, 0, start.copy(y = start.y - 1f))
                assertTrue(fixture.flicks.isEmpty())
                assertEquals(listOf(QWERTYKey.QWERTYKeyCursorRight), fixture.released)
            }
        }
    }

    @Test
    fun finalUpCoordinatesDetectFlickEvenWithoutMoveAndCancelNeverCommits() {
        val fixture = Fixture()
        val start = fixture.center(R.id.cursor_left, 3)
        val end = start.copy(y = start.y - 100f)
        fixture.send(MotionEvent.ACTION_DOWN, 0, start)
        fixture.send(MotionEvent.ACTION_UP, 0, end)
        assertEquals(listOf(QWERTYKey.QWERTYKeyCursorLeft to GestureType.FlickTop), fixture.flicks)
        assertTrue(fixture.released.isEmpty())

        fixture.flicks.clear()
        fixture.send(MotionEvent.ACTION_DOWN, 0, start)
        fixture.send(MotionEvent.ACTION_MOVE, 0, end)
        fixture.send(MotionEvent.ACTION_CANCEL, 0, end)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(650))
        assertTrue(fixture.flicks.isEmpty())
        assertTrue(fixture.released.isEmpty())
        assertTrue(fixture.longPresses.isEmpty())
    }

    @Test
    fun verticalMovementCancelsLongPressButStationaryLongPressStillWorks() {
        for (independent in listOf(false, true)) {
            val fixture = Fixture(independent)
            val start = fixture.center(R.id.cursor_left, 3)
            val end = start.copy(y = start.y - 100f)
            fixture.send(MotionEvent.ACTION_DOWN, 0, start)
            fixture.send(MotionEvent.ACTION_MOVE, 0, end)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(650))
            assertTrue(fixture.longPresses.isEmpty())
            fixture.send(MotionEvent.ACTION_UP, 0, end)
            assertEquals(listOf(QWERTYKey.QWERTYKeyCursorLeft to GestureType.FlickTop), fixture.flicks)

            fixture.flicks.clear()
            fixture.send(MotionEvent.ACTION_DOWN, 0, start)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(650))
            fixture.send(MotionEvent.ACTION_UP, 0, start)
            assertEquals(listOf(QWERTYKey.QWERTYKeyCursorLeft), fixture.longPresses)
            assertEquals(listOf(QWERTYKey.QWERTYKeyCursorLeft), fixture.released)
            assertTrue(fixture.flicks.isEmpty())
        }
    }

    @Test
    fun cursorFlickIsFinalizedOnceWhenAnotherPointerStarts() {
        for (independent in listOf(false, true)) {
            val fixture = Fixture(independent)
            val cursor = fixture.center(R.id.cursor_left, 3)
            val cursorEnd = cursor.copy(y = cursor.y - 100f)
            val second = fixture.center(R.id.key_a, 19)
            fixture.send(MotionEvent.ACTION_DOWN, 0, cursor)
            fixture.send(MotionEvent.ACTION_MOVE, 0, cursorEnd)
            fixture.send(MotionEvent.ACTION_POINTER_DOWN, 1, cursorEnd, second)
            fixture.send(MotionEvent.ACTION_POINTER_UP, 0, cursorEnd, second)
            fixture.send(MotionEvent.ACTION_UP, 0, second)
            assertEquals(listOf(QWERTYKey.QWERTYKeyCursorLeft to GestureType.FlickTop), fixture.flicks)
            assertTrue(QWERTYKey.QWERTYKeyCursorLeft !in fixture.released)
        }
    }

    private data class Pointer(val id: Int, val x: Float, val y: Float)

    private class Fixture(
        independent: Boolean = false,
        letterFlick: Boolean = false,
        glide: Boolean = false,
        romaji: Boolean = false
    ) {
        val released = mutableListOf<QWERTYKey>()
        val flicks = mutableListOf<Pair<QWERTYKey, GestureType>>()
        val characterFlicks = mutableListOf<QWERTYKey>()
        val longPresses = mutableListOf<QWERTYKey>()
        private val downTime = SystemClock.uptimeMillis()
        val keyboard = QWERTYKeyboardView(ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )).apply {
            setSpecialKeyVisibility(true, true, true)
            setPopUpViewState(false)
            setIndependentMultiTouchEnabled(independent)
            setRomajiMode(romaji)
            setFlickUpDetectionEnabled(letterFlick)
            setFlickDownDetectionEnabled(letterFlick)
            setQwertyGlideInputMode(glide)
            setLongPressTimeout(300)
            setOnQWERTYKeyListener(object : QWERTYKeyListener {
                override fun onPressedQWERTYKey(qwertyKey: QWERTYKey) = Unit
                override fun onReleasedQWERTYKey(qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?) {
                    released += qwertyKey
                }
                override fun onCursorFlickQWERTYKey(qwertyKey: QWERTYKey, gestureType: GestureType) {
                    flicks += qwertyKey to gestureType
                }
                override fun onLongPressQWERTYKey(qwertyKey: QWERTYKey) { longPresses += qwertyKey }
                override fun onFlickUPQWERTYKey(qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?) {
                    characterFlicks += qwertyKey
                }
                override fun onFlickDownQWERTYKey(qwertyKey: QWERTYKey, character: Char) {
                    characterFlicks += qwertyKey
                }
            })
            measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
            layout(0, 0, 1080, 500)
        }

        fun center(id: Int, pointerId: Int): Pointer {
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

    private companion object {
        val cursorKeys = listOf(
            R.id.cursor_left to QWERTYKey.QWERTYKeyCursorLeft,
            R.id.cursor_right to QWERTYKey.QWERTYKeyCursorRight
        )
        val verticalDirections = listOf(
            -100f to GestureType.FlickTop,
            100f to GestureType.FlickBottom
        )
    }
}
