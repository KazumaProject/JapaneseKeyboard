package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.view.PopupMapView
import com.kazumaproject.custom_keyboard.view.PopupMapGuideView
import com.kazumaproject.custom_keyboard.view.TfbiGestureArrowView
import com.kazumaproject.custom_keyboard.view.compactFlickGuideSize
import kotlin.math.ceil
import kotlin.math.hypot

@SuppressLint("ClickableViewAccessibility")
class PopupMapEditorCanvas(context: Context) : PopupMapView(context) {
    var inputOptions = PopupMapInputOptions()
        set(value) {
            if(field != value) {trial=null;fingerPosition=null;parent?.requestDisallowInterceptTouchEvent(false)}
            field=value;invalidate()
        }
    private val guide by lazy { PopupMapGuideView(context) }
    private val arrow by lazy { TfbiGestureArrowView(context).apply {setColor(0xff1976d2.toInt())} }
    private var fingerPosition: Pair<Float,Float>? = null
    var config: PopupKeyConfig? = null
    var mapId = "normal"
    var tapSelected = true
    var trying = false
    var onSelect: (String?) -> Unit = {}
    var onChange: (PopupKeyConfig) -> Unit = {}
    var onBlocked: (PopupConflict?) -> Unit = {}
    var onTrial: (String?, PopupMapSession) -> Unit = { _, _ -> }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startX = 0f
    private var startY = 0f
    private var original: PopupRegion? = null
    private var resizeCorner = -1
    private var dragging = false
    private var ghost: PopupBounds? = null
    private var trial: PopupMapSession? = null
    private var beforeDrag: PopupKeyConfig? = null
    val selectedBounds get() = map?.regions?.firstOrNull { it.id == selectedId }?.bounds

