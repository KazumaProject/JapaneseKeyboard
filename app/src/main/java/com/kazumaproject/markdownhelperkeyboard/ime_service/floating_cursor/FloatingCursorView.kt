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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
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
    onSelect: () -> Unit,
) : FloatingPanelFrame(context, onEdit, onHandle, onHide = onHide) {
    private val handler = Handler(Looper.getMainLooper())
    private var repeat: Runnable? = null
    private var padPointer: Int? = null
    private var selected = false
    private var selectionAvailable = true
    private var controlsEnabled = true
    private var selectionAppearance: Triple<Boolean, Boolean, Boolean>? = null
    private val selection = Button(context).apply {
        setText(R.string.floating_cursor_select)
        textSize = 14f
        isAllCaps = false
        isFocusable = false
        minHeight = 0; minimumHeight = 0
        setPadding(dp(16), 0, dp(16), 0)
        stateListAnimator = null
        val icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.floating_cursor_select)
        icon?.setBounds(0, 0, dp(20), dp(20))
        setCompoundDrawablesRelative(icon, null, null, null)
        compoundDrawablePadding = dp(8)
        setOnClickListener { if (selectionAvailable && controlsEnabled) onSelect() }
    }
    private val arrows = mutableMapOf<CursorDirection, Arrow>()
    private val pad = Pad(context)
    private val padGesture = CursorPadGesture(dp(20).toFloat(), onMove)
    private val arrowBox = FrameLayout(context)

    init {
        contentDescription = context.getString(R.string.floating_cursor_title)
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        contentContainer.addView(body, FrameLayout.LayoutParams(-1, -1))
        body.addView(selection, LinearLayout.LayoutParams(-2, dp(48)))
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        body.addView(row, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        row.addView(arrowBox, LinearLayout.LayoutParams(dp(144), dp(144)))
        CursorDirection.entries.forEach { direction ->
            val arrow = Arrow(context, direction)
            arrows[direction] = arrow
            val column = when (direction) { CursorDirection.LEFT -> 0; CursorDirection.RIGHT -> 2; else -> 1 }
            val line = when (direction) { CursorDirection.UP -> 0; CursorDirection.DOWN -> 2; else -> 1 }
            arrowBox.addView(arrow, FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                leftMargin = dp(column * 48); topMargin = dp(line * 48)
            })
            installArrow(arrow, direction)
        }
        row.addView(pad, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(12) })
        installPad()
        paintContent()
    }

    fun updateSelection(value: Boolean, available: Boolean) {
        selected = value; selectionAvailable = available
        val appearance = Triple(value, available, controlsEnabled)
        if (selectionAppearance == appearance) return
        selectionAppearance = appearance
        selection.isEnabled = available && controlsEnabled
        selection.isSelected = value
        selection.contentDescription = context.getString(when {
            !available -> R.string.floating_cursor_selection_unavailable
            value -> R.string.floating_cursor_selection_on
            else -> R.string.floating_cursor_selection_off
        })
        selection.alpha = if (selection.isEnabled) 1f else .4f
        val foreground = if (value) colors.selectionText else inkColor
        selection.setTextColor(foreground)
        selection.compoundDrawablesRelative.firstOrNull()?.setTint(foreground)
        selection.backgroundTintList = null
        selection.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(foreground, 35)),
            GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(if (value) accent else ColorUtils.blendARGB(this@FloatingCursorView.colors.background, inkColor, .05f))
            }, null)
    }

    fun setControlsEnabled(enabled: Boolean) {
        if (controlsEnabled == enabled) return
        controlsEnabled = enabled
        if (!enabled) cancelInput()
        (arrows.values + pad).forEach { it.isEnabled = enabled; it.alpha = if (enabled) 1f else .4f }
        updateSelection(selected, selectionAvailable)
    }

    fun cancelInput() {
        repeat?.let(handler::removeCallbacks)
        repeat = null
        padPointer = null
        arrows.values.forEach { it.isPressed = false }
        pad.contact = null
        pad.isPressed = false
        pad.invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installArrow(arrow: Arrow, direction: CursorDirection) {
        arrow.setOnClickListener { if (controlsEnabled) onMove(direction) }
        var owner: Int? = null
        var repeated = false
        arrow.setOnTouchListener { _, event ->
            if (!controlsEnabled) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelInput()
                    owner = event.getPointerId(0); repeated = false; arrow.isPressed = true
                    val action = object : Runnable {
                        override fun run() {
                            if (!arrow.isPressed || !controlsEnabled) return
                            repeated = true
                            onMove(direction)
                            handler.postDelayed(this, 60)
                        }
                    }
                    repeat = action
                    handler.postDelayed(action, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = owner?.let(event::findPointerIndex) ?: -1
                    if (index < 0 || event.getX(index) !in 0f..arrow.width.toFloat() ||
                        event.getY(index) !in 0f..arrow.height.toFloat()) cancelInput()
                }
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == owner) cancelInput()
                MotionEvent.ACTION_UP -> {
                    val click = arrow.isPressed && !repeated
                    cancelInput()
                    if (click) arrow.performClick()
                }
                MotionEvent.ACTION_CANCEL -> cancelInput()
            }
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installPad() {
        pad.setOnTouchListener { _, event ->
            if (!controlsEnabled) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelInput(); padPointer = event.getPointerId(0)
                    padGesture.begin(event.x, event.y); pad.contact = event.x to event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = padPointer?.let(event::findPointerIndex) ?: -1
                    if (index >= 0) {
                        padGesture.move(event.getX(index), event.getY(index))
                        pad.contact = event.getX(index) to event.getY(index)
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == padPointer) cancelInput()
                MotionEvent.ACTION_UP -> { cancelInput(); pad.performClick() }
                MotionEvent.ACTION_CANCEL -> cancelInput()
            }
            pad.invalidate()
            true
        }
    }

    private inner class Arrow(context: Context, direction: CursorDirection) : View(context) {
        private val angle = when (direction) { CursorDirection.RIGHT -> 0f; CursorDirection.DOWN -> 90f; CursorDirection.LEFT -> 180f; CursorDirection.UP -> 270f }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
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
            super.onInitializeAccessibilityNodeInfo(info); info.className = Button::class.java.name
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = inkColor
            canvas.save(); canvas.translate(width / 2f, height / 2f); canvas.rotate(angle)
            val r = dp(7).toFloat()
            canvas.drawLine(-r, 0f, r, 0f, paint)
            canvas.drawPath(Path().apply { moveTo(0f, -r); lineTo(r, 0f); lineTo(0f, r) }, paint)
            canvas.restore()
        }
    }

    private inner class Pad(context: Context) : View(context) {
        var contact: Pair<Float, Float>? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init { isClickable = true; isFocusable = false; contentDescription = context.getString(R.string.floating_cursor_trackpad) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.style = Paint.Style.FILL
            paint.color = ColorUtils.blendARGB(colors.background, inkColor, .04f)
            canvas.drawRoundRect(1f, 1f, width - 1f, height - 1f, dp(16).toFloat(), dp(16).toFloat(), paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1).toFloat()
            paint.color = ColorUtils.setAlphaComponent(inkColor, 40)
            canvas.drawRoundRect(1f, 1f, width - 1f, height - 1f, dp(16).toFloat(), dp(16).toFloat(), paint)
            paint.style = Paint.Style.FILL; paint.color = ColorUtils.setAlphaComponent(inkColor, 65)
            for (x in -1..1) for (y in -1..1) canvas.drawCircle(width / 2f + x * dp(10), height / 2f + y * dp(10), dp(1).toFloat(), paint)
            contact?.let { (x, y) ->
                paint.color = ColorUtils.setAlphaComponent(accent, 48)
                canvas.drawCircle(x, y, dp(18).toFloat(), paint)
                paint.color = accent; canvas.drawCircle(x, y, dp(3).toFloat(), paint)
            }
        }
    }

    override fun setColors(value: CandidatePanelColors) {
        if (colors == value) return
        super.setColors(value); paintContent()
    }
    private fun paintContent() {
        selectionAppearance = null
        arrowBox.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(ColorUtils.blendARGB(this@FloatingCursorView.colors.background, inkColor, .04f))
        }
        arrows.values.forEach {
            it.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 40)), null,
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
            it.invalidate()
        }
        pad.invalidate(); updateSelection(selected, selectionAvailable)
    }
    override fun setKeyboardFont(snapshot: KeyboardFontSnapshot) {
        super.setKeyboardFont(snapshot); KeyboardFontApplicator.apply(selection, snapshot)
    }
    override fun onDetachedFromWindow() { cancelInput(); super.onDetachedFromWindow() }
    companion object { const val MIN_WIDTH = 300; const val MIN_HEIGHT = 284; const val EDIT_EXTRA = 44 }
}
