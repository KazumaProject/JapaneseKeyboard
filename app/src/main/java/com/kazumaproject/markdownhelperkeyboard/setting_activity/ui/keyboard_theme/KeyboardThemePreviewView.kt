package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference

/** A non-interactive miniature. Cupertino uses the same drawables as the keyboard. */
class KeyboardThemePreviewView(context: Context, id: KeyboardSkinId) : View(context) {
    private val skin = KeyboardSkinRegistry.find(id)
    private val custom = AppPreference.theme_mode == "custom"
    private val dynamic = DynamicColors.isDynamicColorAvailable()
    private fun color(resource: Int) = ContextCompat.getColor(context, resource)
    private val backgroundColor = skin?.palette?.background ?: if (custom) AppPreference.custom_theme_bg_color
        else if (dynamic) MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer)
        else color(com.kazumaproject.core.R.color.keyboard_bg)
    private val keyColor = skin?.palette?.key ?: if (custom) AppPreference.custom_theme_key_color
        else if (dynamic) MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHighest)
        else color(com.kazumaproject.core.R.color.qwety_key_bg_color)
    private val textColor = skin?.palette?.text ?: if (custom) AppPreference.custom_theme_key_text_color
        else if (dynamic) MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        else color(com.kazumaproject.core.R.color.keyboard_icon_color)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyboardDrawable = skin?.keyboardDrawable(resources)
    private val keyDrawable = skin?.keyDrawable(resources)
    private val labels = listOf("↶", "あ", "か", "さ", "⌫", "◀", "た", "な", "は", "▶", "記号", "ま", "や", "ら", "変換", "あa", "小", "わ", "。", "↵")

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize((width * 184f / 320f).toInt(), heightMeasureSpec))
    }

    private fun drawSkinDrawable(canvas: Canvas, drawable: android.graphics.drawable.Drawable, rect: RectF) {
        // Draw at the keyboard's density before reducing to a 320-unit thumbnail.
        val scale = 0.8f / resources.displayMetrics.density
        canvas.save()
        canvas.scale(scale, scale)
        drawable.setBounds((rect.left / scale).toInt(), (rect.top / scale).toInt(),
            (rect.right / scale).toInt(), (rect.bottom / scale).toInt())
        drawable.draw(canvas)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Fixed logical coordinates keep all thumbnails comparable, regardless of font scaling.
        canvas.save()
        canvas.scale(width / 320f, height / 184f)
        paint.color = backgroundColor
        canvas.drawRoundRect(RectF(0f, 0f, 320f, 184f), 12f, 12f, paint)
        keyboardDrawable?.let { drawSkinDrawable(canvas, it, RectF(0f, 0f, 320f, 184f)) }
        paint.color = textColor
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f
        listOf("あ", "ありがとう", "明日").forEachIndexed { index, label ->
            canvas.drawText(label, 52f + index * 106f, 23f, paint)
        }
        for ((index, label) in labels.withIndex()) {
            val left = 6f + (index % 5) * 63f
            val top = 34f + (index / 5) * 37f
            val rect = RectF(left, top, left + 57f, top + 32f)
            if (keyDrawable != null) {
                drawSkinDrawable(canvas, keyDrawable, rect)
            } else {
                paint.color = keyColor
                canvas.drawRoundRect(rect, 5f, 5f, paint)
                paint.color = textColor
                paint.alpha = 55
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.6f
                canvas.drawRoundRect(rect, 5f, 5f, paint)
                paint.style = Paint.Style.FILL
                paint.alpha = 255
            }
            paint.color = textColor
            paint.textSize = if (label.length > 1) 11f else 15f
            val baseline = rect.centerY() - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(label, rect.centerX(), baseline, paint)
        }
        canvas.restore()
    }
}
