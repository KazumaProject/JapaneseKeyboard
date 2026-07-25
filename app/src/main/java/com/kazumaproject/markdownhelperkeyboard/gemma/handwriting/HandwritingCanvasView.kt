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
                addPoint(event.x, event.y, force = true)
                onStrokeStarted?.invoke()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true
                for (historyIndex in 0 until event.historySize) {
                    addPoint(
                        event.getHistoricalX(pointerIndex, historyIndex),
                        event.getHistoricalY(pointerIndex, historyIndex),
                    )
                }
                addPoint(event.getX(pointerIndex), event.getY(pointerIndex))
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: 0
                addPoint(event.getX(pointerIndex), event.getY(pointerIndex), force = true)
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

    private fun addPoint(x: Float, y: Float, force: Boolean = false) {
        val point = HandwritingPoint(
            x = (x / width.toFloat()).coerceIn(0f, 1f),
            y = (y / height.toFloat()).coerceIn(0f, 1f),
        )
        val previous = activePoints.lastOrNull()
        val minimumDistance = 0.0025f
        if (
            force ||
            previous == null ||
            hypot(point.x - previous.x, point.y - previous.y) >= minimumDistance
        ) {
            if (previous != point) activePoints += point
        }
    }

    private fun drawStroke(canvas: Canvas, points: List<HandwritingPoint>) {
        HandwritingBitmapExporter.drawStroke(
            canvas = canvas,
            paint = strokePaint,
            points = points,
            mapX = { normalized -> normalized * width },
            mapY = { normalized -> normalized * height },
        )
    }
}
