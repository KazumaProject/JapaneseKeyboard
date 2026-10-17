package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.content.Intent
import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.SumireFlickInputMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickRotation
import com.kazumaproject.custom_keyboard.data.TfbiGuidePopupState
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.*
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the generated built-in layout in a real window without changing the active IME. */
class SumireFlickRotationTestActivity : Activity() {
    lateinit var keyboard: FlickKeyboardView
    private lateinit var root: FrameLayout
    val actions = mutableListOf<KeyAction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)
    }

    fun configure(
        style: String,
        rotation: TfbiFlickRotation,
        popup: TfbiPopupPresentationMode,
        mode: SumireFlickInputMode = SumireFlickInputMode.ALL
    ) {
        root.removeAllViews()
        actions.clear()
        keyboard = FlickKeyboardView(this).apply {
            setLongPressTimeout(2000)
            setFlickSensitivityValue(1)
            setTfbiPopupPresentationMode(popup)
            setKeyboard(KeyboardDefaultLayouts.createFinalLayout(
                mode = KeyboardInputMode.HIRAGANA,
                dynamicKeyStates = emptyMap(),
                inputLayoutType = "flick",
                inputStyle = style,
                tfbiFlickRotation = rotation,
                sumireFlickInputMode = mode
            ))
            setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
                override fun onPress(action: KeyAction) = Unit
                override fun onAction(action: KeyAction, isFlick: Boolean) { actions += action }
                override fun onActionLongPress(action: KeyAction) = Unit
                override fun onActionUpAfterLongPress(action: KeyAction) = Unit
                override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
                override fun onFlickActionLongPress(action: KeyAction) = Unit
                override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
            })
        }
        root.addView(keyboard, FrameLayout.LayoutParams(-1, 800, Gravity.BOTTOM))
    }

    fun kaKey(): View = (0 until keyboard.childCount).map(keyboard::getChildAt)
        .filterIsInstance<AutoSizeButton>().first { it.text.toString() == "か" }
}

@RunWith(AndroidJUnit4::class)
class SumireFlickRotationInstrumentedTest {
    @Test
    fun generatedSecondAndThirdFlickLayoutsRotateDakutenWithTheirPopups() = withWindow { scenario ->
        for (style in listOf("second-flick", "third-flick")) {
            for (rotation in TfbiFlickRotation.entries) for (popup in POPUP_MODES) {
                configure(scenario, style, rotation, popup)
                val paths = listOf(
                    Triple(LEFT, if (rotation == TfbiFlickRotation.CLOCKWISE) UP_LEFT else DOWN_LEFT, "ぎ"),
                    Triple(UP, if (rotation == TfbiFlickRotation.CLOCKWISE) UP_RIGHT else UP_LEFT, "ぐ"),
                    Triple(RIGHT, if (rotation == TfbiFlickRotation.COUNTERCLOCKWISE) UP_RIGHT else DOWN_RIGHT, "げ"),
                    Triple(DOWN, if (rotation == TfbiFlickRotation.CLOCKWISE) DOWN_LEFT else DOWN_RIGHT, "ご")
                )
                for ((first, second, expected) in paths) {
                    val gesture = Gesture(scenario, style, popup)
                    gesture.down()
                    gesture.move(first)
                    scenario.onActivity { assertPopupOption(it, popup, second, expected) }
                    gesture.move(second)
                    scenario.onActivity { assertPopupSelection(it, popup, second, expected) }
                    gesture.up()
                    scenario.onActivity {
                        assertEquals("$style $rotation $popup $first/$second",
                            listOf(KeyAction.Text(expected)), it.actions)
                        assertPopupDismissed(it, popup)
                    }
                }
            }
        }
    }

