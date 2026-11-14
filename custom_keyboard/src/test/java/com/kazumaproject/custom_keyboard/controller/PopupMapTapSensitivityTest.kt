package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.view.PopupMapGuideView
import com.kazumaproject.custom_keyboard.view.PopupMapView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class PopupMapTapSensitivityTest {
    private fun verify(presentation: PopupMapPresentation, settings: GestureSessionConfig=GestureSessionConfig(0,1,20f,300),
        block: (TextView,PopupMapInputController,MutableList<String>)->Unit) {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
        val frame=FrameLayout(activity);activity.setContentView(frame)
        val key=TextView(activity).apply {text="は"}
        frame.addView(key,FrameLayout.LayoutParams(200,100))
        frame.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY))
        frame.layout(0,0,600,400);key.layout(200,200,400,300)
        val output=mutableListOf<String>()
        val controller=PopupMapInputController(SumirePopupDefaults.create("は"),{frame},{it},{},{_,_->},{text,_->output+=text},{},
            PopupMapInputOptions(presentation=presentation,tapSizeMode=PopupTapSizeMode.FLICK_SENSITIVITY),
            GestureSessionConfigSource {settings})
        controller.attach(key)
        try {block(key,controller,output)} finally {controller.cancel();activity.finish()}
    }
    private fun TextView.event(action: Int,x: Float,y: Float) {
        val event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,x,y,0)
        try {dispatchTouchEvent(event)} finally {event.recycle()}
    }
    @Test fun edgeTapsStayTapsAndMovementUsesTheInitialTouchInBothPresentations() {
        for(presentation in PopupMapPresentation.entries) verify(presentation) { key,_,output ->
            for((x,y) in listOf(1f to 1f,199f to 99f,100f to 50f)) {
                key.event(MotionEvent.ACTION_DOWN,x,y);key.event(MotionEvent.ACTION_UP,x,y)
            }
            assertEquals(listOf("は","は","は"),output)
            key.event(MotionEvent.ACTION_DOWN,199f,99f)
            key.event(MotionEvent.ACTION_MOVE,223f,99f)
            key.event(MotionEvent.ACTION_UP,223f,99f)
            assertEquals("へ",output.last())
        }
    }
    @Test fun bothRenderersReceiveTheExactTapGeometryUsedByTheSession() {
        for(presentation in PopupMapPresentation.entries) verify(presentation) { key,controller,_ ->
            key.event(MotionEvent.ACTION_DOWN,25f,75f)
            val session=controller.javaClass.getDeclaredField("session").apply {isAccessible=true}.get(controller) as PopupMapSession
            val view=if(presentation==PopupMapPresentation.AROUND_KEY)
                controller.javaClass.getDeclaredField("content").apply {isAccessible=true}.get(controller) as PopupMapView
            else {
                val host=controller.javaClass.getDeclaredField("guideHost").apply {isAccessible=true}.get(controller)
                host!!.javaClass.getDeclaredField("view").apply {isAccessible=true}.get(host) as PopupMapGuideView
            }
            assertEquals(PopupBounds(-.1f,-.2f,.1f,.2f),session.tapArea.bounds)
            assertEquals(session.tapArea,view.tapArea)
            if(presentation==PopupMapPresentation.GUIDE_ABOVE_KEY) {
                assertNull("All ranges are visible at ACTION_DOWN without advancing the long-press timer",view.visibleRegionIds)
                assertTrue(view.tapPopupVisible)
            }
            key.event(MotionEvent.ACTION_CANCEL,25f,75f)
        }
    }
    @Test fun disablingBothPopupFlagsStillAllowsInputWithoutShowingAnAboveGuide() {
        verify(PopupMapPresentation.GUIDE_ABOVE_KEY,GestureSessionConfig(0,1,20f,300,showFlickPopup=false,showLongPressPopup=false)) {key,controller,output ->
            key.event(MotionEvent.ACTION_DOWN,199f,99f)
            assertNull(controller.javaClass.getDeclaredField("guideHost").apply {isAccessible=true}.get(controller))
            key.event(MotionEvent.ACTION_UP,199f,99f)
            assertEquals(listOf("は"),output)
        }
    }
}
