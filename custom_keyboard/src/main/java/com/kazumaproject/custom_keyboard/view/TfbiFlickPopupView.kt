package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.extensions.getThemeColor
import com.kazumaproject.core.domain.extensions.isDarkThemeOn

class TfbiFlickPopupView(context: Context) : View(context) {

    // ===== 描画関連のプロパティ =====

    private var tapCharacter: String = ""
    private var petalCharacters = mapOf<TfbiFlickDirection, String>()
    private var highlightedDirection: TfbiFlickDirection = TfbiFlickDirection.TAP

    // 各パーツの描画設定 (初期値はデフォルトテーマから取得)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (context.isDarkThemeOn()) {
            context.getThemeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
        } else {
            context.getThemeColor(com.google.android.material.R.attr.colorSurface)
        }
    }
    private val highlightBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color =
            context.getThemeColor(com.google.android.material.R.attr.colorSecondaryContainer)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
        textAlign = Paint.Align.CENTER
        textSize = spToPx(20f)
    }

    private val rects = mutableMapOf<TfbiFlickDirection, RectF>()
    private var cornerRadius = 20f

    // レイアウト定義
    private val directionLayout = listOf(
        // 0行目 (上段)
        listOf(TfbiFlickDirection.UP_LEFT, TfbiFlickDirection.UP, TfbiFlickDirection.UP_RIGHT),
        // 1行目 (中段)
        listOf(TfbiFlickDirection.LEFT, TfbiFlickDirection.TAP, TfbiFlickDirection.RIGHT),
        // 2行目 (下段)
        listOf(TfbiFlickDirection.DOWN_LEFT, TfbiFlickDirection.DOWN, TfbiFlickDirection.DOWN_RIGHT)
    )

    // ===== 公開メソッド =====

    /**
     * 動的に色を設定する
     * @param backgroundColor 通常時の背景色
     * @param highlightedBackgroundColor ハイライト時の背景色
     * @param textColor テキストおよび枠線の色
     */
    fun setColors(backgroundColor: Int, highlightedBackgroundColor: Int, textColor: Int) {
        bgPaint.color = backgroundColor
        highlightBgPaint.color = highlightedBackgroundColor
        strokePaint.color = textColor
        textPaint.color = textColor
        invalidate()
    }

    fun setCharacters(tapChar: String, petalChars: Map<TfbiFlickDirection, String>) {
        this.tapCharacter = tapChar
        this.petalCharacters = petalChars
        invalidate()
    }

    fun highlightDirection(direction: TfbiFlickDirection) {
        if (this.highlightedDirection != direction) {
            this.highlightedDirection = direction
            invalidate()
        }
    }

    // ===== Viewのライフサイクル =====

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateRects(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        rects[TfbiFlickDirection.TAP]?.let { rect ->
            val paint =
                if (highlightedDirection == TfbiFlickDirection.TAP) highlightBgPaint else bgPaint
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
            drawTextCentered(canvas, tapCharacter, rect)
        }

        for ((direction, char) in petalCharacters) {
            rects[direction]?.let { rect ->
                val paint = if (highlightedDirection == direction) highlightBgPaint else bgPaint
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
                drawTextCentered(canvas, char, rect)
            }
        }
    }

    // ===== 内部ヘルパーメソッド =====

    private fun calculateRects(width: Int, height: Int) {
        rects.clear()
        val cellWidth = width / 3f
        val cellHeight = height / 3f

        for (row in 0..2) {
            for (col in 0..2) {
                val left = col * cellWidth
                val top = row * cellHeight
                val right = (col + 1) * cellWidth
                val bottom = (row + 1) * cellHeight
                val rect = RectF(left, top, right, bottom)

                val direction = directionLayout[row][col]
                rects[direction] = rect
            }
        }
    }

    private fun drawTextCentered(canvas: Canvas, text: String, rect: RectF) {
        if (text.isEmpty()) return
        val textY = rect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(text, rect.centerX(), textY, textPaint)
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
    }
}
