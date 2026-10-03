package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.kazumaproject.core.data.popup.FlickPopupViewStyleSet
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewLifecycleTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val host = FrameLayout(activity).also { activity.setContentView(it) }
    private val events = mutableListOf<KeyAction>()
    private val holds = mutableListOf<KeyAction>()
    private val keyboard = FlickKeyboardView(ContextThemeWrapper(activity,
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)).apply {
        setLongPressTimeout(100)
        setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
            override fun onPress(action: KeyAction) = Unit
            override fun onAction(action: KeyAction, isFlick: Boolean) { events += action }
            override fun onActionLongPress(action: KeyAction) { holds += action }
            override fun onActionUpAfterLongPress(action: KeyAction) = Unit
            override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
            override fun onFlickActionLongPress(action: KeyAction) { holds += action }
            override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
        })
    }.also { host.addView(it) }

    @Test fun all54SumireLayoutsKeepEveryKeysTapAfterHideAndReuse() {
        for (mode in KeyboardInputMode.entries) {
            for (method in listOf("toggle", "flick", "switch-mode-effective")) {
                for (style in listOf("default", "circle", "second-flick", "third-flick", "center-guide-flick", "sumire")) {
                    val scenario = "$mode/$method/$style"
                    val layout = KeyboardDefaultLayouts.createFinalLayout(mode, emptyMap(), method, style)
                    install(layout)
                    tapAllKeys()
                    val expected = events.toList()
                    assertTrue("$scenario has no working keys", expected.isNotEmpty())
                    events.clear()
                    val children = (0 until keyboard.childCount).map(keyboard::getChildAt)
                    keyboard.visibility = View.GONE
                    idle(500)
                    assertTrue("$scenario emitted input while hidden", events.isEmpty())
                    keyboard.visibility = View.VISIBLE
                    keyboard.setKeyboard(layout)
                    children.forEachIndexed { index, key -> assertSame(scenario, key, keyboard.getChildAt(index)) }
                    tapAllKeys()
                    assertEquals(scenario, expected, events)
                    events.clear()
                }
            }
        }
    }

    @Test fun normalAndCrossFlickLongPressWorkAfterRepeatedHideShowWithoutDuplicateHandlers() {
        for (type in listOf(KeyType.NORMAL, KeyType.CROSS_FLICK)) {
            install(actionLayout(type))
            repeat(3) {
                keyboard.visibility = View.INVISIBLE
                keyboard.visibility = View.VISIBLE
                hold(0)
                assertEquals(listOf(KeyAction.Paste), holds)
                holds.clear()
                tap(0)
                assertEquals(listOf(KeyAction.Paste), events)
                events.clear()
            }
        }
    }

    @Test fun all54SumireLayoutsKeepEveryKeysLongPressAfterHide() {
        for (mode in KeyboardInputMode.entries) {
            for (method in listOf("toggle", "flick", "switch-mode-effective")) {
                for (style in listOf("default", "circle", "second-flick", "third-flick", "center-guide-flick", "sumire")) {
                    val scenario = "$mode/$method/$style"
                    install(KeyboardDefaultLayouts.createFinalLayout(mode, emptyMap(), method, style))
                    holdAllKeys()
                    val expectedActions = events.toList()
                    val expectedHolds = holds.toList()
                    assertTrue("$scenario has no long-press output", expectedActions.isNotEmpty() || expectedHolds.isNotEmpty())
                    events.clear()
                    holds.clear()
                    keyboard.visibility = View.GONE
                    idle(500)
                    assertTrue("$scenario emitted input while hidden", events.isEmpty() && holds.isEmpty())
                    keyboard.visibility = View.VISIBLE
                    holdAllKeys()
                    assertEquals("$scenario committed actions", expectedActions, events)
                    assertEquals("$scenario long-press actions", expectedHolds, holds)
                }
            }
        }
    }

    @Test fun detachReattachRestoresTheFirstTapWithoutReplacingKeys() {
        install(actionLayout(KeyType.NORMAL))
        val key = keyboard.getChildAt(0)
        host.removeView(keyboard)
        host.addView(keyboard)
        measure()
        tap(0)
        assertSame(key, keyboard.getChildAt(0))
        assertEquals(listOf(KeyAction.Paste), events)
    }

    @Test fun changingPopupSkinRestoresTapAndLongPressImmediately() {
        install(actionLayout(KeyType.NORMAL))
        val key = keyboard.getChildAt(0)
        val style = PopupViewStyle(100, 20f, skinId = KeyboardSkinId.CUPERTINO_CLASSIC)
        keyboard.applyPopupViewStyleSet(FlickPopupViewStyleSet(style, style, style, style))
        tap(0)
        hold(0)
        assertSame(key, keyboard.getChildAt(0))
        assertEquals(listOf(KeyAction.Paste), events)
        assertEquals(listOf(KeyAction.Paste), holds)
    }

    @Test fun hidingPressedKeyCancelsInputAndPendingLongPress() {
        for (type in listOf(KeyType.NORMAL, KeyType.CROSS_FLICK)) {
            install(actionLayout(type))
            val key = keyboard.getChildAt(0)
            touch(MotionEvent.ACTION_DOWN, key)
            keyboard.visibility = View.GONE
            idle(500)
            keyboard.visibility = View.VISIBLE
            touch(MotionEvent.ACTION_UP, key)
            assertTrue(events.isEmpty())
            assertTrue(holds.isEmpty())
            tap(0)
            assertEquals(listOf(KeyAction.Paste), events)
            events.clear()
        }
    }

    @Test fun circularLongPressScopeIsRestoredAfterHideShow() {
        install(KeyboardDefaultLayouts.createFinalLayout(KeyboardInputMode.HIRAGANA,
            emptyMap(), "toggle", "sumire"))
        keyboard.visibility = View.GONE
        keyboard.visibility = View.VISIBLE
        val controller = field<List<Any>>(keyboard, "flickControllers").first()
        val info = field<List<Any>>(keyboard, "keyInfos").first { field<Any?>(it, "controller") === controller }
        touch(MotionEvent.ACTION_DOWN, field(info, "view"))
        idle(150)
        assertTrue(field(controller, "isLongPressModeActive"))
        touch(MotionEvent.ACTION_UP, field(info, "view"))
    }

    @Test fun customStickyAndFlickLongPressKeysRecoverAfterHide() {
        for (type in listOf(KeyType.STICKY_TWO_STEP_FLICK, KeyType.FLICK_LONG_PRESS)) {
            val key = KeyData("a", 0, 0, true, action = KeyAction.Text("a"), keyId = "a", keyType = type)
            install(KeyboardLayout(listOf(key), emptyMap(), 1, 1,
                twoStepFlickKeyMaps = mapOf("a" to mapOf(TfbiFlickDirection.TAP to mapOf(TfbiFlickDirection.TAP to "a")))))
            keyboard.visibility = View.GONE
            keyboard.visibility = View.VISIBLE
            tap(0)
            assertEquals(type.name, listOf(KeyAction.Text("a")), events)
            events.clear()
        }
    }

    @Test fun dynamicKeyUpdateWhileHiddenKeepsLatestActionAndDoesNotDuplicateControllers() {
        val layout = actionLayout(KeyType.CROSS_FLICK).copyWithKeys(listOf(
            KeyData("paste", 0, 0, false, KeyAction.Paste, keyId = "dynamic", isSpecialKey = true,
                keyType = KeyType.CROSS_FLICK, dynamicStates = listOf(
                    FlickAction.Action(KeyAction.Paste, "paste"), FlickAction.Action(KeyAction.Copy, "copy")))))
        install(layout.copy(flickKeyMaps = mapOf("dynamic" to listOf(mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Paste))))))
        keyboard.visibility = View.GONE
        keyboard.updateDynamicKey("dynamic", 1)
        keyboard.visibility = View.VISIBLE
        tap(0)
        assertEquals(listOf(KeyAction.Copy), events)
        assertEquals(1, field<List<Any>>(keyboard, "crossFlickControllers").size)
    }

    private fun actionLayout(type: KeyType) = KeyboardLayout(
        listOf(KeyData("paste", 0, 0, false, KeyAction.Paste, keyId = "paste", keyType = type)),
        mapOf("paste" to listOf(mapOf(FlickDirection.TAP to FlickAction.Action(KeyAction.Paste)))), 1, 1)
    private fun install(layout: KeyboardLayout) { keyboard.setKeyboard(layout); measure(); events.clear(); holds.clear() }
    private fun measure() {
        keyboard.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        keyboard.layout(0, 0, 1000, 800)
    }
    private fun tapAllKeys() { repeat(keyboard.childCount) { tap(it) }; idle(400) }
    private fun holdAllKeys() { repeat(keyboard.childCount) { hold(it); idle(400) } }
    private fun tap(index: Int) { val key = keyboard.getChildAt(index); touch(MotionEvent.ACTION_DOWN, key); touch(MotionEvent.ACTION_UP, key) }
    private fun hold(index: Int) { val key = keyboard.getChildAt(index); touch(MotionEvent.ACTION_DOWN, key); idle(150); touch(MotionEvent.ACTION_UP, key) }
    private fun touch(action: Int, key: View) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, key.left + key.width / 2f, key.top + key.height / 2f, 0)
        keyboard.onTouchEvent(event)
        event.recycle()
    }
    private fun idle(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS)
    @Suppress("UNCHECKED_CAST") private fun <T> field(target: Any, name: String): T =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as T
}
