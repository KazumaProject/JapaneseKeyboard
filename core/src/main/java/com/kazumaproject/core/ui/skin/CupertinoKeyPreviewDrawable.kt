package com.kazumaproject.core.ui.skin

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** Complete cap, neck and stem contours, with independent edge and central profiles. */
class CupertinoKeyPreviewDrawable(
    private val dark: Boolean,
    private val stemWidth: Float,
    private val stemLeft: Float,
    private val lowerRowPosition: Float? = null,
) : Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {color=Color.WHITE}
    private var path=Path()
    override fun onBoundsChange(bounds:Rect) {
        val right=bounds.width()-stemLeft-stemWidth<=.5f
        val center=stemLeft>.5f && !right
        path=if(center)CupertinoKeyPreviewContours.centerPath(bounds.width().toFloat(),bounds.height().toFloat())
            else CupertinoKeyPreviewContours.path(bounds.width().toFloat(),bounds.height().toFloat(),right)
        paint.shader=if(dark)CupertinoKeyPreviewIllumination.shader(bounds.width(),bounds.height(),right,center,lowerRowPosition)else null
    }
    override fun draw(canvas:Canvas) {
        val saved=canvas.save()
        canvas.translate(bounds.left.toFloat(),bounds.top.toFloat())
        canvas.drawPath(path,paint)
        canvas.restoreToCount(saved)
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha;invalidateSelf()}
    override fun setColorFilter(colorFilter:ColorFilter?){paint.colorFilter=colorFilter;invalidateSelf()}
    @Deprecated("Deprecated in Android") override fun getOpacity():Int=PixelFormat.TRANSLUCENT
}
