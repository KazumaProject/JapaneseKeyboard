package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.kazumaproject.custom_keyboard.data.FlickDirection
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrossFlickInputControllerPopupVisibilityTest {
    @Test fun returningFromAnEmptyDirectionRestoresThePreviousPopup() = fixture { button, controller, _ ->
        touch(button, MotionEvent.ACTION_DOWN)
        assertTrue(controller.popup().isAttachedToWindow)
        touch(button, MotionEvent.ACTION_MOVE, 220f)
        touch(button, MotionEvent.ACTION_MOVE)
        assertTrue("Returning to TAP must reattach its popup", controller.popup().isAttachedToWindow)
    }

    @Test fun edgeFlickAndLongPressStayInsideTheOverlayHost() = fixture { button, controller, host ->
        button.layout(0, 0, 100, 60)
        touch(button, MotionEvent.ACTION_DOWN)
        touch(button, MotionEvent.ACTION_MOVE, 50f, -100f)
        val directional = controller.popup()
        assertTrue("Top-edge flick must not be clipped", directional.top >= 0)
        touch(button, MotionEvent.ACTION_UP)
        touch(button, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)
        val grid = field(controller, "gridPopupView") as View
        assertTrue(grid.isAttachedToWindow)
        assertTrue("Long-press grid must fit at the left edge", grid.left >= 0)
        assertTrue("Long-press grid must fit at the top edge", grid.top >= 0)
        assertTrue(grid.right <= host.width && grid.bottom <= host.height)
    }

    @Test fun repeatedHoldsRebuildVisibleCellsAndRestoreTheKeyLabel() = fixture { button, controller, _ ->
        repeat(3) {
            touch(button, MotionEvent.ACTION_DOWN)
            shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)
            val grid = field(controller, "gridPopupView") as android.view.ViewGroup
            assertTrue(grid.isAttachedToWindow)
            val cells = grid.getChildAt(0) as android.view.ViewGroup
            assertTrue((0 until cells.childCount).map(cells::getChildAt).any { cell -> cell.width > 0 && cell.height > 0 })
            touch(button, MotionEvent.ACTION_MOVE, 50f, -100f)
            touch(button, if (it == 1) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP)
            assertFalse(grid.isAttachedToWindow)
            assertEquals("あ", button.text.toString())
        }
    }

    private fun fixture(test: (Button, CrossFlickInputController, FrameLayout) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val host = FrameLayout(activity)
        val button = Button(activity).apply { text = "あ" }
        host.addView(button, FrameLayout.LayoutParams(100, 60))
        activity.setContentView(host)
        host.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, 500, 500)
        button.layout(150, 150, 250, 210)
        val controller = CrossFlickInputController(activity, flickSensitivity = 50)
        controller.setPopupOverlayHostProvider { host }
        controller.applyPopupViewStyleSet(PopupViewStyle(100, 28f, skinId = KeyboardSkinId.FEVER), PopupViewStyle(100, 18f, skinId = KeyboardSkinId.FEVER))
        controller.attachText(button, mapOf(FlickDirection.TAP to "あ", FlickDirection.UP to "う"))
        try { test(button, controller, host) } finally { controller.cancel(); activity.finish() }
    }

    private fun touch(button: Button, action: Int, x: Float = 50f, y: Float = 30f) {
        val event = MotionEvent.obtain(100, 100, action, x, y, 0)
        try { button.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun field(controller: CrossFlickInputController, name: String): Any? = controller.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(controller)
    private fun CrossFlickInputController.popup() = field(this, "currentVisibleDirectionalPopup") as View
}
