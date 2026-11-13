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

    @Test fun tapOnlyMapPaintsRoundedCornersAndItsSelection() {
        val view = view()
        val selected = render(view)
        assertEquals("Only the appearance is rounded", 0, selected.getPixel(102, 102))
        assertEquals(0, selected.getPixel(297, 197))
        assertEquals(0, selected.getPixel(97, 150))
        assertEquals(0, selected.getPixel(200, 97))
        view.tapActive = false
        assertNotEquals("Leaving the key must clear its selection fill", selected.getPixel(120, 120), render(view).getPixel(120, 120))
    }

    @Test fun switchedMapReplacesTheTapLabelEvenWithoutOuterPopups() {
        val view = view()
        val original = render(view)
        view.map = PopupMap("voiced", "ば行", "ば")
        assertFalse("The popup itself must render the current map's tap label", original.sameAs(render(view)))
        view.map = view.map!!.copy(tapText = "")
        assertFalse("The tap label must be drawn rather than relying on the underlying key", original.sameAs(render(view)))
    }
    @Test fun visualCornersDoNotShrinkTheTapHitArea() {
        val map = com.kazumaproject.custom_keyboard.data.SumirePopupDefaults.create("は")
        for ((x, y) in listOf(-.49f to -.49f, .49f to .49f)) {
            val session = com.kazumaproject.custom_keyboard.data.PopupMapSession(map, 200f, 100f)
            assertEquals("は", session.release(x, y))
            assertTrue(session.isTap)
        }
    }

    @Test fun defaultFlickPopupFillsReachTheTapSizedEdgesAroundTheKey() {
        val config = com.kazumaproject.custom_keyboard.data.SumirePopupDefaults.create("は")
        val centers = listOf(-1f to 0f, 0f to -1f, 1f to 0f, 0f to 1f)
        val edges = listOf(-.45f to 0f, .45f to 0f, 0f to -.45f, 0f to .45f)
        val background = 0xff243648.toInt()
        for (above in listOf(false)) {
            val drawing = view().apply { keyWidth = 200f; keyHeight = 100f; originX = 300f; originY = 200f }
            drawing.map = config.maps.single()
            drawing.tapActive = false
            drawing.applyPopupViewStyle(com.kazumaproject.core.data.popup.PopupViewStyle(100, 20f,
                backgroundColor = background, textColor = 0xffffdd88.toInt()))
            drawing.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            drawing.layout(0, 0, 600, 400)
            val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).also { drawing.draw(Canvas(it)) }
            for ((cx, cy) in centers + (0f to 0f)) {
                for ((dx, dy) in edges) {
                    val x = (drawing.originX + (cx + dx) * drawing.keyWidth).toInt()
                    val y = (drawing.originY + (cy + dy) * drawing.keyHeight).toInt()
                    assertEquals("above=$above popup=($cx,$cy) edge=($dx,$dy)", background, bitmap.getPixel(x, y))
                }
            }
        }
    }

    @Test fun skinsAndOverridesReachAroundAndAboveKeyPopups() {
        for (skinId in com.kazumaproject.core.domain.skin.KeyboardSkinId.entries) {
            for (above in listOf(false, true)) {
                val theme = view().context
                val config = com.kazumaproject.custom_keyboard.data.SumirePopupDefaults.create("は")
                val drawing = if (above) PopupMapGuideView(theme).apply {
                    this.config = config; sourceKeyWidth = 200f; sourceKeyHeight = 100f
                } else view()
                drawing.map = config.maps.single()
                drawing.applyPopupViewStyle(com.kazumaproject.core.data.popup.PopupViewStyle(100, 20f,
                    backgroundColor = 0xff243648.toInt(), textColor = 0xffffdd88.toInt(), skinId = skinId),
                    0xff965432.toInt())
                drawing.tapActive = false
                val bitmap = render(drawing.apply {
                    measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
                    layout(0, 0, 400, 300)
                })
                val skin = com.kazumaproject.core.ui.skin.KeyboardSkinRegistry.find(skinId)
                val x = (drawing.originX - drawing.keyWidth * .25f).toInt()
                val y = (drawing.originY - drawing.keyHeight * .25f).toInt()
                fun guidePanel(background: Int, text: Int): Bitmap {
                    val reference = TfbiGuidePopupView(theme).apply {
                        applyPopupViewStyle(com.kazumaproject.core.data.popup.PopupViewStyle(100,20f,
                            backgroundColor=background,textColor=text,skinId=skinId))
                        setColors(background,0xff965432.toInt(),text)
                        setState(com.kazumaproject.custom_keyboard.data.TfbiGuidePopupState("",TfbiFlickDirection.TAP))
                        measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY))
                        layout(0,0,400,300)
                    }
                    return render(reference)
                }
                val expectedBackground = if (above) guidePanel(0xff243648.toInt(),0xffffdd88.toInt()).getPixel(x,y)
                    else skin?.palette?.key ?: 0xff243648.toInt()
                assertEquals("$skinId above=$above background", expectedBackground, bitmap.getPixel(x, y))
                val expectedText = skin?.palette?.text ?: 0xffffdd88.toInt()
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertTrue("$skinId above=$above text color", pixels.any { it == expectedText })
                drawing.tapActive = true
                val selected = render(drawing)
                val selection = skin?.palette?.selection ?: 0xff965432.toInt()
                if (above) {
                    val selectedPixels = IntArray(selected.width*selected.height)
                    selected.getPixels(selectedPixels,0,selected.width,0,0,selected.width,selected.height)
                    val count = selectedPixels.count { it == selection }
                    assertTrue("The native guide selection pill must be visible",count>0)
                    assertTrue("The guide must not fill a whole key with the selection color",count<drawing.keyWidth*drawing.keyHeight/2)
                } else assertEquals("$skinId above=$above selection",selection,selected.getPixel(x,y))
                drawing.applyPopupViewStyle(com.kazumaproject.core.data.popup.PopupViewStyle(100, 20f,
                    backgroundColor = 0xff765432.toInt(), textColor = 0xffaabbcc.toInt()))
                drawing.tapActive = false
                val updated = if (above) {
                    val reference = TfbiGuidePopupView(theme).apply {
                        applyPopupViewStyle(com.kazumaproject.core.data.popup.PopupViewStyle(100,20f,
                            backgroundColor=0xff765432.toInt(),textColor=0xffaabbcc.toInt()))
                        setState(com.kazumaproject.custom_keyboard.data.TfbiGuidePopupState("",TfbiFlickDirection.TAP))
                        measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY));layout(0,0,400,300)
                    }
                    render(reference).getPixel(x,y)
                } else 0xff765432.toInt()
                assertEquals("Changing themes must update an existing view",updated,render(drawing).getPixel(x,y))
            }
        }
    }

    @Test @Config(sdk = [35], qualifiers = "night")
    fun themesAlsoReachBothPopupModesInDarkContext() = skinsAndOverridesReachAroundAndAboveKeyPopups()

}
