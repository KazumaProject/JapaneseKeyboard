package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.TypedValue
import android.view.View
import androidx.appcompat.R as AppCompatR
import com.kazumaproject.core.domain.extensions.getThemeColor
import kotlin.math.min

/** Draws the current flick direction over the touched key. */
class TfbiGestureArrowView(context: Context) : View(context) {

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(9f)
        color = context.getThemeColor(AppCompatR.attr.colorPrimary)
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = arrowPaint.color
    }
    private var direction: TfbiFlickDirection? = null

    fun setDirection(direction: TfbiFlickDirection?) {
        this.direction = direction?.takeUnless { it == TfbiFlickDirection.TAP }
        invalidate()
    }

    fun setColor(color: Int) {
        arrowPaint.color = color
        headPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val direction = direction ?: return
        val center = PointF(width / 2f, height / 2f)
        val radius = min(width, height) * 0.34f
        val vector = vectorFor(direction)
        val end = PointF(center.x + vector.x * radius, center.y + vector.y * radius)
        val path = Path().apply {
            moveTo(center.x - vector.x * radius * 0.25f, center.y - vector.y * radius * 0.25f)
            lineTo(end.x, end.y)
        }
        canvas.drawPath(path, arrowPaint)

        val headLength = dp(18f)
        val perpendicular = PointF(-vector.y, vector.x)
        val base = PointF(
            end.x - vector.x * headLength,
            end.y - vector.y * headLength
        )
        val triangle = Path().apply {
            moveTo(end.x, end.y)
            lineTo(
                base.x + perpendicular.x * headLength * 0.55f,
                base.y + perpendicular.y * headLength * 0.55f
            )
            lineTo(
                base.x - perpendicular.x * headLength * 0.55f,
                base.y - perpendicular.y * headLength * 0.55f
            )
            close()
        }
        canvas.drawPath(triangle, headPaint)
    }

    private fun vectorFor(direction: TfbiFlickDirection): PointF {
        return when (direction) {
            TfbiFlickDirection.UP -> PointF(0f, -1f)
            TfbiFlickDirection.DOWN -> PointF(0f, 1f)
            TfbiFlickDirection.LEFT -> PointF(-1f, 0f)
            TfbiFlickDirection.RIGHT -> PointF(1f, 0f)
            TfbiFlickDirection.UP_LEFT -> PointF(-0.75f, -0.75f)
            TfbiFlickDirection.UP_RIGHT -> PointF(0.75f, -0.75f)
            TfbiFlickDirection.DOWN_LEFT -> PointF(-0.75f, 0.75f)
            TfbiFlickDirection.DOWN_RIGHT -> PointF(0.75f, 0.75f)
            TfbiFlickDirection.TAP -> PointF(0f, 0f)
        }
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    )
}
