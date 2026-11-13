package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.rotate(keyRotation, originX, originY)
        val surface = MaterialColors.getColor(this, R.attr.colorSurfaceContainerHigh)
        val selected = MaterialColors.getColor(this, R.attr.colorPrimaryContainer)
        val foreground = MaterialColors.getColor(this, R.attr.colorOnSurface)
        val outline = MaterialColors.getColor(this, R.attr.colorOutline)
        fun drawArea(path: Path, rect: RectF, label: String, active: Boolean, contour: Boolean = false) {
            paint.style = Paint.Style.FILL
            paint.color = if (active) selected else surface
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density
            paint.color = outline
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.color = foreground
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = minOf(20 * resources.displayMetrics.scaledDensity, rect.height() * if(contour) .78f else .42f)
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
            drawArea(Path().apply { addRect(tapRect, Path.Direction.CW) }, tapRect, map!!.tapText, tapActive)
        }
        map?.regions?.forEach { region ->
            drawArea(region.outlinePath(originX, originY, keyWidth, keyHeight),
                rect(region.labelBounds(keyWidth, keyHeight)), region.displayLabel(), region.id == selectedId, region.outline != null)
        }
        canvas.restoreToCount(saved)
    }
}
