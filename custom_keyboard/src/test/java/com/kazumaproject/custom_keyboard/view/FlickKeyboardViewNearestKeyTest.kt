package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.SpacerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class FlickKeyboardViewNearestKeyTest {
    private val actions = mutableListOf<KeyAction>()
    private val longs = mutableListOf<KeyAction>()
    private val releases = mutableListOf<KeyAction>()
    private val listener = object : FlickKeyboardView.OnKeyboardActionListener {
        override fun onPress(action: KeyAction) = Unit
        override fun onAction(action: KeyAction, isFlick: Boolean) { actions += action }
        override fun onActionLongPress(action: KeyAction) { longs += action }
        override fun onActionUpAfterLongPress(action: KeyAction) { releases += action }
        override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
        override fun onFlickActionLongPress(action: KeyAction) { longs += action }
        override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) { releases += action }
    }

    @Test fun gapsAndIntersections_selectNearestCenter_atNormalAndSmallSizes() {
        for (size in listOf(100, 0)) {
            val view = keyboard()
            view.applyKeySizing(size, size, 100, 14f, 14f)
            layout(view)
            val a = view.getChildAt(0)
            val b = view.getChildAt(1)
            val c = view.getChildAt(2)
            val gapX = (a.right + b.left) / 2f
            val gapY = (a.bottom + c.top) / 2f
            assertTrue(a.right < b.left && a.bottom < c.top)
            for ((x, y, expected) in listOf(
                Triple(gapX - 1, a.cy(), "a"), Triple(gapX + 1, a.cy(), "b"),
                Triple(a.cx(), gapY - 1, "a"), Triple(a.cx(), gapY + 1, "c"),
                Triple(gapX - 1, gapY - 1, "a"), Triple(gapX + 1, gapY + 1, "d")
            )) {
                actions.clear()
                tap(view, x, y)
                assertEquals("size=$size point=($x,$y)", listOf(KeyAction.Text(expected)), actions)
            }
        }
    }

    @Test fun insideKeyWinsEvenWhenAnotherCenterIsCloser_andTieUsesLayoutOrder() {
        val view = keyboard()
        view.getChildAt(0).layout(10, 10, 300, 110)
        view.getChildAt(1).layout(310, 10, 330, 110)
        view.getChildAt(2).visibility = View.INVISIBLE
        view.getChildAt(3).visibility = View.INVISIBLE
        tap(view, 299f, 60f)
        assertEquals(listOf(KeyAction.Text("a")), actions)
        view.getChildAt(0).layout(10, 10, 110, 110)
        view.getChildAt(1).layout(130, 10, 230, 110)
        actions.clear()
        tap(view, 120f, 60f)
        assertEquals(listOf(KeyAction.Text("a")), actions)
    }

    @Test fun disabledInvisibleAndZeroSizedKeys_areNeverFallbackTargets() {
        val view = keyboard()
        view.getChildAt(0).isEnabled = false
        view.getChildAt(1).visibility = View.INVISIBLE
        view.getChildAt(2).layout(0, 0, 0, 0)
        tap(view, 1f, 1f)
        assertEquals(listOf(KeyAction.Text("d")), actions)
        actions.clear()
        view.getChildAt(3).visibility = View.GONE
        tap(view, 1f, 1f)
        assertTrue(actions.isEmpty())
    }

    @Test fun outsideKeyboardDoesNotSelectAnyKey() {
        val view = keyboard()
        for ((x, y) in listOf(-0.5f to 100f, 600f to 100f, 100f to -1f, 100f to 400f)) {
            tap(view, x, y)
        }
        assertTrue(actions.isEmpty())
    }

    @Test fun switchingToDefaultPolicyCancelsGesture_evenWhenViewsAreReused() {
        val view = keyboard()
        val first = view.getChildAt(0)
        event(view, MotionEvent.ACTION_DOWN, 1f, 1f)
        view.setKeyboard(keys())
        assertSame(first, view.getChildAt(0))
        event(view, MotionEvent.ACTION_UP, 1f, 1f)
        tap(view, 1f, 1f)
        assertTrue(actions.isEmpty())
        tap(view, first.cx(), first.cy())
        assertEquals(listOf(KeyAction.Text("a")), actions)
    }

    @Test fun cancelAndLayoutReplacement_doNotCommitOrLeavePressedKeys() {
        val view = keyboard()
        event(view, MotionEvent.ACTION_DOWN, 1f, 1f)
        assertTrue(view.getChildAt(0).isPressed)
        event(view, MotionEvent.ACTION_CANCEL, 1f, 1f)
        assertFalse(view.getChildAt(0).isPressed)
        event(view, MotionEvent.ACTION_DOWN, 1f, 1f)
        val replacement = keys().keys.map { it.copy(action = KeyAction.Text("replacement")) }
        view.setKeyboard(KeyboardLayout(replacement, emptyMap(), 2, 2), KeyHitTestMode.NEAREST_KEY)
        layout(view)
        event(view, MotionEvent.ACTION_UP, 1f, 1f)
        shadowOf(Looper.getMainLooper()).idleFor(1500, TimeUnit.MILLISECONDS)
        assertTrue(actions.isEmpty())
        assertTrue(longs.isEmpty())
        assertFalse(view.getChildAt(0).isPressed)
    }

    @Test fun farOutsideVisualKey_smallMoveStillClicksFunctionalKey() {
        val view = keyboard()
        val data = keys().keys.mapIndexed { i, key ->
            if (i == 0) key.copy(action = KeyAction.Paste, isSpecialKey = true) else key
        }
        view.setKeyboard(KeyboardLayout(data, emptyMap(), 2, 2), KeyHitTestMode.NEAREST_KEY)
        layout(view)
        view.getChildAt(0).layout(100, 100, 180, 180)
        for (i in 1..3) view.getChildAt(i).isEnabled = false
        // A start more than the cancellation slop outside the visual key must remain valid.
        event(view, MotionEvent.ACTION_DOWN, 1f, 120f)
        event(view, MotionEvent.ACTION_MOVE, 2f, 120f)
        event(view, MotionEvent.ACTION_UP, 2f, 120f)
        assertEquals(listOf(KeyAction.Paste), actions)
    }

    @Test fun localCorrectionIsConstant_andRawScreenCoordinatesArePreserved() {
        val view = keyboard()
        val key = view.getChildAt(0)
        key.layout(100, 100, 180, 180)
        for (i in 1..3) view.getChildAt(i).isEnabled = false
        val samples = mutableListOf<List<Float>>()
        key.setOnTouchListener { _, e -> samples += listOf(e.x, e.y, e.rawX, e.rawY); true }
        event(view, MotionEvent.ACTION_DOWN, 1f, 120f)
        event(view, MotionEvent.ACTION_MOVE, 31f, 140f)
        event(view, MotionEvent.ACTION_UP, 41f, 145f)
        assertEquals(3, samples.size)
        assertTrue(samples[0][0] >= 0 && samples[0][0] < key.width)
        assertEquals(30f, samples[1][0] - samples[0][0], 0f)
        assertEquals(20f, samples[1][1] - samples[0][1], 0f)
        val origin = IntArray(2).also(view::getLocationOnScreen)
        assertEquals(listOf(1f, 31f, 41f).map { it + origin[0] }, samples.map { it[2] })
        assertEquals(listOf(120f, 140f, 145f).map { it + origin[1] }, samples.map { it[3] })
    }

    @Test fun longPressFromMargin_withSmallMove_firesOnceWithoutTap() {
        val view = keyboard()
        view.setLongPressTimeout(200L)
        event(view, MotionEvent.ACTION_DOWN, 1f, 1f)
        event(view, MotionEvent.ACTION_MOVE, 2f, 2f)
        shadowOf(Looper.getMainLooper()).idleFor(210, TimeUnit.MILLISECONDS)
        assertEquals(listOf(KeyAction.Text("a")), longs)
        event(view, MotionEvent.ACTION_UP, 2f, 2f)
        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
        assertEquals(listOf(KeyAction.Text("a")), releases)
        assertTrue(actions.isEmpty())
        assertFalse(view.getChildAt(0).isPressed)
    }

    @Test fun spacerIsNotAKey_andDefaultPolicyKeepsTheEmptyCellUntouchable() {
        val view = keyboard()
        val data = keys().keys.first()
        val layout = KeyboardLayout(listOf(data), emptyMap(), 2, 1,
            items = listOf(KeyItem("a", data, GridPlacement(0, 0)),
                SpacerItem("space", GridPlacement(0, 2))))
        view.setKeyboard(layout, KeyHitTestMode.NEAREST_KEY)
        layout(view)
        tap(view, 450f, 100f)
        assertEquals(listOf(KeyAction.Text("a")), actions)
        actions.clear()
        view.setKeyboard(layout)
        tap(view, 450f, 100f)
        assertTrue(actions.isEmpty())
    }

    @Test fun layoutResizeAndScreenOffset_useFreshBounds_withoutLosingPolicy() {
        val view = keyboard()
        view.applyKeySizing(0, 0, 100, 14f, 14f)
        layout(view)
        view.offsetLeftAndRight(37)
        view.offsetTopAndBottom(83)
        val a = view.getChildAt(0)
        val b = view.getChildAt(1)
        tap(view, (a.right + b.left) / 2f + 1f, a.cy())
        assertEquals(listOf(KeyAction.Text("b")), actions)
        actions.clear()
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
        view.layout(37, 83, 437, 383)
        tap(view, (a.right + b.left) / 2f - 1f, a.cy())
        assertEquals(listOf(KeyAction.Text("a")), actions)
    }

    @Test fun movingWindowOriginAfterDown_doesNotShiftChildGestureCoordinates() {
        val view = keyboard()
        val key = view.getChildAt(0)
        val received = mutableListOf<Pair<Float, Float>>()
        key.setOnTouchListener { _, e -> received += e.x to e.y; true }
        event(view, MotionEvent.ACTION_DOWN, 1f, 1f)
        view.offsetTopAndBottom(80)
        event(view, MotionEvent.ACTION_MOVE, 2f, -78f)
        event(view, MotionEvent.ACTION_UP, 2f, -78f)
        assertEquals(3, received.size)
        assertEquals(1f, received[1].first - received[0].first, 0f)
        assertEquals(1f, received[1].second - received[0].second, 0f)
    }

    @Test fun secondFingerCommitsFirstOnce_andUsesItsOwnNearestTarget() {
        val view = keyboard()
        fun multi(action: Int, points: List<Pair<Float, Float>>) {
            val origin = IntArray(2).also(view::getLocationOnScreen)
            val properties = points.indices.map { i -> MotionEvent.PointerProperties().apply {
                id = if (i == 0) 7 else 11
                toolType = MotionEvent.TOOL_TYPE_FINGER
            } }.toTypedArray()
            val coords = points.map { (px, py) -> MotionEvent.PointerCoords().apply {
                x = px + origin[0]; y = py + origin[1]; pressure = 1f; size = 1f
            } }.toTypedArray()
            val e = MotionEvent.obtain(100, 120, action, points.size, properties, coords,
                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            e.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
            view.onTouchEvent(e)
            e.recycle()
        }
        multi(MotionEvent.ACTION_DOWN, listOf(1f to 1f))
        multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(1f to 1f, 599f to 1f))
        assertEquals(listOf(KeyAction.Text("a")), actions)
        multi(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(1f to 1f, 599f to 1f))
        multi(MotionEvent.ACTION_UP, listOf(1f to 1f))
        assertEquals(listOf(KeyAction.Text("a"), KeyAction.Text("b")), actions)
        assertFalse(view.getChildAt(0).isPressed)
        assertFalse(view.getChildAt(1).isPressed)
    }

    @Test fun standardFlickFromGap_keepsOriginalKeyAndRealMovement() {
        val view = keyboard()
        val data = keys().keys.map { it.copy(keyType = KeyType.STANDARD_FLICK) }
        val maps = data.associate { it.label to listOf(mapOf(
            FlickDirection.TAP to FlickAction.Input(it.label),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(it.label + "-right")
        )) }
        view.setKeyboard(KeyboardLayout(data, maps, 2, 2), KeyHitTestMode.NEAREST_KEY)
        layout(view)
        val a = view.getChildAt(0)
        val b = view.getChildAt(1)
        val startX = (a.right + b.left) / 2f - 1f
        event(view, MotionEvent.ACTION_DOWN, startX, a.cy())
        event(view, MotionEvent.ACTION_MOVE, startX + 100f, a.cy())
        event(view, MotionEvent.ACTION_UP, startX + 100f, a.cy())
        assertEquals(listOf(KeyAction.Text("a-right")), actions)
    }

    private fun keyboard() = FlickKeyboardView(ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
    )).apply {
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(this)
        setOnKeyboardActionListener(listener)
        setKeyboard(keys(), KeyHitTestMode.NEAREST_KEY)
        layout(this)
    }

    private fun keys() = KeyboardLayout(
        keys = listOf("a", "b", "c", "d").mapIndexed { i, label ->
            KeyData(label = label, row = i / 2, column = i % 2,
                isFlickable = false, action = KeyAction.Text(label), keyId = label, keyType = KeyType.NORMAL)
        }, flickKeyMaps = emptyMap(), columnCount = 2, rowCount = 2
    )

    private fun layout(view: FlickKeyboardView) {
        view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 600, 400)
    }
    private fun View.cx() = (left + right) / 2f
    private fun View.cy() = (top + bottom) / 2f
    private fun event(view: FlickKeyboardView, action: Int, x: Float, y: Float) {
        val origin = IntArray(2).also(view::getLocationOnScreen)
        val e = MotionEvent.obtain(100, 120, action, x + origin[0], y + origin[1], 0)
        e.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
        view.onTouchEvent(e)
        e.recycle()
    }
    private fun tap(view: FlickKeyboardView, x: Float, y: Float) {
        event(view, MotionEvent.ACTION_DOWN, x, y)
        event(view, MotionEvent.ACTION_UP, x, y)
    }
}
