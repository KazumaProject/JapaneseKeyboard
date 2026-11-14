package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import com.kazumaproject.core.ui.skin.FeverKeyPulse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeverPopupTrailTest {
    @Test fun movementBeyondThePopupPaintsAFadingEdgeTrailAndSessionClearStopsIt() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity)
        val popup = View(activity)
        activity.setContentView(host)
        host.addView(popup, FrameLayout.LayoutParams(200, 120))
        host.layout(0, 0, 500, 500); popup.layout(50, 50, 250, 170)
        val trail = FeverPopupTrail(popup)
        val position = IntArray(2).also(popup::getLocationOnScreen)
        fun pixels(): IntArray {
            val bitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
            trail.draw(Canvas(bitmap))
            return IntArray(200 * 120).also { bitmap.getPixels(it, 0, 200, 0, 0, 200, 120); bitmap.recycle() }
        }
        try {
            FeverKeyPulse.pulse(Color.CYAN, 2)
            trail.move(position[0] + 80f, position[1] + 70f)
            SystemClock.sleep(20)
            trail.move(position[0] + 600f, position[1] + 90f)
            val moving = pixels()
            assertTrue("Movement must add visible pixels above popup content", moving.any { Color.alpha(it) > 0 })
            SystemClock.sleep(180)
            assertFalse("The trail must fade between frames", moving.contentEquals(pixels()))
            SystemClock.sleep(200)
            assertTrue("Trail expires while the popup remains held", pixels().all { it == 0 })
            trail.move(position[0] + 40f, position[1] + 40f)
            FeverKeyPulse.clear()
            assertTrue("IME teardown must clear the trail immediately", pixels().all { it == 0 })
        } finally { FeverKeyPulse.clear(); activity.finish() }
    }
}
