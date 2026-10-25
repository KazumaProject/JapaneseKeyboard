package com.kazumaproject.gesture_keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/** The same relative rectangles drive both the editor and the IME. */
class GestureCanvasView(context: Context) : FrameLayout(context) {
    val pads = linkedMapOf<String, GesturePadView>()
    var onBoundsChanged: (String, GestureBounds) -> Unit = { _, _ -> }
    private val rectangles = mutableMapOf<View, GestureBounds>()

    fun configure(state: GestureSessionState, sets: List<GestureSet>, editing: Boolean,
                  ink: Int, background: () -> android.graphics.drawable.Drawable,
                  allowed: () -> Boolean, start: (String) -> Unit,
                  stroke: (String, List<GesturePoint>) -> Unit) {
        pads.values.forEach { it.cancelStroke() }
        removeAllViews(); rectangles.clear(); pads.clear()
        state.pads.forEachIndexed { index, pad ->
            val frame = FrameLayout(context)
            frame.background = background()
            val view = GesturePadView(context).apply {
                id = when (index) { 0 -> R.id.gesture_pad_left; 1 -> R.id.gesture_pad_right; 2 -> R.id.gesture_pad_third; else -> R.id.gesture_pad_fourth }
                inkColor = ink
                contentDescription = "${index + 1}: ${sets.firstOrNull { it.id == pad.setId }?.name.orEmpty()}"
                captureAllowed = { !editing && allowed() }
                onStart = { start(pad.id) }
                onStroke = { stroke(pad.id, it) }
            }
            pads[pad.id] = view
            frame.addView(view, LayoutParams(-1, -1))
            rectangles[frame] = pad.bounds
            addView(frame)
            if (editing) {
                frame.addView(TextView(context).apply {
                    text = "${index + 1} · ${sets.firstOrNull { it.id == pad.setId }?.name.orEmpty()}"
                    gravity = Gravity.CENTER; setTextColor(ink)
                    contentDescription = if (resources.configuration.locales[0].language == "ja") "領域${index + 1}を移動" else "Move area ${index + 1}"
                    setOnTouchListener(editorTouch(frame, pad, state, false))
                }, LayoutParams(-1, -1))
                frame.addView(TextView(context).apply {
                    text = "↘"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(ink)
                    contentDescription = if (resources.configuration.locales[0].language == "ja") "領域${index + 1}のサイズ変更" else "Resize area ${index + 1}"
                    setOnTouchListener(editorTouch(frame, pad, state, true))
                }, LayoutParams(dp(32), dp(32), Gravity.BOTTOM or Gravity.END))
            }
        }
    }

    private fun editorTouch(frame: View, pad: GesturePadState, state: GestureSessionState, resize: Boolean): OnTouchListener {
        var downX = 0f; var downY = 0f
        var candidate = pad.bounds
        var valid = true
        return OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    candidate = pad.bounds; valid = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (width == 0 || height == 0) return@OnTouchListener true
                    val dx = (event.rawX - downX) / width
                    val dy = (event.rawY - downY) / height
                    candidate = if (resize) pad.bounds.copy(width = pad.bounds.width + dx, height = pad.bounds.height + dy)
                        else pad.bounds.copy(x = pad.bounds.x + dx, y = pad.bounds.y + dy)
                    valid = candidate.valid() && state.pads.none { it.id != pad.id && candidate.overlaps(it.bounds) }
                    // Keep negative/zero sizes out of Android's measurement, but show the invalid state.
                    rectangles[frame] = candidate.copy(width = candidate.width.coerceAtLeast(.05f), height = candidate.height.coerceAtLeast(.05f))
                    frame.foreground = GradientDrawable().apply {
                        setColor(Color.TRANSPARENT); setStroke(dp(2), if (valid) 0xff1976d2.toInt() else Color.RED)
                    }
                    requestLayout()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    rectangles[frame] = if (event.actionMasked == MotionEvent.ACTION_UP && valid) candidate else pad.bounds
                    frame.foreground = null; requestLayout()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP && valid && candidate != pad.bounds) onBoundsChanged(pad.id, candidate)
                    view.performClick()
                }
            }
            true
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec); val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        rectangles.forEach { (view, rect) -> view.measure(
            MeasureSpec.makeMeasureSpec(((rect.width * w).toInt() - dp(4)).coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(((rect.height * h).toInt() - dp(4)).coerceAtLeast(1), MeasureSpec.EXACTLY)) }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        rectangles.forEach { (view, rect) ->
            val x = (rect.x * width).toInt() + dp(2); val y = (rect.y * height).toInt() + dp(2)
            view.layout(x, y, x + view.measuredWidth, y + view.measuredHeight)
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
