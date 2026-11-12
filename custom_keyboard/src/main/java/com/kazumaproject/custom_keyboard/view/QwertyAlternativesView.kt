package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.custom_keyboard.data.QwertyKeyLayer

/** Drawing and hit testing use the same row-major slots, including empty positions. */
class QwertyAlternativesView(context: Context) : View(context) {
    private var layer = QwertyKeyLayer()
    private var style = PopupViewStyle(100, 28f)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var skinBackground: android.graphics.drawable.Drawable? = null
    var selectedIndex: Int = -1
        private set
    val selectedOutput: String? get() = layer.slots.getOrNull(selectedIndex)?.output?.takeIf { it.isNotEmpty() }

    fun setContent(layer: QwertyKeyLayer, style: PopupViewStyle, font: KeyboardFontSnapshot) {
        this.layer = layer
        this.style = style
        skinBackground = KeyboardSkinRegistry.find(style.skinId)?.variationDrawable(resources)
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            style.textSizeSp, resources.displayMetrics)
        KeyboardFontApplicator.apply(textPaint, font)
        selectedIndex = layer.slots.indexOfFirst { it.output.isNotEmpty() }
        invalidate()
    }

    fun updateSelection(x: Float, y: Float) {
        if (width <= 0 || height <= 0 || layer.rowCount == 0 ||
            x < 0 || y < 0 || x >= width || y >= height) return
        val index = (y / (height.toFloat() / layer.rowCount)).toInt() * layer.columns +
            (x / (width.toFloat() / layer.columns)).toInt()
        val selected = index.takeIf { layer.slots.getOrNull(it)?.output?.isNotEmpty() == true } ?: -1
        if (selectedIndex != selected) {
            selectedIndex = selected
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (layer.rowCount == 0) return
        val skin = KeyboardSkinRegistry.find(style.skinId)
        val background = style.backgroundColor ?: skin?.palette?.key ?:
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.candidate_background)
        paint.color = background
        val radius = 8f * resources.displayMetrics.density
        if (skin != null) {
            skinBackground?.apply { setBounds(0, 0, width, height) }?.draw(canvas)
        } else canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
        val cellWidth = width.toFloat() / layer.columns
        val cellHeight = height.toFloat() / layer.rowCount
        layer.slots.forEachIndexed { index, slot ->
            if (slot.output.isEmpty()) return@forEachIndexed
            val left = index % layer.columns * cellWidth
            val top = index / layer.columns * cellHeight
            val selected = index == selectedIndex
            if (selected) {
                paint.color = skin?.palette?.selection ?: ContextCompat.getColor(context, com.kazumaproject.core.R.color.enter_key_bg)
                canvas.drawRoundRect(left + 2f, top + 2f, left + cellWidth - 2f,
                    top + cellHeight - 2f, radius, radius, paint)
            }
            textPaint.color = if (selected && skin != null) skin.palette.selectionText else
                style.textColor ?: skin?.palette?.text ?: ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
            val label = TextUtils.ellipsize(slot.displayLabel.replace('\n', ' '), textPaint,
                (cellWidth - 8f).coerceAtLeast(1f), TextUtils.TruncateAt.END).toString()
            canvas.drawText(label, left + cellWidth / 2,
                top + cellHeight / 2 - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
        }
    }
}
