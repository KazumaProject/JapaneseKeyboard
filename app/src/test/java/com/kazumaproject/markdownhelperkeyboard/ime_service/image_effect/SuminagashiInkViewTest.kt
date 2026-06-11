package com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun cancelClearsPointerState() {
        view.configure(
            enabled = true,
            colorMode = "fixed",
            fixedColor = Color.rgb(38, 70, 120)
        )
        view.onPointerDown(pointerId = 4, x = 20f, y = 30f)
        assertEquals(1, view.pointerStateCountForTesting())

        view.onCancel()

        assertEquals(0, view.pointerStateCountForTesting())
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
