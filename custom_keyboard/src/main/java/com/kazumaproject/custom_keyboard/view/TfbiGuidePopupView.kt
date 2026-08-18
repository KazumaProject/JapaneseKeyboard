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
import com.kazumaproject.core.data.keyboard.KeyboardSkinId
import com.kazumaproject.core.data.keyboard.KeyboardSkinPopupKind
import com.kazumaproject.core.data.keyboard.KeyboardSkinPopupRenderer
import com.kazumaproject.core.domain.extensions.getThemeColor
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.custom_keyboard.data.TfbiGuideFingerPosition
import com.kazumaproject.custom_keyboard.data.TfbiGuideGrid
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
    private val fingerMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fingerMarkerOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
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
    private var keyboardSkinId: KeyboardSkinId = KeyboardSkinId.DEFAULT

    fun setState(state: TfbiGuidePopupState) {
        this.state = state
        invalidate()
    }

    fun setInputTextTransform(transform: (String) -> String) {
        inputTextTransform = transform
        invalidate()
    }

    fun applyPopupViewStyle(style: PopupViewStyle) {
        val popup = KeyboardSkinPopupRenderer.specFor(keyboardSkinId)
        popupStyle = PopupViewStyle(
            sizeScalePercent = if (popup == null) style.sizeScalePercent.coerceIn(50, 200) else 100,
            textSizeSp = popup?.flickTextSizeSp ?: style.textSizeSp.coerceIn(8f, 48f),
            backgroundColor = if (popup == null) style.backgroundColor else null,
            textColor = if (popup == null) style.textColor else popup.textColor,
        )
        popupBackgroundColor = if (popup == null) {
            style.backgroundColor ?: configuredBackgroundColor
        } else {
            popup.surfaceColor
        }
        activeColor = if (popup == null) {
            configuredHighlightedColor ?: DEFAULT_ACTIVE_COLOR
        } else {
            popup.selectedSurfaceColor
        }
        popupTextColor = if (popup == null) {
            style.textColor ?: configuredTextColor ?: defaultTextColor
        } else {
            popup.textColor
        }
        activeTextColor = readableForeground(activeColor)
        invalidate()
    }

    fun setKeyboardSkin(skinId: KeyboardSkinId) {
        keyboardSkinId = skinId
        val popup = KeyboardSkinPopupRenderer.specFor(skinId)
        if (popup != null) {
            popupBackgroundColor = popup.surfaceColor
            activeColor = popup.selectedSurfaceColor
            popupTextColor = popup.textColor
            activeTextColor = popup.selectedTextColor
            textPaint.typeface = android.graphics.Typeface.create(
                "sans-serif",
                android.graphics.Typeface.NORMAL,
            )
            activeTextPaint.typeface = textPaint.typeface
            KeyboardSkinPopupRenderer.applyPaintStyle(
                textPaint,
                context,
                skinId,
                KeyboardSkinPopupKind.FLICK_GUIDE,
            )
            KeyboardSkinPopupRenderer.applyPaintStyle(
                activeTextPaint,
                context,
                skinId,
                KeyboardSkinPopupKind.FLICK_GUIDE,
                selected = true,
            )
        } else {
            popupBackgroundColor = popupStyle.backgroundColor ?: configuredBackgroundColor
            activeColor = configuredHighlightedColor ?: DEFAULT_ACTIVE_COLOR
            popupTextColor = popupStyle.textColor ?: configuredTextColor ?: defaultTextColor
            activeTextColor = readableForeground(activeColor)
            textPaint.typeface = null
            activeTextPaint.typeface = null
        }
        invalidate()
    }

    fun setColors(
        backgroundColor: Int,
        highlightedBackgroundColor: Int,
        textColor: Int
    ) {
        if (KeyboardSkinPopupRenderer.isFixedCupertino(keyboardSkinId)) {
            setKeyboardSkin(keyboardSkinId)
            return
        }
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
        val fixedCupertino = KeyboardSkinPopupRenderer.isFixedCupertino(keyboardSkinId)
        if (fixedCupertino) {
            KeyboardSkinPopupRenderer.drawRoundRect(
                canvas,
                context,
                keyboardSkinId,
                KeyboardSkinPopupKind.FLICK_GUIDE,
                panel,
            )
            borderPaint.color = Color.TRANSPARENT
            gridPaint.color = Color.TRANSPARENT
        } else {
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
        }

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

        state.fingerPosition?.let { drawFingerMarker(canvas, panel, it) }
    }

    private fun drawFingerMarker(
        canvas: Canvas,
        panel: RectF,
        position: TfbiGuideFingerPosition
    ) {
        val x = panel.left + panel.width() * position.x.coerceIn(0f, 1f)
        val y = panel.top + panel.height() * position.y.coerceIn(0f, 1f)
        val radius = dp(5f).coerceAtMost(minOf(panel.width(), panel.height()) * 0.08f)

        // A contrasting ring keeps the cursor visible over both labels and the active pill.
        fingerMarkerOutlinePaint.color = readableForeground(popupBackgroundColor ?: defaultPanelColor)
        fingerMarkerOutlinePaint.strokeWidth = dp(2f)
        canvas.drawCircle(x, y, radius + dp(2f), fingerMarkerOutlinePaint)

        fingerMarkerPaint.color = activeColor
        fingerMarkerPaint.strokeWidth = dp(2f)
        canvas.drawCircle(x, y, radius, fingerMarkerPaint)
    }

    private fun rectFor(
        direction: TfbiFlickDirection,
        panel: RectF,
        cellWidth: Float,
        cellHeight: Float
    ): RectF {
        val (column, row) = TfbiGuideGrid.cellOf(direction)
        val left = panel.left + column * cellWidth
        val top = panel.top + row * cellHeight
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
        if (!KeyboardSkinPopupRenderer.drawRoundRect(
                canvas,
                context,
                keyboardSkinId,
                KeyboardSkinPopupKind.FLICK_GUIDE,
                pill,
                selected = true,
            )
        ) {
            activePaint.color = activeColor
            canvas.drawRoundRect(
                pill,
                dp(if (compact) 7f else 9f),
                dp(if (compact) 7f else 9f),
                activePaint,
            )
        }
        if (KeyboardSkinPopupRenderer.isFixedCupertino(keyboardSkinId)) {
            KeyboardSkinPopupRenderer.applyPaintStyle(
                activeTextPaint,
                context,
                keyboardSkinId,
                KeyboardSkinPopupKind.FLICK_GUIDE,
                selected = true,
            )
        }
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
        val effectiveScale = if (KeyboardSkinPopupRenderer.isFixedCupertino(keyboardSkinId)) {
            1f
        } else {
            scale
        }
        val configured = sp(popupStyle.textSizeSp * effectiveScale)
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
