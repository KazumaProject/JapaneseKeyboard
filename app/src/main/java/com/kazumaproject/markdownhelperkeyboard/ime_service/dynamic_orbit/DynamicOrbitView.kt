package com.kazumaproject.markdownhelperkeyboard.ime_service.dynamic_orbit

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.*
import android.widget.*
import com.kazumaproject.markdownhelperkeyboard.R
import kotlin.math.roundToInt

internal enum class OrbitCommand { JAPANESE, ENGLISH, NUMBER, SYMBOLS, NEXT_KEYBOARD, IME_PICKER, LEFT, RIGHT, UP, DOWN, SPACE, DELETE, ENTER }

class DynamicOrbitView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    internal var onLetter: (Char) -> Unit = {}
    internal var onCommand: (OrbitCommand) -> Unit = {}
    internal var windowAnchor: () -> View? = { this }
    private val density = resources.displayMetrics.density
    private val gesture = OrbitGesture()
    private var activePointer = -1
    private var originX = 0f
    private var originY = 0f
    private var colors = OrbitColors(Color.WHITE, Color.LTGRAY, Color.BLACK, Color.BLUE)
    private var appliedColors: OrbitColors? = null
    private val guide = OrbitGuideWindow(this) { windowAnchor() }
    private var menu: PopupWindow? = null
    private var guideVisible = false
    private val area = InputArea(context)
    private val bar = LinearLayout(context)
    private val buttons = mutableListOf<Button>()
    private val enter: Button
    private val space: Button
    init {
        orientation = VERTICAL
        isMotionEventSplittingEnabled = false
        area.id = R.id.orbit_touch_area
        area.contentDescription = context.getString(R.string.orbit_start_hint)
        addView(area, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        bar.orientation = HORIZONTAL
        bar.isMotionEventSplittingEnabled = false
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(OrbitGeometry.COMMAND_HEIGHT)))
        button(R.id.orbit_mode, context.getString(R.string.orbit_mode)) {
            popup(listOf(R.string.orbit_japanese to OrbitCommand.JAPANESE, R.string.orbit_english to OrbitCommand.ENGLISH,
                R.string.orbit_number to OrbitCommand.NUMBER, R.string.orbit_symbols to OrbitCommand.SYMBOLS,
                R.string.orbit_next_keyboard to OrbitCommand.NEXT_KEYBOARD, R.string.orbit_ime_picker to OrbitCommand.IME_PICKER))
        }
        button(R.id.orbit_cursor, "↔") {
            popup(listOf(R.string.orbit_left to OrbitCommand.LEFT, R.string.orbit_right to OrbitCommand.RIGHT,
                R.string.orbit_up to OrbitCommand.UP, R.string.orbit_down to OrbitCommand.DOWN))
        }.contentDescription = context.getString(R.string.orbit_cursor)
        space = button(R.id.orbit_space, context.getString(R.string.orbit_space)) { onCommand(OrbitCommand.SPACE) }
        button(R.id.orbit_delete, "⌫") { onCommand(OrbitCommand.DELETE) }.contentDescription = context.getString(R.string.orbit_delete)
        enter = button(R.id.orbit_enter, "↵") { onCommand(OrbitCommand.ENTER) }
    }

    private fun dp(value: Int) = (value * density).roundToInt()
    private fun button(id: Int, title: String, action: () -> Unit): Button = Button(context).also { button ->
        button.id = id; button.text = title; button.textSize = 12f
        button.isAllCaps = false; button.minWidth = 0; button.minimumWidth = 0
        button.setPadding(0, 0, 0, 0)
        button.setOnClickListener { if (activePointer == -1) action() }
        buttons += button
        bar.addView(button, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    internal fun configure(colors: OrbitColors, enterLabel: String, converting: Boolean) {
        this.colors = colors
        setBackgroundColor(Color.TRANSPARENT)
        if (appliedColors != colors) buttons.forEach {
            it.setTextColor(colors.text)
            it.background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(colors.accent),
                GradientDrawable().apply { setColor(colors.key); cornerRadius = dp(8).toFloat(); setStroke(dp(1), colors.background) }, null)
        }
        appliedColors = colors
        enter.text = enterLabel.ifBlank { "↵" }; enter.contentDescription = enterLabel
        space.text = context.getString(if (converting) R.string.orbit_convert else R.string.orbit_space)
        area.invalidate()
    }

    private fun popup(items: List<Pair<Int, OrbitCommand>>) {
        menu?.dismiss()
        val content = LinearLayout(context).apply { orientation = VERTICAL; setBackgroundColor(colors.background) }
        val window = PopupWindow(content, dp(200), LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(GradientDrawable().apply { setColor(this@DynamicOrbitView.colors.background); cornerRadius = dp(12).toFloat() })
            elevation = dp(8).toFloat()
        }
        items.forEach { (title, action) ->
            content.addView(Button(context).apply {
                text = context.getString(title); isAllCaps = false; setTextColor(colors.text)
                backgroundTintList = android.content.res.ColorStateList.valueOf(colors.key)
                setOnClickListener { window.dismiss(); onCommand(action) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        }
        menu = window
        val host = windowAnchor()?.takeIf { it.isAttachedToWindow } ?: return
        try {
            window.showAtLocation(host, Gravity.CENTER, 0, 0)
        } catch (_: WindowManager.BadTokenException) {
            window.dismiss(); menu = null
        }
    }

    fun cancelStroke() {
        activePointer = -1; gesture.cancel(); guide.hide(); guideVisible = false; menu?.dismiss(); menu = null
        area.invalidate()
    }
    override fun onDetachedFromWindow() { cancelStroke(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // This callback can run during View construction, before our fields are initialized.
        if (visibility != VISIBLE && childCount > 0) cancelStroke()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw != 0 && (w != oldw || h != oldh)) cancelStroke()
    }

    private inner class InputArea(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val startBounds = RectF()
        private val screenLocation = IntArray(2)
        private fun updateBounds() {
            val inset = OrbitGeometry.CLEARANCE * density
            startBounds.set(inset, inset, width - inset, height - inset)
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            updateBounds()
            paint.color = colors.accent; paint.style = Paint.Style.FILL; paint.alpha = 24
            if (!startBounds.isEmpty) canvas.drawRoundRect(startBounds, dp(16).toFloat(), dp(16).toFloat(), paint)
            paint.alpha = 255; paint.style = Paint.Style.STROKE; paint.strokeWidth = density
            if (!startBounds.isEmpty) canvas.drawRoundRect(startBounds, dp(16).toFloat(), dp(16).toFloat(), paint)
            paint.style = Paint.Style.FILL; paint.color = colors.text; paint.textAlign = Paint.Align.CENTER
            paint.textSize = 14 * resources.displayMetrics.scaledDensity
            if (activePointer == -1) {
                canvas.drawText("Dynamic Orbit β", width / 2f, height / 2f - dp(10), paint)
                paint.textSize = 11 * resources.displayMetrics.scaledDensity
                canvas.drawText(context.getString(if (width < dp(OrbitGeometry.MIN_WIDTH) || height < dp(208))
                    R.string.orbit_too_small else R.string.orbit_start_hint), width / 2f, height / 2f + dp(14), paint)
            } else {
                getLocationOnScreen(screenLocation)
                val x = originX - screenLocation[0]; val y = originY - screenLocation[1]
                paint.color = colors.accent
                canvas.drawCircle(x, y, dp(4).toFloat(), paint)
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(x, y, OrbitGeometry.NEUTRAL * density, paint)
                if (!guideVisible) {
                    canvas.drawCircle(x, y, OrbitGeometry.COMMIT * density, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawText(gesture.snapshot.preview?.toString() ?: "↩", x, y - dp(20), paint)
                }
            }
        }
        // This surface recognizes continuous paths; a click has no text-entry meaning.
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelStroke(); updateBounds()
                    if (width < dp(OrbitGeometry.MIN_WIDTH) || height < dp(208) || !startBounds.contains(event.x, event.y)) return true
                    // Android resampling can extrapolate a 52dp row stroke to 78dp, inventing
                    // a commit crossing. Deliver real samples immediately for this stroke only.
                    requestUnbufferedDispatch(event)
                    activePointer = event.getPointerId(0)
                    originX = event.rawX; originY = event.rawY
                    gesture.start(); parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(activePointer)
                    if (index < 0) return true
                    val offsetX = event.rawX - event.x
                    val offsetY = event.rawY - event.y
                    for (history in 0 until event.historySize) {
                        consume(event.getHistoricalX(index, history) + offsetX, event.getHistoricalY(index, history) + offsetY)
                    }
                    consume(event.getX(index) + offsetX, event.getY(index) + offsetY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { cancelStroke(); return true }
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == activePointer) { cancelStroke(); return true }
            }
            if (activePointer != -1) guideVisible = guide.show(gesture.snapshot, originX, originY, colors)
            invalidate()
            return true
        }
        private fun consume(x: Float, y: Float) {
            val point = OrbitPoint((x - originX) / density, (y - originY) / density)
            val output = gesture.move(point)
            output.forEach(onLetter)
        }
    }
}
