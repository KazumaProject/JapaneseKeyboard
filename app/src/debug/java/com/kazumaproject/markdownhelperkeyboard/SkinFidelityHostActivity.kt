package com.kazumaproject.markdownhelperkeyboard

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.*
import android.widget.FrameLayout
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Debug-only host: exercises real keyboard views without replacing the user's active IME. */
class SkinFidelityHostActivity : Activity() {
    lateinit var keyboard: ViewGroup
    val events = JSONArray()
    val frames = JSONArray()
    private var eventSerial = 0
    private var frameClock: FrameClockView? = null
    fun startFrameRecording() { frameClock?.start() }
    fun stopFrameRecording() { frameClock?.stop() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setTheme(R.style.Theme_MarkdownKeyboard)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        if (intent.getBooleanExtra("frameMatrix", false)) {
            val mode = display?.supportedModes?.firstOrNull { kotlin.math.abs(it.refreshRate - 60f) < 1f }
            window.attributes = window.attributes.apply {
                preferredRefreshRate = 60f
                if (mode != null) preferredDisplayModeId = mode.modeId
            }
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(128,128,128)) }
        val type = intent.getStringExtra("keyboard") ?: "kana"
        val id = if (intent.getBooleanExtra("dark",false)) KeyboardSkinId.CUPERTINO_DARK else KeyboardSkinId.CUPERTINO_LIGHT
        keyboard = if(type=="kana") {
            val inflated=LayoutInflater.from(this).inflate(R.layout.main_layout,null)
            inflated.findViewById<TenKey>(R.id.keyboard_view).also { (it.parent as ViewGroup).removeView(it) }
        } else QWERTYKeyboardView(this)
        keyboard.visibility=View.VISIBLE
        val palette = requireNotNull(KeyboardSkinRegistry.find(id)).palette
        keyboard.javaClass.methods.single { it.name=="applyKeyboardTheme" }.invoke(keyboard,
            "custom",android.content.res.Configuration.UI_MODE_NIGHT_NO,false,
            palette.background,palette.key,palette.key,palette.text,palette.text,false,false,Color.BLACK,255,1,id)
        keyboard.javaClass.methods.single { it.name=="setLongPressTimeout" }.invoke(keyboard,300L)
        root.addView(keyboard,FrameLayout.LayoutParams(-1,(280*resources.displayMetrics.density).toInt(),Gravity.BOTTOM))
        if (intent.getBooleanExtra("recordFrames", false)) {
            frameClock = FrameClockView()
            root.addView(frameClock, FrameLayout.LayoutParams(640,60).apply {leftMargin=20;topMargin=400})
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
    }
    private inner class FrameClockView : View(this@SkinFidelityHostActivity), Choreographer.FrameCallback {
            var serial=0;var tick=0;val paint=Paint()
            private var running = false
            fun start() { if (!running) { running = true; Choreographer.getInstance().postFrameCallback(this) } }
            fun stop() { running = false; Choreographer.getInstance().removeFrameCallback(this) }
            override fun onDetachedFromWindow(){stop();super.onDetachedFromWindow()}
            override fun doFrame(time:Long){if (!running) return;serial++;tick=(time/1000000).toInt();invalidate();Choreographer.getInstance().postFrameCallback(this)}
            override fun onDraw(canvas:Canvas){
                frames.put(JSONObject().put("serial",serial).put("timestamp",tick)
                    .put("drawTime",SystemClock.uptimeMillis()).put("eventSerial",eventSerial))
                for((row,value) in listOf(serial,tick,eventSerial).withIndex())for(bit in 0..31){
                    paint.color=if((value ushr bit)and 1==1)Color.WHITE else Color.BLACK
                    canvas.drawRect(bit*20f,row*20f,(bit+1)*20f,(row+1)*20f,paint)
                }
            }
    }
    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        eventSerial++
        events.put(JSONObject().put("serial",eventSerial).put("time",event.eventTime)
            .put("elapsedMinusMonotonicNanos",SystemClock.elapsedRealtimeNanos()-System.nanoTime())
            .put("dispatchMonotonicNanos",System.nanoTime()).put("phase",event.actionMasked).put("x",event.rawX).put("y",event.rawY))
        return super.dispatchTouchEvent(event)
    }
    fun exportEvents(name:String){
        File(getExternalFilesDir(null),"$name-events.json").writeText(events.toString(2))
        File(getExternalFilesDir(null),"$name-frames.json").writeText(frames.toString(2))
    }
}
