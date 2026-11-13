package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import com.kazumaproject.custom_keyboard.data.PopupBounds
import com.kazumaproject.custom_keyboard.data.PopupKeyConfig
import com.kazumaproject.custom_keyboard.data.TfbiGuideFingerPosition
import com.kazumaproject.custom_keyboard.data.labelBounds
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Free-layout labels projected into the existing compact two-step guide appearance. */
class PopupMapGuideView(context: Context) : PopupMapView(context) {
    var config: PopupKeyConfig? = null
    var fingerPosition: Pair<Float, Float>? = null
    var title = ""
    var sourceKeyWidth = 100f
    var sourceKeyHeight = 64f
    private val appearance=TfbiGuidePopupView(context)
    private fun dp(value: Float) = value * resources.displayMetrics.density

    fun pointerDirection(): TfbiFlickDirection? {
        if(tapActive) return null
        val (x,y)=fingerPosition ?: return null
        val octant=(atan2(y*sourceKeyHeight,x*sourceKeyWidth)*4/Math.PI).roundToInt()
        return when((octant+8)%8) {
            0 -> TfbiFlickDirection.RIGHT
            1 -> TfbiFlickDirection.DOWN_RIGHT
            2 -> TfbiFlickDirection.DOWN
            3 -> TfbiFlickDirection.DOWN_LEFT
            4 -> TfbiFlickDirection.LEFT
            5 -> TfbiFlickDirection.UP_LEFT
            6 -> TfbiFlickDirection.UP
            else -> TfbiFlickDirection.UP_RIGHT
        }
    }
    override fun onDraw(canvas: Canvas) {
        val value=config ?: return
        val panel=RectF(dp(1f),dp(1f),width-dp(1f),height-dp(1f))
        appearance.drawGuidePanel(canvas,panel,drawGrid=false)
        val bounds=listOf(PopupBounds.KEY)+value.maps.flatMap {it.regions.map { region -> region.bounds }}
        val left=bounds.minOf {it.left};val right=bounds.maxOf {it.right}
        val top=bounds.minOf {it.top};val bottom=bounds.maxOf {it.bottom}
        val area=RectF(panel).apply {inset(dp(2f),dp(2f))}
        if(area.width()<=0 || area.height()<=0) return
        val scale=minOf(area.width()/((right-left)*sourceKeyWidth),area.height()/((bottom-top)*sourceKeyHeight))
        keyWidth=sourceKeyWidth*scale;keyHeight=sourceKeyHeight*scale;keyRotation=0f
        originX=area.centerX()-(left+right)*keyWidth/2
        originY=area.centerY()-(top+bottom)*keyHeight/2
        fun rect(b: PopupBounds)=RectF(originX+b.left*keyWidth,originY+b.top*keyHeight,originX+b.right*keyWidth,originY+b.bottom*keyHeight)
        fun label(text: String, target: RectF, active: Boolean, compact: Boolean=true, contour: Boolean=false) {
            val minimum=TextPaint().apply {textSize=if(contour) minOf(8*resources.displayMetrics.scaledDensity,target.height()*.75f,target.width()*.8f).coerceAtLeast(1f) else 8*resources.displayMetrics.scaledDensity}
            val display=TextUtils.ellipsize(textTransform(text),minimum,(target.width()-if(contour) 0f else dp(6f)).coerceAtLeast(1f),TextUtils.TruncateAt.END).toString()
            if(contour) appearance.drawGuideContourLabel(canvas,display,target)
            else appearance.drawGuideLabel(canvas,display,target,active,compact)
        }
        val tapRect = rect(PopupBounds.KEY)
        val tapPath = tapRect.popupRoundedPath()
        appearance.drawGuideOutline(canvas, tapPath, tapActive)
        label(map?.tapText.orEmpty(),tapRect,tapActive,false)
        map?.regions?.forEach { region ->
            val path = region.outlinePath(originX, originY, keyWidth, keyHeight)
            val active = region.id == selectedId
            appearance.drawGuideOutline(canvas, path, active)
            val clipped = canvas.save()
            canvas.clipPath(path)
            label(region.displayLabel(),rect(region.labelBounds(keyWidth,keyHeight)),active,contour=true)
            canvas.restoreToCount(clipped)
        }
        fingerPosition?.let { (x,y) ->
            appearance.drawGuidePointer(canvas,panel,TfbiGuideFingerPosition(
                (originX+x*keyWidth-panel.left)/panel.width(),(originY+y*keyHeight-panel.top)/panel.height()
            ))
        }
        contentDescription=title
    }
}
