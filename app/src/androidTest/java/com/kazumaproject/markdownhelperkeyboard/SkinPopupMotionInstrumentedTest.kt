package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SkinPopupMotionInstrumentedTest {
    @Test fun kanaCommitDismissesGuideAndDetachRestoresLabelColors() {
        val ins = InstrumentationRegistry.getInstrumentation()
        for (dark in listOf(false, true)) {
            val commits = java.util.concurrent.CopyOnWriteArrayList<Char>()
            lateinit var retained: android.widget.PopupWindow
            lateinit var label: TextView
            lateinit var original: android.content.res.ColorStateList
            ActivityScenario.launch<SkinFidelityHostActivity>(Intent(ins.targetContext, SkinFidelityHostActivity::class.java)
                .putExtra("keyboard", "kana").putExtra("dark", dark)).use { scenario ->
                SystemClock.sleep(500)
                var x = 0f; var y = 0f
                scenario.onActivity { host ->
                    val keyboard = host.keyboard as com.kazumaproject.tenkey.TenKey
                    keyboard.setOnFlickListener(object : com.kazumaproject.core.domain.listener.FlickListener {
                        override fun onFlick(gestureType: com.kazumaproject.core.domain.state.GestureType,
                                             key: com.kazumaproject.core.domain.key.Key, char: Char?) {
                            char?.let(commits::add)
                        }
                    })
                    fun find(view: View): TextView? {
                        if (view is TextView && view.isShown && view.width > 0 && view.text.toString() == "な") return view
                        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                        return null
                    }
                    val key = requireNotNull(find(keyboard)); label = key; original = key.textColors
                    val location = IntArray(2)
                    key.getLocationOnScreen(location); x = location[0] + key.width / 2f; y = location[1] + key.height / 2f
                }
                val down = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    check(ins.uiAutomation.injectInputEvent(event, true)); event.recycle()
                    if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(500)
                }
                scenario.onActivity { host ->
                    org.junit.Assert.assertEquals(listOf('な'), commits.toList())
                    val field = host.keyboard.javaClass.getDeclaredField("popupWindowActive").apply { isAccessible = true }
                    retained = field.get(host.keyboard) as android.widget.PopupWindow
                    org.junit.Assert.assertFalse(retained.isShowing)
                    (host.keyboard.parent as ViewGroup).removeView(host.keyboard)
                    org.junit.Assert.assertFalse(host.keyboard.isAttachedToWindow)
                    org.junit.Assert.assertSame(original, label.textColors)
                }
            }
            org.junit.Assert.assertFalse(retained.isShowing)
            org.junit.Assert.assertEquals(listOf('な'), commits.toList())
            org.junit.Assert.assertSame(original, label.textColors)
        }
    }

    @Test fun recordProductionPopupMotion() {
        val ins=InstrumentationRegistry.getInstrumentation()
        val context=ins.targetContext
        val out=File(context.getExternalFilesDir(null),"motion").apply {mkdirs()}
        val metadata=JSONArray()
        val arguments=InstrumentationRegistry.getArguments()
        val types=arguments.getString("keyboard")?.let {listOf(it)} ?: listOf("kana","qwerty")
        val captureStills=arguments.getString("captureStills")!="false"
        for(type in types) for(dark in listOf(false,true)) {
            val name="$type-${if(dark)"dark" else "light"}"
            ActivityScenario.launch<SkinFidelityHostActivity>(Intent(context,SkinFidelityHostActivity::class.java)
                .putExtra("keyboard",type).putExtra("dark",dark)).use { scenario ->
                SystemClock.sleep(600)
                val labels=if(type=="kana")listOf("な", "な:left", "な:top", "な:right", "な:bottom")else listOf("q","e","p")
                for(label in labels) for(trial in 0..2) {
                    var x=0f;var y=0f;var w=0;var h=0
                    scenario.onActivity { host ->
                        fun find(view:View):TextView? {
                            if(view is TextView && view.isShown && view.width>0 && view.height>0 && view.text.toString().trim().equals(label.substringBefore(":"),true))return view
                            if(view is ViewGroup) for(i in 0 until view.childCount){find(view.getChildAt(i))?.let{return it}}
                            return null
                        }
                        val key=requireNotNull(find(host.keyboard)){"Missing $label"}
                        val loc=IntArray(2);key.getLocationOnScreen(loc)
                        w=key.width;h=key.height;x=loc[0]+w/2f;y=loc[1]+h/2f
                    }
                    check(w>0 && h>0 && x>0 && y>0){"Invalid key geometry for $label: $x,$y $w,$h"}
                    val anchorX=x;val anchorY=y
                    val down=SystemClock.uptimeMillis()
                    fun touch(action:Int){
                        val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
                        e.source=InputDevice.SOURCE_TOUCHSCREEN
                        check(ins.uiAutomation.injectInputEvent(e,true));e.recycle()
                    }
                    touch(MotionEvent.ACTION_DOWN)
                    val preview = type=="qwerty" && label!="e"
                    if (label.contains(":")) {
                        SystemClock.sleep(100)
                        val direction=label.substringAfter(":")
                        val step=5f*context.resources.displayMetrics.density
                        repeat(12) {
                            when(direction) { "left" -> x-=step; "right" -> x+=step; "top" -> y-=step; "bottom" -> y+=step }
                            touch(MotionEvent.ACTION_MOVE);SystemClock.sleep(8)
                        }
                    }
                    SystemClock.sleep(if(preview)120 else 1000)
                    if(trial==0 && captureStills)ins.uiAutomation.takeScreenshot().let { image ->
                        File(out,"$name-$label-held.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                        image.recycle()
                    }
                    SystemClock.sleep(if(preview)20 else 200)
                    touch(MotionEvent.ACTION_UP)
                    metadata.put(JSONObject().put("name",name).put("label",label).put("trial",trial)
                        .put("x",anchorX).put("y",anchorY).put("end_x",x).put("end_y",y).put("width",w).put("height",h).put("down",down).put("up",SystemClock.uptimeMillis()))
                    SystemClock.sleep(350)
                }
                scenario.onActivity {check(it.events.length()>0){"No touch events reached the keyboard host"};it.exportEvents(name)}
            }
        }
        File(out,"gestures.json").writeText(metadata.toString(2))
    }
    @Test fun shortTapCommitsBeforeVisualHoldAndDetachClosesRetainedPopup() {
        val ins=InstrumentationRegistry.getInstrumentation()
        for(dark in listOf(false,true)) {
            val releases=java.util.concurrent.CopyOnWriteArrayList<Char?>()
            lateinit var retained:android.widget.PopupWindow
            ActivityScenario.launch<SkinFidelityHostActivity>(Intent(ins.targetContext,SkinFidelityHostActivity::class.java)
                .putExtra("keyboard","qwerty").putExtra("dark",dark)).use { scenario ->
                SystemClock.sleep(500)
                var x=0f;var y=0f
                scenario.onActivity { host ->
                    val keyboard=host.keyboard as com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
                    val type=com.kazumaproject.core.domain.listener.QWERTYKeyListener::class.java
                    val listener=java.lang.reflect.Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _,method,args ->
                        if(method.name=="onReleasedQWERTYKey") releases.add(args?.get(1) as Char?)
                        null
                    } as com.kazumaproject.core.domain.listener.QWERTYKeyListener
                    keyboard.setOnQWERTYKeyListener(listener)
                    fun find(view:View):TextView? {
                        if(view is TextView && view.isShown && view.width>0 && view.text.toString().equals("q",true))return view
                        if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let{return it}
                        return null
                    }
                    val key=requireNotNull(find(keyboard));val location=IntArray(2);key.getLocationOnScreen(location)
                    x=location[0]+key.width/2f;y=location[1]+key.height/2f
                }
                val down=SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                    val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0)
                    event.source=InputDevice.SOURCE_TOUCHSCREEN
                    check(ins.uiAutomation.injectInputEvent(event,true));event.recycle()
                    if(action==MotionEvent.ACTION_DOWN)SystemClock.sleep(20)
                }
                scenario.onActivity { host ->
                    org.junit.Assert.assertEquals(listOf('q'),releases.toList())
                    val field=host.keyboard.javaClass.getDeclaredField("deferredPopupDismissals").apply {isAccessible=true}
                    val pending=field.get(host.keyboard) as Map<*,*>
                    org.junit.Assert.assertEquals(1,pending.size)
                    retained=pending.keys.single() as android.widget.PopupWindow
                    org.junit.Assert.assertTrue(retained.isShowing)
                }
            }
            org.junit.Assert.assertFalse(retained.isShowing)
            org.junit.Assert.assertEquals(listOf('q'),releases.toList())
        }
    }

}
