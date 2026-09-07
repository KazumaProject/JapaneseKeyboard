package com.kazumaproject.core.ui.skin

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Continuous corners, without the existing neumorphic inset or shadow. */
internal class CupertinoSurfaceDrawable(private val color: Int, private val radius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@CupertinoSurfaceDrawable.color }
    private val path = Path()

    override fun onBoundsChange(bounds: Rect) {
        path.reset()
        val r = min(radius, min(bounds.width(), bounds.height()) / 2f)
        val centers = arrayOf(
            floatArrayOf(bounds.right - r, bounds.top + r),
            floatArrayOf(bounds.right - r, bounds.bottom - r),
            floatArrayOf(bounds.left + r, bounds.bottom - r),
            floatArrayOf(bounds.left + r, bounds.top + r),
        )
        for (corner in 0..3) for (step in 0..24) {
            val angle = Math.PI * ((corner - 1) / 2.0 + step / 48.0)
            fun curve(value: Double) = (kotlin.math.sign(value) * kotlin.math.abs(value).pow(2.0 / 2.4)).toFloat()
            val x = centers[corner][0] + r * curve(cos(angle))
            val y = centers[corner][1] + r * curve(sin(angle))
            if (corner == 0 && step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    override fun draw(canvas: Canvas) { canvas.drawPath(path, paint) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getConstantState(): ConstantState = object : ConstantState() {
        override fun newDrawable(): Drawable = CupertinoSurfaceDrawable(color, radius)
        override fun getChangingConfigurations(): Int = 0
    }
}
