package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
    onEdit: () -> Unit,
    private val onTextSize: (Float, Boolean) -> Unit,
    onHandleEvent: (MotionEvent) -> Unit,
    title: CharSequence? = null,
    onHide: (() -> Unit)? = null,
) : FloatingPanelFrame(context, onEdit, onHandleEvent, title, onHide) {
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
    private val footer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val sizeLabelRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
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
    private var showComposing = true

    init {
        id = R.id.composing_guide_root
        body.addView(readingScroll, LinearLayout.LayoutParams(-1, dp(28)).apply { bottomMargin = dp(4) })
        body.addView(scroll, LinearLayout.LayoutParams(-1, dp(32)).apply { bottomMargin = dp(4) })
        body.addView(candidateContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        contentContainer.addView(body, LayoutParams(-1, -1))
        sizeLabelRow.addView(TextView(context).apply {
            text = context.getString(R.string.composing_guide_text_size)
            textSize = 12f
            setTextColor(inkColor)
        }, LinearLayout.LayoutParams(0, -1, 1f))
        sizeLabelRow.addView(sizeValue, LinearLayout.LayoutParams(dp(28), -1))
        footer.addView(sizeLabelRow, LinearLayout.LayoutParams(-1, dp(20)))
        footer.addView(sizeSlider, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) })
        footerContainer.addView(footer, LayoutParams(-1, -1))
        setFooterEnabled(true)
    }

    override fun setColors(value: CandidatePanelColors) {
        super.setColors(value)
        textView.setTextColor(inkColor)
        readingView.setTextColor(inkColor)
        for (index in 0 until sizeLabelRow.childCount) (sizeLabelRow.getChildAt(index) as? TextView)?.setTextColor(inkColor)
        sizeValue.setTextColor(inkColor)
        sizeSlider.progressTintList = ColorStateList.valueOf(accent)
        sizeSlider.thumbTintList = ColorStateList.valueOf(accent)
    }

    fun setShowComposing(value: Boolean) {
        if (showComposing == value) return
        showComposing = value
        setFooterEnabled(value)
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

    companion object {
        fun composingLineHeight(context: Context, sizeSp: Float) = lineHeight(context, sizeSp)
        fun readingLineHeight(context: Context, composingSizeSp: Float) = lineHeight(context, (composingSizeSp * .65f).coerceIn(12f, 20f))

        private fun lineHeight(context: Context, sizeSp: Float): Int {
            val paint = Paint().apply {
                textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp, context.resources.displayMetrics)
            }
            val metrics = paint.fontMetrics
            return kotlin.math.ceil(metrics.descent - metrics.ascent + 8 * context.resources.displayMetrics.density).toInt()
        }
        const val MOVE_BAND_DP = FloatingPanelFrame.MOVE_BAND_DP
        const val EDIT_EXTRA_DP = 116
    }
}
