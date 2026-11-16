package com.kazumaproject.markdownhelperkeyboard.sumire_popup

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import com.kazumaproject.custom_keyboard.data.*
import com.kazumaproject.custom_keyboard.view.outlinePath
import kotlin.math.ceil
import kotlin.math.hypot

/** A focused, enlarged draft editor. Bounds stay fixed; normal editing moves/resizes the popup. */
@SuppressLint("ClickableViewAccessibility")
class PopupShapeEditorView(context: Context) : View(context) {
    lateinit var region: PopupRegion
    var sourceAspect = 1f
    var maximumCanvasHeight = (320 * resources.displayMetrics.density).toInt()
    var selectedVertex = 0
        private set
    val frame = RectF()
    val vertices get() = region.outline?.vertices ?: PopupOutline.RECTANGLE
    var onChanged: () -> Unit = {}
    var onUndoPoint: (PopupOutline?) -> Unit = {}
    var onInvalid: () -> Unit = {}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var dragVertex = -1
    private var edge = -1
    private var before: PopupOutline? = null
    private var rejected: PopupVertex? = null

    fun selectVertex(index: Int) {
        selectedVertex = index.coerceIn(0, (vertices.size - 1).coerceAtLeast(0))
        onChanged(); invalidate()
    }

    fun showOutline(outline: PopupOutline?) {
        region = region.copy(outline = outline)
        selectedVertex = selectedVertex.coerceAtMost((vertices.size - 1).coerceAtLeast(0))
        rejected = null
        onChanged(); invalidate()
    }

    fun addVertex(after: Int = selectedVertex) {
        if (vertices.size >= PopupOutline.MAX_VERTICES) return
        val points = vertices
        val next = if (points.isEmpty()) PopupOutline.TRIANGLE else {
            val index = after.coerceIn(points.indices)
            val a = points[index]; val b = points[(index + 1) % points.size]
            points.toMutableList().apply { add(index + 1, PopupVertex((a.x + b.x) / 2, (a.y + b.y) / 2)) }
        }
        val outline = PopupOutline(next)
        if (!outline.isValid()) { onInvalid(); return }
        onUndoPoint(region.outline)
        selectedVertex = if (points.isEmpty()) 0 else after.coerceIn(points.indices) + 1
        showOutline(outline)
    }

    fun removeVertex() {
        if (vertices.isEmpty()) return
        val next = if (vertices.size <= 3) emptyList() else vertices.filterIndexed { index, _ -> index != selectedVertex }
        val outline = PopupOutline(next)
        if (!outline.isValid()) { onInvalid(); return }
        onUndoPoint(region.outline); showOutline(outline)
    }

