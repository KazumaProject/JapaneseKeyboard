package com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.view.isVisible
import com.kazumaproject.core.domain.extensions.dpToPx
import kotlin.math.roundToInt

class KeyboardLayoutEditOverlayView(
    context: Context,
) : FrameLayout(context) {

    enum class Mode {
        Normal,
        Floating,
    }

    data class Callbacks(
        val onNormalDraftChanged: (KeyboardLayoutEditValues.Normal) -> Unit = {},
        val onNormalEditCommitted: (KeyboardLayoutEditValues.Normal) -> Unit = {},
        val onFloatingDraftChanged: (KeyboardLayoutEditValues.Floating) -> Unit = {},
        val onFloatingEditCommitted: (KeyboardLayoutEditValues.Floating) -> Unit = {},
        val onDone: () -> Unit = {},
    )

    private val borderView = View(context).apply {
        setBackgroundResource(com.kazumaproject.core.R.drawable.resize_border)
        isClickable = false
    }
    private val topHandle = createHandle(horizontal = true)
    private val bottomHandle = createHandle(horizontal = true)
    private val leftHandle = createHandle(horizontal = false)
    private val rightHandle = createHandle(horizontal = false)
    private val moveHandle = ImageButton(context).apply {
        setImageResource(com.kazumaproject.core.R.drawable.ic_drag_handle)
        background = circleDrawable()
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = "キーボード移動"
    }
    private val positionToggle = ImageButton(context).apply {
        setImageResource(com.kazumaproject.core.R.drawable.keyboard_tab_24px)
        background = circleDrawable()
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = "左右寄せ切替"
    }
    private val doneButton = ImageButton(context).apply {
        setImageResource(com.kazumaproject.core.R.drawable.baseline_check_24)
        background = circleDrawable()
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = "完了"
    }

    private var mode: Mode = Mode.Normal
    private var boundsProvider: () -> Rect = { Rect() }
    private var callbacks = Callbacks()
    private var normalValues: KeyboardLayoutEditValues.Normal? = null
    private var floatingValues: KeyboardLayoutEditValues.Floating? = null

    init {
        isClickable = false
        clipChildren = false
        clipToPadding = false
        addView(borderView)
        addView(topHandle)
        addView(bottomHandle)
        addView(leftHandle)
        addView(rightHandle)
        addView(moveHandle)
        addView(positionToggle)
        addView(doneButton)
        doneButton.setOnClickListener { callbacks.onDone() }
        positionToggle.setOnClickListener { togglePosition() }
    }

    fun configure(
        mode: Mode,
        initialValues: KeyboardLayoutEditValues,
        boundsProvider: () -> Rect,
        callbacks: Callbacks,
    ) {
        this.mode = mode
        this.boundsProvider = boundsProvider
        this.callbacks = callbacks
        when (initialValues) {
            is KeyboardLayoutEditValues.Normal -> {
                normalValues = initialValues
                floatingValues = null
            }

            is KeyboardLayoutEditValues.Floating -> {
                floatingValues = initialValues
                normalValues = null
            }
        }
        val normalMode = mode == Mode.Normal
        moveHandle.isVisible = normalMode
        positionToggle.isVisible = normalMode
        setupTouchHandlers()
        requestLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { requestLayout() }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val bounds = boundsProvider()
        if (bounds.isEmpty) return
        borderView.layout(bounds.left, bounds.top, bounds.right, bounds.bottom)
        layoutCentered(topHandle, bounds.centerX(), bounds.top)
        layoutCentered(bottomHandle, bounds.centerX(), bounds.bottom)
        layoutCentered(leftHandle, bounds.left, bounds.centerY())
        layoutCentered(rightHandle, bounds.right, bounds.centerY())
        layoutCentered(doneButton, bounds.right - dp(24), bounds.top + dp(24))
        if (mode == Mode.Normal) {
            layoutCentered(moveHandle, bounds.centerX(), bounds.centerY())
            layoutCentered(positionToggle, bounds.left + dp(24), bounds.top + dp(24))
        }
    }

    private fun layoutCentered(view: View, centerX: Int, centerY: Int) {
        if (!view.isVisible) return
        val width = view.measuredWidth.takeIf { it > 0 } ?: dp(40)
        val height = view.measuredHeight.takeIf { it > 0 } ?: dp(40)
        val left = centerX - width / 2
        val top = centerY - height / 2
        view.layout(left, top, left + width, top + height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        measureChildWithExactSize(borderView, 1, 1)
        measureChildWithExactSize(topHandle, dp(72), dp(40))
        measureChildWithExactSize(bottomHandle, dp(72), dp(40))
        measureChildWithExactSize(leftHandle, dp(40), dp(72))
        measureChildWithExactSize(rightHandle, dp(40), dp(72))
        measureChildWithExactSize(moveHandle, dp(44), dp(44))
        measureChildWithExactSize(positionToggle, dp(44), dp(44))
        measureChildWithExactSize(doneButton, dp(44), dp(44))
    }

    private fun measureChildWithExactSize(view: View, width: Int, height: Int) {
        view.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchHandlers() {
        topHandle.setOnTouchListener(edgeTouchListener(Edge.Top))
        bottomHandle.setOnTouchListener(edgeTouchListener(Edge.Bottom))
        leftHandle.setOnTouchListener(edgeTouchListener(Edge.Left))
        rightHandle.setOnTouchListener(edgeTouchListener(Edge.Right))
        moveHandle.setOnTouchListener(moveTouchListener())
    }

    private enum class Edge {
        Top,
        Bottom,
        Left,
        Right,
    }

    private fun edgeTouchListener(edge: Edge): View.OnTouchListener {
        var initialX = 0f
        var initialY = 0f
        var initialHeightDp = 0
        var initialWidthPx = 0
        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX
                    initialY = event.rawY
                    initialHeightDp = currentHeightDp()
                    initialWidthPx = boundsProvider().width().takeIf { it > 0 }
                        ?: widthPxFromPercent(currentWidthPercent())
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    applyEdgeDrag(edge, initialX, initialY, initialHeightDp, initialWidthPx, event)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    commitCurrentValues()
                    true
                }

                else -> false
            }
        }
    }

    private fun moveTouchListener(): View.OnTouchListener {
        var initialX = 0f
        var initialY = 0f
        var initialBottomDp = 0
        var initialStartDp = 0
        var initialEndDp = 0
        return View.OnTouchListener { _, event ->
            val values = normalValues ?: return@OnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = event.rawX
                    initialY = event.rawY
                    initialBottomDp = values.bottomMarginDp
                    initialStartDp = values.marginStartDp
                    initialEndDp = values.marginEndDp
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaXDp = ((event.rawX - initialX) / resources.displayMetrics.density)
                    val deltaYDp = ((event.rawY - initialY) / resources.displayMetrics.density)
                    val maxHorizontalMarginDp = maxHorizontalMarginDp()
                    val next = if (values.positionIsEnd) {
                        values.copy(
                            bottomMarginDp = (initialBottomDp - deltaYDp).roundToInt(),
                            marginEndDp = (initialEndDp - deltaXDp).roundToInt()
                                .coerceIn(0, maxHorizontalMarginDp),
                        )
                    } else {
                        values.copy(
                            bottomMarginDp = (initialBottomDp - deltaYDp).roundToInt(),
                            marginStartDp = (initialStartDp + deltaXDp).roundToInt()
                                .coerceIn(0, maxHorizontalMarginDp),
                        )
                    }
                    updateNormalDraft(KeyboardLayoutEditConstraints.normalizeNormal(next))
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    normalValues?.let(callbacks.onNormalEditCommitted)
                    true
                }

                else -> false
            }
        }
    }

    private fun applyEdgeDrag(
        edge: Edge,
        initialX: Float,
        initialY: Float,
        initialHeightDp: Int,
        initialWidthPx: Int,
        event: MotionEvent,
    ) {
        val deltaYDp = ((event.rawY - initialY) / resources.displayMetrics.density)
        when (edge) {
            Edge.Top -> updateHeight((initialHeightDp - deltaYDp).roundToInt())
            Edge.Bottom -> updateHeight((initialHeightDp + deltaYDp).roundToInt())
            Edge.Left -> {
                val nextWidthPx = initialWidthPx - (event.rawX - initialX)
                updateWidthPercent(percentFromWidthPx(nextWidthPx))
            }

            Edge.Right -> {
                val nextWidthPx = initialWidthPx + (event.rawX - initialX)
                updateWidthPercent(percentFromWidthPx(nextWidthPx))
            }
        }
    }

    private fun updateHeight(heightDp: Int) {
        when (mode) {
            Mode.Normal -> {
                val current = normalValues ?: return
                updateNormalDraft(
                    KeyboardLayoutEditConstraints.normalizeNormal(
                        current.copy(heightDp = heightDp)
                    )
                )
            }

            Mode.Floating -> {
                val current = floatingValues ?: return
                updateFloatingDraft(
                    KeyboardLayoutEditConstraints.normalizeFloating(
                        current.copy(heightDp = heightDp)
                    )
                )
            }
        }
    }

    private fun updateWidthPercent(widthPercent: Int) {
        when (mode) {
            Mode.Normal -> {
                val current = normalValues ?: return
                updateNormalDraft(
                    KeyboardLayoutEditConstraints.normalizeNormal(
                        current.copy(widthPercent = widthPercent)
                    )
                )
            }

            Mode.Floating -> {
                val current = floatingValues ?: return
                updateFloatingDraft(
                    KeyboardLayoutEditConstraints.normalizeFloating(
                        current.copy(widthPercent = widthPercent)
                    )
                )
            }
        }
    }

    private fun updateNormalDraft(values: KeyboardLayoutEditValues.Normal) {
        normalValues = values
        callbacks.onNormalDraftChanged(values)
        requestLayout()
    }

    private fun updateFloatingDraft(values: KeyboardLayoutEditValues.Floating) {
        floatingValues = values
        callbacks.onFloatingDraftChanged(values)
        requestLayout()
    }

    private fun commitCurrentValues() {
        when (mode) {
            Mode.Normal -> normalValues?.let(callbacks.onNormalEditCommitted)
            Mode.Floating -> floatingValues?.let(callbacks.onFloatingEditCommitted)
        }
    }

    private fun togglePosition() {
        val values = normalValues ?: return
        val next = values.copy(positionIsEnd = !values.positionIsEnd)
        updateNormalDraft(next)
        callbacks.onNormalEditCommitted(next)
    }

    private fun currentHeightDp(): Int {
        return when (mode) {
            Mode.Normal -> normalValues?.heightDp
            Mode.Floating -> floatingValues?.heightDp
        } ?: KeyboardLayoutEditConstraints.DefaultHeightDp
    }

    private fun currentWidthPercent(): Int {
        return when (mode) {
            Mode.Normal -> normalValues?.widthPercent
            Mode.Floating -> floatingValues?.widthPercent
        } ?: KeyboardLayoutEditConstraints.DefaultWidthPercent
    }

    private fun percentFromWidthPx(widthPx: Float): Int {
        val availableWidth = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        if (availableWidth <= 0) return currentWidthPercent()
        return ((widthPx / availableWidth) * 100f).roundToInt()
    }

    private fun widthPxFromPercent(widthPercent: Int): Int {
        val availableWidth = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return (availableWidth * (widthPercent / 100f)).roundToInt()
    }

    private fun maxHorizontalMarginDp(): Int {
        val availableWidth = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val keyboardWidth = boundsProvider().width().takeIf { it > 0 }
            ?: widthPxFromPercent(currentWidthPercent())
        return ((availableWidth - keyboardWidth).coerceAtLeast(0) / resources.displayMetrics.density)
            .roundToInt()
    }

    private fun createHandle(horizontal: Boolean): View {
        return View(context).apply {
            setBackgroundResource(com.kazumaproject.core.R.drawable.resize_handle)
            contentDescription = if (horizontal) "高さ調整" else "幅調整"
        }
    }

    private fun circleDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(0xCCFFFFFF.toInt())
            setStroke(dp(1), 0x99000000.toInt())
        }
    }

    private fun dp(value: Int): Int = context.dpToPx(value)

    companion object {
        fun defaultLayoutParams(): LayoutParams {
            return LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.BOTTOM,
            )
        }
    }
}
