package com.kazumaproject.custom_keyboard.view

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import com.kazumaproject.core.ui.skin.FeverKeyPulse
import com.kazumaproject.core.ui.skin.FeverSpectrum
import kotlin.math.abs

/** A short, translucent finger wake above popup cells, including a held selection. */
internal class FeverPopupTrail(private val view: View) {
    private data class Mark(val x: Float, val y: Float, val time: Long)
    private val marks = ArrayList<Mark>(16)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val location = IntArray(2)
    private val dp get() = view.resources.displayMetrics.density

    fun move(rawX: Float, rawY: Float) {
        if (!FeverKeyPulse.hasMotionSession || !view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return
        view.getLocationOnScreen(location)
        val inset = minOf(8 * dp, view.width / 2f, view.height / 2f)
        // A flick often ends beyond the guide: keep its light at the nearest guide edge.
        val x = (rawX - location[0]).coerceIn(inset, view.width - inset)
        val y = (rawY - location[1]).coerceIn(inset, view.height - inset)
        val now = SystemClock.uptimeMillis()
        val last = marks.lastOrNull()
        if (last != null && now - last.time < 16) return
        if (last != null && abs(last.x - x) + abs(last.y - y) < dp) return
        if (marks.size == 16) marks.removeAt(0)
        marks.add(Mark(x, y, now))
        view.invalidate()
    }

    fun draw(canvas: Canvas) {
        if (!FeverKeyPulse.hasMotionSession) { clear(); return }
        val now = SystemClock.uptimeMillis()
        marks.removeAll { now - it.time >= 360 }
        if (marks.isEmpty()) return
        canvas.save()
        canvas.clipRect(0, 0, view.width, view.height)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        var previous: Mark? = null
        marks.forEach { mark ->
            val fade = 1f - (now - mark.time) / 360f
            paint.alpha = (110 * fade).toInt()
            paint.strokeWidth = 1.5f * dp
            previous?.let { from ->
                paint.shader = LinearGradient(from.x, from.y, mark.x + .1f, mark.y + .1f,
                    FeverSpectrum.colors, null, Shader.TileMode.CLAMP)
                canvas.drawLine(from.x, from.y, mark.x, mark.y, paint)
                paint.shader = null
            }
            paint.color = FeverSpectrum.colors[2]
            paint.alpha = (160 * fade).toInt()
            val radius = (2 + 4 * fade) * dp
            canvas.drawLine(mark.x - radius, mark.y, mark.x + radius, mark.y, paint)
            canvas.drawLine(mark.x, mark.y - radius, mark.x, mark.y + radius, paint)
            previous = mark
        }
        canvas.restore()
        view.postInvalidateOnAnimation()
    }

    fun clear() { marks.clear() }
}
