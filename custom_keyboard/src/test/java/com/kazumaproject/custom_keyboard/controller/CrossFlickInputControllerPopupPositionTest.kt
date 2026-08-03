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
    fun popupCoordinatesAreRelativeToOverlayHost() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = LocatedView(
            context = context,
            screenLocation = intArrayOf(560, 599),
            windowLocation = intArrayOf(434, 599)
        )
        val overlayHost = LocatedView(
            context = context,
            screenLocation = intArrayOf(126, 95),
            windowLocation = intArrayOf(0, 0)
        )

        assertArrayEquals(
            intArrayOf(434, 504),
            readPopupAnchorLocation(keyAnchor = key, overlayHost = overlayHost)
        )
    }

    @Test
    fun popupCoordinatesRemainStableWhenAnchorAndHostWindowMoveTogether() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val beforeMoveKey = LocatedView(
            context = context,
            screenLocation = intArrayOf(760, 640),
            windowLocation = intArrayOf(760, 640)
        )
        val beforeMoveHost = LocatedView(
            context = context,
            screenLocation = intArrayOf(120, 80),
            windowLocation = intArrayOf(120, 80)
        )
        val afterMoveKey = LocatedView(
            context = context,
            screenLocation = intArrayOf(760, 735),
            windowLocation = intArrayOf(760, 735)
        )
        val afterMoveHost = LocatedView(
            context = context,
            screenLocation = intArrayOf(120, 175),
            windowLocation = intArrayOf(120, 175)
        )

        assertArrayEquals(
            readPopupAnchorLocation(beforeMoveKey, beforeMoveHost),
            readPopupAnchorLocation(afterMoveKey, afterMoveHost)
        )
    }

    @Test
    @Config(qualifiers = "land")
    fun overlayCoordinateRuleDoesNotChangeWithOrientation() {
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
            readPopupAnchorLocation(keyAnchor = key, overlayHost = windowAnchor)
        )
    }

    private fun readPopupAnchorLocation(
        keyAnchor: View,
        overlayHost: View
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
        method.invoke(controller, keyAnchor, overlayHost)

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
