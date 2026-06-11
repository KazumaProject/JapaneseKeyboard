package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuminagashiInkViewTest {

    private lateinit var view: SuminagashiInkView

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        view = SuminagashiInkView(context)
    }

    @Test
    fun pointerDownDoesNotCreateDropsWhenDisabled() {
        view.configure(
            enabled = false,
            colorMode = "random",
            fixedColor = Color.rgb(17, 17, 17)
        )

        view.onPointerDown(pointerId = 0, x = 20f, y = 30f)

        assertEquals(View.GONE, view.visibility)
        assertEquals(0, view.dropCountForTesting())
    }

    @Test
    fun pointerDownCreatesDropsWhenEnabled() {
        view.configure(
            enabled = true,
            colorMode = "random",
            fixedColor = Color.rgb(17, 17, 17)
        )

        view.onPointerDown(pointerId = 0, x = 20f, y = 30f)

        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(view.dropCountForTesting() > 0)
    }

    @Test
    fun clearInkRemovesDropsAndPointerState() {
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(38, 70, 120)
        )
        view.onPointerDown(pointerId = 3, x = 20f, y = 30f)

        view.clearInk()

        assertEquals(0, view.dropCountForTesting())
        assertEquals(0, view.pointerStateCountForTesting())
        assertFalse(view.hasResidualInkForTesting())
    }

    @Test
    fun clearInkRemovesResidualSurfaceState() {
        view.layout(0, 0, 240, 180)
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(38, 70, 120)
        )
        view.onPointerDown(pointerId = 3, x = 20f, y = 30f)
        val bitmap = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888)

        view.draw(Canvas(bitmap))

        assertTrue(view.hasResidualInkForTesting())

        view.clearInk()

        assertEquals(0, view.dropCountForTesting())
        assertEquals(0, view.pointerStateCountForTesting())
        assertFalse(view.hasResidualInkForTesting())
        bitmap.recycle()
    }

    @Test
    fun residualSurfacePersistsAfterActiveDropsAreGone() {
        view.layout(0, 0, 240, 180)
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(38, 70, 120)
        )
        view.onPointerDown(pointerId = 3, x = 20f, y = 30f)
        val bitmap = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        view.draw(canvas)
        view.clearActiveDropsForTesting()
        view.draw(canvas)

        assertEquals(0, view.dropCountForTesting())
        assertTrue(view.hasResidualInkForTesting())
        bitmap.recycle()
    }

    @Test
    fun residualSurfaceStopsAnimatingAfterIdleWindow() {
        view.layout(0, 0, 240, 180)
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(38, 70, 120)
        )
        view.onPointerDown(pointerId = 3, x = 20f, y = 30f)
        val bitmap = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        view.onPointerUp(pointerId = 3, x = 20f, y = 30f)
        view.clearActiveDropsForTesting()
        val now = SystemClock.uptimeMillis()

        view.setLastActiveInkTimeForTesting(now - 2_000L)

        assertTrue(view.hasResidualInkForTesting())
        assertFalse(view.shouldAnimateResidualSurfaceForTesting(now))
        bitmap.recycle()
    }

    @Test
    fun residualTintKeepsDarkInkFromBecomingBlack() {
        val color = view.waterTintColorForTesting(Color.rgb(17, 17, 17), phase = 0f)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        assertTrue(red + green + blue > 220)
        assertTrue(maxOf(red, green, blue) - minOf(red, green, blue) > 20)
    }

    @Test
    fun pointerUpKeepsInkAndClearsPointerState() {
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(38, 70, 120)
        )
        view.onPointerDown(pointerId = 7, x = 20f, y = 30f)
        val dropsBeforeUp = view.dropCountForTesting()
        assertEquals(1, view.pointerStateCountForTesting())

        view.onPointerUp(pointerId = 7, x = 24f, y = 34f)

        assertEquals(0, view.pointerStateCountForTesting())
        assertTrue(view.dropCountForTesting() > dropsBeforeUp)
    }

    @Test
    fun cancelClearsPointerStateButKeepsInk() {
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(38, 70, 120)
        )
        view.onPointerDown(pointerId = 4, x = 20f, y = 30f)
        assertEquals(1, view.pointerStateCountForTesting())

        view.onCancel()

        assertEquals(0, view.pointerStateCountForTesting())
        assertTrue(view.dropCountForTesting() > 0)
    }

    @Test
    fun configureDisabledHidesViewAndClearsDrops() {
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(38, 70, 120)
        )
        view.onPointerDown(pointerId = 5, x = 20f, y = 30f)
        assertTrue(view.dropCountForTesting() > 0)

        view.configure(
            enabled = false,
            colorMode = "fixed",
            fixedColor = Color.rgb(38, 70, 120)
        )

        assertEquals(View.GONE, view.visibility)
        assertEquals(0, view.dropCountForTesting())
        assertEquals(0, view.pointerStateCountForTesting())
    }
}
