package com.kazumaproject.core.ui.skin

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Direction is the popup's position relative to its key. */
internal class CupertinoPopupDrawable(
    private val color: Int,
    private val density: Float,
    private val direction: PopupDirection,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@CupertinoPopupDrawable.color }
    override fun draw(canvas: Canvas) {
        val body = RectF(bounds)
        val pointer = Path()
        val depth = when (direction) {
            PopupDirection.LEFT, PopupDirection.RIGHT -> body.width() / 3f
            PopupDirection.TOP, PopupDirection.BOTTOM -> body.height() / 3f
            PopupDirection.PREVIEW -> body.height() / 4f
            else -> 0f
        }
        when (direction) {
            PopupDirection.TOP, PopupDirection.PREVIEW -> {
                body.bottom -= depth
                pointer.moveTo(body.left + body.width() * .25f, body.bottom - density)
                pointer.lineTo(body.centerX(), bounds.bottom.toFloat())
                pointer.lineTo(body.right - body.width() * .25f, body.bottom - density)
            }
            PopupDirection.BOTTOM -> {
                body.top += depth
                pointer.moveTo(body.left + body.width() * .25f, body.top + density)
                pointer.lineTo(body.centerX(), bounds.top.toFloat())
                pointer.lineTo(body.right - body.width() * .25f, body.top + density)
            }
            PopupDirection.LEFT -> {
                body.right -= depth
                pointer.moveTo(body.right - density, body.top + body.height() * .2f)
                pointer.lineTo(bounds.right.toFloat(), body.centerY())
                pointer.lineTo(body.right - density, body.bottom - body.height() * .2f)
            }
            PopupDirection.RIGHT -> {
                body.left += depth
                pointer.moveTo(body.left + density, body.top + body.height() * .2f)
                pointer.lineTo(bounds.left.toFloat(), body.centerY())
                pointer.lineTo(body.left + density, body.bottom - body.height() * .2f)
            }
            PopupDirection.CENTER -> Unit
        }
        pointer.close()
        canvas.drawPath(pointer, paint)
        val radius = if (direction == PopupDirection.CENTER) 4f else 10f
        canvas.drawRoundRect(body, radius * density, radius * density, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getConstantState(): ConstantState = object : ConstantState() {
        override fun newDrawable(): Drawable = CupertinoPopupDrawable(color, density, direction)
        override fun getChangingConfigurations(): Int = 0
    }
}
