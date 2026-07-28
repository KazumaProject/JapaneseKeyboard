package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.custom_keyboard.data.FlickDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DirectionalKeyPopupViewTest {

    @Test
    fun verticalDirectionsFillLandscapePopupWidthWithoutRotationClipping() {
        val upBitmap = render(FlickDirection.UP)
        val downBitmap = render(FlickDirection.DOWN)

        listOf(40, POPUP_WIDTH - 40).forEach { x ->
            assertEquals(Color.RED, upBitmap.getPixel(x, 40))
            assertEquals(Color.RED, downBitmap.getPixel(x, POPUP_HEIGHT - 40))
        }
    }

    @Test
    fun horizontalDirectionsStillFillLandscapePopupHeight() {
        val leftBitmap = render(FlickDirection.UP_LEFT_FAR)
        val rightBitmap = render(FlickDirection.UP_RIGHT_FAR)

        listOf(30, POPUP_HEIGHT - 30).forEach { y ->
            assertEquals(Color.RED, leftBitmap.getPixel(40, y))
            assertEquals(Color.RED, rightBitmap.getPixel(POPUP_WIDTH - 40, y))
        }
    }

    private fun render(direction: FlickDirection): Bitmap {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        val view = DirectionalKeyPopupView(context).apply {
            text = ""
            applyPopupViewStyle(
                PopupViewStyle(
                    sizeScalePercent = 100,
                    textSizeSp = 28f,
                    backgroundColor = Color.RED,
                    textColor = Color.WHITE
                )
            )
            setFlickDirection(direction)
            measure(
                ViewMeasureSpec.exactly(POPUP_WIDTH),
                ViewMeasureSpec.exactly(POPUP_HEIGHT)
            )
            layout(0, 0, POPUP_WIDTH, POPUP_HEIGHT)
        }
        return Bitmap.createBitmap(
            POPUP_WIDTH,
            POPUP_HEIGHT,
            Bitmap.Config.ARGB_8888
        ).also { bitmap ->
            view.draw(Canvas(bitmap))
        }
    }

    private object ViewMeasureSpec {
        fun exactly(size: Int): Int =
            android.view.View.MeasureSpec.makeMeasureSpec(
                size,
                android.view.View.MeasureSpec.EXACTLY
            )
    }

    private companion object {
        const val POPUP_WIDTH = 401
        const val POPUP_HEIGHT = 120
    }
}
