package com.kazumaproject.custom_keyboard

import android.os.SystemClock
import android.os.Debug
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.data.DoubleTapBinding
import com.kazumaproject.custom_keyboard.data.DoubleTapPolicy
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyCharacterCase
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.custom_keyboard.view.AutoSizeButton
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class DoubleTapFlickKeyboardInstrumentedTest {
    @Test
    fun qwertyCharacterCaseRefresh_measuresRuntimeAndAllocationOnAndroidRuntime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var elapsedNanos = 0L
        var allocatedBytes = 0L
        val iterations = 200

        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(
                instrumentation.targetContext,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
            val keyboard = FlickKeyboardView(context).apply {
                setKeyboard(KeyboardDefaultLayouts.createQwertyTemplateLayout())
                val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY)
                measure(widthSpec, heightSpec)
                layout(0, 0, 1080, 480)
            }

            repeat(10) {
                keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)
                keyboard.setKeyCharacterCase(KeyCharacterCase.AS_DEFINED)
            }
            val allocatedBefore =
                Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L
            val startNanos = SystemClock.elapsedRealtimeNanos()
            repeat(iterations) { index ->
                keyboard.setKeyCharacterCase(
                    if (index % 2 == 0) {
                        KeyCharacterCase.UPPERCASE
                    } else {
                        KeyCharacterCase.AS_DEFINED
                    }
                )
            }
            elapsedNanos = SystemClock.elapsedRealtimeNanos() - startNanos
            val allocatedAfter =
                Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: allocatedBefore
            allocatedBytes = allocatedAfter - allocatedBefore
            assertEquals("q", (keyboard.getChildAt(0) as AutoSizeButton).text.toString())
        }

        val averageMicros = elapsedNanos / iterations / 1_000.0
        val averageAllocatedBytes = allocatedBytes / iterations
        println(
            "KEY_CHARACTER_CASE_PERF iterations=$iterations " +
                    "averageUs=$averageMicros allocatedBytesPerToggle=$averageAllocatedBytes"
        )
    }

    @Test
    fun qwertyKeyLabel_updatesWithoutRebuildingOnAndroidRuntime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(
                instrumentation.targetContext,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
            val keyboard = FlickKeyboardView(context).apply {
                setKeyboard(
                    KeyboardLayout(
                        keys = listOf(
                            KeyData(
                                label = "q",
                                row = 0,
                                column = 0,
                                isFlickable = false,
                                action = KeyAction.Text("q"),
                                keyId = "q",
                                keyType = KeyType.NORMAL
                            )
                        ),
                        flickKeyMaps = emptyMap(),
                        columnCount = 1,
                        rowCount = 1
                    )
                )
            }
            val key = keyboard.getChildAt(0) as AutoSizeButton
            assertEquals("q", key.text.toString())

            keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)
            assertEquals("Q", key.text.toString())

            keyboard.setKeyCharacterCase(KeyCharacterCase.AS_DEFINED)
            assertEquals("q", key.text.toString())
        }
    }

    @Test
    fun shiftDoubleTap_dispatchesPromotedCapsLockActionOnAndroidRuntime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val actions = CopyOnWriteArrayList<KeyAction>()
        lateinit var keyboard: FlickKeyboardView

        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(
                instrumentation.targetContext,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
            keyboard = FlickKeyboardView(context).apply {
                setOnKeyboardActionListener(RecordingListener(actions))
                setKeyboard(
                    KeyboardLayout(
                        keys = listOf(
                            KeyData(
                                label = "",
                                row = 0,
                                column = 0,
                                isFlickable = false,
                                action = KeyAction.ShiftKey,
                                isSpecialKey = true,
                                keyId = "shift",
                                keyType = KeyType.NORMAL,
                                doubleTapBinding = DoubleTapBinding(
                                    action = KeyAction.CapLockKey,
                                    policy = DoubleTapPolicy.PROMOTE
                                )
                            )
                        ),
                        flickKeyMaps = emptyMap(),
                        columnCount = 1,
                        rowCount = 1
                    )
                )
                val widthSpec = View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY)
                measure(widthSpec, heightSpec)
                layout(0, 0, 240, 120)
            }
            tapCenter(keyboard)
        }

        SystemClock.sleep(100)
        instrumentation.runOnMainSync {
            tapCenter(keyboard)
        }

        assertEquals(listOf(KeyAction.ShiftKey, KeyAction.CapLockKey), actions)
    }

    @Test
    fun normalCharacterKey_dispatchesRepeatedTextEvenWithInvalidPersistedBinding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val actions = CopyOnWriteArrayList<KeyAction>()

        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(
                instrumentation.targetContext,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
            val keyboard = FlickKeyboardView(context).apply {
                setOnKeyboardActionListener(RecordingListener(actions))
                setKeyboard(
                    KeyboardLayout(
                        keys = listOf(
                            KeyData(
                                label = "a",
                                row = 0,
                                column = 0,
                                isFlickable = false,
                                action = KeyAction.Text("a"),
                                isSpecialKey = false,
                                keyId = "a",
                                keyType = KeyType.NORMAL,
                                doubleTapBinding = DoubleTapBinding(
                                    action = KeyAction.Copy,
                                    policy = DoubleTapPolicy.PROMOTE
                                )
                            )
                        ),
                        flickKeyMaps = emptyMap(),
                        columnCount = 1,
                        rowCount = 1
                    )
                )
                val widthSpec = View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY)
                measure(widthSpec, heightSpec)
                layout(0, 0, 240, 120)
            }

            tapCenter(keyboard)
            assertEquals(listOf(KeyAction.Text("a")), actions)
            tapCenter(keyboard)
        }

        assertEquals(listOf(KeyAction.Text("a"), KeyAction.Text("a")), actions)
    }

    private fun tapCenter(keyboard: FlickKeyboardView) {
        val key = keyboard.getChildAt(0)
        val x = key.left + key.width / 2f
        val y = key.top + key.height / 2f
        val downTime = SystemClock.uptimeMillis()
        MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).also {
            keyboard.onTouchEvent(it)
            it.recycle()
        }
        MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_UP, x, y, 0).also {
            keyboard.onTouchEvent(it)
            it.recycle()
        }
    }

    private class RecordingListener(
        private val actions: MutableList<KeyAction>
    ) : FlickKeyboardView.OnKeyboardActionListener {
        override fun onPress(action: KeyAction) = Unit
        override fun onAction(action: KeyAction, isFlick: Boolean) {
            actions += action
        }
        override fun onActionLongPress(action: KeyAction) = Unit
        override fun onActionUpAfterLongPress(action: KeyAction) = Unit
        override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
        override fun onFlickActionLongPress(action: KeyAction) = Unit
        override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
    }
}
