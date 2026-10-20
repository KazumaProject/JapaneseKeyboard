package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingWindowCoordinates
import kotlin.math.roundToInt

/** An IME-owned, non-focusable window anchored above the active candidate bar. */
internal class SegmentCandidateTreeWindow(
    private val context: Context,
    private val onSelect: (String, SegmentChoice) -> Unit,
    private val onClose: () -> Unit,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val coordinates = FloatingWindowCoordinates(manager)
    private var anchor: View? = null
    private var body: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastPaths = emptyList<List<SegmentChoice>>()
    private var lastSelected = emptyList<SegmentChoice>()
    private var lastReading = ""
    private var lastCompact = false
    private var lastColors = 0 to 0
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { position() }
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    fun show(view: View, reading: String, paths: List<List<SegmentChoice>>, selected: List<SegmentChoice>, background: Int, textColor: Int) {
        if (anchor !== view) {
            stop()
            anchor = view
            view.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
        val location = IntArray(2).also(view::getLocationOnScreen)
        val compact = location[1] - coordinates.safeArea(view).top < dp(160)
        if (body == null || compact != lastCompact || paths != lastPaths || selected != lastSelected || reading != lastReading || lastColors != (background to textColor)) {
            lastCompact = compact
            lastPaths = paths; lastSelected = selected; lastReading = reading; lastColors = background to textColor
            val panel = body ?: LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                elevation = dp(8).toFloat()
                setPadding(dp(8), dp(4), dp(8), dp(8))
            }.also { body = it }
            panel.removeAllViews()
            panel.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(background)
                setStroke(dp(1), textColor and 0x00ffffff or 0x55000000)
            }
            val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(context).apply {
                text = context.getString(R.string.segment_tree_title)
                setTextColor(textColor)
                textSize = if (compact) 12f else 13f
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, dp(if (compact) 28 else 44), 1f))
            header.addView(TextView(context).apply {
                text = "×"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(textColor)
                contentDescription = context.getString(R.string.segment_tree_close)
                isFocusable = true
                setOnClickListener { onClose() }
            }, LinearLayout.LayoutParams(dp(48), dp(if (compact) 28 else 44)))
            panel.addView(header)
            val tree = TreeView(context, buildCandidateTree(paths), reading, selected, textColor, compact) { onSelect(reading, it) }
            val horizontal = HorizontalScrollView(context).apply { addView(tree) }
            panel.addView(ScrollView(context).apply { addView(horizontal) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            KeyboardFontApplicator.applyToKeyboardViews(panel, KeyboardFontApplicator.processSnapshot)
        }
        position()
    }

    private fun position() {
        val host = anchor ?: return
        val panel = body ?: return
        if (!host.isShown || !host.isAttachedToWindow) { dismiss(); return }
        val safe = coordinates.safeArea(host)
        val location = IntArray(2).also(host::getLocationOnScreen)
        val availableHeight = location[1] - dp(6) - safe.top
        if (availableHeight < dp(72)) { dismiss(); return }
        val width = maxOf(host.width, dp(320)).coerceAtMost(safe.width() - dp(16))
        if (width <= 0) { dismiss(); return }
        val height = minOf(dp(300), availableHeight)
        val x = location[0].coerceIn(safe.left + dp(8), (safe.right - width - dp(8)).coerceAtLeast(safe.left + dp(8)))
        val y = location[1] - height - dp(6)
        val previous = params
        if (previous != null && previous.width == width && previous.height == height) {
            val next = WindowManager.LayoutParams().apply { copyFrom(previous) }
            coordinates.position(next, x, y)
            if (next.x == previous.x && next.y == previous.y) return
        }
        val next = previous ?: WindowManager.LayoutParams(width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT).apply {
            token = host.windowToken
            gravity = Gravity.TOP or Gravity.LEFT
            setTitle("Segment candidate tree")
            coordinates.configure(this)
        }
        next.width = width; next.height = height
        coordinates.position(next, x, y)
        if (previous == null) {
            coordinates.observeLegacyOrigin(panel, { params }, ::position)
            try { manager.addView(panel, next) } catch (_: WindowManager.BadTokenException) { return }
            catch (_: IllegalArgumentException) { return }
        } else try { manager.updateViewLayout(panel, next) } catch (_: IllegalArgumentException) { dismiss(); return }
        params = next
    }

    private fun dismiss() {
        if (params != null) body?.let { manager.removeViewImmediate(it) }
        params = null
    }

    fun stop() {
        anchor?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        dismiss()
        anchor = null
        body = null
        lastPaths = emptyList()
    }

    private class TreeView(
        context: Context,
        private val root: CandidateTreeNode,
        reading: String,
        selected: List<SegmentChoice>,
        textColor: Int,
        private val compact: Boolean,
        onSelect: (SegmentChoice) -> Unit,
    ) : ViewGroup(context) {
        private val dp get() = resources.displayMetrics.density
        private val labels = linkedMapOf<CandidateTreeNode, TextView>()
        private val boxes = linkedMapOf<CandidateTreeNode, android.graphics.Rect>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor and 0x00ffffff or 0x66000000
            style = Paint.Style.STROKE
            strokeWidth = dp * 1.5f
        }

        init {
            setWillNotDraw(false)
            layoutDirection = LAYOUT_DIRECTION_LTR
            fun add(node: CandidateTreeNode) {
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
                        background = segmentBackground(choice.start, choice in selected)
                        isSelected = choice in selected
                        contentDescription = context.getString(R.string.segment_replace_description, choice.text, reading.substring(choice.start, choice.end))
                        isFocusable = true
                        setOnClickListener { onSelect(choice) }
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
            var row = 0
            val rowHeight = maxOf(((if (compact) 32 else 62) * dp).toInt(), (labels.values.maxOfOrNull { it.measuredHeight } ?: 0) + (10 * dp).toInt())
            fun place(node: CandidateTreeNode, x: Int): Int {
                val label = labels.getValue(node)
                val childX = x + label.measuredWidth + (28 * dp).toInt()
                val ys = node.children.map { place(it, childX) }
                val y = if (ys.isEmpty()) row++ * rowHeight + rowHeight / 2 else ys.first()
                boxes[node] = android.graphics.Rect(x, y - label.measuredHeight / 2, x + label.measuredWidth, y + (label.measuredHeight + 1) / 2)
                return y
            }
            root.children.forEach { place(it, (12 * dp).toInt()) }
            setMeasuredDimension((boxes.values.maxOfOrNull { it.right } ?: 0) + (12 * dp).toInt(), maxOf(row * rowHeight, rowHeight))
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            boxes.forEach { (node, box) -> labels.getValue(node).layout(box.left, box.top, box.right, box.bottom) }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            boxes.forEach { (node, from) ->
                node.children.forEach { child ->
                    val to = boxes.getValue(child)
                    val path = Path().apply {
                        moveTo(from.right.toFloat(), from.exactCenterY())
                        val mid = (from.right + to.left) / 2f
                        cubicTo(mid, from.exactCenterY(), mid, to.exactCenterY(), to.left.toFloat(), to.exactCenterY())
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
    }
}
