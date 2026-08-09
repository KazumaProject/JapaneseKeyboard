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
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode

class PopupStylePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(245, 247, 250)
    }
    private val defaultBubbleColor = Color.rgb(55, 71, 79)
    private val defaultTextColor = Color.WHITE
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = defaultBubbleColor
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = defaultTextColor
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
            style.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = style.backgroundColor,
            textColor = style.textColor
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
        bubblePaint.color = style.backgroundColor ?: defaultBubbleColor
        canvas.drawRoundRect(rect, dpToPx(18f), dpToPx(18f), bubblePaint)

        textPaint.color = style.textColor ?: defaultTextColor
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
    private val defaultPopupColor = Color.rgb(69, 90, 100)
    private val defaultHighlightColor = Color.rgb(96, 125, 139)
    private val defaultTextColor = Color.WHITE
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = defaultPopupColor
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = defaultHighlightColor
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = defaultTextColor
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = defaultTextColor
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private var style = PopupViewStyle(100, 28f)
    private var target = Target.DIRECTIONAL
    private var tfbiPresentationMode = TfbiPopupPresentationMode.LEGACY_GRID

    fun applyStyle(target: Target, style: PopupViewStyle) {
        this.target = target
        this.style = PopupViewStyle(
            style.sizeScalePercent.coerceIn(50, 200),
            style.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = style.backgroundColor,
            textColor = style.textColor
        )
        invalidate()
    }

    fun setTfbiPopupPresentationMode(mode: TfbiPopupPresentationMode) {
        tfbiPresentationMode = mode
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
        popupPaint.color = style.backgroundColor ?: defaultPopupColor
        highlightPaint.color = style.backgroundColor ?: defaultHighlightColor
        val resolvedTextColor = style.textColor ?: defaultTextColor
        textPaint.color = resolvedTextColor
        strokePaint.color = resolvedTextColor
        textPaint.textSize = spToPx(style.textSizeSp)
        when (target) {
            Target.DIRECTIONAL -> drawDirectionalPreview(canvas)
            Target.CROSS -> drawCrossPreview(canvas)
            Target.STANDARD -> drawStandardPreview(canvas)
            Target.TFBI -> if (tfbiPresentationMode == TfbiPopupPresentationMode.GUIDE_ABOVE_KEY) {
                drawTfbiGuidePreview(canvas)
            } else {
                drawTfbiPreview(canvas)
            }
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

    private fun drawTfbiGuidePreview(canvas: Canvas) {
        val scale = style.sizeScalePercent / 100f
        val panelWidth = dpToPx(132f) * scale
        val panelHeight = dpToPx(108f) * scale
        val panelLeft = width / 2f - panelWidth / 2f
        val panelTop = dpToPx(12f)
        val panel = RectF(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight)
        val guideBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@FlickPopupStylePreviewView.style.backgroundColor
                ?: Color.rgb(232, 232, 232)
        }
        val guideLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 60, 60, 60)
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(1f)
        }
        val active = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = defaultHighlightColor
        }
        canvas.drawRoundRect(panel, dpToPx(4f), dpToPx(4f), guideBackground)
        canvas.drawRoundRect(panel, dpToPx(4f), dpToPx(4f), guideLine)

        val cellW = panel.width() / 3f
        val cellH = panel.height() / 3f
        for (index in 1..2) {
            canvas.drawLine(panel.left + cellW * index, panel.top, panel.left + cellW * index, panel.bottom, guideLine)
            canvas.drawLine(panel.left, panel.top + cellH * index, panel.right, panel.top + cellH * index, guideLine)
        }

        fun cell(row: Int, column: Int): RectF = RectF(
            panel.left + column * cellW,
            panel.top + row * cellH,
            panel.left + (column + 1) * cellW,
            panel.top + (row + 1) * cellH
        )

        val current = cell(1, 0)
        canvas.drawRoundRect(
            RectF(current.left + dpToPx(3f), current.top + dpToPx(5f), current.right - dpToPx(3f), current.bottom - dpToPx(5f)),
            dpToPx(8f),
            dpToPx(8f),
            active
        )
        drawGuideText(canvas, "き", current, Color.WHITE)
        drawGuideText(canvas, "ゅ", cell(0, 1), Color.DKGRAY)
        drawGuideText(canvas, "ゃ", cell(1, 2), Color.DKGRAY)
        drawGuideText(canvas, "ょ", cell(2, 1), Color.DKGRAY)

        val key = RectF(
            width / 2f - dpToPx(76f),
            panel.bottom + dpToPx(18f),
            width / 2f + dpToPx(76f),
            panel.bottom + dpToPx(74f)
        )
        val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(185, 185, 185) }
        canvas.drawRect(key, keyPaint)
        drawGuideText(canvas, "か", key, Color.GRAY)

        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = defaultHighlightColor
            style = Paint.Style.STROKE
            strokeWidth = dpToPx(8f)
            strokeCap = Paint.Cap.SQUARE
        }
        val arrowHead = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = defaultHighlightColor }
        val centerY = key.centerY()
        canvas.drawLine(key.centerX() + dpToPx(34f), centerY, key.left + dpToPx(24f), centerY, arrowPaint)
        val head = Path().apply {
            moveTo(key.left + dpToPx(12f), centerY)
            lineTo(key.left + dpToPx(30f), centerY - dpToPx(11f))
            lineTo(key.left + dpToPx(30f), centerY + dpToPx(11f))
            close()
        }
        canvas.drawPath(head, arrowHead)
    }

    private fun drawGuideText(canvas: Canvas, text: String, rect: RectF, color: Int) {
        textPaint.color = color
        textPaint.textSize = spToPx(style.textSizeSp.coerceAtMost(24f))
        val y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, rect.centerX(), y, textPaint)
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
