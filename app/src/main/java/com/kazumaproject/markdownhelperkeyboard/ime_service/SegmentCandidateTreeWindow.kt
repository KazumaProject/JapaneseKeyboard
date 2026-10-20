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
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.SeekBar
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
    private var zoomView: ZoomLayout? = null
    private var zoomControls: View? = null
    private var zoomSlider: SeekBar? = null
    private var zoomLabel: TextView? = null
    private val scalePercent get() = preferences.getInt(SEGMENT_TREE_SCALE_KEY, 85).coerceIn(50, 150)
    private var lastPaths = emptyList<List<SegmentChoice>>()
    private var lastSelected = emptyList<SegmentChoice>()
    private var lastReading = ""
    private var lastFocused = -1
    private var lastBranches = emptyList<List<SegmentChoice>>()
    private var lastBranchMode = false
    private var acceptingSelections = false
    private var lastCompact = false
    private var lastColors = 0 to 0
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { position() }
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()
    private val placementKey get() = "segment_tree_" +
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape_" else "portrait_"
    private val minimumHeight get() = if (lastCompact) 48 else 112
    private val editExtra get() = if (editing) dp(64) else 0

    fun show(view: View, reading: String, paths: List<List<SegmentChoice>>, selected: List<SegmentChoice>, focusedIndex: Int, background: Int, textColor: Int,
        branchMode: Boolean, branches: List<List<SegmentChoice>>) {
        acceptingSelections = true
        if (anchor !== view) {
            anchor?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
            anchor = view
            view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
        body?.applicationWindowView = view
        val location = IntArray(2).also(view::getLocationOnScreen)
        val compact = location[1] - coordinates.safeArea(view).top < dp(200)
        if (body == null || branchMode != lastBranchMode || branches != lastBranches || compact != lastCompact || paths != lastPaths || selected != lastSelected || focusedIndex != lastFocused || reading != lastReading || lastColors != (background to textColor)) {
            lastBranchMode = branchMode
            lastBranches = branches
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
            val select: (SegmentChoice) -> Unit = { if (acceptingSelections) onSelect(reading, it) }
            val graph = buildSegmentationCandidateGraph(paths, selected)
            val tree = if (branchMode) BranchesView(context, branches, reading, selected.getOrNull(focusedIndex), textColor, compact, select)
                else TreeView(context, graph, reading, selected, selected.getOrNull(focusedIndex), textColor, compact, select)
            val zoom = ZoomLayout(context, tree, scalePercent).also { zoomView = it }
            val horizontal = HorizontalScrollView(context).apply { id = R.id.segment_tree_scroll; addView(zoom) }
            val vertical = ScrollView(context).apply {
                addView(horizontal)
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    scrollTo(0, (tree.preferredCenterY * zoom.factor - height / 2).roundToInt().coerceAtLeast(0))
                }
            }
            val controls = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = if (editing) View.VISIBLE else View.GONE
            }.also { zoomControls = it }
            controls.addView(TextView(context).apply {
                textSize = 11f
                setTextColor(textColor)
                text = context.getString(R.string.segment_tree_scale_value, scalePercent)
                zoomLabel = this
            }, LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT))
            controls.addView(SeekBar(context).apply {
                id = R.id.segment_tree_scale_control
                max = 100
                progress = scalePercent - 50
                setKeyProgressIncrement(5)
                contentDescription = context.getString(R.string.segment_tree_scale_title)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(bar: SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar) = Unit
                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                        if (fromUser) {
                            preferences.edit().putInt(SEGMENT_TREE_SCALE_KEY, value + 50).apply()
                            updateZoom()
                        }
                    }
                })
                zoomSlider = this
            }, LinearLayout.LayoutParams(0, dp(32), 1f))
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)))
                addView(vertical, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
            panel.contentContainer.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            KeyboardFontApplicator.applyToKeyboardViews(panel, KeyboardFontApplicator.processSnapshot)
        }
        updateZoom()
        position()
    }

    private fun updateZoom() {
        zoomView?.setZoom(scalePercent)
        zoomLabel?.text = context.getString(R.string.segment_tree_scale_value, scalePercent)
        zoomSlider?.progress = scalePercent - 50
    }

    private fun position() {
        val host = anchor ?: return
        val panel = body ?: return
        // Candidate refreshes temporarily hide the bar. Keep the existing window and bounds.
        if (!host.isShown || !host.isAttachedToWindow) return
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
            bounds = bounds?.let { it.copy(height = it.height + editExtra) }
        }
        body?.setEditing(editing)
        zoomControls?.visibility = if (editing) View.VISIBLE else View.GONE
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

    fun retain() {
        // The last complete graph remains visible, but stale choices cannot edit new input.
        acceptingSelections = false
    }

    fun stop() {
        acceptingSelections = false
        finishGesture(false)
        editing = false
        bounds = null
        anchor?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        dismiss()
        anchor = null
        body = null
        zoomView = null
        zoomControls = null
        zoomSlider = null
        zoomLabel = null
        lastPaths = emptyList()
    }

    /** Scale both drawing and hit testing, while scroll extents follow the scaled content. */
    private class ZoomLayout(context: Context, private val graph: View, percent: Int) : ViewGroup(context) {
        var factor = percent / 100f
            private set
        init {
            id = R.id.segment_tree_zoom
            addView(graph)
            graph.pivotX = 0f
            graph.pivotY = 0f
            graph.scaleX = factor
            graph.scaleY = factor
        }
        fun setZoom(percent: Int) {
            val next = percent / 100f
            if (factor == next) return
            factor = next
            graph.scaleX = factor
            graph.scaleY = factor
            requestLayout()
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val unlimited = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            graph.measure(unlimited, unlimited)
            setMeasuredDimension((graph.measuredWidth * factor).roundToInt(), (graph.measuredHeight * factor).roundToInt())
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            graph.layout(0, 0, graph.measuredWidth, graph.measuredHeight)
        }
    }

    private abstract class CandidateTreeLayout(context: Context) : ViewGroup(context) {
        var preferredCenterY = 0
            protected set
    }

    private class TreeView(
        context: Context,
        graph: SegmentationCandidateGraph,
        reading: String,
        selected: List<SegmentChoice>,
        focused: SegmentChoice?,
        textColor: Int,
        private val compact: Boolean,
        onSelect: (SegmentChoice) -> Unit,
    ) : CandidateTreeLayout(context) {
        private val dp get() = resources.displayMetrics.density
        private val root = graph.root
        private val readingLength = reading.length
        private val preferred = linkedSetOf<CandidateGraphNode>()
        private val labels = linkedMapOf<CandidateGraphNode, LinearLayout>()
        private val boxes = linkedMapOf<CandidateGraphNode, android.graphics.Rect>()
        private var endX = 0
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
                    val primary = node in preferred
                    val widthLimit = (if (primary) ((choice.end - choice.start) * 26).coerceIn(84, 220) else ((choice.end - choice.start) * 20).coerceIn(48, 132)) * dp
                    val label = object : LinearLayout(context) {
                        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                            val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
                                MeasureSpec.UNSPECIFIED -> MeasureSpec.makeMeasureSpec(widthLimit.roundToInt(), MeasureSpec.AT_MOST)
                                MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(widthMeasureSpec), widthLimit.roundToInt()), MeasureSpec.AT_MOST)
                                else -> widthMeasureSpec
                            }
                            super.onMeasure(width, heightMeasureSpec)
                        }
                    }.apply {
                        orientation = LinearLayout.VERTICAL
                        background = segmentBackground(choice.start, primary && choice == focused)
                    }
                    label.addView(TextView(context).apply {
                        text = if (compact) choice.text else android.text.SpannableString(
                            choice.text + "\n" + reading.substring(choice.start, choice.end)
                        ).apply {
                            setSpan(android.text.style.RelativeSizeSpan(if (primary) 11f / 16f else 9f / 11f),
                                choice.text.length + 1, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        textSize = if (primary) { if (compact) 14f else 16f } else { if (compact) 10f else 11f }
                        maxLines = if (compact) 1 else 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                        setPadding((8 * dp).toInt(), (5 * dp).toInt(), (8 * dp).toInt(), (5 * dp).toInt())
                        setTextColor(textColor)
                        isSelected = primary && choice == focused
                        isActivated = primary
                        contentDescription = context.getString(
                            if (choice in selected) R.string.segment_select_description else R.string.segment_replace_description,
                            choice.text, reading.substring(choice.start, choice.end))
                        isFocusable = true
                        setOnClickListener { onSelect(choice) }
                    }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                    val alternatives = graph.choices[choice.start to choice.end].orEmpty().filter { it != choice }
                    if (alternatives.isNotEmpty()) {
                        val chips = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), (4 * dp).toInt())
                        }
                        alternatives.forEach { alternative ->
                            chips.addView(TextView(context).apply {
                                text = alternative.text
                                textSize = if (compact) 10f else 11f
                                gravity = Gravity.CENTER
                                maxLines = 1
                                maxWidth = (widthLimit - 8 * dp).roundToInt()
                                minHeight = ((if (compact) 22 else 28) * dp).roundToInt()
                                ellipsize = android.text.TextUtils.TruncateAt.END
                                setTextColor(textColor)
                                setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
                                background = segmentBackground(choice.start, false)
                                contentDescription = context.getString(R.string.segment_replace_description,
                                    alternative.text, reading.substring(choice.start, choice.end))
                                isFocusable = true
                                setOnClickListener { onSelect(alternative) }
                            }, LinearLayout.LayoutParams(
                                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                                setMargins((2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), 0)
                            })
                        }
                        label.addView(HorizontalScrollView(context).apply {
                            isHorizontalScrollBarEnabled = true
                            addView(chips)
                        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                    }
                    labels[node] = label
                    addView(label)
                }
                node.children.forEach(::add)
            }
            add(root)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val unlimited = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            labels.values.forEach { it.measure(unlimited, unlimited) }
            boxes.clear()
            // All branches share one origin. Columns follow reading offsets, so a different
            // segmentation visibly spans a different range rather than an arbitrary depth.
            val gap = 20 * dp
            val originWidth = 24 * dp
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
                val width = (offsets[choice.end] - offsets[choice.start] - gap).roundToInt()
                label.measure(MeasureSpec.makeMeasureSpec(width,
                    if (node in preferred) MeasureSpec.EXACTLY else MeasureSpec.AT_MOST), unlimited)
            }
            val primaryHeight = maxOf(((if (compact) 38 else 64) * dp).roundToInt(),
                (labels.filterKeys { it in preferred }.values.maxOfOrNull { it.measuredHeight } ?: 0) + (10 * dp).roundToInt())
            val branchHeight = maxOf(((if (compact) 26 else 40) * dp).roundToInt(),
                (labels.filterKeys { it !in preferred }.values.maxOfOrNull { it.measuredHeight } ?: 0) + (8 * dp).roundToInt())
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
            val above = -firstLane * branchHeight
            preferredCenterY = above + (labels.entries.firstOrNull { it.key in preferred }?.value?.getChildAt(0)?.measuredHeight ?: primaryHeight) / 2
            labels.forEach { (node, label) ->
                val choice = node.choice!!
                val span = offsets[choice.end] - offsets[choice.start] - gap
                val x = (originWidth + offsets[choice.start] + (span - label.measuredWidth) / 2).roundToInt()
                val lane = lanes.getValue(node)
                val y = when {
                    lane < 0 -> above + lane * branchHeight + branchHeight / 2
                    lane > 0 -> above + primaryHeight + (lane - 1) * branchHeight + branchHeight / 2
                    else -> above + label.measuredHeight / 2
                }
                boxes[node] = Rect(x, y - label.measuredHeight / 2, x + label.measuredWidth, y + (label.measuredHeight + 1) / 2)
            }
            endX = (originWidth + offsets.last() + 4 * dp).roundToInt()
            setMeasuredDimension(endX + (4 * dp).roundToInt(), above + primaryHeight + lastLane * branchHeight)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            boxes.forEach { (node, box) -> labels.getValue(node).layout(box.left, box.top, box.right, box.bottom) }
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
                paint.strokeWidth = (if (value) 2.5f else 1f) * dp
            }
            fun header(node: CandidateGraphNode): Rect = boxes.getValue(node).let { box ->
                Rect(box.left, box.top, box.right, box.top + labels.getValue(node).getChildAt(0).measuredHeight)
            }
            root.children.forEach { node ->
                emphasize(node in preferred)
                connect(4 * dp, preferredCenterY.toFloat(), header(node))
            }
            boxes.forEach { (node, _) ->
                val from = header(node)
                if (node.children.isEmpty()) {
                    emphasize(node in preferred)
                    connect(from.right.toFloat(), from.exactCenterY(), Rect(endX, preferredCenterY, endX, preferredCenterY))
                }
                node.children.forEach { child ->
                    emphasize(node in preferred && child in preferred)
                    connect(from.right.toFloat(), from.exactCenterY(), header(child))
                }
            }
            paint.color = normalColor
        }
    }
    /** Each selected segment is a root; its alternatives branch underneath the same reading. */
    private class BranchesView(
        context: Context,
        branches: List<List<SegmentChoice>>,
        reading: String,
        focused: SegmentChoice?,
        textColor: Int,
        private val compact: Boolean,
        onSelect: (SegmentChoice) -> Unit,
    ) : CandidateTreeLayout(context) {
        private val dp get() = resources.displayMetrics.density
        private val columns = branches.map { choices ->
            choices.mapIndexed { index, choice ->
                TextView(context).apply {
                    text = if (compact) choice.text else choice.text + "\n" + reading.substring(choice.start, choice.end)
                    textSize = if (compact) 12f else 13f
                    maxLines = if (compact) 1 else 2
                    maxWidth = (220 * dp).toInt()
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
                    setTextColor(textColor)
                    background = segmentBackground(choice.start, choice == focused)
                    isSelected = choice == focused
                    isActivated = index == 0
                    contentDescription = context.getString(
                        if (index == 0) R.string.segment_select_description else R.string.segment_replace_description,
                        choice.text, reading.substring(choice.start, choice.end))
                    isFocusable = true
                    setOnClickListener { onSelect(choice) }
                    addView(this)
                }
            }
        }
        private val boxes = linkedMapOf<View, Rect>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor and 0x00ffffff or 0x66000000
            strokeWidth = 1.5f * dp
            style = Paint.Style.STROKE
        }

        init {
            id = R.id.segment_tree_branches
            layoutDirection = LAYOUT_DIRECTION_LTR
            setWillNotDraw(false)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val unlimited = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            columns.flatten().forEach { it.measure(unlimited, unlimited) }
            val rowHeight = maxOf(((if (compact) 32 else 62) * dp).roundToInt(),
                (columns.flatten().maxOfOrNull { it.measuredHeight } ?: 0) + (10 * dp).roundToInt())
            preferredCenterY = rowHeight / 2
            boxes.clear()
            var x = (8 * dp).roundToInt()
            val indent = (20 * dp).roundToInt()
            columns.forEach { column ->
                val width = column.maxOfOrNull { it.measuredWidth } ?: 0
                column.forEachIndexed { index, label ->
                    label.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), unlimited)
                    val left = x + if (index == 0) 0 else indent
                    val top = index * rowHeight + (rowHeight - label.measuredHeight) / 2
                    boxes[label] = Rect(left, top, left + width, top + label.measuredHeight)
                }
                x += width + indent + (24 * dp).roundToInt()
            }
            setMeasuredDimension(x, (columns.maxOfOrNull { it.size } ?: 1) * rowHeight)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            boxes.forEach { (label, box) -> label.layout(box.left, box.top, box.right, box.bottom) }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            columns.mapNotNull { it.firstOrNull() }.zipWithNext().forEach { (left, right) ->
                canvas.drawLine(left.right.toFloat(), left.y + left.height / 2f,
                    right.x, right.y + right.height / 2f, paint)
            }
            columns.forEach { column ->
                val root = column.firstOrNull() ?: return@forEach
                val stemX = root.left + 8 * dp
                column.drop(1).forEach { child ->
                    val y = child.y + child.height / 2f
                    canvas.drawPath(Path().apply {
                        moveTo(stemX, root.bottom.toFloat())
                        lineTo(stemX, y - 6 * dp)
                        quadTo(stemX, y, stemX + 6 * dp, y)
                        lineTo(child.left.toFloat(), y)
                    }, paint)
                }
            }
        }
    }

}
