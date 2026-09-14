package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.kazumaproject.markdownhelperkeyboard.R
import kotlin.math.roundToInt

/** Shared chrome for IME-attached floating panels. Content and persistence belong to callers. */
internal open class FloatingPanelFrame(
    context: Context,
    onEdit: () -> Unit,
    private val onHandleEvent: (MotionEvent) -> Unit = {},
) : FrameLayout(context) {
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    protected var colors = CandidatePanelColors.resolve(context)
        private set
    protected val inkColor get() = colors.text
    protected val accent get() = colors.selection
    val contentContainer = FrameLayout(context)
    val footerContainer = FrameLayout(context)
    private val header = LinearLayout(context).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
    private val moveGrip = HandleView(context, GuideHandle.MOVE)
    private val editButton = ImageButton(context).apply {
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isFocusable = false
        setOnClickListener { onEdit() }
    }
    private val edges = listOf(GuideHandle.LEFT, GuideHandle.TOP, GuideHandle.RIGHT, GuideHandle.BOTTOM)
        .associateWith { HandleView(context, it) }
    private var handlingGesture = false
    private var headerVisible = true
    private var footerEnabled = false
    var editing = false
        private set

    val contentInsets: Rect get() = Rect(dp(if (editing) 24 else 8), dp(if (headerVisible) (if (editing) 72 else 56) else (if (editing) 24 else 8)),
        dp(if (editing) 24 else 8), dp(MOVE_BAND_DP + if (editing) (if (footerEnabled) 104 else 32) else 4))

    init {
        isClickable = true
        elevation = dp(8).toFloat()
        addView(contentContainer)
        header.addView(editButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(header)
        addView(footerContainer)
        addView(moveGrip, LayoutParams(-1, dp(MOVE_BAND_DP), Gravity.BOTTOM))
        edges.values.forEach(::addView)
        renderChrome()
    }

    fun setEditing(value: Boolean) {
        if (editing == value) return
        editing = value
        handlingGesture = false
        moveGrip.isPressed = false
        renderChrome()
    }

    fun setHeaderVisible(value: Boolean) {
        if (headerVisible == value) return
        headerVisible = value
        renderChrome()
    }

    fun setFooterEnabled(value: Boolean) {
        footerEnabled = value
        renderChrome()
    }

    open fun setColors(value: CandidatePanelColors) {
        if (colors == value) return
        colors = value
        renderChrome()
    }

    fun setEditAvailable(available: Boolean) {
        editButton.isEnabled = available
        editButton.alpha = if (available) 1f else .4f
    }

    private fun renderChrome() {
        header.visibility = if (headerVisible) VISIBLE else GONE
        moveGrip.isEnabled = !editing
        moveGrip.alpha = if (editing) .35f else 1f
        footerContainer.visibility = if (editing && footerEnabled) VISIBLE else GONE
        edges.values.forEach { it.visibility = if (editing) VISIBLE else GONE; it.invalidate() }
        editButton.setImageResource(if (editing) com.kazumaproject.core.R.drawable.baseline_check_24 else R.drawable.composing_guide_edit)
        editButton.contentDescription = context.getString(if (editing) R.string.composing_guide_done else R.string.composing_guide_edit)
        editButton.imageTintList = ColorStateList.valueOf(if (editing) accent else colors.icon)
        editButton.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.pressed, 80)), null,
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
        background = GradientDrawable().apply {
            setColor(this@FloatingPanelFrame.colors.background)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(if (editing) 2 else 1), if (editing) accent else ColorUtils.setAlphaComponent(inkColor, 45))
        }
        header.layoutParams = LayoutParams(-1, dp(48)).apply {
            leftMargin = dp(if (editing) 24 else 8); rightMargin = leftMargin
            topMargin = dp(if (editing) 20 else 4)
        }
        val insets = contentInsets
        contentContainer.layoutParams = LayoutParams(-1, -1).apply {
            leftMargin = insets.left; rightMargin = insets.right
            topMargin = insets.top; bottomMargin = insets.bottom
        }
        footerContainer.layoutParams = LayoutParams(-1, dp(72), Gravity.BOTTOM).apply {
            leftMargin = dp(24); rightMargin = dp(24); bottomMargin = dp(MOVE_BAND_DP + 28)
        }
        edges.forEach { (edge, view) ->
            val horizontal = edge == GuideHandle.TOP || edge == GuideHandle.BOTTOM
            view.layoutParams = LayoutParams(dp(if (horizontal) 48 else 24), dp(if (horizontal) 24 else 48), when (edge) {
                GuideHandle.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                GuideHandle.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                GuideHandle.LEFT -> Gravity.LEFT or Gravity.CENTER_VERTICAL
                else -> Gravity.RIGHT or Gravity.CENTER_VERTICAL
            }).apply {
                if (edge == GuideHandle.BOTTOM) bottomMargin = dp(MOVE_BAND_DP)
                if (edge == GuideHandle.LEFT || edge == GuideHandle.RIGHT) bottomMargin = dp(MOVE_BAND_DP / 2)
            }
        }
        moveGrip.invalidate()
    }

    fun handleAt(x: Float, y: Float): GuideHandle? {
        val moveRect = Rect().also(moveGrip::getHitRect)
        if (moveRect.contains(x.toInt(), y.toInt())) return if (editing) null else GuideHandle.MOVE
        if (!editing) return null
        edges.forEach { (edge, view) ->
            val rect = Rect().also(view::getHitRect)
            // Broaden the narrow edge's hit area inward; visual grips remain thin.
            if (edge == GuideHandle.LEFT || edge == GuideHandle.RIGHT) rect.inset(-dp(12), 0)
            if (rect.contains(x.toInt(), y.toInt())) return edge
        }
        return null
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val handle = handleAt(event.x, event.y)
            handlingGesture = handle != null
            moveGrip.isPressed = handle == GuideHandle.MOVE
        }
        return handlingGesture || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!handlingGesture) return super.onTouchEvent(event)
        dispatchHandleEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            handlingGesture = false
            moveGrip.isPressed = false
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        }
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Older accessibility services retain descendant screen bounds when only the window moves.
        addOnLayoutChangeListener(positionAccessibilityListener)
    }

    override fun onDetachedFromWindow() {
        removeOnLayoutChangeListener(positionAccessibilityListener)
        super.onDetachedFromWindow()
    }

    private val positionAccessibilityListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        notifyAccessibilityBoundsChanged()
    }

    private fun notifyAccessibilityBoundsChanged() {
        if (!context.getSystemService(android.view.accessibility.AccessibilityManager::class.java).isEnabled) return
        val event = android.view.accessibility.AccessibilityEvent.obtain(
            android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.contentChangeTypes = android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        sendAccessibilityEventUnchecked(event)
    }

    fun notifyWindowPositionChanged() {
        // Deliver after the window traversal, when descendant screen coordinates are current.
        postOnAnimation { post { if (isAttachedToWindow) notifyAccessibilityBoundsChanged() } }
    }

    protected open fun dispatchHandleEvent(event: MotionEvent) = onHandleEvent(event)

    override fun performClick(): Boolean = super.performClick()

    private inner class HandleView(context: Context, private val handle: GuideHandle) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        init {
            contentDescription = context.getString(when (handle) {
                GuideHandle.MOVE -> R.string.composing_guide_move
                GuideHandle.LEFT -> R.string.composing_guide_resize_left
                GuideHandle.TOP -> R.string.composing_guide_resize_top
                GuideHandle.RIGHT -> R.string.composing_guide_resize_right
                GuideHandle.BOTTOM -> R.string.composing_guide_resize_bottom
            })
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }

        override fun onDraw(canvas: Canvas) {
            paint.color = accent
            if (handle == GuideHandle.MOVE) {
                paint.color = ColorUtils.setAlphaComponent(accent, if (isPressed) 38 else 16)
                val radius = dp(16).toFloat()
                val path = android.graphics.Path().apply {
                    addRoundRect(0f, 0f, width.toFloat(), height.toFloat(),
                        floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius), android.graphics.Path.Direction.CW)
                }
                canvas.drawPath(path, paint)
                paint.color = ColorUtils.setAlphaComponent(inkColor, 35)
                canvas.drawRect(0f, 0f, width.toFloat(), dp(1).toFloat(), paint)
                paint.color = accent
                val center = width / 2f
                for (column in -1..1) for (row in -1..1 step 2) {
                    canvas.drawCircle(center + column * dp(8), height / 2f + row * dp(4), dp(2).toFloat(), paint)
                }
            } else {
                val horizontal = handle == GuideHandle.TOP || handle == GuideHandle.BOTTOM
                val halfWidth = dp(if (horizontal) 14 else 2).toFloat()
                val halfHeight = dp(if (horizontal) 2 else 14).toFloat()
                canvas.drawRoundRect(width / 2f - halfWidth, height / 2f - halfHeight,
                    width / 2f + halfWidth, height / 2f + halfHeight, dp(3).toFloat(), dp(3).toFloat(), paint)
            }
        }
    }

    companion object { const val MOVE_BAND_DP = 24 }
}
