package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
