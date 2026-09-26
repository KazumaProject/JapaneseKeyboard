package com.kazumaproject.markdownhelperkeyboard.physical_keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.core.R as CoreR

/** A touch slop separates button clicks from dragging the popup. */
class FloatingPhysicalToolbarView(context: Context) : LinearLayout(context) {
    var onModeClick: (() -> Unit)? = null
    var onKeyboardClick: (() -> Unit)? = null
    var onVoiceClick: (() -> Unit)? = null
    var onDragStart: (() -> Unit)? = null
    var onDrag: ((Float, Float, Boolean) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val buttonSize = (48 * resources.displayMetrics.density).toInt()
    private val iconPadding = (12 * resources.displayMetrics.density).toInt()

    private val modeButton = TextView(context).apply {
        textSize = 22f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        contentDescription = context.getString(R.string.physical_toolbar_mode_title)
        setOnClickListener { onModeClick?.invoke() }
    }
    private val keyboardButton = ImageView(context).apply {
        setImageResource(CoreR.drawable.keyboard_24px)
        setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        contentDescription = context.getString(R.string.physical_toolbar_keyboard_title)
        setOnClickListener { onKeyboardClick?.invoke() }
    }
    private val voiceButton = ImageView(context).apply {
        setImageResource(CoreR.drawable.settings_voice_24px)
        setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        contentDescription = context.getString(R.string.physical_toolbar_voice_title)
        setOnClickListener { onVoiceClick?.invoke() }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        val radius = 24 * resources.displayMetrics.density
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(ContextCompat.getColor(context, CoreR.color.keyboard_bg))
        }
        elevation = 8 * resources.displayMetrics.density
        val color = ContextCompat.getColor(context, CoreR.color.keyboard_icon_color)
        modeButton.setTextColor(color)
        keyboardButton.setColorFilter(color)
        voiceButton.setColorFilter(color)
        addView(modeButton, LayoutParams(buttonSize, buttonSize))
        addView(keyboardButton, LayoutParams(buttonSize, buttonSize))
        addView(voiceButton, LayoutParams(buttonSize, buttonSize))
    }

    fun render(settings: PhysicalToolbarSettings, modeText: String) {
        modeButton.visibility = if (settings.showMode) View.VISIBLE else View.GONE
        keyboardButton.visibility = if (settings.showKeyboard) View.VISIBLE else View.GONE
        voiceButton.visibility = if (settings.showVoice) View.VISIBLE else View.GONE
        modeButton.text = modeText
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 &&
                    !dragging &&
                    isBeyondTouchSlop(event.rawX - downX, event.rawY - downY, touchSlop)
                ) {
                    dragging = true
                    onDragStart?.invoke()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> dragging = false
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (dragging) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) {
                        onDrag?.invoke(event.rawX - downX, event.rawY - downY, false)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onDrag?.invoke(event.rawX - downX, event.rawY - downY, true)
                    dragging = false
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}

internal fun isBeyondTouchSlop(dx: Float, dy: Float, slop: Int): Boolean =
    dx * dx + dy * dy > slop.toFloat() * slop
