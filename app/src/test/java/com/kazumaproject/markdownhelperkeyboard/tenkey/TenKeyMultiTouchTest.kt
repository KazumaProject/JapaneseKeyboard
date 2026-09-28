package com.kazumaproject.markdownhelperkeyboard.tenkey

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.tenkey.R as TenKeyR
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TenKeyMultiTouchTest {
    @Test
    fun secondFingerDoesNotCommitFirstAndBothCommitOnTheirOwnRelease() {
        val (keyboard, commits) = keyboard()
        val first = center(keyboard, keyboard.findViewById(TenKeyR.id.key_4))
        val second = center(keyboard, keyboard.findViewById(TenKeyR.id.key_1))
        val firstFlick = first.copy(x = first.x - 70f)
        val downTime = SystemClock.uptimeMillis()

        send(keyboard, downTime, MotionEvent.ACTION_DOWN, 0, 0 to first)
        send(keyboard, downTime, MotionEvent.ACTION_MOVE, 0, 0 to firstFlick)
        send(keyboard, downTime, MotionEvent.ACTION_POINTER_DOWN, 1, 0 to firstFlick, 1 to second)
        assertEquals(emptyList<String>(), commits)

        send(keyboard, downTime, MotionEvent.ACTION_POINTER_UP, 1, 0 to firstFlick, 1 to second)
        assertEquals(listOf("あ"), commits)
        send(keyboard, downTime, MotionEvent.ACTION_UP, 0, 0 to firstFlick)
        assertEquals(listOf("あ", "ち"), commits)
    }

    @Test
    fun firstFingerCanReleaseWhileSecondRemainsPressed() {
        val (keyboard, commits) = keyboard()
        val first = center(keyboard, keyboard.findViewById(TenKeyR.id.key_4))
        val second = center(keyboard, keyboard.findViewById(TenKeyR.id.key_1))
        val downTime = SystemClock.uptimeMillis()

        send(keyboard, downTime, MotionEvent.ACTION_DOWN, 0, 3 to first)
        send(keyboard, downTime, MotionEvent.ACTION_POINTER_DOWN, 1, 3 to first, 7 to second)
        send(keyboard, downTime, MotionEvent.ACTION_POINTER_UP, 0, 3 to first, 7 to second)
        assertEquals(listOf("た"), commits)
        send(keyboard, downTime, MotionEvent.ACTION_UP, 0, 7 to second)
        assertEquals(listOf("た", "あ"), commits)
    }

    private fun keyboard(): Pair<TenKey, MutableList<String>> {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_MarkdownKeyboard)
        controller.setup()
        val root = activity.layoutInflater.inflate(R.layout.main_layout, null)
        val keyboard = root.findViewById<TenKey>(R.id.keyboard_view)
        activity.setContentView(root)
        keyboard.visibility = View.VISIBLE
        keyboard.applyKeyboardTheme(
            themeMode = "default",
            currentNightMode = Configuration.UI_MODE_NIGHT_NO,
            isDynamicColorEnabled = false,
            customBgColor = Color.WHITE,
            customKeyColor = Color.WHITE,
            customSpecialKeyColor = Color.WHITE,
            customKeyTextColor = Color.BLACK,
            customSpecialKeyTextColor = Color.BLACK,
            liquidGlassEnable = false,
            customBorderEnable = false,
            customBorderColor = Color.BLACK,
            liquidGlassKeyAlphaEnable = 100,
            borderWidth = 0,
        )
        keyboard.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
        )
        keyboard.layout(0, 0, 1080, 600)
        val commits = mutableListOf<String>()
        keyboard.setOnFlickListener(object : FlickListener {
            override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                if (gestureType != GestureType.Down) char?.let { commits.add(it.toString()) }
            }
        })
        return keyboard to commits
    }

    private data class Point(val x: Float, val y: Float)

    private fun center(keyboard: TenKey, view: View): Point {
        val position = IntArray(2)
        val keyboardPosition = IntArray(2)
        view.getLocationOnScreen(position)
        keyboard.getLocationOnScreen(keyboardPosition)
        return Point(
            position[0] - keyboardPosition[0] + view.width / 2f,
            position[1] - keyboardPosition[1] + view.height / 2f,
        )
    }

    private fun send(
        keyboard: TenKey,
        downTime: Long,
        action: Int,
        actionIndex: Int,
        vararg touches: Pair<Int, Point>,
    ) {
        val properties = touches.map { (id, _) ->
            MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }.toTypedArray()
        val coordinates = touches.map { (_, point) ->
            MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = 1f; size = 1f }
        }.toTypedArray()
        val encodedAction = action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), encodedAction, touches.size,
            properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0,
        )
        try { keyboard.onTouch(keyboard, event) } finally { event.recycle() }
    }
}
