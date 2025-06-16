package com.kazumaproject.custom_keyboard.layout

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.withClip
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

    var highlightDirection: FlickDirection? = null
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

        // 1. Draw the base background
        backgroundPaint.color = baseColor
        canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, backgroundPaint)

        // 2. Create and draw the highlight path
        val highlightPath = getHighlightPath(boundsF)
        if (highlightPath != null) {
            backgroundPaint.color = highlightColor
            // Clipping by the Path maintains the button's rounded corners
            canvas.withClip(highlightPath) {
                drawRoundRect(boundsF, cornerRadius, cornerRadius, backgroundPaint)
            }
        }

        // 3. Draw the label
        canvas.drawText(
            label,
            centerX,
            centerY - (textPaint.descent() + textPaint.ascent()) / 2,
            textPaint
        )
    }

    private fun getHighlightPath(bounds: RectF): Path? {
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val path = Path()

        when (highlightDirection) {
            FlickDirection.TAP -> {
                val tapRadius = bounds.width() / 4.5f
                path.addCircle(centerX, centerY, tapRadius, Path.Direction.CW)
            }

            FlickDirection.UP -> {
                path.moveTo(bounds.left, bounds.top)      // Top-left
                path.lineTo(bounds.right, bounds.top)     // Top-right
                path.lineTo(centerX, centerY)             // Center
                path.close()
            }

            FlickDirection.DOWN -> {
                path.moveTo(bounds.left, bounds.bottom)   // Bottom-left
                path.lineTo(bounds.right, bounds.bottom)  // Bottom-right
                path.lineTo(centerX, centerY)             // Center
                path.close()
            }

            FlickDirection.UP_LEFT_FAR -> { // Left
                path.moveTo(bounds.left, bounds.top)      // Top-left
                path.lineTo(bounds.left, bounds.bottom)   // Bottom-left
                path.lineTo(centerX, centerY)             // Center
                path.close()
            }

            FlickDirection.UP_RIGHT_FAR -> { // Right
                path.moveTo(bounds.right, bounds.top)     // Top-right
                path.lineTo(bounds.right, bounds.bottom)  // Bottom-right
                path.lineTo(centerX, centerY)             // Center
                path.close()
            }

            else -> return null // No highlight
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

    /**
     * ▼▼▼ FIX: Deprecated method updated ▼▼▼
     */
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}
