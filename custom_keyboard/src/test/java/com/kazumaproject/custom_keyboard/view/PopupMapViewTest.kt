package com.kazumaproject.custom_keyboard.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.PopupMap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PopupMapViewTest {
    private fun view() = PopupMapView(ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
        com.google.android.material.R.style.Theme_Material3_DayNight)).apply {
        map = PopupMap("tap-only", "タップのみ", "は")
        keyWidth = 200f; keyHeight = 100f; originX = 200f; originY = 150f
        measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
        layout(0, 0, 400, 300)
    }
    private fun render(view: View) = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    @Test fun tapOnlyMapPaintsTheFullOriginalKeyAndItsSelection() {
        val view = view()
        val selected = render(view)
        assertNotEquals("The full rectangular tap area includes its corners", 0, selected.getPixel(102, 102))
        assertNotEquals(0, selected.getPixel(297, 197))
        assertEquals(0, selected.getPixel(97, 150))
        assertEquals(0, selected.getPixel(200, 97))
        view.tapActive = false
        assertNotEquals("Leaving the key must clear its selection fill", selected.getPixel(110, 110), render(view).getPixel(110, 110))
    }

    @Test fun switchedMapReplacesTheTapLabelEvenWithoutOuterPopups() {
        val view = view()
        val original = render(view)
        view.map = PopupMap("voiced", "ば行", "ば")
        assertFalse("The popup itself must render the current map's tap label", original.sameAs(render(view)))
        view.map = view.map!!.copy(tapText = "")
        assertFalse("The tap label must be drawn rather than relying on the underlying key", original.sameAs(render(view)))
    }
}
