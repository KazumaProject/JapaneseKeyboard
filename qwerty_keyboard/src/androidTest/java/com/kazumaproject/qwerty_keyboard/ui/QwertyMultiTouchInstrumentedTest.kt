package com.kazumaproject.qwerty_keyboard.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.qwerty_keyboard.R
import com.kazumaproject.qwerty_keyboard.glide.QwertyGlideInputListener
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QwertyMultiTouchInstrumentedTest {

    @Test
    fun overlappingLetterTaps_commitBothKeysWhenOlderPointerLiftsFirst() {
        runOnMain {
            val recorder = RecordingQwertyKeyListener()
            val keyboard = createKeyboard(recorder)
            val a = keyboard.keyCenter(R.id.key_a)
            val b = keyboard.keyCenter(R.id.key_b)

            keyboard.sendEvent(100L, 100L, MotionEvent.ACTION_DOWN, 0, pointer(3, a))
            keyboard.sendEvent(
                100L,
                120L,
                MotionEvent.ACTION_POINTER_DOWN,
                1,
                pointer(3, a),
                pointer(7, b)
            )
            keyboard.sendEvent(
                100L,
                140L,
                MotionEvent.ACTION_POINTER_UP,
                0,
                pointer(3, a),
                pointer(7, b)
            )
            keyboard.sendEvent(100L, 160L, MotionEvent.ACTION_UP, 0, pointer(7, b))

            assertEquals(listOf('a', 'b'), recorder.taps)
        }
    }

    @Test
    fun overlappingLetterTaps_commitBothKeysWhenNewerPointerLiftsFirst() {
        runOnMain {
            val recorder = RecordingQwertyKeyListener()
            val keyboard = createKeyboard(recorder)
            val a = keyboard.keyCenter(R.id.key_a)
            val b = keyboard.keyCenter(R.id.key_b)

            keyboard.sendEvent(200L, 200L, MotionEvent.ACTION_DOWN, 0, pointer(11, a))
            keyboard.sendEvent(
                200L,
                220L,
                MotionEvent.ACTION_POINTER_DOWN,
                1,
                pointer(11, a),
                pointer(19, b)
            )
            keyboard.sendEvent(
                200L,
                240L,
                MotionEvent.ACTION_POINTER_UP,
                1,
                pointer(11, a),
                pointer(19, b)
            )
            keyboard.sendEvent(200L, 260L, MotionEvent.ACTION_UP, 0, pointer(11, a))

            assertEquals(listOf('a', 'b'), recorder.taps)
        }
    }

    @Test
    fun firstPointerFlickThenSecondPointerDown_doesNotAddBaseTap() {
        runOnMain {
            val recorder = RecordingQwertyKeyListener()
            val keyboard = createKeyboard(recorder).apply {
                setFlickUpDetectionEnabled(true)
            }
            val a = keyboard.keyCenter(R.id.key_a)
            val b = keyboard.keyCenter(R.id.key_b)
            val flickedA = a.copy(y = a.y - keyboard.findViewById<View>(R.id.key_a).height)

            keyboard.sendEvent(300L, 300L, MotionEvent.ACTION_DOWN, 0, pointer(5, a))
            keyboard.sendEvent(300L, 340L, MotionEvent.ACTION_MOVE, 0, pointer(5, flickedA))
            keyboard.sendEvent(
                300L,
                360L,
                MotionEvent.ACTION_POINTER_DOWN,
                1,
                pointer(5, flickedA),
                pointer(9, b)
            )
            keyboard.sendEvent(
                300L,
                380L,
                MotionEvent.ACTION_POINTER_UP,
                0,
                pointer(5, flickedA),
                pointer(9, b)
            )
            keyboard.sendEvent(300L, 400L, MotionEvent.ACTION_UP, 0, pointer(9, b))

            assertEquals(listOf(QWERTYKey.QWERTYKeyA), recorder.upFlicks)
            assertEquals(listOf('b'), recorder.taps)
        }
    }

    @Test
    fun pendingGlideThenSecondPointerDown_commitsFirstTapInsteadOfDroppingIt() {
        runOnMain {
            val recorder = RecordingQwertyKeyListener()
            val keyboard = createKeyboard(recorder).apply {
                setQwertyGlideInputMode(true)
            }
            val a = keyboard.keyCenter(R.id.key_a)
            val b = keyboard.keyCenter(R.id.key_b)

            keyboard.sendEvent(500L, 500L, MotionEvent.ACTION_DOWN, 0, pointer(13, a))
            keyboard.sendEvent(
                500L,
                520L,
                MotionEvent.ACTION_POINTER_DOWN,
                1,
                pointer(13, a),
                pointer(17, b)
            )
            keyboard.sendEvent(
                500L,
                540L,
                MotionEvent.ACTION_POINTER_UP,
                0,
                pointer(13, a),
                pointer(17, b)
            )
            keyboard.sendEvent(500L, 560L, MotionEvent.ACTION_UP, 0, pointer(17, b))

            assertEquals(listOf('a', 'b'), recorder.taps)
        }
    }

    @Test
    fun activeGlideThenSecondPointerDown_cancelsGlideWithoutReactivatingOldPointer() {
        runOnMain {
            val recorder = RecordingQwertyKeyListener()
            val glideRecorder = RecordingGlideInputListener()
            val keyboard = createKeyboard(recorder).apply {
                setQwertyGlideInputListener(glideRecorder)
                setQwertyGlideInputMode(true)
            }
            val a = keyboard.keyCenter(R.id.key_a)
            val s = keyboard.keyCenter(R.id.key_s)
            val d = keyboard.keyCenter(R.id.key_d)
            val b = keyboard.keyCenter(R.id.key_b)

            keyboard.sendEvent(600L, 600L, MotionEvent.ACTION_DOWN, 0, pointer(31, a))
            keyboard.sendEvent(600L, 640L, MotionEvent.ACTION_MOVE, 0, pointer(31, s))
            keyboard.sendEvent(600L, 680L, MotionEvent.ACTION_MOVE, 0, pointer(31, d))
            assertEquals(1, glideRecorder.started)

            keyboard.sendEvent(
                600L,
                700L,
                MotionEvent.ACTION_POINTER_DOWN,
                1,
                pointer(31, d),
                pointer(27, b)
            )
            keyboard.sendEvent(
                600L,
                720L,
                MotionEvent.ACTION_MOVE,
                0,
                pointer(31, a),
                pointer(27, b)
            )
            keyboard.sendEvent(
                600L,
                740L,
                MotionEvent.ACTION_POINTER_UP,
                0,
                pointer(31, a),
                pointer(27, b)
            )
            keyboard.sendEvent(600L, 760L, MotionEvent.ACTION_UP, 0, pointer(27, b))

            assertEquals(1, glideRecorder.cancelled)
            assertEquals(listOf('b'), recorder.taps)
        }
    }

    @Test
    fun shiftThenSecondPointerDown_appliesShiftToSecondKey() {
        runOnMain {
            val recorder = RecordingQwertyKeyListener()
            val keyboard = createKeyboard(recorder)
            val shift = keyboard.keyCenter(R.id.key_shift)
            val b = keyboard.keyCenter(R.id.key_b)

            keyboard.sendEvent(700L, 700L, MotionEvent.ACTION_DOWN, 0, pointer(23, shift))
            keyboard.sendEvent(
                700L,
                720L,
                MotionEvent.ACTION_POINTER_DOWN,
                1,
                pointer(23, shift),
                pointer(29, b)
            )
            keyboard.sendEvent(
                700L,
                740L,
                MotionEvent.ACTION_POINTER_UP,
                0,
                pointer(23, shift),
                pointer(29, b)
            )
            keyboard.sendEvent(700L, 760L, MotionEvent.ACTION_UP, 0, pointer(29, b))

            assertTrue(recorder.releasedKeys.contains(QWERTYKey.QWERTYKeyShift))
            assertEquals(listOf('B'), recorder.taps)
        }
    }

    private fun createKeyboard(listener: RecordingQwertyKeyListener): QWERTYKeyboardView {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val themedContext: Context = ContextThemeWrapper(
            targetContext,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        return QWERTYKeyboardView(themedContext).apply {
            setPopUpViewState(false)
            setOnQWERTYKeyListener(listener)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
            measure(widthSpec, heightSpec)
            layout(0, 0, 1080, 500)
        }
    }

    private fun QWERTYKeyboardView.keyCenter(id: Int): Point {
        val key = findViewById<View>(id)
        assertTrue("Expected key $id to have a measured width", key.width > 0)
        assertTrue("Expected key $id to have a measured height", key.height > 0)
        return Point(
            x = key.left + key.width / 2f,
            y = key.top + key.height / 2f
        )
    }

    private fun QWERTYKeyboardView.sendEvent(
        downTime: Long,
        eventTime: Long,
        actionMasked: Int,
        actionIndex: Int,
        vararg pointers: TestPointer
    ) {
        val properties = Array(pointers.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = pointers[index].id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(pointers.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = pointers[index].point.x
                y = pointers[index].point.y
                pressure = 1f
                size = 1f
            }
        }
        val action = actionMasked or
            (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            pointers.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
        try {
            onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun pointer(id: Int, point: Point): TestPointer = TestPointer(id, point)

    private fun runOnMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private data class Point(val x: Float, val y: Float)

    private data class TestPointer(val id: Int, val point: Point)

    private class RecordingQwertyKeyListener :
        com.kazumaproject.core.domain.listener.QWERTYKeyListener {

        val taps = mutableListOf<Char>()
        val upFlicks = mutableListOf<QWERTYKey>()
        val releasedKeys = mutableListOf<QWERTYKey>()

        override fun onPressedQWERTYKey(qwertyKey: QWERTYKey) = Unit

        override fun onReleasedQWERTYKey(
            qwertyKey: QWERTYKey,
            tap: Char?,
            variations: List<Char>?
        ) {
            releasedKeys += qwertyKey
            tap?.let(taps::add)
        }

        override fun onLongPressQWERTYKey(qwertyKey: QWERTYKey) = Unit

        override fun onFlickUPQWERTYKey(
            qwertyKey: QWERTYKey,
            tap: Char?,
            variations: List<Char>?
        ) {
            upFlicks += qwertyKey
        }

        override fun onFlickDownQWERTYKey(
            qwertyKey: QWERTYKey,
            character: Char
        ) = Unit
    }

    private class RecordingGlideInputListener : QwertyGlideInputListener {
        var started = 0
        var cancelled = 0

        override fun onQwertyGlideStarted() {
            started += 1
        }

        override fun onQwertyGlideUpdated(
            inputPointers: QwertyInputPointers,
            proximityInfo: QwertyKeyboardProximityInfo
        ) = Unit

        override fun onQwertyGlideEnded(
            inputPointers: QwertyInputPointers,
            proximityInfo: QwertyKeyboardProximityInfo
        ) = Unit

        override fun onQwertyGlideCancelled() {
            cancelled += 1
        }
    }
}
