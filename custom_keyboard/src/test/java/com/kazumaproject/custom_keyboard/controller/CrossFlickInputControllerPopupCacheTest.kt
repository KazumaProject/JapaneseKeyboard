package com.kazumaproject.custom_keyboard.controller

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrossFlickInputControllerPopupCacheTest {

    @Test
    fun pressNotificationRunsBeforePopupAllocationAndPopupsAreReused() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        val button = Button(context).apply {
            text = "あ"
            layout(0, 0, 180, 120)
        }
        val controller = CrossFlickInputController(context, flickSensitivity = 74)
        var popupCountObservedByPressListener = -1
        controller.listener = object : NoopCrossFlickListener() {
            override fun onPress(action: KeyAction) {
                popupCountObservedByPressListener = controller.directionalPopups().size
            }
        }
        controller.attachText(
            view = button,
            map = mapOf(
                FlickDirection.TAP to "あ",
                FlickDirection.UP_LEFT_FAR to "い",
                FlickDirection.UP to "う",
                FlickDirection.UP_RIGHT_FAR to "え",
                FlickDirection.DOWN to "お"
            )
        )

        button.dispatchTouch(MotionEvent.ACTION_DOWN, eventTime = 100L)

        assertEquals(0, popupCountObservedByPressListener)
        val firstPopups = controller.directionalPopups().toMap()
        assertEquals(5, firstPopups.size)

        button.dispatchTouch(MotionEvent.ACTION_UP, eventTime = 120L)
        button.dispatchTouch(MotionEvent.ACTION_DOWN, eventTime = 200L)

        val secondPopups = controller.directionalPopups()
        assertEquals(5, secondPopups.size)
        firstPopups.forEach { (direction, popup) ->
            assertSame(popup, secondPopups[direction])
        }

        button.dispatchTouch(MotionEvent.ACTION_UP, eventTime = 220L)
        controller.cancel()
    }

    private fun Button.dispatchTouch(action: Int, eventTime: Long) {
        val event = MotionEvent.obtain(
            100L,
            eventTime,
            action,
            width / 2f,
            height / 2f,
            0
        )
        dispatchTouchEvent(event)
        event.recycle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun CrossFlickInputController.directionalPopups():
        Map<FlickDirection, View> {
        val field = CrossFlickInputController::class.java
            .getDeclaredField("directionalPopupMap")
            .apply { isAccessible = true }
        return field.get(this) as Map<FlickDirection, View>
    }
}

private open class NoopCrossFlickListener :
    CrossFlickInputController.CrossFlickListener {
    override fun onPress(action: KeyAction) = Unit
    override fun onFlick(action: KeyAction, isFlick: Boolean) = Unit
    override fun onFlickLongPress(action: KeyAction) = Unit
    override fun onFlickUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
}
