package com.kazumaproject.custom_keyboard.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
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
    @Test fun guideKeepsTheCardButLeavesUnconfiguredSpaceWithoutFixedGridLines() {
        val theme=ContextThemeWrapper(ApplicationProvider.getApplicationContext(),com.google.android.material.R.style.Theme_Material3_DayNight)
        val free=PopupMapGuideView(theme).apply {
            config=SumirePopupDefaults.create("は")
            map=config!!.maps.single()
            tapActive=false
        }
        val appearance=TfbiGuidePopupView(theme)
        val panelOnly=object : View(theme) {
            override fun onDraw(canvas: Canvas) {
                val inset=resources.displayMetrics.density
                appearance.drawGuidePanel(canvas,RectF(inset,inset,width-inset,height-inset),drawGrid=false)
            }
        }
        fun render(view: View): Bitmap {
            view.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY))
            view.layout(0,0,600,400)
            return Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888).also {view.draw(Canvas(it))}
        }
        val actual=render(free)
        val expected=render(panelOnly)
        assertNotEquals(0,actual.getPixel(30,30))
        assertEquals(expected.getPixel(200,30),actual.getPixel(200,30))
        assertEquals(expected.getPixel(30,133),actual.getPixel(30,133))
        assertFalse("Configured contours still have to be drawn",actual.sameAs(expected))
    }

    @Test fun renderedCircleAndPolygonPathsMatchTheirPhysicalHitContours() {
        val regions=listOf(
            PopupRegion("circle",PopupBounds(.6f,-.4f,1.6f,.6f),outline=PopupOutline()),
            PopupRegion("triangle",PopupBounds(-1.6f,-.4f,-.6f,.6f),outline=PopupOutline(PopupOutline.TRIANGLE))
        )
        for(region in regions) {
            val path=region.outlinePath(300f,200f,200f,100f)
            val samples=path.approximate(.1f)
            for(i in samples.indices step 3) {
                val x=(samples[i+1]-300)/200;val y=(samples[i+2]-200)/100
                assertEquals(region.id,0f,region.distanceTo(x,y,200f,100f),.2f)
            }
        }
    }

    @Test @Config(sdk=[35],qualifiers="xxhdpi")
    fun aCircleLabelRemainsVisibleAtTheActualCompactGuideSize() {
        val theme=ContextThemeWrapper(ApplicationProvider.getApplicationContext(),com.google.android.material.R.style.Theme_Material3_DayNight)
        val initial=SumirePopupDefaults.create("は")
        val map=initial.maps.single().copy(tapText="",regions=initial.maps.single().regions.mapIndexed { i,r ->
            r.copy(action=PopupAction(text=if(i==1) "ふ" else ""),outline=if(i==1) PopupOutline() else null)
        })
        val view=PopupMapGuideView(theme).apply {
            config=initial.withMap(map);this.map=map;selectedId=map.regions[1].id;tapActive=false
            sourceKeyWidth=200f;sourceKeyHeight=128f
            measure(View.MeasureSpec.makeMeasureSpec(237,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(211,View.MeasureSpec.EXACTLY))
            layout(0,0,237,211)
        }
        fun render()=Bitmap.createBitmap(237,211,Bitmap.Config.ARGB_8888).also {view.draw(Canvas(it))}
        val labelled=render()
        view.map=map.copy(regions=map.regions.map {it.copy(action=PopupAction())})
        assertFalse("The contour label must not disappear due to fixed padding",labelled.sameAs(render()))
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
