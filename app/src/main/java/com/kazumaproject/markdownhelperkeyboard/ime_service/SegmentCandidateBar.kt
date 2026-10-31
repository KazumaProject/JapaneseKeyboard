package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.kazumaproject.markdownhelperkeyboard.R
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun segmentColor(start: Int) = Color.HSVToColor(floatArrayOf((214f + start * 137.508f) % 360f, .62f, .72f))
internal fun segmentBackground(start: Int, selected: Boolean) = GradientDrawable().apply {
    cornerRadius = 10f
    setColor(Color.argb(if (selected) 55 else 24, Color.red(segmentColor(start)), Color.green(segmentColor(start)), Color.blue(segmentColor(start))))
    setStroke(if (selected) 3 else 1, segmentColor(start))
}

/** The dashed rail is a movement guide. Only the solid boundary handles accept drags. */
class SegmentCandidateBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    internal var onFocusSegment: (Int) -> Unit = {}
    internal var onReorderSegment: (Int, Int) -> Unit = { _, _ -> }
    internal var onResizeSegment: (Int, Int) -> Unit = { _, _ -> }
    private val dp get() = resources.displayMetrics.density
    private val cards = LinearLayout(context)
    private val rail = BoundaryRail(context)
    private val column = LinearLayout(context).apply { orientation = VERTICAL }
    private val scroll = object : HorizontalScrollView(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            return handled
        }
    }.apply {
        id = R.id.segment_candidate_scroll
        isHorizontalScrollBarEnabled = true
    }
    private data class SegmentDrag(val signature: List<SegmentChoice>, val reading: String, val from: Int)
    private var lastSegments: List<BunsetsuSegmentState> = emptyList()
    private var lastFocus = -1
    private var lastColor = 0

    private var dragScrollDirection = 0
    private val dragScroller = object : Runnable {
        override fun run() {
            if (dragScrollDirection == 0 || !isAttachedToWindow) return
            scroll.scrollBy((dragScrollDirection * 8 * dp).roundToInt(), 0)
            scroll.postDelayed(this, 32)
        }
    }

    private fun updateDragScroll(direction: Int) {
        if (dragScrollDirection == direction) return
        dragScrollDirection = direction
        scroll.removeCallbacks(dragScroller)
        if (direction != 0) scroll.post(dragScroller)
    }

    override fun onDetachedFromWindow() {
        updateDragScroll(0)
        super.onDetachedFromWindow()
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = LAYOUT_DIRECTION_LTR
        setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), 0)
        column.addView(cards, LayoutParams(LayoutParams.WRAP_CONTENT, (38 * dp).toInt()))
        column.addView(rail, LayoutParams(LayoutParams.MATCH_PARENT, (42 * dp).toInt()))
        scroll.addView(column)
        addView(scroll, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        rail.onResize = { start, end -> onResizeSegment(start, end) }
    }

    internal fun bind(segments: List<BunsetsuSegmentState>, focus: Int, textColor: Int) {
        if (segments == lastSegments && focus == lastFocus && textColor == lastColor) return
        val followFocus = focus != lastFocus || segmentChoices(segments).map { it.start to it.end } !=
            segmentChoices(lastSegments).map { it.start to it.end }
        lastSegments = segments
        lastFocus = focus
        lastColor = textColor
        cards.removeAllViews()
        val input = segments.joinToString("") { it.reading }
        val legal = readingBoundaries(input)
        val choices = segmentChoices(segments)
        val positions = legal.mapIndexed { index, offset -> offset to (16 + index * 26) * dp }.toMap()
        cards.setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
        choices.forEachIndexed { index, choice ->
            val label = object : TextView(context) {
                override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(info)
                    if (index > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.segment_move_left, context.getString(R.string.segment_move_left)))
                    if (index < choices.lastIndex) info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.segment_move_right, context.getString(R.string.segment_move_right)))
                }
                override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
                    val target = when (action) { R.id.segment_move_left -> index - 1; R.id.segment_move_right -> index + 1; else -> return super.performAccessibilityAction(action, arguments) }
                    if (target !in choices.indices) return false
                    onReorderSegment(index, target)
                    return true
                }
            }.apply {
                text = choice.text
                textSize = 16f
                setTextColor(textColor)
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                background = segmentBackground(choice.start, index == focus)
                contentDescription = context.getString(R.string.segment_select_description, choice.text, input.substring(choice.start, choice.end))
                isSelected = index == focus
                isFocusable = true
                setOnClickListener { onFocusSegment(index) }
                setOnLongClickListener {
                    if (choices.size < 2) false else startDragAndDrop(
                        ClipData.newPlainText("segment", ""), DragShadowBuilder(this), SegmentDrag(choices, input, index), 0)
                }
                setOnDragListener { _, event ->
                    val dragged = event.localState as? SegmentDrag
                    val valid = dragged != null && dragged.signature == segmentChoices(lastSegments) && dragged.reading == lastSegments.joinToString("") { it.reading }
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> valid
                        DragEvent.ACTION_DRAG_ENTERED -> { if (valid) alpha = .5f; valid }
                        DragEvent.ACTION_DRAG_LOCATION -> {
                            if (valid) {
                                val labelLocation = IntArray(2).also(::getLocationOnScreen)
                                val viewport = IntArray(2).also(scroll::getLocationOnScreen)
                                val x = labelLocation[0] + event.x - viewport[0]
                                updateDragScroll(when { x < 24 * dp -> -1; x > scroll.width - 24 * dp -> 1; else -> 0 })
                            } else updateDragScroll(0)
                            valid
                        }
                        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { alpha = 1f; updateDragScroll(0); valid }
                        DragEvent.ACTION_DROP -> {
                            alpha = 1f
                            updateDragScroll(0)
                            if (valid) onReorderSegment(dragged!!.from, index)
                            valid
                        }
                        else -> valid
                    }
                }
            }
            cards.addView(label, LayoutParams(((positions[choice.end] ?: 0f) - (positions[choice.start] ?: 0f)).roundToInt(), LayoutParams.MATCH_PARENT))
        }
        com.kazumaproject.core.ui.font.KeyboardFontApplicator.applyToKeyboardViews(
            this, com.kazumaproject.core.ui.font.KeyboardFontApplicator.processSnapshot)
        rail.bind(input, choices.getOrNull(focus), positions, textColor)
        rail.layoutParams.width = ((legal.size - 1) * 26 * dp + 32 * dp).roundToInt()
        column.requestLayout()
        if (followFocus) post {
            val focused = cards.getChildAt(focus) ?: return@post
            val rectangle = android.graphics.Rect(0, 0, focused.width, focused.height)
            column.offsetDescendantRectToMyCoords(focused, rectangle)
            scroll.requestChildRectangleOnScreen(column, rectangle, false)
        }
    }

    private class BoundaryRail(context: Context) : View(context) {
        var onResize: (Int, Int) -> Unit = { _, _ -> }
        private val dp get() = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var input = ""
        private var selected: SegmentChoice? = null
        private var positions = emptyMap<Int, Float>()
        private var textColor = Color.BLACK
        private var draggingStart: Boolean? = null
        private var preview = 0

        init { isFocusable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

        fun bind(value: String, choice: SegmentChoice?, offsets: Map<Int, Float>, color: Int) {
            input = value; selected = choice; positions = offsets; textColor = color
            draggingStart = null
            contentDescription = context.getString(R.string.segment_range_description, choice?.start ?: 0, choice?.end ?: 0)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val choice = selected ?: return
            val left = if (draggingStart == true) preview else choice.start
            val right = if (draggingStart == false) preview else choice.end
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp
            paint.color = textColor
            paint.alpha = 130
            paint.pathEffect = DashPathEffect(floatArrayOf(4 * dp, 3 * dp), 0f)
            val legal = positions.keys.toList()
            val rangeStart = if (draggingStart == false) legal.first { it > choice.start } else 0
            val rangeEnd = if (draggingStart == true) legal.last { it < choice.end } else input.length
            canvas.drawLine(positions.getValue(rangeStart), 40 * dp, positions.getValue(rangeEnd), 40 * dp, paint)
            paint.pathEffect = null
            val selection = RectF(positions.getValue(left), 3 * dp, positions.getValue(right), 37 * dp)
            paint.style = Paint.Style.FILL
            paint.color = segmentColor(choice.start)
            paint.alpha = 55
            canvas.drawRoundRect(selection, 6 * dp, 6 * dp, paint)
            paint.alpha = 255
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * dp
            canvas.drawRoundRect(selection, 6 * dp, 6 * dp, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 12 * resources.displayMetrics.scaledDensity
            paint.textAlign = Paint.Align.CENTER
            legal.zipWithNext().forEach { (a, b) ->
                val inside = a >= left && b <= right
                paint.color = textColor
                paint.alpha = if (inside) 255 else 150
                paint.isFakeBoldText = inside
                canvas.drawText(input.substring(a, b), (positions.getValue(a) + positions.getValue(b)) / 2, 27 * dp, paint)
            }
            paint.isFakeBoldText = false
            paint.alpha = 255
            listOf(left, right).forEach { offset ->
                val x = positions.getValue(offset)
                paint.color = segmentColor(choice.start)
                canvas.drawRoundRect(RectF(x - 6 * dp, 3 * dp, x + 6 * dp, 37 * dp), 6 * dp, 6 * dp, paint)
                paint.color = Color.WHITE
                canvas.drawRoundRect(RectF(x - dp, 13 * dp, x + dp, 27 * dp), dp, dp, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val choice = selected ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val startDistance = abs(event.x - positions.getValue(choice.start))
                    val endDistance = abs(event.x - positions.getValue(choice.end))
                    if (minOf(startDistance, endDistance) > 12 * dp) return false
                    draggingStart = startDistance <= endDistance
                    preview = if (draggingStart == true) choice.start else choice.end
                    parent.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val start = draggingStart ?: return false
                    preview = positions.keys.filter { if (start) it < choice.end else it > choice.start }
                        .minByOrNull { abs(positions.getValue(it) - event.x) } ?: return true
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val start = draggingStart ?: return false
                    draggingStart = null
                    parent.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    if (preview != if (start) choice.start else choice.end) {
                        onResize(if (start) preview else choice.start, if (start) choice.end else preview)
                    }
                    invalidate()
                }
                MotionEvent.ACTION_CANCEL -> { draggingStart = null; parent.requestDisallowInterceptTouchEvent(false); invalidate() }
            }
            return true
        }

        override fun performClick(): Boolean { super.performClick(); return true }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            val choice = selected ?: return
            val legal = positions.keys.toList()
            val actions = listOf(
                Triple(R.id.segment_start_left, R.string.segment_start_left, choice.start > 0),
                Triple(R.id.segment_start_right, R.string.segment_start_right, legal.any { it > choice.start && it < choice.end }),
                Triple(R.id.segment_end_left, R.string.segment_end_left, legal.any { it > choice.start && it < choice.end }),
                Triple(R.id.segment_end_right, R.string.segment_end_right, choice.end < input.length),
            )
            actions.filter { it.third }.forEach { info.addAction(AccessibilityNodeInfo.AccessibilityAction(it.first, context.getString(it.second))) }
        }

        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
            val choice = selected ?: return super.performAccessibilityAction(action, arguments)
            val legal = positions.keys.toList()
            val start = when (action) {
                R.id.segment_start_left -> legal.lastOrNull { it < choice.start }
                R.id.segment_start_right -> legal.firstOrNull { it > choice.start && it < choice.end }
                else -> choice.start
            } ?: return false
            val end = when (action) {
                R.id.segment_end_left -> legal.lastOrNull { it < choice.end && it > choice.start }
                R.id.segment_end_right -> legal.firstOrNull { it > choice.end }
                else -> choice.end
            } ?: return false
            if (action in setOf(R.id.segment_start_left, R.id.segment_start_right, R.id.segment_end_left, R.id.segment_end_right)) {
                onResize(start, end); return true
            }
            return super.performAccessibilityAction(action, arguments)
        }
    }
}
