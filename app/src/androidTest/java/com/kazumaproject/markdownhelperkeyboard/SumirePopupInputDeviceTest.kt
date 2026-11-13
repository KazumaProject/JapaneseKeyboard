package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Matrix
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.controller.PopupMapInputController
import com.kazumaproject.custom_keyboard.view.PopupMapView
import com.kazumaproject.markdownhelperkeyboard.sumire_popup.SumirePopupRepository
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the installed IME and real InputConnection, including popup screen coordinates. */
@RunWith(AndroidJUnit4::class)
class SumirePopupInputDeviceTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val automation get()=ins.uiAutomation
    private val context get()=ins.targetContext
    private fun shell(command: String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use {it.readText()}
    @Test fun rotatedKeyUsesItsPhysicalCentreAndCancelKeepsItsLabel() {
        assumeTrue(context.packageName.endsWith(".popuptest"))
        val host=ins.startActivitySync(Intent(context,FastInputHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as FastInputHostActivity
        lateinit var key: TextView
        lateinit var controller: PopupMapInputController
        var committed: String?=null
        try {
            ins.runOnMainSync {
                val themed=ContextThemeWrapper(host,R.style.Theme_MarkdownKeyboard)
                val frame=FrameLayout(themed)
                key=TextView(themed).apply {text="は";rotation=30f}
                frame.addView(key,FrameLayout.LayoutParams(200,100).apply {leftMargin=200;topMargin=500})
                host.setContentView(frame)
                controller=PopupMapInputController(SumirePopupDefaults.create("は"),{null},{it},{},{_,_->},{text,_->committed=text},{})
                controller.attach(key)
            }
            await {key.width==200 && key.isAttachedToWindow}
            ins.runOnMainSync {
                fun touch(action: Int,x: Float,y: Float) {
                    val e=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,x,y,0)
                    key.dispatchTouchEvent(e);e.recycle()
                }
                touch(MotionEvent.ACTION_DOWN,100f,50f)
            }
            await {popup()?.width?.let {it>0}==true}
            ins.runOnMainSync {
                fun touch(action: Int,x: Float,y: Float) {
                    val e=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,x,y,0)
                    key.dispatchTouchEvent(e);e.recycle()
                }
                val drawing=popup()!!
                val position=IntArray(2).also(drawing::getLocationOnScreen)
                val centre=floatArrayOf(100f,50f)
                val matrix=Matrix();key.transformMatrixToGlobal(matrix);matrix.mapPoints(centre)
                assertEquals(centre[0],position[0]+drawing.originX,3f)
                assertEquals(centre[1],position[1]+drawing.originY,3f)
                assertEquals(30f,drawing.keyRotation,0f)
                touch(MotionEvent.ACTION_MOVE,-100f,-20f)
                touch(MotionEvent.ACTION_UP,-100f,-20f)
                assertEquals("ひ",committed)
                controller.cancel()
                assertEquals("は",key.text.toString())
                committed=null
                touch(MotionEvent.ACTION_DOWN,100f,50f)
                key.layoutParams=key.layoutParams.apply {width=220}
            }
            await {key.width==220}
            ins.runOnMainSync {
                val up=MotionEvent.obtain(0,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,100f,50f,0)
                key.dispatchTouchEvent(up);up.recycle()
                assertNull("Relayout must cancel instead of committing at stale coordinates",committed)
                assertEquals("は",key.text.toString())
            }
        } finally {ins.runOnMainSync {host.finish()}}
    }
    @Test fun fixedKeyOriginTextMapSwitchImmediateTextAndBack() {
        assumeTrue("Use an isolated debug application ID",context.packageName.endsWith(".popuptest"))
        val target="${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme=shell("settings get secure default_input_method").trim()
        val enabled=shell("ime list -s").lineSequence().any {it.trim()==target}
        val floating=InstrumentationRegistry.getArguments().getString("surface","normal")=="floating"
        val preferences=PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear()
            .putString("keyboard_order_preference","[\"SUMIRE\"]")
            .putBoolean("save_last_used_keyboard",false)
            .putBoolean("gojuon_keyboard_type_migrated_v1",true)
            .putString("sumire_input_method_preference",InstrumentationRegistry.getArguments().getString("inputStyle","flick"))
            .putString("sumire_keyboard_style_preference",SumirePopupDefaults.STYLE)
            .putBoolean("keyboard_floating_preference",floating)
            .putBoolean("sumire_restore_input_mode_on_restart_preference",false)
            .putBoolean("landscape_force_qwerty_preference",false)
            .putBoolean("live_conversion_preference",false)
            .putBoolean("learn_dictionary_preference",false)
            .putBoolean("flick_editor_preview_preference",false)
            .putBoolean("key_sound_preference",false).commit()
        val original=SumirePopupDefaults.create("は")
        val entry=original.map("normal")!!.regions.first {it.action.type==PopupActionType.SWITCH}
        SumirePopupRepository(context).save(original.withMap(original.map("voiced")!!.let { page ->
            page.copy(regions=page.regions.map { if (it.action.text=="び") it.copy(action=PopupAction(text="ありがとう")) else it }+PopupRegion("immediate",entry.bounds,PopupAction(text="★")))
        }))
        automation.serviceInfo=automation.serviceInfo.apply {flags=flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS}
        try {
            shell("ime enable $target");shell("ime set $target")
            shell("am start -n ${context.packageName}/${FastInputHostActivity::class.java.name}")
            await {host()!=null}
            if (InstrumentationRegistry.getArguments().getString("orientation")=="landscape") {
                ins.runOnMainSync {host()!!.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
                await {host()?.resources?.configuration?.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
                SystemClock.sleep(300)
            }
            reset()
            var key=findKey("は")
            preferences.edit().putString("sumire_keyboard_style_preference", "default").commit()
            SystemClock.sleep(200)
            preferences.edit().putString("sumire_keyboard_style_preference", SumirePopupDefaults.STYLE).commit()
            SystemClock.sleep(200)
            val origins=mutableListOf<Pair<Int,Int>>()
            for(fraction in listOf(.15f,.85f)) {
                reset();key=findKey("は")
                val down=SystemClock.uptimeMillis()
                inject(down,MotionEvent.ACTION_DOWN,key.left+key.width()*fraction,key.centerY().toFloat())
                await {popup()!=null}
                ins.runOnMainSync {
                    val view=popup()!!
                    val xy=IntArray(2).also(view::getLocationOnScreen)
                    val origin=(xy[0]+view.originX).toInt() to (xy[1]+view.originY).toInt()
                    origins+=origin
                    assertEquals("Popup x must use the original key",key.centerX().toFloat(),origin.first.toFloat(),3f)
                    assertEquals("Popup y must use the original key",key.centerY().toFloat(),origin.second.toFloat(),3f)
                }
                inject(down,MotionEvent.ACTION_UP,key.left+key.width()*fraction,key.centerY().toFloat())
                awaitText("は")
            }
            assertEquals(origins[0],origins[1])
            reset();key=findKey("は")
            gesture(key,listOf(-1f to -.7f));awaitText("ひ")
            reset();key=findKey("は")
            gesture(key,listOf(-1f to -2f,0f to 0f));awaitText("ば")
            reset();key=findKey("は")
            gesture(key,listOf(-1f to -2f));awaitText("★")
            reset();key=findKey("は")
            gesture(key,listOf(-1f to -2f,-1f to -.7f));awaitText("ありがとう")
            reset();key=findKey("は")
            gesture(key,listOf(-1f to -2f,0f to -3f,-1f to -.7f));awaitText("ひ")
            reset();key=findKey("は")
            gesture(key,listOf(1.6f to .6f));awaitText("")
        } finally {
            ins.runOnMainSync {host()?.let {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;it.finish()}}
            if(oldIme.isNotBlank() && oldIme!="null") shell("ime set $oldIme")
            if(!enabled) shell("ime disable $target")
        }
    }
    private fun host()=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FastInputHostActivity>().firstOrNull()
    private fun reset() {ins.runOnMainSync {host()!!.restartEditorInput(true)};SystemClock.sleep(250)}
    private fun awaitText(value: String) {await {host()?.editText?.text?.toString()==value}}
    private fun await(test: ()->Boolean) {
        val end=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<end) {
            var ready=false;ins.runOnMainSync {ready=test()}
            if(ready) return
            SystemClock.sleep(50)
        }
        var actual="";ins.runOnMainSync {actual=host()?.editText?.text?.toString().orEmpty()}
        fail("Timed out. Editor text=$actual")
    }
    private fun descendants(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap {descendants(view.getChildAt(it))} else emptyList()
    private fun popup()=WindowInspector.getGlobalWindowViews().flatMap(::descendants).filterIsInstance<PopupMapView>().firstOrNull()
    private fun findKey(label: String): Rect {
        val end=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<end) {
            val nodes=mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) {nodes+=node;repeat(node.childCount) {node.getChild(it)?.let(::visit)}}
            automation.windows.forEach {it.root?.let(::visit)}
            nodes.firstOrNull {it.packageName?.toString()==context.packageName && it.isVisibleToUser && it.isClickable && (it.text?.toString()?.lineSequence()?.firstOrNull()?.trim()==label || it.contentDescription?.toString()==label)}?.let {
                val bounds=Rect().also(it::getBoundsInScreen)
                if(!bounds.isEmpty) return bounds
            }
            SystemClock.sleep(50)
        }
        error("Key $label not found")
    }
    private fun inject(down: Long,action: Int,x: Float,y: Float) {
        val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0).apply {source=InputDevice.SOURCE_TOUCHSCREEN}
        check(automation.injectInputEvent(event,true));event.recycle();SystemClock.sleep(60)
    }
    private fun gesture(key: Rect,points: List<Pair<Float,Float>>) {
        val down=SystemClock.uptimeMillis()
        inject(down,MotionEvent.ACTION_DOWN,key.centerX().toFloat(),key.centerY().toFloat())
        points.forEach {(x,y)->inject(down,MotionEvent.ACTION_MOVE,key.centerX()+x*key.width(),key.centerY()+y*key.height())}
        val last=points.last();inject(down,MotionEvent.ACTION_UP,key.centerX()+last.first*key.width(),key.centerY()+last.second*key.height())
    }
}
