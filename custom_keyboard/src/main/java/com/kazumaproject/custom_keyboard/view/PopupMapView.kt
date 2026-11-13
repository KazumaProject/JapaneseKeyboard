package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import android.view.View
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import com.kazumaproject.custom_keyboard.data.PopupMap
import com.kazumaproject.custom_keyboard.data.PopupBounds
import com.kazumaproject.custom_keyboard.data.labelBounds

/** Draws the same key-relative contours used by PopupMapSession. */
open class PopupMapView(context: Context) : View(context) {
    var map: PopupMap? = null
    var keyWidth = 1f
    var keyHeight = 1f
    var originX = 0f
    var originY = 0f
    var selectedId: String? = null
    var tapActive = true
    protected open val drawsTapRegion = true
    var keyRotation = 0f
    var textTransform: (String) -> String = { it }
    protected var popupStyle = PopupViewStyle(100, 20f)
        private set
    private var highlightColor: Int? = null
    private var borderColor: Int? = null
    protected val popupBackgroundColor get() = KeyboardSkinRegistry.find(popupStyle.skinId)?.palette?.key
        ?: popupStyle.backgroundColor ?: MaterialColors.getColor(this, R.attr.colorSurfaceContainerHigh)
    val popupHighlightColor get() = KeyboardSkinRegistry.find(popupStyle.skinId)?.palette?.selection
        ?: highlightColor ?: MaterialColors.getColor(this, R.attr.colorPrimaryContainer)
    protected val popupTextColor get() = KeyboardSkinRegistry.find(popupStyle.skinId)?.palette?.text
        ?: popupStyle.textColor ?: MaterialColors.getColor(this, R.attr.colorOnSurface)
    protected val popupSelectedTextColor get() = KeyboardSkinRegistry.find(popupStyle.skinId)?.palette?.selectionText
        ?: popupTextColor
    protected val popupBorderColor get() = borderColor ?: ColorUtils.setAlphaComponent(popupTextColor, 150)

    open fun applyPopupViewStyle(style: PopupViewStyle, highlightColor: Int? = null, borderColor: Int? = null) {
        popupStyle = style.copy(textSizeSp = style.textSizeSp.coerceIn(8f, 48f))
        this.highlightColor = highlightColor
        this.borderColor = borderColor
        invalidate()
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.rotate(keyRotation, originX, originY)
        val surface = popupBackgroundColor
        val selected = popupHighlightColor
        val foreground = popupTextColor
        val outline = popupBorderColor
        fun drawArea(path: Path, rect: RectF, label: String, active: Boolean, contour: Boolean = false) {
            paint.style = Paint.Style.FILL
            paint.color = if (active) selected else surface
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density
            paint.color = outline
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (active) popupSelectedTextColor else foreground
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = minOf(popupStyle.textSizeSp * resources.displayMetrics.scaledDensity, rect.height() * if(contour) .78f else .42f)
            val labelWidth = if (contour) rect.width() * .9f else rect.width() - 8 * resources.displayMetrics.density
            val text = android.text.TextUtils.ellipsize(textTransform(label), android.text.TextPaint(paint), labelWidth.coerceAtLeast(1f), android.text.TextUtils.TruncateAt.END).toString()
            val clipped = canvas.save()
            canvas.clipPath(path)
            canvas.drawText(text, rect.centerX(), rect.centerY() - (paint.ascent() + paint.descent()) / 2, paint)
            canvas.restoreToCount(clipped)
        }
        fun rect(b: PopupBounds) = RectF(originX + b.left * keyWidth, originY + b.top * keyHeight, originX + b.right * keyWidth, originY + b.bottom * keyHeight)
        if (drawsTapRegion && map != null) {
            val tapRect = rect(PopupBounds.KEY)
            drawArea(tapRect.popupRoundedPath(), tapRect, map!!.tapText, tapActive)
        }
        map?.regions?.forEach { region ->
            drawArea(region.outlinePath(originX, originY, keyWidth, keyHeight),
                rect(region.labelBounds(keyWidth, keyHeight)), region.displayLabel(), region.id == selectedId, region.outline != null)
        }
        canvas.restoreToCount(saved)
    }
}
