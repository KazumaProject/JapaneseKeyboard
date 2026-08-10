package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.domain.extensions.getThemeColor
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.custom_keyboard.data.TfbiGuidePopupState

/**
 * Compact guide used by the optional TFBi/Arte-style popup presentation.
 *
 * Unlike [TfbiFlickPopupView], this view deliberately does not paint a filled cell for every
 * direction. It paints a light guide card, a blue current-output pill, and only the available
 * next labels, matching the guide figures used by TFBi documentation.
 */
class TfbiGuidePopupView(context: Context) : View(context) {

    private val defaultPanelColor = if (context.isDarkThemeOn()) {
        context.getThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
    } else {
        context.getThemeColor(com.google.android.material.R.attr.colorSurface)
    }
    private val defaultTextColor = ContextCompat.getColor(
        context,
        com.kazumaproject.core.R.color.keyboard_icon_color
    )

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val activeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var state = TfbiGuidePopupState("。", TfbiFlickDirection.TAP)
    private var popupStyle = PopupViewStyle(100, 20f)
    private var configuredBackgroundColor: Int? = null
    private var configuredHighlightedColor: Int? = null
    private var configuredTextColor: Int? = null
    private var popupBackgroundColor: Int? = null
    private var activeColor: Int = DEFAULT_ACTIVE_COLOR
    private var popupTextColor: Int = defaultTextColor
    private var activeTextColor: Int = Color.WHITE
    private var inputTextTransform: (String) -> String = { it }

    private val directionLayout = listOf(
        listOf(TfbiFlickDirection.UP_LEFT, TfbiFlickDirection.UP, TfbiFlickDirection.UP_RIGHT),
        listOf(TfbiFlickDirection.LEFT, TfbiFlickDirection.TAP, TfbiFlickDirection.RIGHT),
        listOf(TfbiFlickDirection.DOWN_LEFT, TfbiFlickDirection.DOWN, TfbiFlickDirection.DOWN_RIGHT)
    )

    fun setState(state: TfbiGuidePopupState) {
        this.state = state
        invalidate()
    }

    fun setInputTextTransform(transform: (String) -> String) {
        inputTextTransform = transform
        invalidate()
    }

    fun applyPopupViewStyle(style: PopupViewStyle) {
        popupStyle = PopupViewStyle(
            sizeScalePercent = style.sizeScalePercent.coerceIn(50, 200),
            textSizeSp = style.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = style.backgroundColor,
            textColor = style.textColor
        )
        popupBackgroundColor = style.backgroundColor ?: configuredBackgroundColor
        activeColor = configuredHighlightedColor ?: DEFAULT_ACTIVE_COLOR
        popupTextColor = style.textColor ?: configuredTextColor ?: defaultTextColor
        activeTextColor = readableForeground(activeColor)
        invalidate()
    }

    fun setColors(
        backgroundColor: Int,
        highlightedBackgroundColor: Int,
        textColor: Int
    ) {
        configuredBackgroundColor = backgroundColor
        configuredHighlightedColor = highlightedBackgroundColor
        configuredTextColor = textColor
        popupBackgroundColor = popupStyle.backgroundColor ?: backgroundColor
        activeColor = highlightedBackgroundColor
        popupTextColor = popupStyle.textColor ?: textColor
        activeTextColor = readableForeground(activeColor)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val inset = dp(1f)
        val panel = RectF(inset, inset, width - inset, height - inset)
        val panelColor = popupBackgroundColor ?: defaultPanelColor
        panelPaint.shader = LinearGradient(
            0f,
            panel.top,
            0f,
            panel.bottom,
            ColorUtils.blendARGB(panelColor, Color.WHITE, 0.16f),
            ColorUtils.blendARGB(panelColor, Color.BLACK, 0.06f),
            Shader.TileMode.CLAMP
        )
        borderPaint.color = ColorUtils.setAlphaComponent(popupTextColor, 105)
        gridPaint.color = ColorUtils.setAlphaComponent(popupTextColor, 70)
        canvas.drawRoundRect(panel, dp(4f), dp(4f), panelPaint)
        panelPaint.shader = null
        canvas.drawRoundRect(panel, dp(4f), dp(4f), borderPaint)

        val cellWidth = panel.width() / 3f
        val cellHeight = panel.height() / 3f
        for (index in 1..2) {
            val x = panel.left + cellWidth * index
            val y = panel.top + cellHeight * index
            canvas.drawLine(x, panel.top, x, panel.bottom, gridPaint)
            canvas.drawLine(panel.left, y, panel.right, y, gridPaint)
        }

        val currentRect = rectFor(state.currentSlot, panel, cellWidth, cellHeight)
        drawActiveLabel(canvas, inputTextTransform(state.currentText), currentRect)

        state.optionLabels.forEach { (direction, label) ->
            if (label.isEmpty() || direction == state.currentSlot) return@forEach
            val rect = rectFor(direction, panel, cellWidth, cellHeight)
            if (direction == state.selectedOption) {
                drawActiveLabel(canvas, inputTextTransform(label), rect, compact = true)
            } else {
                drawPlainLabel(canvas, inputTextTransform(label), rect)
            }
        }
    }

