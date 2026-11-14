package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class PopupMapEditorCanvasTouchTest {
    private val initial=PopupBounds(-1f,-.175f,-.65f,.175f)
    private fun canvas(): PopupMapEditorCanvas {
        val context=ContextThemeWrapper(ApplicationProvider.getApplicationContext(),com.google.android.material.R.style.Theme_Material3_DayNight)
        val canvas=PopupMapEditorCanvas(context)
        val parent=FrameLayout(context).apply {addView(canvas,FrameLayout.LayoutParams(600,500))}
        parent.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY))
        parent.layout(0,0,600,500)
        canvas.showConfig(PopupKeyConfig("は","normal",listOf(PopupMap("normal","通常","は",
            listOf(PopupRegion("small",initial,PopupAction(text="ひ")))))),"normal","small",false)
        return canvas
    }
    private fun touch(canvas: PopupMapEditorCanvas, action: Int, x: Float, y: Float) {
        val event=MotionEvent.obtain(0,20,action,canvas.originX+x*canvas.keyWidth,canvas.originY+y*canvas.keyHeight,0)
        canvas.dispatchTouchEvent(event);event.recycle()
    }
    @Test fun draggingTheCenterOfASelectedSmallPopupMovesWithoutResizing() {
        val canvas=canvas();var changes=0;canvas.onChange={changes++}
        val cx=(initial.left+initial.right)/2
        touch(canvas,MotionEvent.ACTION_DOWN,cx,0f)
        touch(canvas,MotionEvent.ACTION_MOVE,cx+.2f,0f)
        touch(canvas,MotionEvent.ACTION_UP,cx+.2f,0f)
        val result=canvas.selectedBounds!!
        assertTrue("The body drag must move the selected popup",result.left>initial.left)
        assertEquals(initial.width,result.width,.0001f);assertEquals(initial.height,result.height,.0001f)
        assertEquals(1,changes);assertTrue(PopupMapValidator.conflicts(canvas.config!!).isEmpty())
    }
    @Test fun theVisibleEdgeHandleStillResizesASmallPopup() {
        val canvas=canvas();val cx=(initial.left+initial.right)/2
        touch(canvas,MotionEvent.ACTION_DOWN,cx,initial.top)
        touch(canvas,MotionEvent.ACTION_MOVE,cx,initial.top-.4f)
        touch(canvas,MotionEvent.ACTION_UP,cx,initial.top-.4f)
        val result=canvas.selectedBounds!!
        assertEquals(initial.left,result.left,0f);assertEquals(initial.right,result.right,0f)
        assertTrue(result.top<initial.top);assertEquals(initial.bottom,result.bottom,0f)
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun sensitivityTryPopupsKeepTheSameImageForCenterAndOffCenterPresses() {
        val canvas=canvas()
        AppPreference.init(canvas.context)
        AppPreference.flick_sensitivity_preference=1
        canvas.setSourceKeySize(200f,100f)
        val config=SumirePopupDefaults.create("は")
        fun render()=Bitmap.createBitmap(canvas.width,canvas.height,Bitmap.Config.ARGB_8888).also {canvas.draw(Canvas(it))}
        for(presentation in PopupMapPresentation.entries) {
            canvas.inputOptions=PopupMapInputOptions(presentation=presentation,tapSizeMode=PopupTapSizeMode.FLICK_SENSITIVITY)
            canvas.showConfig(config,"normal",null,true)
            touch(canvas,MotionEvent.ACTION_DOWN,0f,0f)
            val centered=render()
            touch(canvas,MotionEvent.ACTION_CANCEL,0f,0f)
            touch(canvas,MotionEvent.ACTION_DOWN,-.375f,.25f)
            val offCenter=render()
            assertTrue("$presentation must keep the tap and surrounding popups in place",centered.sameAs(offCenter))
            touch(canvas,MotionEvent.ACTION_CANCEL,-.375f,.25f)
            centered.recycle();offCenter.recycle()
        }
    }
    @Test fun sensitivityTrySelectsTheDisplayedSmallPopupAfterAnOffCenterPress() {
        val canvas=canvas()
        AppPreference.init(canvas.context)
        AppPreference.flick_sensitivity_preference=1
        canvas.setSourceKeySize(200f,100f)
        val config=PopupKeyConfig("は","normal",listOf(PopupMap("normal","通常","は",listOf(
            PopupRegion("small",PopupBounds(.7f,-.1f,.9f,.1f),PopupAction(text="へ"))))))
        for(presentation in PopupMapPresentation.entries) {
            canvas.inputOptions=PopupMapInputOptions(nearestEnabled=false,presentation=presentation,tapSizeMode=PopupTapSizeMode.FLICK_SENSITIVITY)
            canvas.showConfig(config,"normal",null,true)
            val output=mutableListOf<String>()
            canvas.onTrial={text,_->if(text!=null) output+=text}
            touch(canvas,MotionEvent.ACTION_DOWN,-.375f,.25f)
            touch(canvas,MotionEvent.ACTION_UP,-.375f,.25f)
            assertEquals(listOf("は"),output)
            touch(canvas,MotionEvent.ACTION_DOWN,-.375f,.25f)
            touch(canvas,MotionEvent.ACTION_MOVE,.8f,0f)
            touch(canvas,MotionEvent.ACTION_UP,.8f,0f)
            assertEquals(listOf("は","へ"),output)
        }
    }
}
