package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt

interface BubbleClickListener {
    fun onBubbleClick()
}

class BubbleTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var listener: BubbleClickListener? = null

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#80DEEA".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_bg)
        style = Paint.Style.FILL
    }

    private val bubblePath = Path()
    private val bubbleRect = RectF()

    private val cornerRadius = 20f

    init {
        setPadding(20, 20, 20, 20)
        setTextColor(
            ContextCompat.getColor(
                context,
                com.kazumaproject.core.R.color.keyboard_icon_color
            )
        )
        this.setOnClickListener {
            listener?.onBubbleClick()
        }
    }

    fun setOnBubbleClickListener(listener: BubbleClickListener) {
        this.listener = listener
    }

    override fun onDraw(canvas: Canvas) {
        bubblePath.reset()

        bubbleRect.set(
            borderPaint.strokeWidth / 2,
            borderPaint.strokeWidth / 2,
            width - borderPaint.strokeWidth / 2,
            height - borderPaint.strokeWidth / 2
        )
        bubblePath.addRoundRect(bubbleRect, cornerRadius, cornerRadius, Path.Direction.CW)

        canvas.drawPath(bubblePath, fillPaint)
        canvas.drawPath(bubblePath, borderPaint)

        super.onDraw(canvas)
    }
}