    @Test
    fun generatedThirdFlickLayoutPreservesYoonLongVowelsAfterRotatedDakuten() = withWindow { scenario ->
        for (rotation in TfbiFlickRotation.entries) for (popup in POPUP_MODES) {
            configure(scenario, "third-flick", rotation, popup)
            val yoon = UP
            val longVowel = UP_LEFT
            for (dakuten in listOf(false, true)) {
                val gesture = Gesture(scenario, "third-flick", popup)
                gesture.down()
                gesture.move(LEFT)
                if (dakuten) {
                    gesture.move(if (rotation == TfbiFlickRotation.CLOCKWISE) UP_LEFT else DOWN_LEFT)
                }
                val base = if (dakuten) "ぎゅ" else "きゅ"
                scenario.onActivity {
                    assertPopupOption(it, popup, yoon,
                        if (popup == TfbiPopupPresentationMode.GUIDE_ABOVE_KEY && !dakuten) "ゅ" else base)
                }
                gesture.move(yoon)
                scenario.onActivity {
                    assertPopupOption(it, popup, longVowel,
                        if (popup == TfbiPopupPresentationMode.GUIDE_ABOVE_KEY) "う" else "${base}う")
                }
                gesture.move(longVowel)
                scenario.onActivity { assertPopupSelection(it, popup, longVowel, "${base}う") }
                gesture.up()
                scenario.onActivity {
                    assertEquals("$rotation $popup dakuten=$dakuten",
                        listOf(KeyAction.Text("${base}う")), it.actions)
                    assertPopupDismissed(it, popup)
                }
            }
        }
    }

    @Test
    fun cancellingRotatedDakutenGestureClearsPopupAndNextGestureStartsNormally() = withWindow { scenario ->
        for (style in listOf("second-flick", "third-flick")) {
            for (rotation in TfbiFlickRotation.entries) for (popup in POPUP_MODES) {
                configure(scenario, style, rotation, popup)
                val gesture = Gesture(scenario, style, popup)
                gesture.down()
                gesture.move(LEFT)
                gesture.move(if (rotation == TfbiFlickRotation.CLOCKWISE) UP_LEFT else DOWN_LEFT)
                gesture.cancel()
                scenario.onActivity {
                    assertTrue(it.actions.isEmpty())
                    assertPopupDismissed(it, popup)
                }
                val next = Gesture(scenario, style, popup)
                next.down()
                next.move(LEFT)
                next.up()
                scenario.onActivity {
                    assertEquals("$style $rotation $popup", listOf(KeyAction.Text("き")), it.actions)
                    assertPopupDismissed(it, popup)
                }
            }
        }
    }

    @Test
    fun selectedInputModeControlsAvailablePopupOptionsAndCommittedGestures() = withWindow { scenario ->
        for (mode in SumireFlickInputMode.entries) {
            for (rotation in listOf(TfbiFlickRotation.DEFAULT, TfbiFlickRotation.CLOCKWISE)) {
                for (popup in POPUP_MODES) {
                    configure(scenario, "third-flick", rotation, popup, mode)
                    val voicedDirection = if (rotation == TfbiFlickRotation.CLOCKWISE) UP_LEFT else DOWN_LEFT
                    val gesture = Gesture(scenario, "third-flick", popup)
                    gesture.down()
                    gesture.move(LEFT)
                    scenario.onActivity {
                        if (mode == SumireFlickInputMode.CONTRACTED) {
                            assertPopupOptionAbsent(it, popup, voicedDirection)
                        } else {
                            assertPopupOption(it, popup, voicedDirection, "ぎ")
                        }
                        if (mode == SumireFlickInputMode.VOICED) {
                            assertPopupOptionAbsent(it, popup, UP)
                            assertPopupOptionAbsent(it, popup, RIGHT)
                            assertPopupOptionAbsent(it, popup, DOWN)
                        } else {
                            assertPopupOption(it, popup, UP,
                                if (popup == TfbiPopupPresentationMode.GUIDE_ABOVE_KEY) "ゅ" else "きゅ")
                        }
                    }
                    val expected = if (mode == SumireFlickInputMode.VOICED) {
                        gesture.move(voicedDirection)
                        "ぎ"
                    } else {
                        gesture.move(UP)
                        gesture.move(UP_LEFT)
                        "きゅう"
                    }
                    gesture.up()
                    scenario.onActivity {
                        assertEquals("$mode $rotation $popup", listOf(KeyAction.Text(expected)), it.actions)
                    }
                }
            }
        }
    }