    private fun rectFor(
        direction: TfbiFlickDirection,
        panel: RectF,
        cellWidth: Float,
        cellHeight: Float
    ): RectF {
        val position = directionLayout
            .asSequence()
            .withIndex()
            .flatMap { (row, directions) ->
                directions.withIndex().map { (column, value) ->
                    Triple(row, column, value)
                }
            }
            .firstOrNull { it.third == direction }
            ?: Triple(1, 1, TfbiFlickDirection.TAP)
        val left = panel.left + position.second * cellWidth
        val top = panel.top + position.first * cellHeight
        return RectF(left, top, left + cellWidth, top + cellHeight)
    }

    private fun drawActiveLabel(
        canvas: Canvas,
        text: String,
        rect: RectF,
        compact: Boolean = false
    ) {
        if (text.isEmpty()) return
        val horizontalPadding = if (compact) dp(4f) else dp(8f)
        val verticalPadding = if (compact) dp(3f) else dp(5f)
        val maxWidth = rect.width() - dp(4f)
        val textSize = fitTextSize(
            text = text,
            maxWidth = maxWidth - horizontalPadding * 2,
            scale = if (compact) OPTION_TEXT_SCALE else MAIN_TEXT_SCALE,
            minimumSp = if (compact) OPTION_MIN_TEXT_SIZE_SP else MAIN_MIN_TEXT_SIZE_SP
        )
        activeTextPaint.color = activeTextColor
        activeTextPaint.textSize = textSize
        val textWidth = minOf(
            maxWidth,
            activeTextPaint.measureText(text) + horizontalPadding * 2
        )
        val pill = RectF(
            rect.centerX() - textWidth / 2f,
            rect.centerY() - (textSize + verticalPadding * 2) / 2f,
            rect.centerX() + textWidth / 2f,
            rect.centerY() + (textSize + verticalPadding * 2) / 2f
        )
        activePaint.color = activeColor
        canvas.drawRoundRect(pill, dp(if (compact) 7f else 9f), dp(if (compact) 7f else 9f), activePaint)
        val baseline = pill.centerY() - (activeTextPaint.ascent() + activeTextPaint.descent()) / 2f
        canvas.drawText(text, pill.centerX(), baseline, activeTextPaint)
    }

    private fun drawPlainLabel(canvas: Canvas, text: String, rect: RectF) {
        if (text.isEmpty()) return
        textPaint.color = popupTextColor
        textPaint.textSize = fitTextSize(
            text = text,
            maxWidth = rect.width() - dp(6f),
            scale = OPTION_TEXT_SCALE,
            minimumSp = OPTION_MIN_TEXT_SIZE_SP
        )
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(text, rect.centerX(), baseline, textPaint)
    }

    private fun fitTextSize(
        text: String,
        maxWidth: Float,
        scale: Float,
        minimumSp: Float
    ): Float {
        val configured = sp(popupStyle.textSizeSp * scale)
        textPaint.textSize = configured
        val measured = textPaint.measureText(text)
        val safeWidth = maxWidth.coerceAtLeast(1f)
        if (measured <= safeWidth || measured <= 0f) return configured
        return (configured * safeWidth / measured).coerceAtLeast(sp(minimumSp))
    }

    private fun readableForeground(background: Int): Int {
        return if (ColorUtils.calculateLuminance(background) > 0.55) Color.BLACK else Color.WHITE
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    )

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )

    private companion object {
        const val DEFAULT_ACTIVE_COLOR: Int = 0xff1976d2.toInt()
        const val MAIN_TEXT_SCALE: Float = 1.20f
        const val OPTION_TEXT_SCALE: Float = 0.68f
        const val MAIN_MIN_TEXT_SIZE_SP: Float = 12f
        const val OPTION_MIN_TEXT_SIZE_SP: Float = 8f
    }
}
