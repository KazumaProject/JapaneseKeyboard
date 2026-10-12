package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_cursor

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingPanelFrame

/** Font-independent icons and an unlabelled touch surface, with accessible names. */
internal class FloatingCursorView(
    context: Context,
    onEdit: () -> Unit,
    onHandle: (MotionEvent) -> Unit,
    onHide: () -> Unit,
    private val onMove: (CursorDirection) -> Unit,
    onToggleSelection: () -> Unit,
) : FloatingPanelFrame(context, onEdit, onHandle, onHide = onHide) {
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatAction: Runnable? = null
    private var padPointer: Int? = null
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val selection = android.widget.Button(context).apply {
        text = context.getString(R.string.floating_cursor_select)
        textSize = 13f
        isAllCaps = false; isFocusable = false
        minHeight = 0; minimumHeight = 0
        stateListAnimator = null
        setPadding(dp(16), 0, dp(16), 0)
        val icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.floating_cursor_select)
        icon?.setBounds(0, 0, dp(20), dp(20))
        setCompoundDrawablesRelative(icon, null, null, null)
        compoundDrawablePadding = dp(8)
        setOnClickListener { onToggleSelection() }
    }
    private val status = View(context)
    private val arrows = linkedMapOf<CursorDirection, DirectionView>()
    private val pad = PadView(context)
    private var available = false
    private var selected = false
    private var controlsEnabled = true
    private var presentation: Triple<Boolean, Boolean, Boolean>? = null
    private val trackpadGesture = CursorTrackpadGesture(20f * resources.displayMetrics.density, onMove)

    init {
        contentDescription = context.getString(R.string.floating_cursor_title)
        contentContainer.addView(body, FrameLayout.LayoutParams(-1, -1))
        val toolbar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(selection, LinearLayout.LayoutParams(-2, dp(48)))
        toolbar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        toolbar.addView(status, LinearLayout.LayoutParams(dp(6), dp(6)).apply { rightMargin = dp(12) })
        body.addView(toolbar, LinearLayout.LayoutParams(-1, dp(48)))
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        body.addView(row, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        val dpad = FrameLayout(context).apply {
            background = surface(20, .035f)
        }
        row.addView(dpad, LinearLayout.LayoutParams(dp(144), dp(144)))
        CursorDirection.entries.forEach { direction ->
            val button = DirectionView(context, direction)
            arrows[direction] = button
            val col = when (direction) { CursorDirection.LEFT -> 0; CursorDirection.RIGHT -> 2; else -> 1 }
            val line = when (direction) { CursorDirection.UP -> 0; CursorDirection.DOWN -> 2; else -> 1 }
            dpad.addView(button, FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                leftMargin = dp(48 * col); topMargin = dp(48 * line)
            })
            installArrow(button, direction)
        }
        row.addView(pad, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(12) })
        installPad()
        paintContent()
    }

    private fun surface(radius: Int, amount: Float) = GradientDrawable().apply {
        setColor(ColorUtils.blendARGB(this@FloatingCursorView.colors.background, inkColor, amount))
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), ColorUtils.setAlphaComponent(inkColor, 24))
    }

    fun updateSelection(selected: Boolean, available: Boolean) {
        this.selected = selected; this.available = available
        val next = Triple(selected, available, editing || !controlsEnabled)
        if (presentation == next) return
        presentation = next
        selection.isEnabled = available && !next.third
        selection.isSelected = selected
        selection.contentDescription = context.getString(when {
            !available -> R.string.floating_cursor_selection_unavailable
            selected -> R.string.floating_cursor_selection_on
            else -> R.string.floating_cursor_selection_off
        })
        selection.alpha = if (available && !next.third) 1f else .4f
        val foreground = if (selected) colors.selectionText else inkColor
        selection.setTextColor(foreground)
        selection.compoundDrawablesRelative.firstOrNull()?.setTint(foreground)
        val shape = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(if (selected) accent else ColorUtils.blendARGB(this@FloatingCursorView.colors.background, inkColor, .04f))
            setStroke(dp(1), if (selected) accent else ColorUtils.setAlphaComponent(inkColor, 40))
        }
        selection.backgroundTintList = null
        selection.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(foreground, 35)), shape, null)
        status.background = GradientDrawable().apply {
            this.shape = GradientDrawable.OVAL
            setColor(if (selected && available) accent else ColorUtils.setAlphaComponent(inkColor, 50))
        }
    }

    fun setControlsEnabled(enabled: Boolean) {
        if (controlsEnabled == enabled) return
        controlsEnabled = enabled
        if (!enabled) cancelInput()
        arrows.values.forEach { it.isEnabled = enabled; it.alpha = if (enabled) 1f else .4f }
        pad.isEnabled = enabled; pad.alpha = if (enabled) 1f else .4f
        updateSelection(selected, available)
    }

    fun cancelInput() {
        repeatAction?.let(repeatHandler::removeCallbacks)
        repeatAction = null; padPointer = null
        arrows.values.forEach { it.isPressed = false }
        pad.contact = null; pad.isPressed = false; pad.invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installArrow(button: DirectionView, direction: CursorDirection) {
        button.setOnClickListener { if (button.isEnabled) onMove(direction) }
        var repeated = false
        var owner: Int? = null
        button.setOnTouchListener { _, event ->
            if (!button.isEnabled) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelInput(); repeated = false; owner = event.getPointerId(0); button.isPressed = true
                    val action = object : Runnable {
                        override fun run() {
                            if (!button.isEnabled || !button.isPressed) return
                            repeated = true; onMove(direction); repeatHandler.postDelayed(this, 60L)
                        }
                    }
                    repeatAction = action
                    repeatHandler.postDelayed(action, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = owner?.let(event::findPointerIndex) ?: -1
                    if (index < 0 || event.getX(index) !in 0f..button.width.toFloat() || event.getY(index) !in 0f..button.height.toFloat()) cancelInput()
                }
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == owner) { owner = null; cancelInput() }
                MotionEvent.ACTION_UP -> {
                    val clicked = button.isPressed && !repeated
                    cancelInput(); if (clicked) button.performClick()
                }
                MotionEvent.ACTION_CANCEL -> cancelInput()
            }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installPad() {
        pad.setOnTouchListener { _, event ->
            if (!pad.isEnabled) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelInput(); padPointer = event.getPointerId(0)
                    trackpadGesture.begin(event.x, event.y); pad.contact = event.x to event.y
                    pad.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = padPointer?.let(event::findPointerIndex) ?: -1
                    if (index >= 0) {
                        pad.contact = event.getX(index) to event.getY(index)
                        trackpadGesture.move(event.getX(index), event.getY(index))
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == padPointer) cancelInput()
                MotionEvent.ACTION_UP -> { cancelInput(); pad.performClick() }
                MotionEvent.ACTION_CANCEL -> cancelInput()
            }
            pad.invalidate(); true
        }
    }

    private inner class DirectionView(context: Context, direction: CursorDirection) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val angle = when (direction) { CursorDirection.RIGHT -> 0f; CursorDirection.DOWN -> 90f; CursorDirection.LEFT -> 180f; CursorDirection.UP -> 270f }
        init {
            isClickable = true; isFocusable = false
            contentDescription = context.getString(when (direction) {
                CursorDirection.LEFT -> R.string.floating_cursor_left
                CursorDirection.RIGHT -> R.string.floating_cursor_right
                CursorDirection.UP -> R.string.floating_cursor_up
                CursorDirection.DOWN -> R.string.floating_cursor_down
            })
        }
        override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = android.widget.Button::class.java.name
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = inkColor
            canvas.save()
            canvas.translate(width / 2f, height / 2f); canvas.rotate(angle)
            val length = dp(7).toFloat()
            canvas.drawLine(-length, 0f, length, 0f, paint)
            canvas.drawPath(Path().apply { moveTo(0f, -length); lineTo(length, 0f); lineTo(0f, length) }, paint)
            canvas.restore()
        }
    }

    private inner class PadView(context: Context) : View(context) {
        var contact: Pair<Float, Float>? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init { isClickable = true; isFocusable = false; contentDescription = context.getString(R.string.floating_cursor_trackpad) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = dp(1).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = ColorUtils.blendARGB(colors.background, inkColor, .035f)
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, dp(20).toFloat(), dp(20).toFloat(), paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1).toFloat()
            paint.color = ColorUtils.setAlphaComponent(inkColor, 40)
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, dp(20).toFloat(), dp(20).toFloat(), paint)
            paint.style = Paint.Style.FILL; paint.color = ColorUtils.setAlphaComponent(inkColor, 65)
            for (x in -1..1) for (y in -1..1) canvas.drawCircle(width / 2f + x * dp(10), height / 2f + y * dp(10), dp(1).toFloat(), paint)
            paint.color = ColorUtils.setAlphaComponent(inkColor, 40)
            canvas.drawRoundRect(width / 2f - dp(12), height - dp(14).toFloat(), width / 2f + dp(12), height - dp(12).toFloat(), dp(1).toFloat(), dp(1).toFloat(), paint)
            contact?.let { (x, y) ->
                paint.color = ColorUtils.setAlphaComponent(accent, 48)
                canvas.drawCircle(x.coerceIn(0f, width.toFloat()), y.coerceIn(0f, height.toFloat()), dp(18).toFloat(), paint)
                paint.color = accent
                canvas.drawCircle(x.coerceIn(0f, width.toFloat()), y.coerceIn(0f, height.toFloat()), dp(3).toFloat(), paint)
            }
        }
    }

    override fun setColors(value: CandidatePanelColors) {
        if (colors == value) return
        super.setColors(value); paintContent()
    }
    private fun paintContent() {
        arrows.values.forEach { button ->
            button.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 45)), null,
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
            button.invalidate()
        }
        (arrows.values.firstOrNull()?.parent as? View)?.background = surface(20, .035f)
        pad.invalidate(); presentation = null; updateSelection(selected, available)
    }
    override fun setKeyboardFont(snapshot: KeyboardFontSnapshot) {
        super.setKeyboardFont(snapshot); KeyboardFontApplicator.apply(selection, snapshot)
    }
    override fun onDetachedFromWindow() { cancelInput(); super.onDetachedFromWindow() }

    companion object {
        const val MIN_WIDTH_DP = 300
        const val MIN_HEIGHT_DP = 284
        const val EDIT_EXTRA_DP = 44
    }
}
