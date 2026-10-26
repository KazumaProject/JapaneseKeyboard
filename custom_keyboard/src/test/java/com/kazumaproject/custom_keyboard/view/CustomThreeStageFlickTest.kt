package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomThreeStageFlickTest {
    @Test fun idMapsWinOverLabelsAndThreeStageGesturesCommitOnlyTheirOwnOutput() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = FlickKeyboardView(ContextThemeWrapper(activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar))
        val actions = mutableListOf<KeyAction>()
        view.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
            override fun onPress(action: KeyAction) = Unit
            override fun onAction(action: KeyAction, isFlick: Boolean) { actions += action }
            override fun onActionLongPress(action: KeyAction) = Unit
            override fun onActionUpAfterLongPress(action: KeyAction) = Unit
            override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
            override fun onFlickActionLongPress(action: KeyAction) = Unit
            override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
        })
        view.setFlickSensitivityValue(200)
        fun node(text: String) = TfbiFlickNode.StatefulKey(mapOf(
            TAP to TfbiFlickNode.Input("tap"),
            RIGHT to TfbiFlickNode.SubMenu(mapOf(
                RIGHT to TfbiFlickNode.Input("first"),
                DOWN to TfbiFlickNode.SubMenu(mapOf(
                    DOWN to TfbiFlickNode.Input("second"), LEFT to TfbiFlickNode.Input(text)
                ))
            ))
        ), label = "same")
        val keys = (0..1).map { KeyData(label = "same", row = 0, column = it, isFlickable = true,
            keyType = KeyType.HIERARCHICAL_FLICK, keyId = "id$it") }
        view.setKeyboard(KeyboardLayout(keys, emptyMap(), 2, 1,
            hierarchicalFlickMaps = mapOf("id0" to node("one"), "id1" to node("two"), "same" to node("wrong"))))
        activity.setContentView(view)
        view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 800, 400)
        fun gesture(index: Int, cancel: Boolean = false, releaseFirst: Boolean = false) {
            val key = view.getChildAt(index)
            val x = key.width / 2f
            val y = key.height / 2f
            val points = mutableListOf(Triple(MotionEvent.ACTION_DOWN, x, y),
                Triple(MotionEvent.ACTION_MOVE, x + 80f, y))
            if (!releaseFirst) {
                points += Triple(MotionEvent.ACTION_MOVE, x, y + 80f)
                points += Triple(MotionEvent.ACTION_MOVE, x - 80f, y)
            }
            val last = points.last()
            points += Triple(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, last.second, last.third)
            points.forEachIndexed { n, point ->
                val event = MotionEvent.obtain(0L, n * 20L, point.first, point.second, point.third, 0)
                try { key.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
        gesture(0); gesture(1); gesture(0, cancel = true); gesture(0, releaseFirst = true)
        assertEquals(listOf(KeyAction.Text("one"), KeyAction.Text("two"), KeyAction.Text("first")), actions)
        activity.finish()
    }
}
