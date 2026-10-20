package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.*
import kotlin.math.roundToInt

/** Opens above the candidate bar; user-edited placement is saved separately per orientation. */
internal class SegmentCandidateTreeWindow(
    private val context: Context,
    private val onSelect: (String, SegmentChoice) -> Unit,
    private val onClose: () -> Unit,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val coordinates = FloatingWindowCoordinates(manager)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var anchor: View? = null
    private var body: FloatingPanelFrame? = null
    private var params: WindowManager.LayoutParams? = null
    private var bounds: GuideBounds? = null
    private var gesture: ComposingGuideGesture? = null
    private var editing = false
    private var lastPaths = emptyList<List<SegmentChoice>>()
    private var lastSelected = emptyList<SegmentChoice>()
    private var lastReading = ""
    private var lastFocused = -1
    private var lastCompact = false
    private var lastColors = 0 to 0
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { position() }
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private val placementKey get() = "segment_tree_" +
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape_" else "portrait_"
    private val minimumHeight get() = if (lastCompact) 48 else 112
    private val editExtra get() = if (editing) dp(32) else 0

    fun show(view: View, reading: String, paths: List<List<SegmentChoice>>, selected: List<SegmentChoice>, focusedIndex: Int, background: Int, textColor: Int) {
        if (anchor !== view) {
            stop()
            anchor = view
            view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
        val location = IntArray(2).also(view::getLocationOnScreen)
        val compact = location[1] - coordinates.safeArea(view).top < dp(200)
        if (body == null || compact != lastCompact || paths != lastPaths || selected != lastSelected || focusedIndex != lastFocused || reading != lastReading || lastColors != (background to textColor)) {
            lastCompact = compact
            lastFocused = focusedIndex
            lastPaths = paths; lastSelected = selected; lastReading = reading; lastColors = background to textColor
            val panel = body ?: FloatingPanelFrame(context, ::toggleEditing, ::handleEvent, onHide = onClose,
                hideDescription = R.string.segment_tree_close).also {
                it.id = R.id.segment_candidate_tree_panel
                it.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateGestureExclusion() }
                body = it
            }
            panel.applicationWindowView = view
            panel.setCompactChrome(compact)
            panel.setColors(CandidatePanelColors.resolve(context).copy(background = background, text = textColor, icon = textColor))
            panel.setEditing(editing)
            panel.contentContainer.removeAllViews()
            val tree = TreeView(context, buildCandidateGraph(paths), reading, selected, selected.getOrNull(focusedIndex), textColor, compact) { onSelect(reading, it) }
            val horizontal = HorizontalScrollView(context).apply { id = R.id.segment_tree_scroll; addView(tree) }
            val vertical = ScrollView(context).apply {
                addView(horizontal)
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    scrollTo(0, (tree.preferredCenterY - height / 2).coerceAtLeast(0))
                }
            }
            panel.contentContainer.addView(vertical)
            KeyboardFontApplicator.applyToKeyboardViews(panel, KeyboardFontApplicator.processSnapshot)
        }
        position()
    }

    private fun position() {
        val host = anchor ?: return
        val panel = body ?: return
        if (!host.isShown || !host.isAttachedToWindow) { dismiss(); return }
        val safe = coordinates.safeArea(host)
        val area = GuideBounds(safe.left, safe.top, safe.width(), safe.height())
        val location = IntArray(2).also(host::getLocationOnScreen)
        val availableHeight = location[1] - dp(6) - safe.top
        val saved = preferences.contains(placementKey + "width")
        val normal = if (saved) ComposingGuidePlacement(
            preferences.getFloat(placementKey + "x", .5f), preferences.getFloat(placementKey + "y", 0f),
            preferences.getFloat(placementKey + "width", 320f), preferences.getFloat(placementKey + "height", 300f),
        ).resolve(area.x, area.y, area.width, area.height, density, minimumHeight.toFloat()) else {
            val width = maxOf(host.width, dp(320)).coerceAtMost(safe.width() - dp(16))
            if (width <= 0) return
            val height = availableHeight.coerceAtLeast(dp(minimumHeight)).coerceAtMost(minOf(dp(300), safe.height()))
            val x = location[0].coerceIn(safe.left + dp(8), (safe.right - width - dp(8)).coerceAtLeast(safe.left + dp(8)))
            GuideBounds(x, (location[1] - height - dp(6)).coerceAtLeast(safe.top), width, height)
        }
        val desired = if (editing || gesture != null) bounds ?: normal.copy(height = normal.height + editExtra) else normal
        val width = desired.width.coerceIn(1, area.width)
        val height = desired.height.coerceAtLeast(minOf(dp(if (editing) 160 else minimumHeight), area.height)).coerceAtMost(area.height)
        bounds = desired.copy(width = width, height = height,
            x = desired.x.coerceIn(area.x, area.right - width), y = desired.y.coerceIn(area.y, area.bottom - height))
        updateWindow(bounds!!, host, panel)
    }

    private fun updateWindow(value: GuideBounds, host: View, panel: FloatingPanelFrame) {
        val previous = params
        val next = WindowManager.LayoutParams().apply {
            if (previous != null) copyFrom(previous) else {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                format = PixelFormat.TRANSLUCENT
                token = host.applicationWindowToken
                gravity = Gravity.TOP or Gravity.LEFT
                setTitle("Segment candidate tree")
                coordinates.configure(this)
            }
            width = value.width; height = value.height
            coordinates.position(this, value.x, value.y)
        }
        if (previous != null && previous.width == next.width && previous.height == next.height && previous.x == next.x && previous.y == next.y) return
        if (previous == null) {
            coordinates.observeLegacyOrigin(panel, { params }, ::position)
            try { manager.addView(panel, next) } catch (_: WindowManager.BadTokenException) { return }
            catch (_: IllegalArgumentException) { return }
        } else try { manager.updateViewLayout(panel, next) } catch (_: IllegalArgumentException) { dismiss(); return }
        params = next
    }

    private fun toggleEditing() {
        if (editing) {
            finishGesture(true)
            bounds?.let(::saveBounds)
            editing = false
            bounds = null
        } else {
            editing = true
            bounds = bounds?.let { it.copy(height = it.height + dp(32)) }
        }
        body?.setEditing(editing)
        updateGestureExclusion()
        position()
    }

    private fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < 29) return
        val panel = body ?: return
        val top = (panel.height / 2 - dp(48)).coerceAtLeast(0)
        val bottom = (panel.height / 2 + dp(48)).coerceAtMost(panel.height)
        panel.systemGestureExclusionRects = if (editing && panel.width > 0 && bottom > top) listOf(
            Rect(0, top, minOf(dp(48), panel.width), bottom),
            Rect((panel.width - dp(48)).coerceAtLeast(0), top, panel.width, bottom),
        ) else emptyList()
    }

    private fun handleEvent(event: MotionEvent) {
        val host = anchor ?: return
        val panel = body ?: return
        val safe = coordinates.safeArea(host)
        val offsetX = event.rawX - event.x
        val offsetY = event.rawY - event.y
        val points = (0 until event.pointerCount).associate { index ->
            event.getPointerId(index) to GuidePoint(event.getX(index) + offsetX, event.getY(index) + offsetY)
        }
        val index = event.actionIndex
        val id = event.getPointerId(index)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                gesture?.move(points)?.let { bounds = it }
                val handle = panel.handleAt(event.getX(index), event.getY(index)) ?: return
                val current = bounds ?: return
                val reducer = gesture ?: ComposingGuideGesture(current,
                    GuideBounds(safe.left, safe.top, safe.width(), safe.height()),
                    minOf(dp(if (editing) 208 else 160), safe.width()), minOf(dp(if (editing) 160 else minimumHeight), safe.height()))
                    .also { gesture = it }
                reducer.add(id, handle, points.getValue(id))
            }
            MotionEvent.ACTION_MOVE -> gesture?.move(points)?.let { bounds = it }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                gesture?.move(points)?.let { bounds = it }
                gesture?.remove(id)
                if (gesture?.pointerCount == 0) finishGesture(true)
            }
            MotionEvent.ACTION_CANCEL -> finishGesture(false)
        }
        bounds?.let { updateWindow(it, host, panel) }
    }

    private fun finishGesture(commit: Boolean) {
        val current = gesture ?: return
        bounds = if (commit) current.bounds else current.cancel()
        gesture = null
        if (commit) bounds?.let(::saveBounds)
    }

    private fun saveBounds(value: GuideBounds) {
        val safe = coordinates.safeArea(anchor ?: return)
        val height = (value.height - editExtra).coerceAtLeast(dp(minimumHeight)).coerceAtMost(safe.height())
        preferences.edit()
            .putFloat(placementKey + "x", ((value.x - safe.left).toFloat() / (safe.width() - value.width).coerceAtLeast(1)).coerceIn(0f, 1f))
            .putFloat(placementKey + "y", ((value.y - safe.top).toFloat() / (safe.height() - height).coerceAtLeast(1)).coerceIn(0f, 1f))
            .putFloat(placementKey + "width", value.width / density)
            .putFloat(placementKey + "height", height / density).apply()
    }

    private fun dismiss() {
        if (params != null) body?.let { manager.removeViewImmediate(it) }
        params = null
    }

    fun stop() {
        finishGesture(false)
        editing = false
        bounds = null
        anchor?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        dismiss()
        anchor = null
        body = null
        lastPaths = emptyList()
    }

    private class TreeView(
        context: Context,
        private val root: CandidateGraphNode,
        reading: String,
        selected: List<SegmentChoice>,
        focused: SegmentChoice?,
        textColor: Int,
        private val compact: Boolean,
        onSelect: (SegmentChoice) -> Unit,
    ) : ViewGroup(context) {
        private val dp get() = resources.displayMetrics.density
        private val readingLength = reading.length
        var preferredCenterY = 0
            private set
        private val preferred = linkedSetOf<CandidateGraphNode>()
        private val startLabel = terminal(context.getString(R.string.segment_graph_start), textColor)
        private val endLabel = terminal(context.getString(R.string.segment_graph_end), textColor)
        private val labels = linkedMapOf<CandidateGraphNode, TextView>()
        private val boxes = linkedMapOf<CandidateGraphNode, android.graphics.Rect>()
        private val startBox = Rect()
        private val endBox = Rect()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor and 0x00ffffff or 0x66000000
            style = Paint.Style.STROKE
            strokeWidth = dp * 1.5f
        }

        init {
            setWillNotDraw(false)
            layoutDirection = LAYOUT_DIRECTION_LTR
            var previous = root
            selected.forEach { choice ->
                previous.children.firstOrNull { it.choice == choice }?.let { next -> preferred.add(next); previous = next }
            }
            val visited = mutableSetOf<CandidateGraphNode>()
            fun add(node: CandidateGraphNode) {
                if (!visited.add(node)) return
                node.choice?.let { choice ->
                    val label = TextView(context).apply {
                        text = if (compact) choice.text else choice.text + "\n" + reading.substring(choice.start, choice.end)
                        textSize = if (compact) 12f else 13f
                        maxLines = if (compact) 1 else 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        maxWidth = (220 * dp).toInt()
                        setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
                        gravity = Gravity.CENTER
                        setTextColor(textColor)
                        background = segmentBackground(choice.start, choice == focused)
                        isSelected = choice == focused
                        isActivated = choice in selected
                        contentDescription = context.getString(
                            if (choice in selected) R.string.segment_select_description else R.string.segment_replace_description,
                            choice.text, reading.substring(choice.start, choice.end))
                        isFocusable = true
                        setOnClickListener { onSelect(choice) }
                    }
                    labels[node] = label
                    addView(label)
                }
                node.children.forEach(::add)
            }
            add(root)
            addView(startLabel)
            addView(endLabel)
        }

        private fun terminal(label: String, color: Int) = TextView(context).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(color)
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 4 * dp
                setColor(color and 0x00ffffff or 0x10000000)
                setStroke(dp.roundToInt().coerceAtLeast(1), color and 0x00ffffff or 0x99000000.toInt())
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val unlimited = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            labels.values.forEach { it.measure(unlimited, unlimited) }
            startLabel.measure(unlimited, unlimited)
            endLabel.measure(unlimited, unlimited)
            boxes.clear()
            // All branches share one origin. Columns follow reading offsets, so a different
            // segmentation visibly spans a different range rather than an arbitrary depth.
            val gap = 20 * dp
            val originWidth = startLabel.measuredWidth + 28 * dp
            val offsets = FloatArray(readingLength + 1) { it * 26 * dp }
            labels.forEach { (node, label) ->
                val choice = node.choice ?: return@forEach
                val deficit = label.measuredWidth + gap - (offsets[choice.end] - offsets[choice.start])
                if (deficit > 0) {
                    for (index in choice.start + 1..readingLength) {
                        offsets[index] += deficit * minOf(index - choice.start, choice.end - choice.start) /
                            (choice.end - choice.start)
                    }
                }
            }
            labels.forEach { (node, label) ->
                val choice = node.choice ?: return@forEach
                label.measure(MeasureSpec.makeMeasureSpec((offsets[choice.end] - offsets[choice.start] - gap).roundToInt(), MeasureSpec.EXACTLY), unlimited)
            }
            val rowHeight = maxOf(((if (compact) 32 else 62) * dp).toInt(), (labels.values.maxOfOrNull { it.measuredHeight } ?: 0) + (10 * dp).toInt())
            val lanes = linkedMapOf<CandidateGraphNode, Int>()
            val occupied = mutableMapOf<Int, MutableList<SegmentChoice>>()
            preferred.forEach { node -> lanes[node] = 0; occupied.getOrPut(0, ::mutableListOf).add(node.choice!!) }
            labels.keys.filter { it !in preferred }.sortedBy { it.choice!!.start }.forEach { node ->
                val choice = node.choice!!
                var laneIndex = 1
                fun lane(index: Int) = if (index % 2 == 1) (index + 1) / 2 else -index / 2
                while (occupied[lane(laneIndex)].orEmpty().any { it.start < choice.end && choice.start < it.end }) laneIndex++
                val value = lane(laneIndex)
                lanes[node] = value
                occupied.getOrPut(value, ::mutableListOf).add(choice)
            }
            val firstLane = minOf(0, lanes.values.minOrNull() ?: 0)
            val lastLane = maxOf(0, lanes.values.maxOrNull() ?: 0)
            preferredCenterY = -firstLane * rowHeight + rowHeight / 2
            labels.forEach { (node, label) ->
                val x = (originWidth + offsets[node.choice!!.start]).roundToInt()
                val y = (lanes.getValue(node) - firstLane) * rowHeight + rowHeight / 2
                boxes[node] = Rect(x, y - label.measuredHeight / 2, x + label.measuredWidth, y + (label.measuredHeight + 1) / 2)
            }
            val endX = (originWidth + offsets.last() + 8 * dp).roundToInt()
            startBox.set(0, preferredCenterY - startLabel.measuredHeight / 2, startLabel.measuredWidth, preferredCenterY + (startLabel.measuredHeight + 1) / 2)
            endBox.set(endX, preferredCenterY - endLabel.measuredHeight / 2, endX + endLabel.measuredWidth, preferredCenterY + (endLabel.measuredHeight + 1) / 2)
            setMeasuredDimension(endX + endLabel.measuredWidth + (8 * dp).roundToInt(), (lastLane - firstLane + 1) * rowHeight)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            boxes.forEach { (node, box) -> labels.getValue(node).layout(box.left, box.top, box.right, box.bottom) }
            startLabel.layout(startBox.left, startBox.top, startBox.right, startBox.bottom)
            endLabel.layout(endBox.left, endBox.top, endBox.right, endBox.bottom)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            fun connect(fromX: Float, fromY: Float, to: android.graphics.Rect) {
                val path = Path().apply {
                    moveTo(fromX, fromY)
                    val mid = (fromX + to.left) / 2f
                    cubicTo(mid, fromY, mid, to.exactCenterY(), to.left.toFloat(), to.exactCenterY())
                }
                canvas.drawPath(path, paint)
            }
            val normalColor = paint.color
            fun emphasize(value: Boolean) {
                paint.color = if (value) segmentColor(0) else normalColor
                paint.strokeWidth = (if (value) 2f else 1.25f) * dp
            }
            root.children.forEach { node ->
                emphasize(node in preferred)
                connect(startLabel.right.toFloat(), preferredCenterY.toFloat(), boxes.getValue(node))
            }
            boxes.forEach { (node, from) ->
                if (node.children.isEmpty()) {
                    emphasize(node in preferred)
                    connect(from.right.toFloat(), from.exactCenterY(), Rect(endLabel.left, endLabel.top, endLabel.right, endLabel.bottom))
                }
                node.children.forEach { child ->
                    emphasize(node in preferred && child in preferred)
                    connect(from.right.toFloat(), from.exactCenterY(), boxes.getValue(child))
                }
            }
            paint.color = normalColor
        }
    }
}
