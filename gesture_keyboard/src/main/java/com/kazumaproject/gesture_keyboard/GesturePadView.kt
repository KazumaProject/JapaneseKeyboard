package com.kazumaproject.gesture_keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot

/** Single pointer, bounded capture. No text is emitted by this view. */
class GesturePadView(context: Context) : View(context) {
    var onStroke: (List<GesturePoint>) -> Unit = {}
    var onStart: () -> Unit = {}
    var captureAllowed: () -> Boolean = { true }
    var inkColor: Int = Color.BLUE
    var guide: List<GesturePoint>? = null
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f * resources.displayMetrics.density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val points = mutableListOf<GesturePoint>()
    private var pointer = -1
    private var pathLength = 0f
    init {
        contentDescription = if (resources.configuration.locales[0].language == "ja") "ジェスチャー描画領域" else "Gesture drawing area"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    fun cancelStroke() { pointer = -1; points.clear(); pathLength = 0f; invalidate() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!captureAllowed()) return false
        if (event.pointerCount != 1 || event.actionMasked == MotionEvent.ACTION_CANCEL) { cancelStroke(); return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelStroke(); pointer = event.getPointerId(0); onStart()
                parent?.requestDisallowInterceptTouchEvent(true)
                append(event.x, event.y); return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (pointer != event.getPointerId(0)) return true
                for (i in 0 until event.historySize) {
                    if (!append(event.getHistoricalX(i), event.getHistoricalY(i))) { cancelStroke(); return true }
                }
                if (!append(event.x, event.y)) { cancelStroke(); return true }
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val stroke = points.toList()
                    val valid = pathLength >= ViewConfiguration.get(context).scaledTouchSlop * 2
                    cancelStroke()
                    if (valid && stroke.size >= 2) onStroke(stroke)
                    performClick()
                }
                return true
            }
        }
        return true
    }
    private fun append(x: Float, y: Float): Boolean {
        if (x < 0 || y < 0 || x >= width || y >= height) return false
        val previous = points.lastOrNull()
        if (previous != null) {
            val distance = hypot(x - previous.x, y - previous.y)
            if (distance < 1f && points.size > 1) return true
            pathLength += distance
        }
        // Bound capture while retaining later points instead of truncating the end.
        if (points.size >= 512) {
            val reduced = points.filterIndexed { index, _ -> index % 2 == 0 }
            points.clear(); points.addAll(reduced)
        }
        points.add(GesturePoint(x, y)); invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        guide?.takeIf { it.size > 1 }?.let { sample ->
            val minX = sample.minOf { it.x }; val minY = sample.minOf { it.y }
            val w = (sample.maxOf { it.x } - minX).coerceAtLeast(0.01f)
            val h = (sample.maxOf { it.y } - minY).coerceAtLeast(0.01f)
            val scale = minOf(width * 0.72f / w, height * 0.72f / h)
            val display = sample.map { GesturePoint((it.x - minX - w / 2) * scale + width / 2, (it.y - minY - h / 2) * scale + height / 2) }
            drawPath(canvas, display, inkColor and 0x00ffffff or 0x55000000)
            paint.style = Paint.Style.FILL; paint.color = inkColor
            canvas.drawCircle(display.first().x, display.first().y, 5f * resources.displayMetrics.density, paint)
            val end = display.last(); val before = display[(display.size - 2).coerceAtLeast(0)]
            val angle = kotlin.math.atan2(end.y - before.y, end.x - before.x)
            val size = 10f * resources.displayMetrics.density
            paint.style = Paint.Style.STROKE
            for (delta in listOf(-0.55f, 0.55f)) canvas.drawLine(end.x, end.y,
                end.x - size * kotlin.math.cos(angle + delta), end.y - size * kotlin.math.sin(angle + delta), paint)
        }
        drawPath(canvas, points, inkColor)
    }
    private fun drawPath(canvas: Canvas, data: List<GesturePoint>, color: Int) {
        if (data.isEmpty()) return
        val path = Path().apply { moveTo(data.first().x, data.first().y); data.drop(1).forEach { lineTo(it.x, it.y) } }
        paint.color = color; paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint)
    }
    override fun onDetachedFromWindow() { cancelStroke(); super.onDetachedFromWindow() }
}
