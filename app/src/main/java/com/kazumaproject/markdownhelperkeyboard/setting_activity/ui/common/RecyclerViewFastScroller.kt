package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.R as AppCompatR
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.roundToInt

class RecyclerViewFastScroller @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val thumbWidth = 16.dp
    private val minThumbHeight = 48.dp
    private val trackWidth = 4.dp
    private val touchSlopExtra = 8.dp
    private val thumbRect = RectF()
    private val trackRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resolveThemeColor(AppCompatR.attr.colorControlNormal, 0x33000000)
        alpha = 60
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resolveThemeColor(AppCompatR.attr.colorPrimary, 0xFF666666.toInt())
        alpha = 210
    }

    private var recyclerView: RecyclerView? = null
    private var observedAdapter: RecyclerView.Adapter<*>? = null
    private var isDragging = false

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            invalidate()
        }
    }

    private val adapterObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = invalidate()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = invalidate()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) =
            invalidate()

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = invalidate()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = invalidate()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
            invalidate()
    }

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        if (this.recyclerView === recyclerView) {
            return
        }
        detachFromRecyclerView()
        this.recyclerView = recyclerView
        recyclerView.addOnScrollListener(scrollListener)
        observedAdapter = recyclerView.adapter
        observedAdapter?.registerAdapterDataObserver(adapterObserver)
        invalidate()
    }

    fun detachFromRecyclerView() {
        observedAdapter?.unregisterAdapterDataObserver(adapterObserver)
        observedAdapter = null
        recyclerView?.removeOnScrollListener(scrollListener)
        recyclerView = null
        isDragging = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rv = recyclerView ?: return
        val itemCount = rv.adapter?.itemCount ?: return
        if (height <= 0 || itemCount <= 1) {
            return
        }

        if (!updateRects(rv) || thumbRect.height() <= 0f) {
            return
        }

        canvas.drawRoundRect(trackRect, trackRect.width() / 2f, trackRect.width() / 2f, trackPaint)
        canvas.drawRoundRect(thumbRect, thumbWidth / 2f, thumbWidth / 2f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rv = recyclerView ?: return false
        val itemCount = rv.adapter?.itemCount ?: return false
        if (height <= 0 || itemCount <= 1) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!updateRects(rv)) {
                    return false
                }
                val expandedThumb = RectF(
                    thumbRect.left - touchSlopExtra,
                    thumbRect.top - touchSlopExtra,
                    thumbRect.right + touchSlopExtra,
                    thumbRect.bottom + touchSlopExtra
                )
                if (!expandedThumb.contains(event.x, event.y) && event.x < width - 32.dp) {
                    return false
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                scrollRecyclerViewTo(event.y, rv, itemCount)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    return false
                }
                scrollRecyclerViewTo(event.y, rv, itemCount)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    isDragging = false
                    invalidate()
                    return true
                }
            }
        }

        return isDragging
    }

    override fun onDetachedFromWindow() {
        detachFromRecyclerView()
        super.onDetachedFromWindow()
    }

    private fun scrollRecyclerViewTo(y: Float, recyclerView: RecyclerView, itemCount: Int) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val clampedY = y.coerceIn(0f, height.toFloat())
        val progress = if (height == 0) {
            0f
        } else {
            clampedY / height.toFloat()
        }
        val targetPosition = (progress * (itemCount - 1)).roundToInt()
            .coerceIn(0, itemCount - 1)
        layoutManager.scrollToPositionWithOffset(targetPosition, 0)
        invalidate()
    }

    private fun updateRects(recyclerView: RecyclerView): Boolean {
        val range = recyclerView.computeVerticalScrollRange()
        val extent = recyclerView.computeVerticalScrollExtent()
        val offset = recyclerView.computeVerticalScrollOffset()

        val trackLeft = width - thumbWidth.toFloat()
        val trackCenterX = trackLeft + thumbWidth / 2f
        trackRect.set(
            trackCenterX - trackWidth / 2f,
            0f,
            trackCenterX + trackWidth / 2f,
            height.toFloat()
        )

        if (range <= 0 || extent <= 0 || range <= extent) {
            thumbRect.setEmpty()
            return false
        }

        val thumbHeight = max(minThumbHeight.toFloat(), height * (extent / range.toFloat()))
            .coerceAtMost(height.toFloat())
        val maxThumbTop = height - thumbHeight
        val thumbTop = (maxThumbTop * (offset / (range - extent).toFloat()))
            .coerceIn(0f, maxThumbTop)

        thumbRect.set(
            trackLeft,
            thumbTop,
            width.toFloat(),
            thumbTop + thumbHeight
        )
        return true
    }

    private fun resolveThemeColor(attr: Int, fallback: Int): Int {
        val typedValue = TypedValue()
        if (!context.theme.resolveAttribute(attr, typedValue, true)) {
            return fallback
        }
        val typedArray = context.obtainStyledAttributes(intArrayOf(attr))
        return try {
            typedArray.getColor(0, fallback)
        } finally {
            typedArray.recycle()
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()
}