    fun showConfig(value: PopupKeyConfig, pageId: String, selection: String?, test: Boolean) {
        config = value; mapId = pageId; map = value.map(pageId)
        selectedId = selection; tapSelected = selection == null; trying = test
        if (!test) {trial = null;fingerPosition=null}
        invalidate()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        keyWidth = minOf(w / 3.65f, 100 * resources.displayMetrics.density)
        keyHeight = minOf(h / 5.2f, 64 * resources.displayMetrics.density)
        originX = w / 2f; originY = h - keyHeight * 1.6f
    }
    private fun rect(b: PopupBounds) = RectF(originX+b.left*keyWidth,originY+b.top*keyHeight,originX+b.right*keyWidth,originY+b.bottom*keyHeight)
    private fun inViewport(b: PopupBounds): Boolean {
        val r=rect(b); val margin=4*resources.displayMetrics.density
        return r.left>=margin && r.top>=margin && r.right<=width-margin && r.bottom<=height-margin
    }
    fun canPlace(value: PopupKeyConfig, bounds: PopupBounds): Boolean = inViewport(bounds) && PopupMapValidator.conflicts(value).isEmpty()
    override fun onDraw(canvas: Canvas) {
        if (!trying || inputOptions.presentation == PopupMapPresentation.AROUND_KEY) {
            super.onDraw(canvas)
        } else {
            val size=compactFlickGuideSize(keyWidth.toInt(),keyHeight.toInt(),resources.displayMetrics.density)
            val panelWidth=minOf(width-8*resources.displayMetrics.density,size.width.toFloat()).toInt().coerceAtLeast(1)
            val panelHeight=minOf(size.height,(originY-keyHeight/2-4*resources.displayMetrics.density).toInt().coerceAtLeast(1))
            val panelTop=(originY-keyHeight/2-4*resources.displayMetrics.density-panelHeight).coerceAtLeast(0f)
            guide.apply {
                config=this@PopupMapEditorCanvas.config;map=this@PopupMapEditorCanvas.map
                selectedId=this@PopupMapEditorCanvas.selectedId;tapActive=this@PopupMapEditorCanvas.tapSelected
                sourceKeyWidth=this@PopupMapEditorCanvas.keyWidth;sourceKeyHeight=this@PopupMapEditorCanvas.keyHeight
                fingerPosition=this@PopupMapEditorCanvas.fingerPosition
                title=trial?.mapPath?.joinToString(" → ") { id -> config?.map(id)?.name.orEmpty() } ?: map?.name.orEmpty()
                measure(MeasureSpec.makeMeasureSpec(panelWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(panelHeight,MeasureSpec.EXACTLY))
                layout(0,0,panelWidth,panelHeight)
            }
            val saved=canvas.save();canvas.translate((width-panelWidth)/2f,panelTop);guide.draw(canvas);canvas.restoreToCount(saved)
        }
        val primary=MaterialColors.getColor(this,androidx.appcompat.R.attr.colorPrimary)
        val foreground=MaterialColors.getColor(this,R.attr.colorOnSurface)
        pen.style=Paint.Style.FILL
        pen.color=MaterialColors.getColor(this,if(tapSelected) R.attr.colorPrimaryContainer else R.attr.colorSurfaceContainerHigh)
        val key=rect(PopupBounds.KEY)
        canvas.drawRoundRect(key,10f,10f,pen)
        pen.style=Paint.Style.STROKE;pen.strokeWidth=if(tapSelected) 3*resources.displayMetrics.density else resources.displayMetrics.density
        pen.color=if(tapSelected) primary else MaterialColors.getColor(this,R.attr.colorOutline)
        canvas.drawRoundRect(key,10f,10f,pen)
        pen.style=Paint.Style.FILL;pen.color=foreground;pen.textAlign=Paint.Align.CENTER;pen.textSize=24*resources.displayMetrics.scaledDensity
        val tap=map?.tapText.orEmpty().ifEmpty { "—" }
        val text=android.text.TextUtils.ellipsize(tap,android.text.TextPaint(pen),key.width()-12,android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(text,key.centerX(),key.centerY()-(pen.ascent()+pen.descent())/2,pen)
        if (trying && inputOptions.presentation==PopupMapPresentation.GUIDE_ABOVE_KEY) {
            arrow.setDirection(guide.pointerDirection())
            arrow.measure(MeasureSpec.makeMeasureSpec(keyWidth.toInt(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(keyHeight.toInt(),MeasureSpec.EXACTLY))
            arrow.layout(0,0,keyWidth.toInt(),keyHeight.toInt())
            val saved=canvas.save();canvas.translate(key.left,key.top);arrow.draw(canvas);canvas.restoreToCount(saved)
        }
        if (!trying) {
            config?.maps?.forEach { source -> source.regions.filter { it.action.type==PopupActionType.SWITCH && it.action.targetMapId==mapId }.forEach {
                pen.style=Paint.Style.STROKE;pen.strokeWidth=resources.displayMetrics.density
                pen.color=MaterialColors.getColor(this,R.attr.colorTertiary);pen.pathEffect=DashPathEffect(floatArrayOf(8f,6f),0f)
                canvas.drawRect(rect(it.bounds),pen);pen.pathEffect=null
            } }
            selectedBounds?.let { b ->
                val r=rect(b);pen.style=Paint.Style.FILL;pen.color=primary
                listOf(r.left to r.top,r.right to r.top,r.left to r.bottom,r.right to r.bottom).forEach { canvas.drawCircle(it.first,it.second,5*resources.displayMetrics.density,pen) }
            }
            ghost?.let { pen.style=Paint.Style.STROKE;pen.color=MaterialColors.getColor(this,androidx.appcompat.R.attr.colorError);pen.strokeWidth=2*resources.displayMetrics.density;pen.pathEffect=DashPathEffect(floatArrayOf(10f,6f),0f);canvas.drawRect(rect(it),pen);pen.pathEffect=null }
        }
        contentDescription="${config?.keyId} / ${map?.name} / ${map?.tapText}"
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val value=config ?: return false
        val x=(event.x-originX)/keyWidth;val y=(event.y-originY)/keyHeight
        if (trying) {
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (x !in -.5f.. .5f || y !in -.5f.. .5f) return true
                    parent.requestDisallowInterceptTouchEvent(true)
                    trial=PopupMapSession(value,keyWidth,keyHeight,inputOptions)
                    fingerPosition=x to y
                }
                MotionEvent.ACTION_MOVE -> trial?.let { active ->
                    for(i in 0 until event.historySize) {
                        val hx=(event.getHistoricalX(i)-originX)/keyWidth;val hy=(event.getHistoricalY(i)-originY)/keyHeight
                        active.move(hx,hy)
                    }
                    fingerPosition=x to y
                    active.move(x,y)
                }
                MotionEvent.ACTION_UP -> trial?.let { active ->
                    val output=active.release(x,y);onTrial(output,active)
                    trial=null;fingerPosition=null;parent.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL -> {trial=null;fingerPosition=null;parent.requestDisallowInterceptTouchEvent(false)}
            }
            trial?.let { map=it.currentMap;selectedId=it.selectedRegionId;tapSelected=it.isTap;onTrial(null,it) }
            if(trial==null) {map=value.map(mapId);selectedId=null;tapSelected=true}
            invalidate();return true
        }
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX=event.x;startY=event.y;dragging=false;ghost=null
                val corner=selectedBounds?.let { b ->
                    val r=rect(b)
                    val corners=listOf(r.left to r.top,r.right to r.top,r.left to r.bottom,r.right to r.bottom)
                    corners.indices.minByOrNull { i -> hypot(event.x-corners[i].first,event.y-corners[i].second) }?.takeIf { i -> hypot(event.x-corners[i].first,event.y-corners[i].second)<24*resources.displayMetrics.density }
                } ?: -1
                val hit=if(corner>=0) map?.regions?.firstOrNull { it.id==selectedId } else map?.regions?.firstOrNull { it.bounds.contains(x,y,keyWidth,keyHeight) }
                original=hit;beforeDrag=value;resizeCorner=corner
                selectedId=hit?.id;tapSelected=hit==null;onSelect(selectedId)
                parent.requestDisallowInterceptTouchEvent(true);invalidate();return true
            }
            MotionEvent.ACTION_MOVE -> {
                val source=original ?: return true
                if(!dragging && hypot(event.x-startX,event.y-startY)<ViewConfiguration.get(context).scaledTouchSlop) return true
                dragging=true
                val dx=(event.x-startX)/keyWidth;val dy=(event.y-startY)/keyHeight
                val b=source.bounds
                val desired=when(resizeCorner) {
                    0 -> b.copy(left=(b.left+dx).coerceAtMost(b.right-.35f),top=(b.top+dy).coerceAtMost(b.bottom-.35f))
                    1 -> b.copy(right=(b.right+dx).coerceAtLeast(b.left+.35f),top=(b.top+dy).coerceAtMost(b.bottom-.35f))
                    2 -> b.copy(left=(b.left+dx).coerceAtMost(b.right-.35f),bottom=(b.bottom+dy).coerceAtLeast(b.top+.35f))
                    3 -> b.copy(right=(b.right+dx).coerceAtLeast(b.left+.35f),bottom=(b.bottom+dy).coerceAtLeast(b.top+.35f))
                    else -> b.offset(dx,dy)
                }
                val current=selectedBounds ?: source.bounds
                val distance=maxOf(kotlin.math.abs(desired.left-current.left),kotlin.math.abs(desired.top-current.top),kotlin.math.abs(desired.right-current.right),kotlin.math.abs(desired.bottom-current.bottom))
                val steps=ceil(distance*keyWidth/2).toInt().coerceIn(1,512)
                var accepted=current
                var blocked=false
                var collision: PopupConflict?=null
                for(i in 1..steps) {
                    val t=i.toFloat()/steps
                    val b=PopupBounds(current.left+(desired.left-current.left)*t,current.top+(desired.top-current.top)*t,current.right+(desired.right-current.right)*t,current.bottom+(desired.bottom-current.bottom)*t)
                    val page=config!!.map(mapId)!!
                    val candidate=config!!.withMap(page.copy(regions=page.regions.map { if(it.id==source.id) it.copy(bounds=b) else it }))
                    collision=PopupMapValidator.conflicts(candidate).firstOrNull()
                    if(!inViewport(b) || collision!=null) {blocked=true;break}
                    accepted=b
                }
                val page=config!!.map(mapId)!!
                config=config!!.withMap(page.copy(regions=page.regions.map { if(it.id==source.id) it.copy(bounds=accepted) else it }))
                map=config!!.map(mapId);ghost=if(blocked) desired else null
                if(blocked) onBlocked(collision)
                invalidate();return true
            }
            MotionEvent.ACTION_UP -> {
                performClick();parent.requestDisallowInterceptTouchEvent(false)
                if(dragging && config!=beforeDrag) onChange(config!!)
                original=null;ghost=null;invalidate();return true
            }
            MotionEvent.ACTION_CANCEL -> {
                beforeDrag?.let { config=it;map=it.map(mapId) };original=null;ghost=null
                parent.requestDisallowInterceptTouchEvent(false);invalidate();return true
            }
        }
        return true
    }
}
