package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.kazumaproject.markdownhelperkeyboard.R
import kotlin.math.roundToInt

/** Presentation only: gesture geometry and persistence belong to the controller/reducer. */
internal class ComposingGuideView(
    context: Context,
    private val onEdit: () -> Unit,
    private val onHide: () -> Unit,
    private val onTextSize: (Float, Boolean) -> Unit,
    private val onHandleEvent: (MotionEvent) -> Unit,
) : FrameLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private var colors = CandidatePanelColors.resolve(context)
    private val surface get() = colors.background
    private val inkColor get() = colors.text
    private val accent get() = colors.selection
    private val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val moveGrip = HandleView(context, GuideHandle.MOVE)
    private val editButton = icon(R.drawable.composing_guide_edit, R.string.composing_guide_edit, onEdit)
    private val hideButton = icon(R.drawable.composing_guide_hide, R.string.composing_guide_hide, onHide)
    private val textView = TextView(context).apply {
        setTextColor(inkColor)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine(true)
        id = R.id.composing_guide_composing_text
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private val scroll = TextScrollView(context).apply { isFillViewport = true; addView(textView, LayoutParams(-2, -1)) }
    private val readingView = TextView(context).apply {
        id = R.id.composing_guide_reading_text
        setSingleLine(true)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(inkColor)
    }
    private val readingScroll = TextScrollView(context).apply {
        isFillViewport = true
        visibility = GONE
        addView(readingView, LayoutParams(-2, -1))
    }
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val candidateContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val sizeValue = TextView(context).apply { setTextColor(inkColor); textSize = 12f; gravity = Gravity.CENTER }
    private var trackingTextSize = false
    private val sizeSlider = SeekBar(context).apply {
        max = 38
        progress = 10
        progressTintList = ColorStateList.valueOf(accent)
        thumbTintList = ColorStateList.valueOf(accent)
        contentDescription = context.getString(R.string.composing_guide_text_size)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sizeValue.text = (progress + 18).toString()
                if (fromUser) onTextSize((progress + 18).toFloat(), !trackingTextSize)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { trackingTextSize = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                trackingTextSize = false
                onTextSize((progress + 18).toFloat(), true)
            }
        })
    }
    private val edges = listOf(GuideHandle.LEFT, GuideHandle.TOP, GuideHandle.RIGHT, GuideHandle.BOTTOM)
        .associateWith { HandleView(context, it) }
    private var showComposing = true
    private var handlingGesture = false
    var editing = false
        private set

    init {
        id = R.id.composing_guide_root
        elevation = dp(8).toFloat()
        header.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        header.addView(editButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(hideButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(readingScroll, LinearLayout.LayoutParams(-1, dp(28)))
        body.addView(scroll, LinearLayout.LayoutParams(-1, dp(64)))
        body.addView(candidateContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(body)
        addView(header)
        footer.addView(TextView(context).apply {
            text = context.getString(R.string.composing_guide_text_size)
            textSize = 12f
            setTextColor(inkColor)
        }, LinearLayout.LayoutParams(-2, -2))
        footer.addView(sizeSlider, LinearLayout.LayoutParams(0, dp(48), 1f))
        footer.addView(sizeValue, LinearLayout.LayoutParams(dp(28), -2))
        addView(footer)
        addView(moveGrip, LayoutParams(-1, dp(MOVE_BAND_DP), Gravity.BOTTOM))
        edges.values.forEach(::addView)
        setEditing(false)
    }

    private fun icon(drawable: Int, description: Int, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(drawable)
        imageTintList = ColorStateList.valueOf(colors.icon)
        contentDescription = context.getString(description)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.pressed, 80)), null,
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
        isFocusable = false
        setOnClickListener { action() }
    }

    fun setEditing(value: Boolean) {
        editing = value
        handlingGesture = false
        moveGrip.isEnabled = !value
        moveGrip.alpha = if (value) .35f else 1f
        moveGrip.isPressed = false
        footer.visibility = if (value) VISIBLE else GONE
        edges.values.forEach { it.visibility = if (value) VISIBLE else GONE }
        editButton.setImageResource(if (value) com.kazumaproject.core.R.drawable.baseline_check_24 else R.drawable.composing_guide_edit)
        editButton.contentDescription = context.getString(if (value) R.string.composing_guide_done else R.string.composing_guide_edit)
        editButton.imageTintList = ColorStateList.valueOf(if (value) accent else colors.icon)
        background = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(if (value) 2 else 1), if (value) accent else ColorUtils.setAlphaComponent(inkColor, 45))
        }
        header.layoutParams = LayoutParams(-1, dp(48)).apply {
            leftMargin = dp(if (value) 24 else 12); rightMargin = leftMargin
            topMargin = dp(if (value) 24 else 8)
        }
        body.layoutParams = LayoutParams(-1, -1).apply {
            leftMargin = dp(if (value) 24 else 16); rightMargin = leftMargin
            topMargin = dp(if (value) 80 else 64); bottomMargin = dp(MOVE_BAND_DP + if (value) 92 else 12)
        }
        footer.layoutParams = LayoutParams(-1, dp(48), Gravity.BOTTOM).apply {
            leftMargin = dp(24); rightMargin = dp(24); bottomMargin = dp(MOVE_BAND_DP + 36)
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
    }

    fun setColors(value: CandidatePanelColors) {
        if (colors == value) return
        colors = value
        textView.setTextColor(inkColor)
        readingView.setTextColor(inkColor)
        editButton.imageTintList = ColorStateList.valueOf(if (editing) accent else colors.icon)
        hideButton.imageTintList = ColorStateList.valueOf(colors.icon)
        listOf(editButton, hideButton).forEach { button ->
            button.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.pressed, 80)), null,
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
        }
        for (index in 0 until footer.childCount) (footer.getChildAt(index) as? TextView)?.setTextColor(inkColor)
        sizeValue.setTextColor(inkColor)
        sizeSlider.progressTintList = ColorStateList.valueOf(accent)
        sizeSlider.thumbTintList = ColorStateList.valueOf(accent)
        background = GradientDrawable().apply {
            setColor(surface); cornerRadius = dp(16).toFloat()
            setStroke(dp(if (editing) 2 else 1), if (editing) accent else ColorUtils.setAlphaComponent(inkColor, 45))
        }
        moveGrip.invalidate()
        edges.values.forEach { it.invalidate() }
    }

    fun setEditAvailable(available: Boolean) { editButton.isEnabled = available; editButton.alpha = if (available) 1f else .4f }

    fun setShowComposing(value: Boolean) {
        if (showComposing == value) return
        showComposing = value
        setEditing(editing)
        scroll.visibility = if (value) VISIBLE else GONE
        readingScroll.visibility = if (value && readingView.text.isNotEmpty()) VISIBLE else GONE
        sizeSlider.isEnabled = value
    }

    fun setContent(value: String, size: Float, reading: String = "") {
        val displayed = value.ifEmpty { context.getString(R.string.composing_guide_empty) }
        if (textView.text.toString() != displayed) {
            textView.text = displayed
            scroll.showUpdatedTextEnd()
        }
        if (readingView.text.toString() != reading) {
            readingView.text = reading
            readingScroll.showUpdatedTextEnd()
        }
        readingView.textSize = (size * .65f).coerceIn(12f, 20f)
        readingView.setTextColor(inkColor)
        val readingHeight = readingLineHeight(context, size)
        if (readingScroll.layoutParams.height != readingHeight) readingScroll.layoutParams = readingScroll.layoutParams.apply { height = readingHeight }
        readingScroll.visibility = if (showComposing && reading.isNotEmpty()) VISIBLE else GONE
        textView.textSize = if (value.isEmpty()) 14f else size
        val lineHeight = composingLineHeight(context, if (value.isEmpty()) 14f else size)
        if (scroll.layoutParams.height != lineHeight) scroll.layoutParams = scroll.layoutParams.apply { height = lineHeight }
        textView.setTextColor(ColorUtils.setAlphaComponent(inkColor, if (value.isEmpty()) 160 else 255))
        if (!sizeSlider.isPressed) sizeSlider.progress = size.roundToInt() - 18
        sizeValue.text = size.roundToInt().toString()
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
        onHandleEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            handlingGesture = false
            moveGrip.isPressed = false
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private class TextScrollView(context: Context) : HorizontalScrollView(context) {
        private var followUpdatedText = false

        fun showUpdatedTextEnd() {
            followUpdatedText = true
            requestLayout()
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            if (followUpdatedText) {
                scrollTo((getChildAt(0).width - width + paddingLeft + paddingRight).coerceAtLeast(0), 0)
                followUpdatedText = false
            }
        }
    }

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

    companion object {
        fun composingLineHeight(context: Context, sizeSp: Float) = lineHeight(context, sizeSp, 64)
        fun readingLineHeight(context: Context, composingSizeSp: Float) = lineHeight(context, (composingSizeSp * .65f).coerceIn(12f, 20f), 28)

        private fun lineHeight(context: Context, sizeSp: Float, minimumDp: Int): Int {
            val paint = Paint().apply {
                textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp, context.resources.displayMetrics)
            }
            val metrics = paint.fontMetrics
            return maxOf((minimumDp * context.resources.displayMetrics.density).roundToInt(),
                kotlin.math.ceil(metrics.descent - metrics.ascent + 8 * context.resources.displayMetrics.density).toInt())
        }
        const val MOVE_BAND_DP = 24
        const val EDIT_EXTRA_DP = 96
    }
}
