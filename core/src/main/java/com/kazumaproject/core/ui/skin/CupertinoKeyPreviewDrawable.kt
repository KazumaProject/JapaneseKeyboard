package com.kazumaproject.core.ui.skin

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Expanded cap with a curved neck and the original key's stem, including edge keys. */
class CupertinoKeyPreviewDrawable(
    private val dark: Boolean,
    private val stemWidth: Float,
    private val stemLeft: Float,
    private val density: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val r = minOf(12 * density, w / 4, h / 5)
        val cap = h * .52f
        val neck = h * .65f
        val left = stemLeft.coerceIn(0f, (w - stemWidth).coerceAtLeast(0f))
        val right = (left + stemWidth).coerceAtMost(w)
        val sr = minOf(r * .7f, (right - left) / 2)
        path.reset()
        path.moveTo(r, 0f)
        path.lineTo(w - r, 0f)
        path.quadTo(w, 0f, w, r)
        path.lineTo(w, cap - r)
        path.cubicTo(w, cap + r / 2, right, cap, right, neck)
        path.lineTo(right, h - sr)
        path.quadTo(right, h, right - sr, h)
        path.lineTo(left + sr, h)
        path.quadTo(left, h, left, h - sr)
        path.lineTo(left, neck)
        path.cubicTo(left, cap, 0f, cap + r / 2, 0f, cap - r)
        path.lineTo(0f, r)
        path.quadTo(0f, 0f, r, 0f)
        path.close()
        paint.shader = if (dark) LinearGradient(0f, 0f, 0f, h,
            0xff303030.toInt(), 0xff565656.toInt(), Shader.TileMode.CLAMP) else null
        paint.color = 0xffffffff.toInt()
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
