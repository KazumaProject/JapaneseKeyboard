package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.popup_style

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.kazumaproject.core.data.popup.PopupViewStyle

class PopupStylePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 247, 250)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 71, 79)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private var style = PopupViewStyle(100, 28f)
    var previewText: String = "あ"
        set(value) {
            field = value
            invalidate()
        }

    fun applyStyle(style: PopupViewStyle) {
        this.style = PopupViewStyle(
            style.sizeScalePercent.coerceIn(50, 200),
            style.textSizeSp.coerceIn(8f, 48f)
        )
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dpToPx(160f).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundPaint.color)
        val scale = style.sizeScalePercent.coerceIn(50, 200) / 100f
        val baseW = dpToPx(86f)
        val baseH = dpToPx(92f)
        val w = baseW * scale
        val h = baseH * scale
        val left = width / 2f - w / 2f
        val top = height / 2f - h / 2f
        val rect = RectF(left, top, left + w, top + h)
        canvas.drawRoundRect(rect, dpToPx(18f), dpToPx(18f), bubblePaint)

        textPaint.textSize = spToPx(style.textSizeSp)
        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(previewText, rect.centerX(), textY, textPaint)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
    }
}

class FlickPopupStylePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Target {
        DIRECTIONAL,
        CROSS,
        STANDARD,
        TFBI
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 247, 250)
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(69, 90, 100)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(96, 125, 139)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private var style = PopupViewStyle(100, 28f)
    private var target = Target.DIRECTIONAL

    fun applyStyle(target: Target, style: PopupViewStyle) {
        this.target = target
        this.style = PopupViewStyle(
            style.sizeScalePercent.coerceIn(50, 200),
            style.textSizeSp.coerceIn(8f, 48f)
        )
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dpToPx(190f).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bgPaint.color)
        textPaint.textSize = spToPx(style.textSizeSp)
        when (target) {
            Target.DIRECTIONAL -> drawDirectionalPreview(canvas)
            Target.CROSS -> drawCrossPreview(canvas)
            Target.STANDARD -> drawStandardPreview(canvas)
            Target.TFBI -> drawTfbiPreview(canvas)
        }
    }

    private fun drawDirectionalPreview(canvas: Canvas) {
        val scale = style.sizeScalePercent / 100f
        val w = dpToPx(112f) * scale
        val h = dpToPx(72f) * scale
        val left = width / 2f - w / 2f
        val top = height / 2f - h / 2f
        val pointer = dpToPx(22f) * scale
        val path = Path().apply {
            moveTo(left + dpToPx(16f), top)
            lineTo(left + w - pointer, top)
            lineTo(left + w, top + h / 2f)
            lineTo(left + w - pointer, top + h)
            lineTo(left + dpToPx(16f), top + h)
            quadTo(left, top + h, left, top + h - dpToPx(16f))
            lineTo(left, top + dpToPx(16f))
            quadTo(left, top, left + dpToPx(16f), top)
            close()
        }
        canvas.drawPath(path, popupPaint)
        canvas.drawPath(path, strokePaint)
        drawCenteredText(canvas, "あ", RectF(left, top, left + w - pointer, top + h))
    }

    private fun drawCrossPreview(canvas: Canvas) {
        val scale = style.sizeScalePercent / 100f
        val cell = dpToPx(46f) * scale
        val startX = width / 2f - cell * 1.5f
        val startY = height / 2f - cell * 1.5f
        val labels = mapOf(
            1 to "上",
            3 to "左",
            4 to "あ",
            5 to "右",
            7 to "下"
        )
        for (row in 0..2) {
            for (col in 0..2) {
                val index = row * 3 + col
                val rect = RectF(
                    startX + col * cell,
                    startY + row * cell,
                    startX + (col + 1) * cell,
                    startY + (row + 1) * cell
                )
                canvas.drawRoundRect(rect, dpToPx(10f), dpToPx(10f), if (index == 4) highlightPaint else popupPaint)
                canvas.drawRoundRect(rect, dpToPx(10f), dpToPx(10f), strokePaint)
                labels[index]?.let { drawCenteredText(canvas, it, rect) }
            }
        }
    }

    private fun drawStandardPreview(canvas: Canvas) {
        val scale = style.sizeScalePercent / 100f
        val size = dpToPx(92f) * scale
        val rect = RectF(width / 2f - size / 2f, height / 2f - size / 2f, width / 2f + size / 2f, height / 2f + size / 2f)
        canvas.drawOval(rect, popupPaint)
        canvas.drawOval(rect, strokePaint)
        drawCenteredText(canvas, "あ", rect)
    }

    private fun drawTfbiPreview(canvas: Canvas) {
        val scale = style.sizeScalePercent / 100f
        val cell = dpToPx(43f) * scale
        val startX = width / 2f - cell * 1.5f
        val startY = height / 2f - cell * 1.5f
        for (row in 0..2) {
            for (col in 0..2) {
                val rect = RectF(
                    startX + col * cell,
                    startY + row * cell,
                    startX + (col + 1) * cell,
                    startY + (row + 1) * cell
                )
                canvas.drawRoundRect(rect, dpToPx(8f), dpToPx(8f), if (row == 1 && col == 1) highlightPaint else popupPaint)
                canvas.drawRoundRect(rect, dpToPx(8f), dpToPx(8f), strokePaint)
                if (row == 1 && col == 1) drawCenteredText(canvas, "あ", rect)
            }
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, rect: RectF) {
        val y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, rect.centerX(), y, textPaint)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
    }
}
