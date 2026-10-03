package com.kazumaproject.core.ui.input_mode_witch

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.PathParser
import com.kazumaproject.core.R
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.ui.font.KeyboardFontGlyphDrawable
import org.xmlpull.v1.XmlPullParser

/** Retains the original artwork's intrinsic dimensions, including its stroke and label spacing. */
internal class InputModeSwitchIconDrawable(
    context: Context,
    resourceId: Int,
    private val typeface: Typeface?,
    private val selectedColor: Int?,
    private val idleColor: Int?,
    mode: InputMode,
) : Drawable() {
    private data class Layer(val path: Path, val box: RectF, val color: Int,
        val strokeColor: Int, val strokeWidth: Float, val fillAlpha: Float, val strokeAlpha: Float,
        val strokeCap: Paint.Cap, val strokeJoin: Paint.Join)
    private val original = requireNotNull(AppCompatResources.getDrawable(context, resourceId))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val layers: List<Layer>
    private val viewportWidth: Float
    private val viewportHeight: Float
    private var opacity = 255
    private var tint: ColorStateList? = null
    private var filter: ColorFilter? = null
    private val selectedIndex = when (mode) {
        InputMode.ModeJapanese -> 0
        InputMode.ModeEnglish -> 1
        InputMode.ModeNumber -> 2
    }
    private val labels = when (resourceId) {
        R.drawable.input_mode_japanese_select_gojuon,
        R.drawable.input_mode_japanese_select_custom -> listOf("あ", "い", "う")
        R.drawable.input_mode_english_select_gojuon,
        R.drawable.input_mode_english_custom -> listOf("A", "B", "C")
        R.drawable.input_mode_number_select_gojuon -> listOf("1", "2", "3")
        R.drawable.language_japanese_kana_left_bold_24px,
        R.drawable.language_japanese_kana_right_bold_24px -> listOf("あ", "a")
        else -> listOf("あ", "a", "1")
    }
    private val wordIcon = resourceId in setOf(R.drawable.input_mode_japanese_select_gojuon,
        R.drawable.input_mode_english_select_gojuon, R.drawable.input_mode_number_select_gojuon,
        R.drawable.input_mode_japanese_select_custom, R.drawable.input_mode_english_custom)

    init {
        val result = mutableListOf<Layer>()
        val namespace = "http://schemas.android.com/apk/res/android"
        context.resources.getXml(resourceId).use { xml ->
            while (xml.eventType != XmlPullParser.START_TAG) xml.next()
            viewportWidth = xml.getAttributeFloatValue(namespace, "viewportWidth", 1f)
            viewportHeight = xml.getAttributeFloatValue(namespace, "viewportHeight", 1f)
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG || xml.name != "path") continue
                val path = requireNotNull(PathParser.createPathFromPathData(xml.getAttributeValue(namespace, "pathData")))
                val attributes = context.resources.obtainAttributes(xml, intArrayOf(
                    android.R.attr.fillColor, android.R.attr.strokeColor, android.R.attr.strokeWidth,
                    android.R.attr.fillAlpha, android.R.attr.strokeAlpha, android.R.attr.strokeLineCap,
                    android.R.attr.strokeLineJoin, android.R.attr.fillType))
                try {
                    if (attributes.getInt(7, 0) == 1) path.fillType = Path.FillType.EVEN_ODD
                    val box = RectF().also { path.computeBounds(it, true) }
                    result += Layer(path, box, attributes.getColor(0, Color.TRANSPARENT),
                        attributes.getColor(1, Color.TRANSPARENT), attributes.getFloat(2, 0f),
                        attributes.getFloat(3, 1f), attributes.getFloat(4, 1f),
                        when (attributes.getInt(5, 0)) { 1 -> Paint.Cap.ROUND; 2 -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT },
                        when (attributes.getInt(6, 0)) { 1 -> Paint.Join.ROUND; 2 -> Paint.Join.BEVEL; else -> Paint.Join.MITER })
                } finally { attributes.recycle() }
            }
        }
        layers = result.sortedBy { it.box.centerX() }
    }

    override fun getIntrinsicWidth() = original.intrinsicWidth
    override fun getIntrinsicHeight() = original.intrinsicHeight

    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / viewportWidth, bounds.height() / viewportHeight)
        layers.forEachIndexed { index, layer ->
            val selected = wordIcon || index == selectedIndex
            val palette = if (selected) selectedColor else idleColor
            val color = palette ?: tint?.getColorForState(state, layer.color) ?: layer.color
            if (typeface == null) {
                paint.style = Paint.Style.FILL
                paint.color = color
                paint.alpha = (Color.alpha(color) * opacity / 255f * layer.fillAlpha).toInt()
                paint.colorFilter = if (selectedColor == null) filter else null
                canvas.drawPath(layer.path, paint)
                if (layer.strokeWidth > 0 && Color.alpha(layer.strokeColor) > 0) {
                    val stroke = palette ?: tint?.getColorForState(state, layer.strokeColor) ?: layer.strokeColor
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = layer.strokeWidth
                    paint.strokeCap = layer.strokeCap
                    paint.strokeJoin = layer.strokeJoin
                    paint.color = stroke
                    paint.alpha = (Color.alpha(stroke) * opacity / 255f * layer.strokeAlpha).toInt()
                    canvas.drawPath(layer.path, paint)
                }
            } else {
                paint.style = Paint.Style.FILL
                val box = RectF(layer.box).apply { inset(-layer.strokeWidth / 2, -layer.strokeWidth / 2) }
                KeyboardFontGlyphDrawable.drawGlyph(canvas, paint, labels[index], box,
                    Color.argb(Color.alpha(color) * opacity / 255, Color.red(color), Color.green(color), Color.blue(color)),
                    typeface, if (selected && layers.size == 2) Typeface.BOLD else Typeface.NORMAL,
                    if (selectedColor == null) filter else null)
            }
        }
        canvas.restoreToCount(saved)
    }

    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun getAlpha() = opacity
    override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
    override fun setTintList(tint: ColorStateList?) { this.tint = tint; invalidateSelf() }
    override fun isStateful() = tint?.isStateful == true
    override fun onStateChange(state: IntArray): Boolean { invalidateSelf(); return isStateful }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
