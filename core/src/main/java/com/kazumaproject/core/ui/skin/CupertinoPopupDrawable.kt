package com.kazumaproject.core.ui.skin

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

internal class CupertinoPopupDrawable(
    private val color: Int,
    private val density: Float,
    private val direction: PopupDirection,
    private val dark: Boolean = false,
    private val selected: Boolean = false,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.color = this@CupertinoPopupDrawable.color }
    private var path = Path()
    override fun onBoundsChange(bounds: Rect) {
        if (direction != PopupDirection.CENTER && direction != PopupDirection.PREVIEW) {
            path = CupertinoPopupContours.path(bounds.width().toFloat(),bounds.height().toFloat(),direction)
            paint.shader = if (dark) CupertinoPopupIllumination.shader(bounds.width(),bounds.height(),direction) else null
        }
    }
    override fun draw(canvas: Canvas) {
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(),bounds.top.toFloat())
        if (direction == PopupDirection.CENTER || direction == PopupDirection.PREVIEW) {
            val radius = if (selected) 0f else 4f * density
            canvas.drawRoundRect(0f,0f,bounds.width().toFloat(),bounds.height().toFloat(),radius,radius,paint)
        } else canvas.drawPath(path,paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getConstantState(): ConstantState = object : ConstantState() {
        override fun newDrawable(): Drawable = CupertinoPopupDrawable(color,density,direction,dark,selected)
        override fun getChangingConfigurations(): Int = 0
    }
}
