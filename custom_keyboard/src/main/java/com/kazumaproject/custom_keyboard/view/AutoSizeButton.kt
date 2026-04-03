package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatButton
import kotlin.math.min

class AutoSizeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {

    data class FlickGuideLabels(
        val tap: String = "",
        val up: String = "",
        val right: String = "",
        val down: String = "",
        val left: String = ""
    ) {
        fun hasVisibleGuides(): Boolean {
            return up.isNotEmpty() || right.isNotEmpty() || down.isNotEmpty() || left.isNotEmpty()
        }
    }

    private var defaultTextSize = 14f

    private val textBounds = Rect()
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val centerGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var flickGuideLabels: FlickGuideLabels? = null
    private var flickGuideTextColor: Int = Color.BLACK

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            adjustTextSize(w, h)
        }
    }

    fun setDefaultTextSize(textSize: Float) {
        this.defaultTextSize = textSize
        refreshTextSize()
    }

    fun refreshTextSize() {
        if (width > 0 && height > 0) {
            adjustTextSize(width, height)
        } else {
            requestLayout()
            invalidate()
        }
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        refreshTextSize()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawFlickGuides(canvas)
    }

    fun setFlickGuideLabels(labels: FlickGuideLabels?, textColor: Int = currentTextColor) {
        flickGuideLabels = labels
        flickGuideTextColor = textColor
        invalidate()
    }

    private fun adjustTextSize(buttonWidth: Int, buttonHeight: Int) {
        if (text.isNullOrEmpty() || buttonWidth <= 0 || buttonHeight <= 0) return

        var currentTextSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            defaultTextSize,
            context.resources.displayMetrics
        )
        paint.textSize = currentTextSizePx

        // ★修正点1: 利用可能な「高さ」も計算する
        val availableWidth = buttonWidth - paddingLeft - paddingRight
        val availableHeight = buttonHeight - paddingTop - paddingBottom

        paint.getTextBounds(text.toString(), 0, text.length, textBounds)

        // ★修正点2: 条件を更新 -> 幅「または」高さがはみ出す場合に縮小を開始
        if (textBounds.width() > availableWidth || textBounds.height() > availableHeight) {
            // ★修正点3: ループ条件も更新 -> 幅「または」高さが収まるまでループ
            while (textBounds.width() > availableWidth || textBounds.height() > availableHeight) {
                currentTextSizePx -= 1f // 1ピクセルずつ小さくする
                if (currentTextSizePx <= 2f) { // 小さくなりすぎないように下限を設定
                    break
                }
                paint.textSize = currentTextSizePx
                paint.getTextBounds(text.toString(), 0, text.length, textBounds)
            }
        }

        // 最終的なテキストサイズをピクセル単位で設定
        setTextSize(TypedValue.COMPLEX_UNIT_PX, currentTextSizePx)
    }

    private fun drawFlickGuides(canvas: Canvas) {
        val labels = flickGuideLabels ?: return
        if (!labels.hasVisibleGuides() && !shouldDrawCenterTap(labels)) return

        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return

        val minContentSize = min(availableWidth, availableHeight).toFloat()
        val guideTextSize = min(
            spToPx((defaultTextSize * 0.58f).coerceIn(8f, 13f)),
            minContentSize * 0.22f
        )
        if (guideTextSize <= 0f) return

        guidePaint.color = flickGuideTextColor
        guidePaint.textSize = guideTextSize
        guidePaint.typeface = typeface

        centerGuidePaint.color = flickGuideTextColor
        centerGuidePaint.textSize = textSize
        centerGuidePaint.typeface = typeface

        val centerX = width / 2f
        val centerY = height / 2f
        val guideFm = guidePaint.fontMetrics
        val topBaseline = paddingTop + dpToPx(3) - guideFm.ascent
        val bottomBaseline = height - paddingBottom - dpToPx(3) - guideFm.descent
        val sideBaseline = centerY - (guideFm.ascent + guideFm.descent) / 2f
        val sideInset = maxOf(dpToPx(10).toFloat(), availableWidth * 0.16f)

        drawGuideText(canvas, labels.up, centerX, topBaseline)
        drawGuideText(canvas, labels.right, width - paddingRight - sideInset, sideBaseline)
        drawGuideText(canvas, labels.down, centerX, bottomBaseline)
        drawGuideText(canvas, labels.left, paddingLeft + sideInset, sideBaseline)

        if (shouldDrawCenterTap(labels)) {
            val centerFm = centerGuidePaint.fontMetrics
            val centerBaseline = centerY - (centerFm.ascent + centerFm.descent) / 2f
            canvas.drawText(labels.tap, centerX, centerBaseline, centerGuidePaint)
        }
    }

    private fun drawGuideText(canvas: Canvas, text: String, x: Float, baselineY: Float) {
        if (text.isEmpty()) return
        canvas.drawText(text, x, baselineY, guidePaint)
    }

    private fun shouldDrawCenterTap(labels: FlickGuideLabels): Boolean {
        return labels.tap.isNotEmpty() && Color.alpha(currentTextColor) == 0
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            resources.displayMetrics
        )
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
