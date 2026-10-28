package com.kazumaproject.gesture_keyboard

import com.kazumaproject.custom_keyboard.data.KeyAction

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GesturePadViewTest {
    @Test fun releaseEmitsOneStrokeButCancelOutsideAndTapEmitNothing() {
        val pad = GesturePadView(ApplicationProvider.getApplicationContext())
        pad.layout(0, 0, 200, 200)
        var strokes = 0
        pad.onStroke = { strokes++ }
        fun event(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(0, 100, action, x, y, 0)
            pad.dispatchTouchEvent(event); event.recycle()
        }
        event(MotionEvent.ACTION_DOWN, 20f, 20f); event(MotionEvent.ACTION_MOVE, 100f, 100f); event(MotionEvent.ACTION_UP, 160f, 160f)
        assertEquals(1, strokes)
        event(MotionEvent.ACTION_DOWN, 20f, 20f); event(MotionEvent.ACTION_MOVE, 210f, 100f); event(MotionEvent.ACTION_UP, 160f, 160f)
        event(MotionEvent.ACTION_DOWN, 20f, 20f); event(MotionEvent.ACTION_CANCEL, 100f, 100f); event(MotionEvent.ACTION_UP, 160f, 160f)
        event(MotionEvent.ACTION_DOWN, 20f, 20f); event(MotionEvent.ACTION_UP, 21f, 21f)
        assertEquals(1, strokes)
    }
    @Test fun editorTapSelectsStableIdWithoutDispatchingCommand() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = GestureKeyboardView(context)
        var selected: String? = null; var commands = 0
        view.onControlSelected = { selected = it }
        view.onCommand = { commands++ }
        val key = GestureControlKey("custom-id", KeyAction.Delete, "Selected key")
        view.configure(GesturePresets.sets(), GestureSessionState(), GestureControlLayout(listOf(key), emptyList()))
        fun buttons(parent: android.view.ViewGroup): List<Button> = (0 until parent.childCount).flatMap {
            when (val child = parent.getChildAt(it)) {
                is Button -> listOf(child)
                is android.view.ViewGroup -> buttons(child)
                else -> emptyList()
            }
        }
        buttons(view).first { it.text == "Selected key" }.performClick()
        assertEquals("custom-id", selected)
        assertEquals(0, commands)
    }
    @Test fun appLanguageIsUsedForTheImeDisplayContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(android.app.LocaleManager::class.java)
        val previous = manager.applicationLocales
        try {
            manager.applicationLocales = android.os.LocaleList(java.util.Locale.JAPANESE)
            val view = GestureKeyboardView(context)
            assertEquals("1: かな", view.leftPad.contentDescription)
            assertNull(view.findViewById<Button>(R.id.gesture_set_left))
        } finally { manager.applicationLocales = previous }
    }
    @Test fun sharedModeActionsOnlyChangeTheActiveAreaAndUseDrawableIcons() {
        val view = GestureKeyboardView(ApplicationProvider.getApplicationContext())
        val state = GestureSessionState().withCount(4).copy(activePadId = "pad_3")
        val controls = GestureControlLayout(emptyList(), listOf(GestureControlKey("delete", KeyAction.Delete)))
        view.configure(GesturePresets.sets(), state, controls)
        assertTrue(view.handlePadAction(KeyAction.ShiftKey))
        assertTrue(view.session.pads[2].shift)
        assertFalse(view.session.pads[0].shift)
        assertTrue(view.handlePadAction(KeyAction.SwitchDirectMode))
        assertTrue(view.session.pads[2].direct)
        assertFalse(view.session.pads[3].direct)
        assertTrue(view.handlePadAction(KeyAction.SwitchToNumberLayout))
        assertEquals(GesturePresets.DIGITS, view.session.pads[2].setId)
        assertFalse(view.handlePadAction(KeyAction.Delete))
        fun buttons(parent: android.view.ViewGroup): List<Button> = (0 until parent.childCount).flatMap {
            when (val child = parent.getChildAt(it)) {
                is Button -> listOf(child)
                is android.view.ViewGroup -> buttons(child)
                else -> emptyList()
            }
        }
        val delete = buttons(view).single { it.contentDescription == GestureLabels.command(KeyAction.Delete, view.context) }
        assertEquals("", delete.text.toString())
        assertNotNull(delete.compoundDrawables[1])
        assertNull(view.findViewById<Button>(R.id.gesture_set_left))
        assertNotNull(view.findViewById<Button>(R.id.gesture_menu))
    }
    @Test fun enterTapCannotOvertakeTheStrokeThatWasJustReleased() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        val activity = controller.get()
        try {
            val view = GestureKeyboardView(activity)
            val entry = GestureEntry("one", GestureOutput.Reading("あ"), listOf(GesturePresets.path("0,0 10,10 20,0")))
            val set = GestureSet("one", "One", listOf(entry))
            val controls = GestureControlLayout(emptyList(), listOf(GestureControlKey("enter", KeyAction.Enter)))
            view.configure(listOf(set), GestureSessionState().withSet("pad_1", "one").withSet("pad_2", "one"), controls)
            activity.setContentView(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1080, 600)
            val events = mutableListOf<String>()
            view.onOutput = { events.add("reading"); true }
            view.onCommand = { events.add("enter") }
            fun event(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(0, 100, action, x, y, 0)
                view.leftPad.dispatchTouchEvent(event); event.recycle()
            }
            event(MotionEvent.ACTION_DOWN, 20f, 20f); event(MotionEvent.ACTION_MOVE, 100f, 100f)
            event(MotionEvent.ACTION_UP, 180f, 20f)
            fun buttons(parent: android.view.ViewGroup): List<Button> = (0 until parent.childCount).flatMap {
                when (val child = parent.getChildAt(it)) {
                    is Button -> listOf(child)
                    is android.view.ViewGroup -> buttons(child)
                    else -> emptyList()
                }
            }
            buttons(view).single { it.contentDescription == "Enter" }.performClick()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (events.size < 2 && System.nanoTime() < deadline) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertEquals(listOf("reading", "enter"), events)
        } finally { controller.pause().stop().destroy() }
    }
}
