package com.kazumaproject.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton

/** Scales a label around its displayed center without changing the key's layout. */
open class ScalableKeyLabelButton(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageButton(context, attrs) {

    var labelScale: Float = 1f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val imageBounds = RectF()
    private var inkWidthFraction = 1f
    private var inkHeightFraction = 1f

    /** The fraction of the centered drawable that encloses its glyphs, including any strokes. */
    fun setLabelInkFractions(width: Float, height: Float) {
        inkWidthFraction = width
        inkHeightFraction = height
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val icon = drawable
        if (labelScale == 1f || icon == null || icon.bounds.isEmpty) {
            super.onDraw(canvas)
            return
        }

        // Start with the same fitted image as ImageView. Only the ink needs room to grow.
        imageBounds.set(icon.bounds)
        imageMatrix.mapRect(imageBounds)
        if (imageBounds.isEmpty) return
        imageBounds.offset(paddingLeft.toFloat(), paddingTop.toFloat())
        val centerX = imageBounds.centerX()
        val centerY = imageBounds.centerY()
        val right = width - paddingRight
        val bottom = height - paddingBottom
        val availableWidth = 2f * minOf(centerX - paddingLeft, right - centerX)
        val availableHeight = 2f * minOf(centerY - paddingTop, bottom - centerY)
        val scale = minOf(
            labelScale,
            availableWidth / (imageBounds.width() * inkWidthFraction),
            availableHeight / (imageBounds.height() * inkHeightFraction),
        )
        if (scale <= 0f) return

        val saved = canvas.save()
        canvas.clipRect(paddingLeft, paddingTop, right, bottom)
        canvas.scale(scale, scale, centerX, centerY)
        super.onDraw(canvas)
        canvas.restoreToCount(saved)
    }
}
