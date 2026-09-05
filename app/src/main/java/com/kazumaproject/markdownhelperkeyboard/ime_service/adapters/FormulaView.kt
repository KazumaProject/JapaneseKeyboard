package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaCandidatePresentation
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaDrawOperation
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaLayout
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaLayoutConfig
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaLayoutEngine
import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaTextMeasurer
import kotlin.math.ceil

/**
 * Small Canvas renderer used only inside candidate cells.  The input field receives the
 * presentation's commitText; this view only draws the AST and never participates in committing.
 */
class FormulaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = PaintTypeface.default
    }

    private var presentation: FormulaCandidatePresentation? = null
    private var formulaTextSizeSp: Float = 16f
    private var formulaTextColor: Int = Color.BLACK
    private var measuredFormula: FormulaLayout? = null
    private var drawLinearFallback: Boolean = false
    private var fallbackText: String? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setWillNotDraw(false)
    }

    fun setPresentation(value: FormulaCandidatePresentation?) {
        if (presentation == value) return
        presentation = value
        contentDescription = value?.commitText ?: fallbackText
        requestLayout()
        invalidate()
    }

    fun setFallbackText(value: String?) {
        if (fallbackText == value) return
        fallbackText = value
        if (presentation == null) contentDescription = value
        requestLayout()
        invalidate()
    }

    fun setFormulaTextSizeSp(size: Float) {
        val sanitized = size.coerceIn(8f, 48f)
        if (formulaTextSizeSp == sanitized) return
        formulaTextSizeSp = sanitized
        requestLayout()
        invalidate()
    }

    fun setFormulaTextColor(color: Int) {
        if (formulaTextColor == color) return
        formulaTextColor = color
        paint.color = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val currentPresentation = presentation
        if (currentPresentation == null) {
            measuredFormula = null
            drawLinearFallback = false
            val linearText = fallbackText
            if (linearText == null) {
                setMeasuredDimension(0, 0)
                return
            }
            val fontSizePx = formulaTextSizeSp * resources.displayMetrics.scaledDensity
            paint.textSize = fontSizePx
            setMeasuredDimension(
                resolveSize(ceil(paint.measureText(linearText)).toInt(), widthMeasureSpec),
                resolveSize(
                    ceil(-paint.fontMetrics.ascent + paint.fontMetrics.descent).toInt(),
                    heightMeasureSpec,
                ),
            )
            return
        }

        val fontSizePx = formulaTextSizeSp * resources.displayMetrics.scaledDensity
        val config = layoutConfig(fontSizePx)
        val layout = FormulaLayoutEngine.layout(
            node = currentPresentation.ast,
            config = config,
            measureText = FormulaTextMeasurer { value, size ->
                paint.textSize = size
                paint.measureText(value)
            },
        )
        paint.textSize = fontSizePx
        val widthLimit = measureSpecContentSize(widthMeasureSpec)
        val heightLimit = measureSpecContentSize(heightMeasureSpec)
        val desiredWidth = ceil(layout.width).toInt()
        val desiredHeight = ceil(layout.height).toInt()
        val maxUnboundedHeight = ceil(fontSizePx * 4.8f).toInt()
        drawLinearFallback =
            (widthLimit != null && desiredWidth > widthLimit) ||
                (heightLimit != null && desiredHeight > heightLimit) ||
                desiredHeight > maxUnboundedHeight

        measuredFormula = if (drawLinearFallback) null else layout
        val fallbackWidth = ceil(paint.measureText(currentPresentation.fallbackText)).toInt()
        val fallbackHeight = ceil(-paint.fontMetrics.ascent + paint.fontMetrics.descent).toInt()
        val contentWidth = if (drawLinearFallback) fallbackWidth else desiredWidth
        val contentHeight = if (drawLinearFallback) fallbackHeight else desiredHeight
        setMeasuredDimension(
            resolveSize(contentWidth, widthMeasureSpec),
            resolveSize(contentHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentPresentation = presentation
        if (currentPresentation == null) {
            val linearText = fallbackText ?: return
            paint.color = formulaTextColor
            paint.textSize = formulaTextSizeSp * resources.displayMetrics.scaledDensity
            paint.typeface = PaintTypeface.default
            paint.isFakeBoldText = false
            canvas.drawText(linearText, 0f, -paint.fontMetrics.ascent, paint)
            return
        }
        paint.color = formulaTextColor
        val baseline = if (drawLinearFallback || measuredFormula == null) {
            paint.textSize = formulaTextSizeSp * resources.displayMetrics.scaledDensity
            -paint.fontMetrics.ascent
        } else {
            val layout = measuredFormula ?: return
            layout.ascent
        }

        if (drawLinearFallback || measuredFormula == null) {
            paint.typeface = PaintTypeface.default
            paint.isFakeBoldText = false
            canvas.drawText(currentPresentation.fallbackText, 0f, baseline, paint)
            return
        }

        val layout = measuredFormula ?: return
        layout.operations.forEach { operation ->
            when (operation) {
                is FormulaDrawOperation.Text -> {
                    paint.textSize = operation.fontSize
                    paint.isFakeBoldText = operation.bold
                    canvas.drawText(
                        operation.value,
                        operation.x,
                        baseline + operation.baseline,
                        paint,
                    )
                }

                is FormulaDrawOperation.Line -> {
                    paint.isFakeBoldText = false
                    paint.strokeWidth = operation.strokeWidth
                    canvas.drawLine(
                        operation.startX,
                        baseline + operation.startY,
                        operation.endX,
                        baseline + operation.endY,
                        paint,
                    )
                }
            }
        }
        paint.isFakeBoldText = false
    }

    private fun layoutConfig(fontSizePx: Float): FormulaLayoutConfig {
        paint.textSize = fontSizePx
        val fontMetrics = paint.fontMetrics
        return FormulaLayoutConfig(
            fontSize = fontSizePx,
            ascentRatio = (-fontMetrics.ascent / fontSizePx).coerceAtLeast(0.6f),
            descentRatio = (fontMetrics.descent / fontSizePx).coerceAtLeast(0.16f),
        )
    }

    private fun measureSpecContentSize(spec: Int): Int? = when (MeasureSpec.getMode(spec)) {
        MeasureSpec.AT_MOST,
        MeasureSpec.EXACTLY -> MeasureSpec.getSize(spec)
        else -> null
    }

    /** Android's default typeface is intentionally kept in one place for consistent fallback. */
    private object PaintTypeface {
        val default = android.graphics.Typeface.DEFAULT
    }
}
