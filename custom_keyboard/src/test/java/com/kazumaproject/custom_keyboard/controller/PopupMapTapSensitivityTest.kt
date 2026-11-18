package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
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
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class PopupMapTapSensitivityTest {
    private fun verify(presentation: PopupMapPresentation, settings: GestureSessionConfig=GestureSessionConfig(0,1,20f,300),
        config: PopupKeyConfig=SumirePopupDefaults.create("は"),nearestEnabled: Boolean=true,
        tapSizeMode: PopupTapSizeMode=PopupTapSizeMode.FLICK_SENSITIVITY,
        block: (TextView,PopupMapInputController,MutableList<String>)->Unit) {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
        val frame=FrameLayout(activity);activity.setContentView(frame)
        val key=TextView(activity).apply {text="は"}
        frame.addView(key,FrameLayout.LayoutParams(200,100))
        frame.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY))
        frame.layout(0,0,600,400);key.layout(200,200,400,300)
        val output=mutableListOf<String>()
        val controller=PopupMapInputController(config,{frame},{it},{},{_,_->},{text,_->output+=text},{},
            PopupMapInputOptions(nearestEnabled=nearestEnabled,presentation=presentation,tapSizeMode=tapSizeMode),
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
    @Test fun aroundKeyPopupStaysCenteredOnTheKeyForOffCenterPresses() {
        for(mode in PopupTapSizeMode.entries) verify(PopupMapPresentation.AROUND_KEY,tapSizeMode=mode) { key,controller,_ ->
            for((x,y) in listOf(25f to 75f,199f to 1f,100f to 50f)) {
                key.event(MotionEvent.ACTION_DOWN,x,y)
                val drawing=controller.javaClass.getDeclaredField("content").apply {isAccessible=true}.get(controller) as PopupMapView
                @Suppress("UNCHECKED_CAST")
                val placement=controller.javaClass.getDeclaredField("lastPlacement").apply {isAccessible=true}.get(controller) as List<Int>
                val location=getLocationRelativeToWindowAnchor(key,key.parent as View)
                assertEquals("Popup X must stay at the key center for press ($x,$y)",location[0]+100f,placement[0]+drawing.originX,.5f)
                assertEquals("Popup Y must stay at the key center for press ($x,$y)",location[1]+50f,placement[1]+drawing.originY,.5f)
                key.event(MotionEvent.ACTION_CANCEL,x,y)
            }
        }
    }
    @Test fun aroundKeyPopupShowsTheDestinationMapOnAQuickSwitch() {
        val start = PopupMap("start", "Start", "は", listOf(
            PopupRegion("switch", PopupBounds(.5f, -.5f, 1.5f, .5f),
                PopupAction(PopupActionType.SWITCH, targetMapId = "next"))
        ))
        val next = PopupMap("next", "Next", "ば", listOf(
            PopupRegion("text", PopupBounds(-1.5f, -.5f, -.5f, .5f), PopupAction(text = "び")),
            PopupRegion("back", PopupBounds(-.5f, -1.5f, .5f, -.5f), PopupAction(PopupActionType.BACK))
        ))
        verify(
            PopupMapPresentation.AROUND_KEY,
            config = PopupKeyConfig("は", start.id, listOf(start, next)),
            nearestEnabled = false,
            tapSizeMode = PopupTapSizeMode.KEY
        ) { key, controller, output ->
            key.event(MotionEvent.ACTION_DOWN, 100f, 50f)
            val view = ReflectionHelpers.getField<PopupMapView>(controller, "content")
            val popup = ReflectionHelpers.getField<PopupWindow>(controller, "popup")
            assertEquals(emptySet<String>(), view.visibleRegionIds)

            key.event(MotionEvent.ACTION_MOVE, 300f, 50f)
            assertTrue(popup.isShowing)
            assertEquals(next, view.map)
            assertNull(view.selectedId)
            assertNull("All destination regions should be shown before a long press", view.visibleRegionIds)
            assertTrue(view.tapPopupVisible)

            key.event(MotionEvent.ACTION_MOVE, 100f, -50f)
            assertEquals(start, view.map)
            assertEquals(emptySet<String>(), view.visibleRegionIds)

            key.event(MotionEvent.ACTION_MOVE, 300f, 50f)
            key.event(MotionEvent.ACTION_MOVE, -100f, 50f)
            key.event(MotionEvent.ACTION_UP, -100f, 50f)
            assertEquals(listOf("び"), output)
            assertFalse(popup.isShowing)
        }
    }
    @Test fun directSelectionUsesDisplayedKeyRelativeRegionsAfterAnOffCenterPress() {
        val config=PopupKeyConfig("は","normal",listOf(PopupMap("normal","通常","は",listOf(
            PopupRegion("small",PopupBounds(.7f,-.1f,.9f,.1f),PopupAction(text="へ"))))))
        for(presentation in PopupMapPresentation.entries) verify(presentation,config=config,nearestEnabled=false) {key,_,output ->
            key.event(MotionEvent.ACTION_DOWN,25f,75f)
            key.event(MotionEvent.ACTION_MOVE,260f,50f)
            key.event(MotionEvent.ACTION_UP,260f,50f)
            assertEquals(listOf("へ"),output)
        }
    }
    @Test fun aboveKeyPointerUsesMovementFromInitialTouchRatherThanTheKeyCenter() {
        verify(PopupMapPresentation.GUIDE_ABOVE_KEY) { key,controller,_ ->
            key.event(MotionEvent.ACTION_DOWN,25f,75f)
            key.event(MotionEvent.ACTION_MOVE,49f,75f)
            val host=controller.javaClass.getDeclaredField("guideHost").apply {isAccessible=true}.get(controller)!!
            val view=host.javaClass.getDeclaredField("view").apply {isAccessible=true}.get(host) as PopupMapGuideView
            assertEquals(com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.RIGHT,view.pointerDirection())
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
    @Test fun bothPresentationsCommitDirectionalFlicksFromCenterAndEdgePresses() {
        for (presentation in PopupMapPresentation.entries) verify(presentation) { key, _, output ->
            for ((start, end) in listOf(
                (100f to 50f) to (130f to 66f),
                (100f to 99f) to (130f to 104f),
                (100f to 1f) to (130f to -4f),
            )) {
                key.event(MotionEvent.ACTION_DOWN, start.first, start.second)
                key.event(MotionEvent.ACTION_MOVE, end.first, end.second)
                key.event(MotionEvent.ACTION_UP, end.first, end.second)
                assertEquals("へ", output.last())
            }
        }
    }

}
