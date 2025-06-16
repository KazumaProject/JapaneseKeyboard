package com.kazumaproject.custom_keyboard.layout

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.kazumaproject.custom_keyboard.data.FlickDirection

/**
 * フリック方向に応じて背景の特定領域をハイライト描画するDrawable
 */
class SegmentedBackgroundDrawable(
    private val label: String,
    private val baseColor: Int,
    private val highlightColor: Int,
    private val textColor: Int,
    private val cornerRadius: Float
) : Drawable() {

    var highlightedDirection: FlickDirection? = null
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        textSize = 60f
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val boundsF = RectF(bounds)
        val centerX = boundsF.centerX()
        val centerY = boundsF.centerY()

        // 1. ベースとなる背景を描画
        backgroundPaint.color = baseColor
        canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, backgroundPaint)

        // 2. ハイライト領域のPathを作成して描画
        val highlightPath = getHighlightPath(boundsF)
        if (highlightPath != null) {
            backgroundPaint.color = highlightColor
            // Pathの領域でクリッピングすることで、ボタンの角丸を維持する
            canvas.save()
            canvas.clipPath(highlightPath)
            canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, backgroundPaint)
            canvas.restore()
        }

        // 3. ラベルを描画
        canvas.drawText(
            label,
            centerX,
            centerY - (textPaint.descent() + textPaint.ascent()) / 2,
            textPaint
        )
    }

    /**
     * ▼▼▼ FIX: ハイライト領域を完全な三角形で生成するように修正 ▼▼▼
     * 方向に応じたハイライト用のPath（三角形/円）を生成する
     */
    private fun getHighlightPath(bounds: RectF): Path? {
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val path = Path()

        when (highlightedDirection) {
            FlickDirection.TAP -> {
                val tapRadius = bounds.width() / 4.5f
                path.addCircle(centerX, centerY, tapRadius, Path.Direction.CW)
            }

            FlickDirection.UP -> {
                path.moveTo(bounds.left, bounds.top)      // 左上
                path.lineTo(bounds.right, bounds.top)     // 右上
                path.lineTo(centerX, centerY)             // 中央
                path.close()
            }

            FlickDirection.DOWN -> {
                path.moveTo(bounds.left, bounds.bottom)   // 左下
                path.lineTo(bounds.right, bounds.bottom)  // 右下
                path.lineTo(centerX, centerY)             // 中央
                path.close()
            }

            FlickDirection.UP_LEFT_FAR -> { // Left
                path.moveTo(bounds.left, bounds.top)      // 左上
                path.lineTo(bounds.left, bounds.bottom)   // 左下
                path.lineTo(centerX, centerY)             // 中央
                path.close()
            }

            FlickDirection.UP_RIGHT_FAR -> { // Right
                path.moveTo(bounds.right, bounds.top)     // 右上
                path.lineTo(bounds.right, bounds.bottom)  // 右下
                path.lineTo(centerX, centerY)             // 中央
                path.close()
            }

            else -> return null // ハイライトなし
        }
        return path
    }


    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Deprecated(
        "Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
    )
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}
