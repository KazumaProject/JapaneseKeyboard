package com.kazumaproject.morse_keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Resolution-independent symbols; spoken labels remain available to accessibility services. */
internal class MorseGlyphView @JvmOverloads constructor(context: Context, var glyph: Glyph = Glyph.DOT) : View(context) {
    enum class Glyph { DOT, DASH, STRAIGHT, BOUNDARY, DELETE, CONVERT, ENTER, SMALL, KEYBOARD, SPACE, COMMIT, LEFT, RIGHT, MAP, DAKUTEN, HANDAKUTEN, WABUN, ROMAJI, GLOBE, CUSTOM }
    var ink: Int = 0xff243049.toInt()
        set(value) { field = value; invalidate() }
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
            Glyph.BOUNDARY -> { path(4f,7f,4f,12f,17f,12f,13f,8f); line(17f,12f,13f,16f); line(20f,5f,20f,19f) }
            Glyph.DELETE -> { path(9f,5f,21f,5f,21f,19f,9f,19f,2f,12f,9f,5f); line(12f,9f,17f,15f); line(17f,9f,12f,15f) }
            Glyph.CONVERT -> { canvas.drawArc(4f,4f,20f,20f,205f,145f,false,pen); canvas.drawArc(4f,4f,20f,20f,25f,145f,false,pen); path(16f,3f,20f,8f,15f,8f); path(8f,21f,4f,16f,9f,16f) }
            Glyph.ENTER -> { path(20f,5f,20f,13f,4f,13f,9f,8f); line(4f,13f,9f,18f) }
            Glyph.SMALL -> { path(3f,3f,9f,9f,9f,4f); line(9f,9f,4f,9f); path(21f,21f,15f,15f,20f,15f); line(15f,15f,15f,20f); canvas.drawRoundRect(10f,10f,14f,14f,1f,1f,pen) }
            Glyph.SPACE -> path(4f,10f,4f,16f,20f,16f,20f,10f)
            Glyph.COMMIT -> path(4f,12f,10f,18f,21f,6f)
            Glyph.LEFT -> { path(11f,5f,4f,12f,11f,19f); line(4f,12f,21f,12f) }
            Glyph.RIGHT -> { path(13f,5f,20f,12f,13f,19f); line(3f,12f,20f,12f) }
            Glyph.MAP -> { canvas.drawRoundRect(3f,4f,16f,17f,2f,2f,pen); path(10f,20f,21f,20f,21f,9f); line(7f,9f,12f,9f); line(7f,13f,12f,13f) }
            Glyph.DAKUTEN -> { line(8f,6f,6f,13f); line(15f,6f,13f,13f) }
            Glyph.HANDAKUTEN -> canvas.drawCircle(12f,11f,5f,pen)
            Glyph.KEYBOARD -> {
                canvas.drawRoundRect(2f,5f,22f,19f,3f,3f,pen)
                for (y in listOf(9f,12f)) for(x in listOf(6f,10f,14f,18f)) dot(x,y,.65f)
                line(8f,16f,16f,16f)
            }
            Glyph.WABUN -> { for(y in listOf(7f,12f,17f)) canvas.drawPath(glyphPath.apply { reset(); moveTo(3f,y); cubicTo(9f,y-5,15f,y+5,21f,y) },pen) }
            Glyph.ROMAJI -> { path(4f,8f,20f,8f,16f,4f); path(20f,16f,4f,16f,8f,20f) }
            Glyph.GLOBE -> { canvas.drawCircle(12f,12f,9f,pen); canvas.drawOval(8f,3f,16f,21f,pen); line(3f,12f,21f,12f) }
            Glyph.CUSTOM -> { for(x in listOf(4f,14f)) for(y in listOf(4f,14f)) canvas.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,pen) }
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