    fun resetRectangle() {
        if (region.outline == null) return
        onUndoPoint(region.outline); showOutline(null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val margin = 72 * density
        val desired = (margin + (width - margin).coerceAtLeast(1f) / sourceAspect.coerceAtLeast(.01f)).toInt()
        val height = desired.coerceIn(minOf((144 * density).toInt(), maximumCanvasHeight), maximumCanvasHeight)
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val availableWidth = (w - 72 * density).coerceAtLeast(1f)
        val availableHeight = (h - 72 * density).coerceAtLeast(1f)
        val aspect = sourceAspect.coerceAtLeast(.01f)
        val width = minOf(availableWidth, availableHeight * aspect)
        val height = width / aspect
        frame.set((w - width) / 2, (h - height) / 2, (w + width) / 2, (h + height) / 2)
    }

    fun vertexPosition(index: Int): Pair<Float, Float> = vertices[index].let {
        (frame.left + it.x * frame.width()) to (frame.top + it.y * frame.height())
    }

    override fun onDraw(canvas: Canvas) {
        if (!::region.isInitialized) return
        val primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        val foreground = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        paint.color = foreground; paint.style = Paint.Style.STROKE; paint.strokeWidth = density
        paint.pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
        canvas.drawRect(frame, paint); paint.pathEffect = null
        val path = region.copy(bounds = PopupBounds(0f, 0f, 1f, 1f)).outlinePath(frame.left, frame.top, frame.width(), frame.height())
        paint.style = Paint.Style.FILL; paint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(primary, 40)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density; paint.color = primary
        canvas.drawPath(path, paint)
        val points = vertices
        if (points.size < PopupOutline.MAX_VERTICES) points.indices.forEach { i ->
            val a = vertexPosition(i); val b = vertexPosition((i + 1) % points.size)
            val x = (a.first + b.first) / 2; val y = (a.second + b.second) / 2
            paint.strokeWidth = density; paint.color = primary
            canvas.drawCircle(x, y, 8 * density, paint)
            canvas.drawLine(x - 4 * density, y, x + 4 * density, y, paint)
            canvas.drawLine(x, y - 4 * density, x, y + 4 * density, paint)
        }
        points.indices.forEach { i ->
            val (x, y) = vertexPosition(i)
            paint.style = Paint.Style.FILL; paint.color = primary
            canvas.drawCircle(x, y, (if (i == selectedVertex) 9 else 6) * density, paint)
            paint.color = foreground; paint.textSize = 12 * resources.displayMetrics.scaledDensity; paint.textAlign = Paint.Align.CENTER
            canvas.drawText((i + 1).toString(), x, y - 14 * density, paint)
        }
        rejected?.let {
            paint.color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density
            canvas.drawCircle(frame.left + it.x * frame.width(), frame.top + it.y * frame.height(), 10 * density, paint)
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::region.isInitialized) return false
        fun distance(point: Pair<Float, Float>) = hypot(event.x - point.first, event.y - point.second)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                before = region.outline; rejected = null
                dragVertex = vertices.indices.minByOrNull { distance(vertexPosition(it)) }
                    ?.takeIf { distance(vertexPosition(it)) <= 24 * density } ?: -1
                edge = if (dragVertex >= 0) -1 else vertices.indices.minByOrNull { i ->
                    val a = vertexPosition(i); val b = vertexPosition((i + 1) % vertices.size)
                    distance((a.first + b.first) / 2 to (a.second + b.second) / 2)
                }?.takeIf { i ->
                    val a = vertexPosition(i); val b = vertexPosition((i + 1) % vertices.size)
                    distance((a.first + b.first) / 2 to (a.second + b.second) / 2) <= 16 * density
                } ?: -1
                if (dragVertex >= 0) { selectedVertex = dragVertex; onChanged() }
                parent?.requestDisallowInterceptTouchEvent(dragVertex >= 0 || edge >= 0)
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragVertex < 0) return true
                val desired = PopupVertex(((event.x - frame.left) / frame.width()).coerceIn(0f, 1f), ((event.y - frame.top) / frame.height()).coerceIn(0f, 1f))
                val current = vertices[dragVertex]
                val steps = ceil(hypot((desired.x - current.x) * frame.width(), (desired.y - current.y) * frame.height()) / 2).toInt().coerceIn(1, 512)
                var accepted = current
                rejected = null
                for (step in 1..steps) {
                    val t = step.toFloat() / steps
                    val point = PopupVertex(current.x + (desired.x - current.x) * t, current.y + (desired.y - current.y) * t)
                    val candidate = PopupOutline(vertices.mapIndexed { index, p -> if (index == dragVertex) point else p })
                    if (!candidate.isValid()) { rejected = desired; onInvalid(); break }
                    accepted = point
                }
                region = region.copy(outline = PopupOutline(vertices.mapIndexed { index, p -> if (index == dragVertex) accepted else p }))
                onChanged(); invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                if (dragVertex >= 0 && region.outline != before) onUndoPoint(before)
                if (edge >= 0) addVertex(edge)
                dragVertex = -1; edge = -1; rejected = null
                parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                if (dragVertex >= 0) showOutline(before)
                dragVertex = -1; edge = -1; rejected = null
                parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true
            }
        }
        return true
    }
}
