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
        assertEquals(expected.getPixel(200,5),actual.getPixel(200,5))
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
        val appearance=PopupMapGuideView::class.java.getDeclaredField("appearance").apply {isAccessible=true}.get(view)
        val text=TfbiGuidePopupView::class.java.getDeclaredField("activeTextPaint").apply {isAccessible=true}.get(appearance) as android.graphics.Paint
        assertTrue("Compact contour padding must leave room for at least 8sp text",text.textSize>=8*theme.resources.displayMetrics.scaledDensity)
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

    @Test fun distantPopupShrinksTheSharedProjectionInsideTheSameGuide() {
        val theme=ContextThemeWrapper(ApplicationProvider.getApplicationContext(),com.google.android.material.R.style.Theme_Material3_DayNight)
        val original=SumirePopupDefaults.create("は")
        val view=PopupMapGuideView(theme).apply {
            config=original;map=original.maps.first();sourceKeyWidth=200f;sourceKeyHeight=128f
            measure(View.MeasureSpec.makeMeasureSpec(237,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(211,View.MeasureSpec.EXACTLY))
            layout(0,0,237,211)
        }
        val canvas=Canvas(Bitmap.createBitmap(237,211,Bitmap.Config.ARGB_8888))
        view.draw(canvas)
        val nearKeyWidth=view.keyWidth
        val far=PopupMap("far","遠い配置","ば",listOf(PopupRegion("far-top",PopupBounds(-.45f,-4.4f,.45f,-3.6f),PopupAction(text="ぶ"))))
        view.config=original.copy(maps=original.maps+far)
        view.draw(canvas)
        assertEquals(237,view.width);assertEquals(211,view.height)
        assertTrue("Distant regions reduce the projection scale, not increase the window",view.keyWidth<nearKeyWidth)
        val shared=listOf(view.originX,view.originY,view.keyWidth,view.keyHeight)
        view.map=far;view.draw(canvas)
        assertEquals("Even an inactive map contributes to the common scale",shared,listOf(view.originX,view.originY,view.keyWidth,view.keyHeight))
        val bounds=far.regions.single().bounds
        assertTrue(view.originY+bounds.top*view.keyHeight>0f)
        assertTrue(view.originY+original.maps.flatMap { it.regions }.maxOf { it.bounds.bottom }*view.keyHeight<view.height)
    }
    @Test fun revealingAllPopupsDoesNotMoveOrRescaleTheGuide() {
        val theme=ContextThemeWrapper(ApplicationProvider.getApplicationContext(),com.google.android.material.R.style.Theme_Material3_DayNight)
        val view=PopupMapGuideView(theme).apply {
            config=SumirePopupDefaults.create("は");map=config!!.maps.single()
            visibleRegionIds=emptySet();tapPopupVisible=true
            measure(View.MeasureSpec.makeMeasureSpec(237,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(211,View.MeasureSpec.EXACTLY))
            layout(0,0,237,211)
        }
        fun render()=android.graphics.Bitmap.createBitmap(237,211,android.graphics.Bitmap.Config.ARGB_8888).also {view.draw(Canvas(it))}
        val single=render()
        val before=listOf(view.originX,view.originY,view.keyWidth,view.keyHeight)
        view.visibleRegionIds=null
        val expanded=render()
        assertEquals(before,listOf(view.originX,view.originY,view.keyWidth,view.keyHeight))
        assertFalse("Long press must reveal additional contours",single.sameAs(expanded))
    }

    @Test fun pointerNeverCoversASelectedPopupLabel() {
        val theme=ContextThemeWrapper(ApplicationProvider.getApplicationContext(),com.google.android.material.R.style.Theme_Material3_DayNight)
        val original=SumirePopupDefaults.create("は")
        val region=original.maps.single().regions.first().copy(action=PopupAction(text="い"),outline=PopupOutline())
        val page=original.maps.single().copy(regions=listOf(region))
        val view=PopupMapGuideView(theme).apply {
            config=original.withMap(page);map=page;selectedId=region.id;tapActive=false
            visibleRegionIds=setOf(region.id);tapPopupVisible=false
            applyPopupViewStyle(com.kazumaproject.core.data.popup.PopupViewStyle(100,20f,
                backgroundColor=0xff243648.toInt(),textColor=0xffffdd88.toInt()),0xff965432.toInt())
            measure(View.MeasureSpec.makeMeasureSpec(248,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(221,View.MeasureSpec.EXACTLY))
            layout(0,0,248,221)
        }
        fun render()=Bitmap.createBitmap(248,221,Bitmap.Config.ARGB_8888).also {view.draw(Canvas(it))}
        val clear=render()
        view.fingerPosition=(region.bounds.left+region.bounds.right)/2 to (region.bounds.top+region.bounds.bottom)/2
        val pointing=render()
        val b=region.labelBounds(view.keyWidth,view.keyHeight)
        for(y in (view.originY+b.top*view.keyHeight).toInt()+3 until (view.originY+b.bottom*view.keyHeight).toInt()-3)
            for(x in (view.originX+b.left*view.keyWidth).toInt()+3 until (view.originX+b.right*view.keyWidth).toInt()-3)
                assertEquals("The position marker must leave the popup label readable at $x,$y",clear.getPixel(x,y),pointing.getPixel(x,y))
    }

    @Test fun guideShowsTheSavedCircularRangeWithoutFillingItAsAKey() {
        val theme=ContextThemeWrapper(ApplicationProvider.getApplicationContext(),com.google.android.material.R.style.Theme_Material3_DayNight)
        val original=SumirePopupDefaults.create("は")
        val region=original.maps.single().regions.first().copy(outline=PopupOutline(),action=PopupAction(text="○"))
        val page=original.maps.single().copy(regions=listOf(region))
        val guide=PopupMapGuideView(theme).apply {
            config=original.withMap(page);map=page;tapPopupVisible=false;tapActive=false
            sourceKeyWidth=200f;sourceKeyHeight=100f
            applyPopupViewStyle(com.kazumaproject.core.data.popup.PopupViewStyle(100,20f,
                backgroundColor=0xff243648.toInt(),textColor=0xffffdd88.toInt()),0xff1976d2.toInt())
            measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY));layout(0,0,600,400)
        }
        fun render()=Bitmap.createBitmap(600,400,Bitmap.Config.ARGB_8888).also {guide.draw(Canvas(it))}
        val actual=render()
        guide.map=page.copy(regions=emptyList())
        val panel=render()
        val cx=guide.originX-guide.keyWidth
        val cy=guide.originY
        val insideX=(cx+guide.keyWidth*.1f).toInt()
        val insideY=(cy+guide.keyHeight*.25f).toInt()
        assertTrue(region.contains(-.9f,.25f,guide.keyWidth,guide.keyHeight))
        assertEquals("Unselected ranges use the two-step guide panel, not filled key surfaces",panel.getPixel(insideX,insideY),actual.getPixel(insideX,insideY))
        val outsideX=(guide.originX-1.45f*guide.keyWidth).toInt()
        val outsideY=(guide.originY-.45f*guide.keyHeight).toInt()
        assertFalse(region.contains(-1.45f,-.45f,guide.keyWidth,guide.keyHeight))
        assertEquals("The bounds corner must not replace the actual circular range",panel.getPixel(outsideX,outsideY),actual.getPixel(outsideX,outsideY))
        val top=(cy-guide.keyHeight/2).toInt()
        assertTrue("The actual circular boundary must be visible",(-2..2).any {dy -> (-2..2).any {dx ->
            actual.getPixel(cx.toInt()+dx,top+dy)!=panel.getPixel(cx.toInt()+dx,top+dy)
        }})
    }

}
