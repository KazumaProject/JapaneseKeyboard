package com.kazumaproject.custom_keyboard.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PopupMapGuideViewTest {
    @Test fun guideUsesTheSameCardBackgroundAsTwoStepFlick() {
        val theme=ContextThemeWrapper(ApplicationProvider.getApplicationContext(),com.google.android.material.R.style.Theme_Material3_DayNight)
        val free=PopupMapGuideView(theme).apply {
            config=PopupKeyConfig("は","root",listOf(PopupMap("root","通常","")))
            map=config!!.maps.single()
        }
        val twoStep=TfbiGuidePopupView(theme).apply {
            setState(TfbiGuidePopupState("",TfbiFlickDirection.TAP))
        }
        fun render(view: View): Bitmap {
            view.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(220,View.MeasureSpec.EXACTLY))
            view.layout(0,0,240,220)
            return Bitmap.createBitmap(240,220,Bitmap.Config.ARGB_8888).also {view.draw(Canvas(it))}
        }
        val actual=render(free)
        assertNotEquals(0,actual.getPixel(30,30))
        assertTrue(actual.sameAs(render(twoStep)))
    }
    @Test fun compactDimensionsMatchTheExistingTwoStepFormulaAndMinimums() {
        assertEquals(CompactFlickGuideSize(237,211),compactFlickGuideSize(200,128,2.75f))
        assertEquals(CompactFlickGuideSize(370,330),compactFlickGuideSize(200,100,1f,200))
        assertEquals(CompactFlickGuideSize(144,136),compactFlickGuideSize(10,10,2f))
    }
    @Test fun mapsShareOneProjectionAndRespectThePhysicalKeyAspectRatio() {
        val theme=ContextThemeWrapper(ApplicationProvider.getApplicationContext(),com.google.android.material.R.style.Theme_Material3_DayNight)
        val original=SumirePopupDefaults.create("は")
        val extra=PopupMap("extra","追加","ば",listOf(PopupRegion("far",PopupBounds(-3f,-3f,-2f,-2f),PopupAction(text="★"))))
        val view=PopupMapGuideView(theme).apply {
            config=original.copy(maps=original.maps+extra);map=original.maps.first()
            sourceKeyWidth=200f;sourceKeyHeight=100f
            fingerPosition=1f to -1f
            measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY))
            layout(0,0,600,400)
        }
        val canvas=Canvas(Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888))
        view.draw(canvas)
        val before=listOf(view.originX,view.originY,view.keyWidth,view.keyHeight)
        assertEquals(2f,view.keyWidth/view.keyHeight,.001f)
        view.map=extra;view.draw(canvas)
        assertEquals(before,listOf(view.originX,view.originY,view.keyWidth,view.keyHeight))
        assertEquals(1f to -1f,view.fingerPosition)
    }
}
