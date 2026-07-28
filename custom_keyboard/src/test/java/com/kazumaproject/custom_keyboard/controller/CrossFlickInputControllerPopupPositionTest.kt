package com.kazumaproject.custom_keyboard.controller

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrossFlickInputControllerPopupPositionTest {

    @Test
    fun normalKeyboardUsesWindowCoordinatesWhenSystemInsetMovesWindowOrigin() {
        val key = LocatedView(
            context = ApplicationProvider.getApplicationContext(),
            screenLocation = intArrayOf(560, 599),
            windowLocation = intArrayOf(434, 599)
        )

        assertArrayEquals(
            intArrayOf(434, 599),
            readPopupAnchorLocation(keyAnchor = key, windowAnchor = key)
        )
    }

    @Test
    fun floatingKeyboardUsesCoordinatesRelativeToProvidedWindowAnchor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = LocatedView(
            context = context,
            screenLocation = intArrayOf(760, 640),
            windowLocation = intArrayOf(760, 640)
        )
        val windowAnchor = LocatedView(
            context = context,
            screenLocation = intArrayOf(120, 80),
            windowLocation = intArrayOf(120, 80)
        )

        assertArrayEquals(
            intArrayOf(640, 560),
            readPopupAnchorLocation(keyAnchor = key, windowAnchor = windowAnchor)
        )
    }

    private fun readPopupAnchorLocation(
        keyAnchor: View,
        windowAnchor: View
    ): IntArray {
        val controller = CrossFlickInputController(
            context = ApplicationProvider.getApplicationContext(),
            flickSensitivity = 80
        )
        val method = CrossFlickInputController::class.java.getDeclaredMethod(
            "readPopupAnchorLocation",
            View::class.java,
            View::class.java
        ).apply {
            isAccessible = true
        }
        method.invoke(controller, keyAnchor, windowAnchor)

        return CrossFlickInputController::class.java
            .getDeclaredField("popupAnchorLocation")
            .apply { isAccessible = true }
            .get(controller) as IntArray
    }

    private class LocatedView(
        context: Context,
        private val screenLocation: IntArray,
        private val windowLocation: IntArray
    ) : View(context) {

        override fun getLocationOnScreen(outLocation: IntArray) {
            outLocation[0] = screenLocation[0]
            outLocation[1] = screenLocation[1]
        }

        override fun getLocationInWindow(outLocation: IntArray) {
            outLocation[0] = windowLocation[0]
            outLocation[1] = windowLocation[1]
        }
    }
}
