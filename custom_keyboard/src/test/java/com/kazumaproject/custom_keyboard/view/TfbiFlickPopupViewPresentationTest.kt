package com.kazumaproject.custom_keyboard.view

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ContextThemeWrapper
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TfbiFlickPopupViewPresentationTest {

    @Test
    fun longPressGetsItsOwnPanelAcrossCupertinoSkinsAndReturnsToFlickPresentation() {
        for (skin in listOf(
                KeyboardSkinId.CUPERTINO_LIGHT,
                KeyboardSkinId.CUPERTINO_DARK,
                KeyboardSkinId.CUPERTINO_CLASSIC
            )
        ) {
            val view = popupView(skin)
            val flickPixels = render(view)

            view.setPresentationMode(TfbiFlickPopupView.PresentationMode.LONG_PRESS)
            val longPressPixels = render(view)
            assertEquals(TfbiFlickPopupView.PresentationMode.LONG_PRESS, presentationMode(view))
            val changedPixels = flickPixels.indices.count { flickPixels[it] != longPressPixels[it] }
            assertTrue("$skin should use a distinct long-press panel", changedPixels > 0)
            assertEquals(
                "$skin should leave an unused diagonal cell transparent",
                0,
                Color.alpha(longPressPixels[75 * view.width + 50])
            )
            assertEquals(
                "$skin should leave an empty hold-only direction transparent",
                0,
                Color.alpha(longPressPixels[375 * view.width + 50])
            )
            assertTrue(
                "$skin should draw the configured top choice",
                Color.alpha(longPressPixels[75 * view.width + 150]) > 0
            )

            view.setPresentationMode(TfbiFlickPopupView.PresentationMode.FLICK)
            assertArrayEquals(flickPixels, render(view))
        }
    }

    @Test
    fun longPressPresentationModeDoesNotChangeOtherThemes() {
        val view = popupView(KeyboardSkinId.DEFAULT)
        val flickPixels = render(view)

        view.setPresentationMode(TfbiFlickPopupView.PresentationMode.LONG_PRESS)

        assertArrayEquals(flickPixels, render(view))
    }

    private fun popupView(skin: KeyboardSkinId): TfbiFlickPopupView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val context = ContextThemeWrapper(
            activity,
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        return TfbiFlickPopupView(context).apply {
            applyPopupViewStyle(PopupViewStyle(100, 20f, skinId = skin))
            setCharacters(
                ".",
                mapOf(
                    TfbiFlickDirection.UP to "あ",
                    TfbiFlickDirection.LEFT to "い",
                    TfbiFlickDirection.RIGHT to "う",
                    TfbiFlickDirection.DOWN to "え",
                    TfbiFlickDirection.DOWN_LEFT to ""
                )
            )
            measure(exactly(300), exactly(450))
            layout(0, 0, 300, 450)
        }
    }

    private fun render(view: View): IntArray {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return IntArray(view.width * view.height).also {
            bitmap.getPixels(it, 0, view.width, 0, 0, view.width, view.height)
            bitmap.recycle()
        }
    }

    private fun presentationMode(view: TfbiFlickPopupView) =
        TfbiFlickPopupView::class.java.getDeclaredField("presentationMode")
            .apply { isAccessible = true }
            .get(view) as TfbiFlickPopupView.PresentationMode

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
