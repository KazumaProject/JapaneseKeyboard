package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectionalKeyPopupViewInstrumentedTest {

    @Test
    fun outlineIsFullyDrawnInsidePopupBoundsForEveryDirection() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
        val strokeSample = (context.resources.displayMetrics.density * 0.75f)
            .roundToInt()
            .coerceAtLeast(1)

        val tapBitmap = render(context, FlickDirection.TAP)
        val upBitmap = render(context, FlickDirection.UP)
        val downBitmap = render(context, FlickDirection.DOWN)
        val leftBitmap = render(context, FlickDirection.UP_LEFT_FAR)
        val rightBitmap = render(context, FlickDirection.UP_RIGHT_FAR)

        assertStroke(tapBitmap.getPixel(POPUP_WIDTH / 2, strokeSample))
        assertStroke(upBitmap.getPixel(POPUP_WIDTH / 2, strokeSample))
        assertStroke(
            downBitmap.getPixel(POPUP_WIDTH / 2, POPUP_HEIGHT - 1 - strokeSample)
        )
        assertStroke(leftBitmap.getPixel(strokeSample, POPUP_HEIGHT / 2))
        assertStroke(
            rightBitmap.getPixel(POPUP_WIDTH - 1 - strokeSample, POPUP_HEIGHT / 2)
        )
    }

    private fun render(context: Context, direction: FlickDirection): Bitmap {
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
            setColors(
                FlickPopupColorTheme(
                    segmentColor = Color.RED,
                    segmentHighlightGradientStartColor = Color.RED,
                    segmentHighlightGradientEndColor = Color.RED,
                    centerGradientStartColor = Color.RED,
                    centerGradientEndColor = Color.RED,
                    centerHighlightGradientStartColor = Color.RED,
                    centerHighlightGradientEndColor = Color.RED,
                    separatorColor = Color.GREEN,
                    textColor = Color.WHITE
                )
            )
            setFlickDirection(direction)
            measure(
                View.MeasureSpec.makeMeasureSpec(POPUP_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(POPUP_HEIGHT, View.MeasureSpec.EXACTLY)
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

    private fun assertStroke(actual: Int) {
        val distanceToStroke = colorDistance(actual, Color.GREEN)
        val distanceToFill = colorDistance(actual, Color.RED)
        assertTrue(
            "Expected a stroke-dominant pixel but was #${actual.toUInt().toString(16)}",
            distanceToStroke < distanceToFill
        )
    }

    private fun colorDistance(first: Int, second: Int): Int {
        return kotlin.math.abs(Color.red(first) - Color.red(second)) +
            kotlin.math.abs(Color.green(first) - Color.green(second)) +
            kotlin.math.abs(Color.blue(first) - Color.blue(second))
    }

    private companion object {
        const val POPUP_WIDTH = 401
        const val POPUP_HEIGHT = 120
    }
}
