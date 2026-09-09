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
internal class CupertinoSurfaceDrawable(private val color: Int, private val radius: Float, private val corners: Int = 15,
    private val guideIllumination: PopupDirection? = null,
    private val variationIllumination: Boolean = false) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@CupertinoSurfaceDrawable.color }
    private val path = Path()

    override fun onBoundsChange(bounds: Rect) {
        paint.shader = if (variationIllumination && bounds.width() > 0 && bounds.height() > 0)
            CupertinoVariationIllumination.shader(bounds.width(), bounds.height())
        else guideIllumination?.takeIf { bounds.width() > 0 && bounds.height() > 0 }?.let {
            CupertinoGuideIllumination.shader(bounds.width(), bounds.height(), it).apply {
                val matrix = android.graphics.Matrix()
                getLocalMatrix(matrix)
                matrix.postTranslate(bounds.left.toFloat(), bounds.top.toFloat())
                setLocalMatrix(matrix)
            }
        }
        path.reset()
        val r = min(radius, min(bounds.width(), bounds.height()) / 2f)
        for (corner in 0..3) {
            val cr = if (corners and (1 shl corner) != 0) r else 0f
            val cx = if (corner < 2) bounds.right-cr else bounds.left+cr
            val cy = if (corner == 0 || corner == 3) bounds.top+cr else bounds.bottom-cr
            for (step in 0..24) {
                val angle = Math.PI * ((corner - 1) / 2.0 + step / 48.0)
                fun curve(value: Double) = (kotlin.math.sign(value) * kotlin.math.abs(value).pow(2.0 / 2.4)).toFloat()
                val x = cx + cr * curve(cos(angle))
                val y = cy + cr * curve(sin(angle))
                if (corner == 0 && step == 0) path.moveTo(x,y) else path.lineTo(x,y)
            }
        }
        path.close()
    }

    override fun draw(canvas: Canvas) { canvas.drawPath(path, paint) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getConstantState(): ConstantState = object : ConstantState() {
        override fun newDrawable(): Drawable = CupertinoSurfaceDrawable(color, radius, corners, guideIllumination, variationIllumination)
        override fun getChangingConfigurations(): Int = 0
    }
}
