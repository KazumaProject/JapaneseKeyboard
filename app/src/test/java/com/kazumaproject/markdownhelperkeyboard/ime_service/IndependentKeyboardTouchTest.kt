package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.app.Application
import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.gojuon_keyboard.GojuonKeyboardView
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Runs the same pointer streams through every actual keyboard recognizer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class IndependentKeyboardTouchTest {
    private enum class Kind { TENKEY, GOJUON, QWERTY, CUSTOM }
    private data class Point(val x: Float, val y: Float)
    private data class Pointer(val id: Int, val point: Point)
    private class Keyboard(
        val view: View,
        val keys: List<View>,
        val names: List<String>,
        val committed: MutableList<String>,
        val longPresses: MutableList<String>,
        val enable: (Boolean) -> Unit,
        val dispatch: (MotionEvent) -> Unit
    )

    @Test fun disabledAndDefaultKeepEagerCommitOnSecondFingerDown() {
        Kind.entries.forEach { kind ->
            for (explicitOff in listOf(false, true)) {
                val k = keyboard(kind, enabled = false, applySetting = explicitOff)
                val a = pointer(0, k.keys[0]); val b = pointer(1, k.keys[1])
                send(k, MotionEvent.ACTION_DOWN, 0, a)
                send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
                assertEquals("$kind default/off eagerly commits first", listOf(k.names[0]), k.committed)
                send(k, MotionEvent.ACTION_POINTER_UP, 1, a, b)
                send(k, MotionEvent.ACTION_UP, 0, a)
                assertEquals("$kind has no duplicate release", k.names.take(2), k.committed)
            }
        }
    }

    @Test fun enabledCommitsInEitherReleaseOrderAndKeepsOtherKeyPressed() {
        Kind.entries.forEach { kind ->
            for (newerFirst in listOf(false, true)) {
                val k = keyboard(kind)
                val a = pointer(3, k.keys[0]); val b = pointer(19, k.keys[1])
                send(k, MotionEvent.ACTION_DOWN, 0, a)
                send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
                assertEquals("$kind no eager commit", emptyList<String>(), k.committed)
                assertTrue("$kind first key pressed", k.keys[0].isPressed)
                assertTrue("$kind second key pressed", k.keys[1].isPressed)
                send(k, MotionEvent.ACTION_POINTER_UP, if (newerFirst) 1 else 0, a, b)
                val remaining = if (newerFirst) a else b
                assertTrue("$kind remaining key pressed", k.keys[if (newerFirst) 0 else 1].isPressed)
                send(k, MotionEvent.ACTION_UP, 0, remaining)
                val expected = if (newerFirst) k.names.take(2).reversed() else k.names.take(2)
                assertEquals("$kind release order", expected, k.committed)
                assertFalse(k.keys[0].isPressed)
                assertFalse(k.keys[1].isPressed)
            }
        }
    }

    @Test fun sameKeySecondTouchIsIgnoredUntilItLifts() {
        Kind.entries.forEach { kind ->
            val k = keyboard(kind)
            val a = pointer(2, k.keys[0]); val duplicate = a.copy(id = 9)
            send(k, MotionEvent.ACTION_DOWN, 0, a)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, duplicate)
            send(k, MotionEvent.ACTION_POINTER_UP, 0, a, duplicate)
            assertEquals("$kind one commit", listOf(k.names[0]), k.committed)
            send(k, MotionEvent.ACTION_MOVE, 0, duplicate)
            send(k, MotionEvent.ACTION_UP, 0, duplicate)
            assertEquals("$kind ignored finger never commits", listOf(k.names[0]), k.committed)
        }
    }

    @Test fun threePointersRemainIndependentAfterIndicesChange() {
        Kind.entries.forEach { kind ->
            val k = keyboard(kind)
            val a = pointer(3, k.keys[0]); val b = pointer(7, k.keys[1]); val c = pointer(21, k.keys[2])
            send(k, MotionEvent.ACTION_DOWN, 0, a)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 2, a, b, c)
            send(k, MotionEvent.ACTION_POINTER_UP, 1, a, b, c)
            send(k, MotionEvent.ACTION_POINTER_UP, 1, a, c)
            send(k, MotionEvent.ACTION_UP, 0, a)
            assertEquals("$kind three pointers", listOf(k.names[1], k.names[2], k.names[0]), k.committed)
        }
    }

    @Test fun cancellationReleasesEveryKeyAndLongPressTimerWithoutCommitting() {
        Kind.entries.forEach { kind ->
            val k = keyboard(kind)
            val a = pointer(3, k.keys[0]); val b = pointer(19, k.keys[1])
            send(k, MotionEvent.ACTION_DOWN, 0, a)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
            send(k, MotionEvent.ACTION_CANCEL, 0, a, b)
            shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
            assertEquals("$kind canceled input", emptyList<String>(), k.committed)
            assertEquals("$kind canceled timers", emptyList<String>(), k.longPresses)
            assertTrue("$kind released visuals", k.keys.none { it.isPressed })
        }
    }

    @Test fun changingSettingMidStreamTakesEffectOnNextDown() {
        Kind.entries.forEach { kind ->
            val k = keyboard(kind)
            val a = pointer(0, k.keys[0]); val b = pointer(1, k.keys[1])
            send(k, MotionEvent.ACTION_DOWN, 0, a)
            k.enable(false)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
            assertTrue("$kind stream remains independent", k.committed.isEmpty())
            send(k, MotionEvent.ACTION_POINTER_UP, 1, a, b)
            send(k, MotionEvent.ACTION_UP, 0, a)
            assertEquals(k.names.take(2).reversed(), k.committed)
            k.committed.clear()
            send(k, MotionEvent.ACTION_DOWN, 0, a)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
            assertEquals("$kind next stream is legacy", listOf(k.names[0]), k.committed)
            send(k, MotionEvent.ACTION_CANCEL, 0, a, b)
        }
    }

    @Test fun heldFirstFingerCanStillFlickAfterSecondFingerStarts() {
        Kind.entries.forEach { kind ->
            val k = keyboard(kind)
            val a = pointer(3, k.keys[0]); val b = pointer(19, k.keys[1])
            val flickA = a.copy(point = a.point.copy(y = a.point.y - 160))
            send(k, MotionEvent.ACTION_DOWN, 0, a)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
            send(k, MotionEvent.ACTION_MOVE, 0, flickA, b)
            assertTrue("$kind MOVE doesn't commit", k.committed.isEmpty())
            send(k, MotionEvent.ACTION_POINTER_UP, 1, flickA, b)
            send(k, MotionEvent.ACTION_UP, 0, flickA)
            assertEquals("$kind preserves first flick", listOf(k.names[1], k.names[0] + "↑"), k.committed)
        }
    }

    @Test fun releasingAnotherKeyDoesNotCancelTheHeldFingerLongPress() {
        listOf(Kind.TENKEY, Kind.GOJUON, Kind.QWERTY).forEach { kind ->
            val k = keyboard(kind)
            val a = pointer(3, k.keys[0]); val b = pointer(19, k.keys[1])
            send(k, MotionEvent.ACTION_DOWN, 0, a)
            shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
            send(k, MotionEvent.ACTION_POINTER_UP, 1, a, b)
            shadowOf(Looper.getMainLooper()).idleFor(901, TimeUnit.MILLISECONDS)
            assertEquals("$kind first timer still belongs to first finger", 1, k.longPresses.size)
            assertTrue(k.keys[0].isPressed)
            send(k, MotionEvent.ACTION_CANCEL, 0, a)
        }
    }

    @Test fun realViewDispatchRoutesBothPointersToTheIndependentRecognizer() {
        Kind.entries.forEach { kind ->
            val k = keyboard(kind)
            val a = pointer(3, k.keys[0]); val b = pointer(19, k.keys[1])
            send(k, MotionEvent.ACTION_DOWN, 0, a, realDispatch = true)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, b, realDispatch = true)
            assertTrue("$kind real dispatch holds both", k.committed.isEmpty())
            send(k, MotionEvent.ACTION_POINTER_UP, 1, a, b, realDispatch = true)
            send(k, MotionEvent.ACTION_UP, 0, a, realDispatch = true)
            assertEquals("$kind real dispatch release order", k.names.take(2).reversed(), k.committed)
        }
    }

    @Test fun hidingKeyboardCancelsBothPointersAndTheirTimers() {
        Kind.entries.forEach { kind ->
            val k = keyboard(kind)
            val a = pointer(3, k.keys[0]); val b = pointer(19, k.keys[1])
            send(k, MotionEvent.ACTION_DOWN, 0, a)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
            k.view.visibility = View.GONE
            shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
            send(k, MotionEvent.ACTION_POINTER_UP, 1, a, b)
            send(k, MotionEvent.ACTION_UP, 0, a)
            assertTrue("$kind hidden keyboard has no commits", k.committed.isEmpty())
            assertTrue("$kind hidden keyboard has no long presses", k.longPresses.isEmpty())
            assertTrue("$kind hidden keyboard has no held keys", k.keys.none { it.isPressed })
        }
    }

    @Test fun qwertyCursorModeFollowsTheSpaceOwnerWhenItIsTheSecondPointer() {
        val k = keyboard(Kind.QWERTY)
        val keyboard = k.view as QWERTYKeyboardView
        val a = pointer(3, k.keys[0])
        val space = pointer(19, keyboard.findViewById(com.kazumaproject.qwerty_keyboard.R.id.key_space))
        send(k, MotionEvent.ACTION_DOWN, 0, a)
        send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, space)
        keyboard.setCursorMode(true)
        assertFalse("Cursor mode cancels the unrelated letter", k.keys[0].isPressed)
        val movedLetter = a.copy(point = a.point.copy(x = a.point.x + 60))
        send(k, MotionEvent.ACTION_MOVE, 0, movedLetter, space)
        assertTrue("The unrelated pointer cannot move the cursor", k.committed.isEmpty())
        val movedSpace = space.copy(point = space.point.copy(x = space.point.x + 60))
        send(k, MotionEvent.ACTION_MOVE, 0, movedLetter, movedSpace)
        assertEquals(listOf("KeyCursorRight"), k.committed)
        send(k, MotionEvent.ACTION_POINTER_UP, 1, movedLetter, movedSpace)
        send(k, MotionEvent.ACTION_UP, 0, movedLetter)
        assertEquals("Neither pointer adds a text tap", listOf("KeyCursorRight"), k.committed)
    }

    @Test fun generatedSumireLayoutAlsoCommitsThroughRealViewDispatchInReleaseOrder() {
        val layout = KeyboardDefaultLayouts.createFinalLayout(
            KeyboardInputMode.HIRAGANA, emptyMap(), "toggle", "default"
        )
        for (enabled in listOf(false, true)) {
            val k = keyboard(Kind.CUSTOM, enabled = enabled, customLayout = layout)
            val a = pointer(3, k.keys[0]); val ka = pointer(19, k.keys[1])
            send(k, MotionEvent.ACTION_DOWN, 0, a, realDispatch = true)
            send(k, MotionEvent.ACTION_POINTER_DOWN, 1, a, ka, realDispatch = true)
            assertEquals(if (enabled) emptyList<String>() else listOf("あ"), k.committed)
            send(k, MotionEvent.ACTION_POINTER_UP, 1, a, ka, realDispatch = true)
            send(k, MotionEvent.ACTION_UP, 0, a, realDispatch = true)
            assertEquals(if (enabled) listOf("か", "あ") else listOf("あ", "か"), k.committed)
        }
    }

    private fun keyboard(kind: Kind, enabled: Boolean = true, applySetting: Boolean = true, customLayout: KeyboardLayout? = null): Keyboard {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val committed = mutableListOf<String>()
        val longPresses = mutableListOf<String>()
        val listener = object : FlickListener {
            override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                if (gestureType != GestureType.Down && gestureType != GestureType.Null) {
                    committed += key.toString() + if (gestureType == GestureType.FlickTop) "↑" else ""
                }
            }
        }
        val longListener = object : LongPressListener {
            override fun onLongPress(key: Key) { longPresses += key.toString() }
        }
        lateinit var view: View
        lateinit var enable: (Boolean) -> Unit
        lateinit var dispatch: (MotionEvent) -> Unit
        val ids: List<Int>
        val names: List<String>
        when (kind) {
            Kind.TENKEY -> {
                val keyboard = TenKey(context, Robolectric.buildAttributeSet().build())
                keyboard.applyKeyboardTheme(
                    themeMode = "default", currentNightMode = 16, isDynamicColorEnabled = false,
                    customBgColor = -1, customKeyColor = -1, customSpecialKeyColor = -1,
                    customKeyTextColor = -16777216, customSpecialKeyTextColor = -16777216,
                    liquidGlassEnable = false, customBorderEnable = false, customBorderColor = 0,
                    liquidGlassKeyAlphaEnable = 255, borderWidth = 1
                )
                keyboard.setOnFlickListener(listener)
                keyboard.setOnLongPressListener(longListener)
                keyboard.setLongPressTimeout(1000)
                view = keyboard; enable = keyboard::setIndependentMultiTouchEnabled
                dispatch = { keyboard.onTouch(keyboard, it) }
                ids = listOf(com.kazumaproject.tenkey.R.id.key_1, com.kazumaproject.tenkey.R.id.key_2, com.kazumaproject.tenkey.R.id.key_3)
                names = listOf("KeyA", "KeyKA", "KeySA")
            }
            Kind.GOJUON -> {
                val keyboard = GojuonKeyboardView(context)
                keyboard.applyKeyboardTheme(
                    themeMode = "default", currentNightMode = 16, isDynamicColorEnabled = false,
                    customBgColor = -1, customKeyColor = -1, customSpecialKeyColor = -1,
                    customKeyTextColor = -16777216, customSpecialKeyTextColor = -16777216,
                    liquidGlassEnable = false, customBorderEnable = false, customBorderColor = 0,
                    liquidGlassKeyAlphaEnable = 255, borderWidth = 1
                )
                keyboard.setOnFlickListener(listener)
                keyboard.setOnLongPressListener(longListener)
                keyboard.setLongPressTimeout(1000)
                view = keyboard; enable = keyboard::setIndependentMultiTouchEnabled
                dispatch = { keyboard.onTouch(keyboard, it) }
                ids = listOf(com.kazumaproject.gojuon_keyboard.R.id.key_51, com.kazumaproject.gojuon_keyboard.R.id.key_46, com.kazumaproject.gojuon_keyboard.R.id.key_41)
                names = listOf("KeyA", "KeyKA", "KeySA")
            }
            Kind.QWERTY -> {
                val keyboard = QWERTYKeyboardView(context)
                keyboard.setPopUpViewState(false)
                keyboard.setFlickUpDetectionEnabled(true)
                keyboard.setLongPressTimeout(1000)
                keyboard.setOnQWERTYKeyListener(object : QWERTYKeyListener {
                    override fun onPressedQWERTYKey(qwertyKey: QWERTYKey) = Unit
                    override fun onReleasedQWERTYKey(qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?) {
                        committed += qwertyKey.toString().removePrefix("QWERTY")
                    }
                    override fun onLongPressQWERTYKey(qwertyKey: QWERTYKey) { longPresses += qwertyKey.toString() }
                    override fun onFlickUPQWERTYKey(qwertyKey: QWERTYKey, tap: Char?, variations: List<Char>?) {
                        committed += qwertyKey.toString().removePrefix("QWERTY") + "↑"
                    }
                    override fun onFlickDownQWERTYKey(qwertyKey: QWERTYKey, character: Char) = Unit
                })
                view = keyboard; enable = keyboard::setIndependentMultiTouchEnabled
                dispatch = keyboard::onTouchEvent.let { method -> { event -> method(event); Unit } }
                ids = listOf(com.kazumaproject.qwerty_keyboard.R.id.key_a, com.kazumaproject.qwerty_keyboard.R.id.key_b, com.kazumaproject.qwerty_keyboard.R.id.key_c)
                names = listOf("KeyA", "KeyB", "KeyC")
            }
            Kind.CUSTOM -> {
                val keyboard = FlickKeyboardView(context)
                val keys = listOf("a", "b", "c").mapIndexed { index, text ->
                    KeyData(label = text, row = 0, column = index, isFlickable = true,
                        action = KeyAction.Text(text), keyId = text, keyType = KeyType.STANDARD_FLICK)
                }
                keyboard.setKeyboard(customLayout ?: KeyboardLayout(keys, keys.associate { key -> key.label to listOf(mapOf(
                    FlickDirection.TAP to FlickAction.Input(key.label),
                    FlickDirection.UP to FlickAction.Input(key.label + "↑"))) }, 3, 1))
                keyboard.setLongPressTimeout(1000)
                keyboard.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
                    override fun onPress(action: KeyAction) = Unit
                    override fun onAction(action: KeyAction, isFlick: Boolean) { if (action is KeyAction.Text) committed += action.text }
                    override fun onActionLongPress(action: KeyAction) { longPresses += action.toString() }
                    override fun onActionUpAfterLongPress(action: KeyAction) = Unit
                    override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
                    override fun onFlickActionLongPress(action: KeyAction) { longPresses += action.toString() }
                    override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
                })
                view = keyboard; enable = keyboard::setIndependentMultiTouchEnabled
                dispatch = { keyboard.onTouchEvent(it) }
                ids = emptyList(); names = if (customLayout == null) listOf("a", "b", "c") else listOf("あ", "か", "た")
            }
        }
        activity.setContentView(view)
        shadowOf(Looper.getMainLooper()).idle()
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 500)
        val keys = if (view is FlickKeyboardView) {
            if (customLayout == null) (0..2).map { view.getChildAt(it) }
            else names.map { label -> view.getChildAt(customLayout.items.indexOfFirst { it is KeyItem && it.keyData.label == label }) }
        } else ids.map { view.findViewById<View>(it) }
        assertTrue("$kind keys measured", keys.all { it.width > 0 && it.height > 0 })
        if (applySetting) enable(enabled)
        return Keyboard(view, keys, names, committed, longPresses, enable, dispatch)
    }

    private fun pointer(id: Int, key: View): Pointer {
        val location = IntArray(2).also(key::getLocationOnScreen)
        return Pointer(id, Point(location[0] + key.width / 2f, location[1] + key.height / 2f))
    }

    private fun send(k: Keyboard, action: Int, index: Int, vararg pointers: Pointer, realDispatch: Boolean = false) {
        val props = Array(pointers.size) { i -> MotionEvent.PointerProperties().apply { id = pointers[i].id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(pointers.size) { i -> MotionEvent.PointerCoords().apply { x = pointers[i].point.x; y = pointers[i].point.y; pressure = 1f; size = 1f } }
        val event = MotionEvent.obtain(100L, 120L, action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            pointers.size, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        val location = IntArray(2).also(k.view::getLocationOnScreen)
        event.offsetLocation(-location[0].toFloat(), -location[1].toFloat())
        try { if (realDispatch) k.view.dispatchTouchEvent(event) else k.dispatch(event) } finally { event.recycle() }
    }
}
