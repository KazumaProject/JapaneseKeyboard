package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class HandwritingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var store: HandwritingStrokeStore = HandwritingStrokeStore()
    private val activePoints = mutableListOf<HandwritingPoint>()
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var strokeWidthDp: Int = GemmaHandwritingSettings.DEFAULT_PEN_SIZE_DP

    var onStrokeStarted: (() -> Unit)? = null
    var onStrokeCommitted: (() -> Unit)? = null
    var onStrokeCancelled: (() -> Unit)? = null

    init {
        setBackgroundColor(Color.WHITE)
        isClickable = true
        isFocusable = true
    }

    fun bindStore(store: HandwritingStrokeStore) {
        if (this.store === store) {
            invalidate()
            return
        }
        this.store = store
        activePoints.clear()
        activePointerId = MotionEvent.INVALID_POINTER_ID
        invalidate()
    }

    fun refresh() {
        invalidate()
    }

    fun setStrokeColor(color: Int) {
        strokePaint.color = color
        invalidate()
    }

    fun setStrokeWidthDp(widthDp: Int) {
        strokeWidthDp = widthDp.coerceIn(
            GemmaHandwritingSettings.MIN_PEN_SIZE_DP,
            GemmaHandwritingSettings.MAX_PEN_SIZE_DP,
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokePaint.strokeWidth =
            (resources.displayMetrics.density * strokeWidthDp).coerceAtLeast(1f)
        store.strokes.forEach { stroke ->
            drawStroke(canvas, stroke.points)
        }
        drawStroke(canvas, activePoints)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || width <= 0 || height <= 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activePointerId = event.getPointerId(0)
                activePoints.clear()
                addPoint(
                    x = event.x,
                    y = event.y,
                    eventTimeMillis = event.eventTime,
                    pressure = event.pressure,
                    toolType = event.getToolType(0),
                    force = true,
                )
                onStrokeStarted?.invoke()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true
                for (historyIndex in 0 until event.historySize) {
                    addPoint(
                        x = event.getHistoricalX(pointerIndex, historyIndex),
                        y = event.getHistoricalY(pointerIndex, historyIndex),
                        eventTimeMillis = event.getHistoricalEventTime(historyIndex),
                        pressure = event.getHistoricalPressure(pointerIndex, historyIndex),
                        toolType = event.getToolType(pointerIndex),
                    )
                }
                addPoint(
                    x = event.getX(pointerIndex),
                    y = event.getY(pointerIndex),
                    eventTimeMillis = event.eventTime,
                    pressure = event.getPressure(pointerIndex),
                    toolType = event.getToolType(pointerIndex),
                )
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: 0
                addPoint(
                    x = event.getX(pointerIndex),
                    y = event.getY(pointerIndex),
                    eventTimeMillis = event.eventTime,
                    pressure = event.getPressure(pointerIndex),
                    toolType = event.getToolType(pointerIndex),
                    force = true,
                )
                val committed = store.addStroke(activePoints.toList())
                activePoints.clear()
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                if (committed) onStrokeCommitted?.invoke()
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                val strokeWasActive = activePointerId != MotionEvent.INVALID_POINTER_ID
                activePoints.clear()
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                if (strokeWasActive) onStrokeCancelled?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun addPoint(
        x: Float,
        y: Float,
        eventTimeMillis: Long,
        pressure: Float,
        toolType: Int,
        force: Boolean = false,
    ) {
        val inkUnitPx = height.toFloat().coerceAtLeast(1f)
        val point = HandwritingPoint(
            x = x.coerceIn(0f, width.toFloat()) / inkUnitPx,
            y = y.coerceIn(0f, height.toFloat()) / inkUnitPx,
            eventTimeMillis = eventTimeMillis,
            pressure = pressure.coerceAtLeast(0f),
            toolType = toolType,
        )
        val previous = activePoints.lastOrNull()
        val minimumDistance =
            resources.displayMetrics.density * MINIMUM_POINT_DISTANCE_DP / inkUnitPx
        if (
            force ||
            previous == null ||
            hypot(point.x - previous.x, point.y - previous.y) >= minimumDistance
        ) {
            if (previous == null || previous.x != point.x || previous.y != point.y) {
                activePoints += point
            }
        }
    }

    private fun drawStroke(canvas: Canvas, points: List<HandwritingPoint>) {
        HandwritingBitmapExporter.drawStroke(
            canvas = canvas,
            paint = strokePaint,
            points = points,
            mapX = { inkX -> inkX * height },
            mapY = { inkY -> inkY * height },
        )
    }

    private companion object {
        const val MINIMUM_POINT_DISTANCE_DP = 0.75f
    }
}
