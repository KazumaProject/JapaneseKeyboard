package com.kazumaproject.morse_keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.core.ui.font.KeyboardFontGlyphDrawable

/** Resolution-independent symbols; spoken labels remain available to accessibility services. */
internal class MorseGlyphView @JvmOverloads constructor(context: Context, glyph: Glyph = Glyph.DOT) : View(context) {
    var glyph: Glyph = glyph
        set(value) { if (field != value) { field = value; icon?.callback = null; icon = sharedIcon(value)?.apply { setTint(ink) }; invalidate() } }
    private fun sharedIcon(glyph: Glyph) = when (glyph) {
        Glyph.BOUNDARY -> CoreR.drawable.keyboard_tab_24px
        Glyph.MAP -> CoreR.drawable.swap_horiz_24px
        Glyph.DELETE -> CoreR.drawable.baseline_backspace_24
        Glyph.CONVERT -> CoreR.drawable.henkan
        Glyph.SPACE -> CoreR.drawable.baseline_space_bar_24
        Glyph.ENTER -> CoreR.drawable.baseline_keyboard_return_24
        Glyph.CLEAR -> CoreR.drawable.ic_close
        Glyph.SMALL -> CoreR.drawable.kana_small
        Glyph.COMMIT -> CoreR.drawable.baseline_check_24
        Glyph.KEYBOARD -> CoreR.drawable.keyboard_24px
        Glyph.LEFT -> CoreR.drawable.baseline_arrow_left_24
        Glyph.RIGHT -> CoreR.drawable.baseline_arrow_right_24
        Glyph.GLOBE -> CoreR.drawable.language_24dp
        else -> null
    }?.let { KeyboardFontGlyphDrawable.create(context, it)?.mutate()?.apply {
        // kana_small has its ink in the middle third of a 100 × 100 viewport.
        if (glyph == Glyph.SMALL) setBounds(-21, -21, 45, 45)
        else {
            val ratio = intrinsicWidth.toFloat() / intrinsicHeight.coerceAtLeast(1)
            val w = if (ratio >= 1f) 24 else (24 * ratio).toInt()
            val h = if (ratio >= 1f) (24 / ratio).toInt() else 24
            setBounds((24 - w) / 2, (24 - h) / 2, (24 + w) / 2, (24 + h) / 2)
        }
        callback = this@MorseGlyphView
    } }
    private var icon = sharedIcon(glyph)
    private var drawingIcon = false
    override fun invalidateDrawable(who: android.graphics.drawable.Drawable) {
        // Static glyph delegates may invalidate while applying their draw-time tint/state.
        // Keep font/theme updates outside draw, without scheduling another frame from this draw.
        if (!drawingIcon) super.invalidateDrawable(who)
    }
    override fun performClick(): Boolean = super.performClick()
    override fun verifyDrawable(who: android.graphics.drawable.Drawable) = who === icon || super.verifyDrawable(who)
    enum class Glyph { DOT, DASH, STRAIGHT, CLEAR, BOUNDARY, DELETE, CONVERT, ENTER, SMALL, KEYBOARD, SPACE, COMMIT, LEFT, RIGHT, MAP, DAKUTEN, HANDAKUTEN, WABUN, ROMAJI, GLOBE, CUSTOM }
    var ink: Int = 0xff243049.toInt()
        set(value) { field = value; icon?.setTint(value); invalidate() }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPath = Path()
    init { isClickable = true; isSoundEffectsEnabled = false }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = glyph == Glyph.DOT || glyph == Glyph.DASH || glyph == Glyph.STRAIGHT
        val size = if (pad) minOf(width, height) * .38f else minOf(width, height) * .48f
        canvas.save()
        canvas.translate((width - size) / 2, (height - size) / 2)
        canvas.scale(size / 24, size / 24)
        icon?.let {
            drawingIcon = true
            try { it.draw(canvas) } finally { drawingIcon = false }
            canvas.restore()
            return
        }
        pen.color = ink; pen.strokeWidth = 1.65f; pen.strokeCap = Paint.Cap.ROUND
        pen.strokeJoin = Paint.Join.ROUND; pen.style = Paint.Style.STROKE
        fun line(x: Float, y: Float, xx: Float, yy: Float) = canvas.drawLine(x,y,xx,yy,pen)
        fun path(vararg points: Float) {
            val p = glyphPath.apply { reset(); moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i],points[i+1]) }
            canvas.drawPath(p,pen)
        }
        fun dot(x: Float, y: Float, r: Float) { pen.style=Paint.Style.FILL; canvas.drawCircle(x,y,r,pen); pen.style=Paint.Style.STROKE }
        when (glyph) {
            Glyph.DOT -> dot(12f,12f,3.5f)
            Glyph.DASH -> { pen.strokeWidth=5f; line(5f,12f,19f,12f) }
            Glyph.STRAIGHT -> { dot(5f,12f,2.5f); pen.strokeWidth=4f; line(13f,12f,21f,12f) }
            Glyph.DAKUTEN -> { line(8f,6f,6f,13f); line(15f,6f,13f,13f) }
            Glyph.HANDAKUTEN -> canvas.drawCircle(12f,11f,5f,pen)
            Glyph.WABUN -> { for(y in listOf(7f,12f,17f)) canvas.drawPath(glyphPath.apply { reset(); moveTo(3f,y); cubicTo(9f,y-5,15f,y+5,21f,y) },pen) }
            Glyph.ROMAJI -> { path(4f,8f,20f,8f,16f,4f); path(20f,16f,4f,16f,8f,20f) }
            Glyph.CUSTOM -> { for(x in listOf(4f,14f)) for(y in listOf(4f,14f)) canvas.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,pen) }
            else -> Unit
        }
        canvas.restore()
    }
}

internal class MorseSignalView(context: Context) : View(context) {
    var signal = ""
    var ink = 0xff758197.toInt()
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        pen.color = ink
        val symbols = signal.takeLast(24)
        if (symbols.isEmpty()) {
            pen.alpha = 60
            for(i in -1..1) canvas.drawCircle(width/2f + i*8*density,height/2f,1.5f*density,pen)
            return
        }
        val unit = minOf(5f*density, width / (symbols.sumOf { if(it == '.') 2 else 4 } + 2).toFloat())
        var x = (width - symbols.sumOf { if(it == '.') 2 else 4 }*unit + unit)/2
        for(symbol in symbols) {
            val length = if(symbol == '.') unit else unit*3
            canvas.drawRoundRect(x,height/2f-unit/2,x+length,height/2f+unit/2,unit/2,unit/2,pen)
            x += length+unit
        }
    }
}