    @Test
    fun replacingInputModeCancelsCurrentVoicedGestureAndUsesNewPopupMap() = withWindow { scenario ->
        for (popup in POPUP_MODES) {
            configure(scenario, "third-flick", TfbiFlickRotation.CLOCKWISE, popup)
            val gesture = Gesture(scenario, "third-flick", popup)
            gesture.down()
            gesture.move(LEFT)
            gesture.move(UP_LEFT)
            scenario.onActivity {
                it.keyboard.setKeyboard(KeyboardDefaultLayouts.createFinalLayout(
                    mode = KeyboardInputMode.HIRAGANA,
                    dynamicKeyStates = emptyMap(),
                    inputLayoutType = "flick",
                    inputStyle = "third-flick",
                    tfbiFlickRotation = TfbiFlickRotation.CLOCKWISE,
                    sumireFlickInputMode = SumireFlickInputMode.CONTRACTED
                ))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            gesture.up(expectHandled = false)
            scenario.onActivity {
                assertTrue(it.actions.isEmpty())
                assertPopupDismissed(it, popup)
            }
            val next = Gesture(scenario, "third-flick", popup)
            next.down()
            next.move(LEFT)
            scenario.onActivity { assertPopupOptionAbsent(it, popup, UP_LEFT) }
            next.move(UP)
            next.move(UP_LEFT)
            next.up()
            scenario.onActivity { assertEquals(listOf(KeyAction.Text("きゅう")), it.actions) }
        }
    }

    private fun withWindow(block: (ActivityScenario<SumireFlickRotationTestActivity>) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().context
        ActivityScenario.launch<SumireFlickRotationTestActivity>(Intent(
            context, SumireFlickRotationTestActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use(block)
    }

    private fun configure(
        scenario: ActivityScenario<SumireFlickRotationTestActivity>,
        style: String,
        rotation: TfbiFlickRotation,
        popup: TfbiPopupPresentationMode,
        mode: SumireFlickInputMode = SumireFlickInputMode.ALL
    ) {
        scenario.onActivity { it.configure(style, rotation, popup, mode) }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity {
            assertTrue("Keyboard must be attached and laid out", it.keyboard.isAttachedToWindow)
            assertTrue(it.kaKey().width > 0 && it.kaKey().height > 0)
        }
    }

    /** Preserve raw screen coordinates so FlickKeyboardView's production touch router is used. */
    private class Gesture(
        private val scenario: ActivityScenario<SumireFlickRotationTestActivity>,
        private val style: String,
        private val popup: TfbiPopupPresentationMode
    ) {
        private val start = PointF()
        private val point = PointF()
        private val firstPoint = PointF()
        private var moveCount = 0
        private var radius = 0f
        private val downTime = SystemClock.uptimeMillis()

        fun down() {
            scenario.onActivity {
                it.actions.clear()
                val key = it.kaKey()
                val origin = IntArray(2).also(key::getLocationOnScreen)
                start.set(origin[0] + key.width / 2f, origin[1] + key.height / 2f)
                point.set(start)
                radius = 60f * it.resources.displayMetrics.density
            }
            send(MotionEvent.ACTION_DOWN)
        }

        fun move(direction: TfbiFlickDirection) {
            val angle = Math.toRadians(when (direction) {
                RIGHT -> 0.0; DOWN_RIGHT -> 35.0; DOWN -> 90.0; DOWN_LEFT -> 125.0
                LEFT -> 180.0; UP_LEFT -> -125.0; UP -> -90.0; UP_RIGHT -> -35.0
                TAP -> error("Use down/up for taps")
            })
            val origin = if (moveCount > 0 && style == "second-flick" &&
                popup == TfbiPopupPresentationMode.LEGACY_GRID) firstPoint else start
            point.set(origin.x + cos(angle).toFloat() * radius,
                origin.y + sin(angle).toFloat() * radius)
            if (moveCount++ == 0) firstPoint.set(point)
            send(MotionEvent.ACTION_MOVE)
        }

        fun up(expectHandled: Boolean = true) = send(MotionEvent.ACTION_UP, expectHandled)
        fun cancel() = send(MotionEvent.ACTION_CANCEL)

        private fun send(action: Int, expectHandled: Boolean = true) {
            scenario.onActivity { activity ->
                val origin = IntArray(2).also(activity.keyboard::getLocationOnScreen)
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    point.x, point.y, 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                    offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
                }
                try {
                    val handled = activity.keyboard.dispatchTouchEvent(event)
                    if (expectHandled) assertTrue(handled)
                }
                finally { event.recycle() }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    private fun assertPopupOption(
        activity: SumireFlickRotationTestActivity,
        popup: TfbiPopupPresentationMode,
        direction: TfbiFlickDirection,
        expected: String
    ) {
        val controller = controller(activity)
        if (popup == TfbiPopupPresentationMode.LEGACY_GRID) {
            val window = field(controller, "popupWindow") as PopupWindow
            assertTrue("Production popup must be displayed", window.isShowing)
            val view = field(controller, "popupView")!!
            assertEquals(expected, (field(view, "petalCharacters") as Map<*, *>)[direction])
        } else {
            val host = guideHost(controller)
            assertNotNull((field(host, "guideView") as View).parent)
            val state = field(host, "currentState") as TfbiGuidePopupState
            assertEquals(expected, state.optionLabels[direction])
        }
    }

    private fun assertPopupSelection(
        activity: SumireFlickRotationTestActivity,
        popup: TfbiPopupPresentationMode,
        direction: TfbiFlickDirection,
        expected: String
    ) {
        val controller = controller(activity)
        if (popup == TfbiPopupPresentationMode.LEGACY_GRID) {
            assertPopupOption(activity, popup, direction, expected)
            assertEquals(direction, field(field(controller, "popupView")!!, "highlightedDirection"))
        } else {
            val state = field(guideHost(controller), "currentState") as TfbiGuidePopupState
            assertEquals(expected, state.currentText)
            assertEquals(direction, state.selectedOption)
        }
    }

    private fun assertPopupOptionAbsent(
        activity: SumireFlickRotationTestActivity,
        popup: TfbiPopupPresentationMode,
        direction: TfbiFlickDirection
    ) {
        val controller = controller(activity)
        val options = if (popup == TfbiPopupPresentationMode.LEGACY_GRID) {
            field(field(controller, "popupView")!!, "petalCharacters") as Map<*, *>
        } else {
            (field(guideHost(controller), "currentState") as TfbiGuidePopupState).optionLabels
        }
        assertTrue("Blocked option $direction must be absent",
            options[direction] == null || options[direction] == "")
    }

    private fun assertPopupDismissed(activity: SumireFlickRotationTestActivity, popup: TfbiPopupPresentationMode) {
        val controller = controller(activity)
        if (popup == TfbiPopupPresentationMode.LEGACY_GRID) {
            assertNull(field(controller, "popupWindow"))
        } else {
            val host = guideHost(controller)
            assertNull(field(host, "currentState"))
            assertNull((field(host, "guideView") as? View)?.parent)
        }
    }

    private fun controller(activity: SumireFlickRotationTestActivity): Any {
        val infos = field(activity.keyboard, "keyInfos") as List<*>
        val info = infos.first { field(it!!, "view") === activity.kaKey() }!!
        return field(info, "controller") ?: error("Generated ka key has no controller")
    }

    private fun guideHost(controller: Any): Any = controller::class.java
        .getDeclaredMethod("getGuidePopupHost").apply { isAccessible = true }.invoke(controller)!!

    private fun field(instance: Any, name: String): Any? = instance::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(instance)

    private companion object {
        val POPUP_MODES = listOf(TfbiPopupPresentationMode.LEGACY_GRID, TfbiPopupPresentationMode.GUIDE_ABOVE_KEY)
    }
}
