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
import com.kazumaproject.custom_keyboard.view.popupRoundedPath
import com.kazumaproject.custom_keyboard.view.PopupMapView
import com.kazumaproject.custom_keyboard.view.PopupMapGuideView
import com.kazumaproject.custom_keyboard.view.TfbiGestureArrowView
import com.kazumaproject.custom_keyboard.view.compactFlickGuideSize
import kotlin.math.hypot

@SuppressLint("ClickableViewAccessibility")
class PopupMapEditorCanvas(context: Context) : PopupMapView(context) {
    // The editor paints its own tap selection and editing outline below.
    override val drawsTapRegion = false
    var inputOptions = PopupMapInputOptions()
        set(value) {
            if(field != value) cancelTrial()
            field=value;updateTapArea();invalidate()
        }
    private var sourceKeyWidth = 100 * resources.displayMetrics.density
    private var sourceKeyHeight = 64 * resources.displayMetrics.density
    private var trialOffsetX = 0f
    private var trialOffsetY = 0f
    fun setSourceKeySize(width: Float, height: Float) {
        if (width <= 0f || height <= 0f || (sourceKeyWidth == width && sourceKeyHeight == height)) return
        cancelTrial();sourceKeyWidth=width;sourceKeyHeight=height
        updateGeometry();updateTapArea();invalidate()
    }
    private fun updateTapArea() {
        tapArea=PopupTapArea.resolve(inputOptions.tapSizeMode,sourceKeyWidth,sourceKeyHeight,
            FreeFlickPreferences.gestureConfig(context).flickThresholdPx)
    }
    private fun updateGeometry() {
        if(width<=0 || height<=0) return
        keyWidth=minOf(width/3.65f,100*resources.displayMetrics.density,height/5.2f*sourceKeyWidth/sourceKeyHeight)
        keyHeight=keyWidth*sourceKeyHeight/sourceKeyWidth
        originX=width/2f;originY=height-keyHeight*1.6f
    }
    private val guide by lazy { PopupMapGuideView(context) }
    override fun applyPopupViewStyle(style: com.kazumaproject.core.data.popup.PopupViewStyle, highlightColor: Int?, borderColor: Int?) {
        super.applyPopupViewStyle(style, highlightColor, borderColor)
        guide.applyPopupViewStyle(style, highlightColor, borderColor)
        arrow.setColor(popupHighlightColor)
    }
    private val arrow by lazy { TfbiGestureArrowView(context).apply {setColor(0xff1976d2.toInt())} }
    private var fingerPosition: Pair<Float,Float>? = null
    var config: PopupKeyConfig? = null
    var mapId = "normal"
    var tapSelected = true
    var trying = false
    var onSelect: (String?) -> Unit = {}
    var onEdit: () -> Unit = {}
    var onChange: (PopupKeyConfig) -> Unit = {}
    var onBlocked: (PopupConflict?) -> Unit = {}
    var onTrial: (String?, PopupMapSession) -> Unit = { _, _ -> }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startX = 0f
    private var startY = 0f
    private var original: PopupRegion? = null
    private var resizeHandle = PopupBoundsSnapper.MOVE
    private var snapper: PopupBoundsSnapper? = null
    private var dragging = false
    private var editRequested = false
    private var ghost: PopupBounds? = null
    private var trial: PopupMapSession? = null
    private var trialVisibility: PopupMapVisibility? = null
    private val expandTrial = Runnable {
        if(trial?.let { trialVisibility?.longPress(it.isTap) }==true) {updateTrialVisibility();invalidate()}
    }
    private fun updateTrialVisibility() {
        val active=trial
        visibleRegionIds=if(active==null) emptySet() else trialVisibility?.regions(active)
        tapPopupVisible=active!=null && trialVisibility?.showsTap(active)==true
    }
    private fun cancelTrial() {
        removeCallbacks(expandTrial);trial=null;trialVisibility=null;fingerPosition=null
        trialOffsetX=0f;trialOffsetY=0f
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    override fun onDetachedFromWindow() {cancelTrial();super.onDetachedFromWindow()}

    private var beforeDrag: PopupKeyConfig? = null
    val selectedBounds get() = map?.regions?.firstOrNull { it.id == selectedId }?.bounds

    fun showConfig(value: PopupKeyConfig, pageId: String, selection: String?, test: Boolean) {
        config = value; mapId = pageId; map = value.map(pageId)
        selectedId = selection; tapSelected = selection == null; trying = test
        cancelTrial()
        visibleRegionIds=if(test) emptySet() else null
        tapPopupVisible=!test
        updateTapArea()
        invalidate()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cancelTrial()
        updateGeometry();updateTapArea()
    }
    private fun rect(b: PopupBounds) = RectF(originX+b.left*keyWidth,originY+b.top*keyHeight,originX+b.right*keyWidth,originY+b.bottom*keyHeight)
    private fun inViewport(b: PopupBounds): Boolean {
        val r=rect(b); val margin=4*resources.displayMetrics.density
        return r.left>=margin && r.top>=margin && r.right<=width-margin && r.bottom<=height-margin
    }
    fun canPlace(value: PopupKeyConfig, bounds: PopupBounds): Boolean = inViewport(bounds) && PopupMapValidator.conflicts(value).isEmpty()
    override fun onDraw(canvas: Canvas) {
        val above=trying && trialVisibility?.visible==true && inputOptions.presentation==PopupMapPresentation.GUIDE_ABOVE_KEY
        if (!trying || (trialVisibility?.visible==true && inputOptions.presentation == PopupMapPresentation.AROUND_KEY)) {
            super.onDraw(canvas)
        } else if(trialVisibility?.visible==true) {
            val size=compactFlickGuideSize(keyWidth.toInt(),keyHeight.toInt(),resources.displayMetrics.density)
            val panelWidth=minOf(width-8*resources.displayMetrics.density,size.width.toFloat()).toInt().coerceAtLeast(1)
            val panelHeight=minOf(size.height,(originY-keyHeight/2-4*resources.displayMetrics.density).toInt().coerceAtLeast(1))
            val panelTop=(originY-keyHeight/2-4*resources.displayMetrics.density-panelHeight).coerceAtLeast(0f)
            guide.apply {
                config=this@PopupMapEditorCanvas.config;map=this@PopupMapEditorCanvas.map
                selectedId=this@PopupMapEditorCanvas.selectedId;tapActive=this@PopupMapEditorCanvas.tapSelected
                visibleRegionIds=this@PopupMapEditorCanvas.visibleRegionIds;tapPopupVisible=this@PopupMapEditorCanvas.tapPopupVisible
                sourceKeyWidth=this@PopupMapEditorCanvas.keyWidth;sourceKeyHeight=this@PopupMapEditorCanvas.keyHeight
                tapArea=this@PopupMapEditorCanvas.tapArea
                fingerPosition=this@PopupMapEditorCanvas.fingerPosition
                title=trial?.mapPath?.joinToString(" → ") { id -> config?.map(id)?.name.orEmpty() } ?: map?.name.orEmpty()
                measure(MeasureSpec.makeMeasureSpec(panelWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(panelHeight,MeasureSpec.EXACTLY))
                layout(0,0,panelWidth,panelHeight)
            }
            val saved=canvas.save();canvas.translate((width-panelWidth)/2f,panelTop);guide.draw(canvas);canvas.restoreToCount(saved)
        }
        val primary=popupHighlightColor
        val foreground=if(tapSelected) popupSelectedTextColor else popupTextColor
        pen.style=Paint.Style.FILL
        pen.color=if(tapSelected) popupHighlightColor else popupBackgroundColor
        val key=rect(if(above) PopupBounds.KEY else tapArea.bounds)
        canvas.drawPath(key.popupRoundedPath(),pen)
        pen.style=Paint.Style.STROKE;pen.strokeWidth=if(tapSelected) 3*resources.displayMetrics.density else resources.displayMetrics.density
        pen.color=if(tapSelected) primary else popupBorderColor
        canvas.drawPath(key.popupRoundedPath(),pen)
        pen.style=Paint.Style.FILL;pen.color=foreground;pen.textAlign=Paint.Align.CENTER
        pen.textSize=minOf(24*resources.displayMetrics.scaledDensity,key.height()*.6f)
        val tap=map?.tapText.orEmpty().ifEmpty { "—" }
        val text=android.text.TextUtils.ellipsize(tap,android.text.TextPaint(pen),key.width()-12,android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(text,key.centerX(),key.centerY()-(pen.ascent()+pen.descent())/2,pen)
        if (trying && trialVisibility?.visible==true && inputOptions.presentation==PopupMapPresentation.GUIDE_ABOVE_KEY) {
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
                listOf(r.left to r.top,r.right to r.top,r.left to r.bottom,r.right to r.bottom, r.centerX() to r.top, r.centerX() to r.bottom, r.left to r.centerY(), r.right to r.centerY()).forEach { canvas.drawCircle(it.first,it.second,5*resources.displayMetrics.density,pen) }
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
                    cancelTrial()
                    parent.requestDisallowInterceptTouchEvent(true)
                    updateTapArea()
                    trialOffsetX=if(tapArea.followsSensitivity) event.x-originX else 0f
                    trialOffsetY=if(tapArea.followsSensitivity) event.y-originY else 0f
                    trial=PopupMapSession(value,keyWidth,keyHeight,inputOptions,tapArea,trialOffsetX/keyWidth,trialOffsetY/keyHeight)
                    fingerPosition=(event.x-originX-trialOffsetX)/keyWidth to (event.y-originY-trialOffsetY)/keyHeight
                    val settings=FreeFlickPreferences.gestureConfig(context)
                    trialVisibility=PopupMapVisibility(settings,event.x,event.y,inputOptions.presentation)
                    if(inputOptions.presentation==PopupMapPresentation.AROUND_KEY) postDelayed(expandTrial,settings.longPressTimeoutMillis)
                }
                MotionEvent.ACTION_MOVE -> trial?.let { active ->
                    for(i in 0 until event.historySize) {
                        val hx=(event.getHistoricalX(i)-originX)/keyWidth;val hy=(event.getHistoricalY(i)-originY)/keyHeight
                        trialVisibility?.move(event.getHistoricalX(i),event.getHistoricalY(i))
                        active.move(hx,hy)
                    }
                    trialVisibility?.move(event.x,event.y)
                    if(trialVisibility?.longPressEligible==false) removeCallbacks(expandTrial)
                    val tx=(event.x-originX-trialOffsetX)/keyWidth;val ty=(event.y-originY-trialOffsetY)/keyHeight
                    fingerPosition=tx to ty
                    active.move(x,y)
                }
                MotionEvent.ACTION_UP -> trial?.let { active ->
                    val output=active.release(x,y);onTrial(output,active)
                    cancelTrial()
                }
                MotionEvent.ACTION_CANCEL -> {cancelTrial()}
            }
            trial?.let { map=it.currentMap;selectedId=it.selectedRegionId;tapSelected=it.isTap;onTrial(null,it) }
            if(trial==null) {map=value.map(mapId);selectedId=null;tapSelected=true}
            updateTrialVisibility()
            invalidate();return true
        }
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX=event.x;startY=event.y;dragging=false;ghost=null
                val handles=listOf(PopupBoundsSnapper.LEFT or PopupBoundsSnapper.TOP,
                    PopupBoundsSnapper.RIGHT or PopupBoundsSnapper.TOP, PopupBoundsSnapper.LEFT or PopupBoundsSnapper.BOTTOM,
                    PopupBoundsSnapper.RIGHT or PopupBoundsSnapper.BOTTOM, PopupBoundsSnapper.TOP, PopupBoundsSnapper.BOTTOM,
                    PopupBoundsSnapper.LEFT, PopupBoundsSnapper.RIGHT)
                val handleIndex=selectedBounds?.let { b ->
                    val r=rect(b)
                    val points=listOf(r.left to r.top,r.right to r.top,r.left to r.bottom,r.right to r.bottom,
                        r.centerX() to r.top,r.centerX() to r.bottom,r.left to r.centerY(),r.right to r.centerY())
                    // Keep the popup's body available for movement, including at the minimum size.
                    val hitRadius=if(r.contains(event.x,event.y)) minOf(18*resources.displayMetrics.density,minOf(r.width(),r.height())*.25f)
                        else 18*resources.displayMetrics.density
                    points.indices.minByOrNull { i -> hypot(event.x-points[i].first,event.y-points[i].second) }
                        ?.takeIf { i -> hypot(event.x-points[i].first,event.y-points[i].second)<hitRadius }
                } ?: -1
                val hit=if(handleIndex>=0) map?.regions?.firstOrNull { it.id==selectedId }
                    else map?.regions?.firstOrNull { it.contains(x,y,keyWidth,keyHeight) }
                original=hit;beforeDrag=value
                resizeHandle=if(handleIndex>=0) handles[handleIndex] else PopupBoundsSnapper.MOVE
                val margin=4*resources.displayMetrics.density
                snapper=hit?.let { PopupBoundsSnapper(value,mapId,it.id,
                    PopupBounds((margin-originX)/keyWidth,(margin-originY)/keyHeight,
                        (width-margin-originX)/keyWidth,(height-margin-originY)/keyHeight),
                    keyWidth,keyHeight,resources.displayMetrics.density,resizeHandle) }
                selectedId=hit?.id;tapSelected=hit==null;onSelect(selectedId)
                editRequested=hit!=null || tapArea.contains(x,y,keyWidth,keyHeight)
                parent.requestDisallowInterceptTouchEvent(true);invalidate();return true
            }
            MotionEvent.ACTION_MOVE -> {
                val source=original ?: return true
                if(!dragging && hypot(event.x-startX,event.y-startY)<ViewConfiguration.get(context).scaledTouchSlop) return true
                dragging=true
                val dx=(event.x-startX)/keyWidth;val dy=(event.y-startY)/keyHeight
                val b=source.bounds
                val minWidth=minOf(b.width,PopupBoundsSnapper.MIN_SIZE)
                val minHeight=minOf(b.height,PopupBoundsSnapper.MIN_SIZE)
                val desired=if(resizeHandle==PopupBoundsSnapper.MOVE) b.offset(dx,dy) else b.copy(
                    left=if(resizeHandle and PopupBoundsSnapper.LEFT!=0) minOf(b.left+dx,b.right-minWidth) else b.left,
                    right=if(resizeHandle and PopupBoundsSnapper.RIGHT!=0) maxOf(b.right+dx,b.left+minWidth) else b.right,
                    top=if(resizeHandle and PopupBoundsSnapper.TOP!=0) minOf(b.top+dy,b.bottom-minHeight) else b.top,
                    bottom=if(resizeHandle and PopupBoundsSnapper.BOTTOM!=0) maxOf(b.bottom+dy,b.top+minHeight) else b.bottom)
                val accepted=snapper?.constrain(selectedBounds ?: b,desired) ?: b
                val page=config!!.map(mapId)!!
                val candidate=config!!.withMap(page.copy(regions=page.regions.map { if(it.id==source.id) it.copy(bounds=accepted) else it }))
                val conflict=PopupMapValidator.conflicts(candidate).firstOrNull()
                if(conflict==null && inViewport(accepted)) {config=candidate;map=candidate.map(mapId);ghost=null}
                else {ghost=desired;onBlocked(conflict)}
                invalidate();return true
            }
            MotionEvent.ACTION_UP -> {
                performClick();parent.requestDisallowInterceptTouchEvent(false)
                if(dragging && config!=beforeDrag) onChange(config!!)
                if(!dragging && editRequested && hypot(event.x-startX,event.y-startY)<ViewConfiguration.get(context).scaledTouchSlop) onEdit()
                original=null;snapper=null;ghost=null;invalidate();return true
            }
            MotionEvent.ACTION_CANCEL -> {
                beforeDrag?.let { config=it;map=it.map(mapId) };original=null;snapper=null;ghost=null
                parent.requestDisallowInterceptTouchEvent(false);invalidate();return true
            }
        }
        return true
    }
}
