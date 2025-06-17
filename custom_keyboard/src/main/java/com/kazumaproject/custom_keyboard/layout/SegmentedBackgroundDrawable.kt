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
 * A Drawable that highlights specific areas of the background based on the flick direction.
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

    // Paint for the first (or single) line of text
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        textSize = 60f
    }

    // Paint for the smaller, second line of text
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
        textSize = 40f // Smaller text size for the second line
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
        if (label.contains("\n")) {
            val parts = label.split("\n", limit = 2)
            val primaryText = parts[0]
            val secondaryText = parts.getOrNull(1) ?: ""

            // --- PRECISE VERTICAL CENTERING LOGIC WITH ADJUSTABLE SPACING ---

            // Adjust this value to increase/decrease space between lines.
            // Negative values reduce the space.
            val lineSpacing = 2f

            // Get FontMetrics for precise height calculations
            val primaryFm = textPaint.fontMetrics
            val secondaryFm = secondaryTextPaint.fontMetrics

            // Calculate the height of each line of text
            val primaryTextHeight = primaryFm.descent - primaryFm.ascent
            val secondaryTextHeight = secondaryFm.descent - secondaryFm.ascent

            // Calculate the total height of the block, including the custom line spacing
            val totalTextHeight = primaryTextHeight + secondaryTextHeight + lineSpacing

            // Calculate the Y coordinate for the top of the entire text block
            val textBlockTop = centerY - (totalTextHeight / 2f)

            // Calculate the baseline for the first line
            val primaryTextBaselineY = textBlockTop - primaryFm.ascent

            // Calculate the baseline for the second line, accounting for the first line's height and the custom spacing
            val secondaryTextBaselineY =
                textBlockTop + primaryTextHeight + lineSpacing - secondaryFm.ascent

            // Draw both lines of text centered horizontally
            canvas.drawText(primaryText, centerX, primaryTextBaselineY, textPaint)
            canvas.drawText(secondaryText, centerX, secondaryTextBaselineY, secondaryTextPaint)


        } else {
            // Original logic for single-line text, which works correctly.
            canvas.drawText(
                label,
                centerX,
                centerY - (textPaint.descent() + textPaint.ascent()) / 2,
                textPaint
            )
        }
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
        secondaryTextPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        secondaryTextPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}
