package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingPanelFrame

internal class FloatingCursorView(
    context: Context,
    onEdit: () -> Unit,
    onHandle: (MotionEvent) -> Unit,
    onHide: () -> Unit,
    private val onMove: (CursorDirection) -> Unit,
    onToggleSelection: () -> Unit,
) : FloatingPanelFrame(context, onEdit, onHandle, context.getString(R.string.floating_cursor_title), onHide) {
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatAction: Runnable? = null
    private var padPointer: Int? = null
    private var selectionPresentation: Triple<Boolean, Boolean, Boolean>? = null
    private val trackpadGesture = CursorTrackpadGesture(20f * resources.displayMetrics.density, onMove)
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val selectionButton = Button(context).apply {
        isFocusable = false; isAllCaps = false
        setOnClickListener { onToggleSelection() }
    }
    private val arrows = linkedMapOf<CursorDirection, Button>()
    private val trackpad = TextView(context).apply {
        text = context.getString(R.string.floating_cursor_trackpad)
        contentDescription = text
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = false
    }

    init {
        contentDescription = context.getString(R.string.floating_cursor_title)
        contentContainer.addView(body, FrameLayout.LayoutParams(-1, -1))
        body.addView(selectionButton, LinearLayout.LayoutParams(-1, dp(48)))
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        body.addView(row, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        val directionPad = FrameLayout(context)
        row.addView(directionPad, LinearLayout.LayoutParams(dp(144), dp(144)))
        CursorDirection.entries.forEach { direction ->
            val button = directionButton(direction)
            arrows[direction] = button
            val col = when (direction) { CursorDirection.LEFT -> 0; CursorDirection.RIGHT -> 2; else -> 1 }
            val line = when (direction) { CursorDirection.UP -> 0; CursorDirection.DOWN -> 2; else -> 1 }
            directionPad.addView(button, FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                leftMargin = dp(48 * col); topMargin = dp(48 * line)
            })
        }
        row.addView(trackpad, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(8) })
        installTrackpadTouch()
        paintContent()
    }

    fun updateSelection(selected: Boolean, available: Boolean) {
        val state = Triple(selected, available, editing)
        if (selectionPresentation == state) return
        selectionPresentation = state
        selectionButton.isEnabled = available && !editing
        selectionButton.isSelected = selected
        selectionButton.text = context.getString(when {
            !available -> R.string.floating_cursor_selection_unavailable
            selected -> R.string.floating_cursor_selection_on
            else -> R.string.floating_cursor_selection_off
        })
        selectionButton.alpha = if (available) 1f else .5f
        selectionButton.setTextColor(if (selected) colors.selectionText else inkColor)
        selectionButton.backgroundTintList = ColorStateList.valueOf(if (selected) accent else colors.pressed)
    }

    fun setControlsEnabled(enabled: Boolean) {
        if (arrows.values.all { it.isEnabled == enabled } && trackpad.isEnabled == enabled) return
        if (!enabled) cancelInput()
        arrows.values.forEach { it.isEnabled = enabled }
        trackpad.isEnabled = enabled
    }

    fun cancelInput() {
        repeatAction?.let(repeatHandler::removeCallbacks)
        repeatAction = null
        padPointer = null
        arrows.values.forEach { it.isPressed = false }
        trackpad.isPressed = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun directionButton(direction: CursorDirection): Button = Button(context).apply {
        isFocusable = false; isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(0, 0, 0, 0)
        text = when (direction) {
            CursorDirection.LEFT -> "←"; CursorDirection.RIGHT -> "→"
            CursorDirection.UP -> "↑"; CursorDirection.DOWN -> "↓"
        }
        contentDescription = context.getString(when (direction) {
            CursorDirection.LEFT -> R.string.floating_cursor_left
            CursorDirection.RIGHT -> R.string.floating_cursor_right
            CursorDirection.UP -> R.string.floating_cursor_up
            CursorDirection.DOWN -> R.string.floating_cursor_down
        })
        setOnClickListener { if (isEnabled) onMove(direction) }
        var repeated = false
        var pointerId: Int? = null
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelInput(); repeated = false; isPressed = true
                    pointerId = event.getPointerId(0)
                    val action = object : Runnable {
                        override fun run() {
                            if (!isEnabled || !isPressed) return
                            repeated = true
                            onMove(direction)
                            repeatHandler.postDelayed(this, 60L)
                        }
                    }
                    repeatAction = action
                    repeatHandler.postDelayed(action, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = pointerId?.let(event::findPointerIndex) ?: -1
                    if (index < 0 || event.getX(index) !in 0f..width.toFloat() || event.getY(index) !in 0f..height.toFloat()) cancelInput()
                }
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointerId) {
                    pointerId = null; cancelInput()
                }
                MotionEvent.ACTION_UP -> {
                    val clicked = isPressed && !repeated
                    cancelInput()
                    if (clicked) performClick()
                }
                MotionEvent.ACTION_CANCEL -> cancelInput()
            }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installTrackpadTouch() {
        trackpad.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelInput(); padPointer = event.getPointerId(0)
                    trackpadGesture.begin(event.x, event.y); trackpad.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = padPointer?.let(event::findPointerIndex) ?: -1
                    if (index >= 0) trackpadGesture.move(event.getX(index), event.getY(index))
                }
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == padPointer) cancelInput()
                MotionEvent.ACTION_UP -> { cancelInput(); trackpad.performClick() }
                MotionEvent.ACTION_CANCEL -> cancelInput()
            }
            true
        }
    }

    override fun setColors(value: CandidatePanelColors) {
        if (colors == value) return
        super.setColors(value)
        paintContent()
        selectionPresentation?.let { state ->
            selectionPresentation = null
            updateSelection(state.first, state.second)
        }
    }
    private fun paintContent() {
        selectionButton.setTextColor(inkColor)
        arrows.values.forEach { it.setTextColor(inkColor); it.backgroundTintList = ColorStateList.valueOf(colors.pressed) }
        trackpad.setTextColor(inkColor)
        trackpad.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(this@FloatingCursorView.colors.pressed); cornerRadius = dp(12).toFloat()
        }
    }
    override fun setKeyboardFont(snapshot: KeyboardFontSnapshot) {
        super.setKeyboardFont(snapshot)
        KeyboardFontApplicator.applyToKeyboardViews(body, snapshot)
    }
    override fun onDetachedFromWindow() { cancelInput(); super.onDetachedFromWindow() }

    companion object {
        const val MIN_WIDTH_DP = 296
        const val MIN_HEIGHT_DP = 284
        const val EDIT_EXTRA_DP = 44
    }
}
