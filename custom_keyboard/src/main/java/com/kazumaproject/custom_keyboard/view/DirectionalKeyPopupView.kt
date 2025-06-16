package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import com.kazumaproject.custom_keyboard.data.FlickDirection

/**
 * 通常フリック時に表示される、方向を示す矢印型のポップアップ
 */
class DirectionalKeyPopupView(context: Context) : AppCompatTextView(context) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#37474F".toColorInt()
        style = Paint.Style.FILL
    }
    private val backgroundPath = Path()
    private val textBounds = Rect()
    private var viewRotation = 0f
    private var currentDirection: FlickDirection = FlickDirection.TAP

    init {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        // ▼▼▼ FIX: パディングを削除し、テキスト描画を完全に手動制御する ▼▼▼
    }

    fun setFlickDirection(direction: FlickDirection) {
        this.currentDirection = direction
        this.viewRotation = when (direction) {
            FlickDirection.UP -> 90f
            FlickDirection.DOWN -> -90f
            FlickDirection.UP_LEFT_FAR -> 0f
            FlickDirection.UP_RIGHT_FAR -> 180f
            else -> 0f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        updatePath(w, h)

        // 1. 背景のPathを指定の角度で回転させて描画
        canvas.withRotation(viewRotation, w / 2f, h / 2f) {
            drawPath(backgroundPath, backgroundPaint)
        }

        // 2. テキストは回転させずに、手動で中央に描画
        val textToDraw = this.text.toString()
        val textPaint = this.paint
        textPaint.textAlign = Paint.Align.CENTER

        textPaint.getTextBounds(textToDraw, 0, textToDraw.length, textBounds)

        val textX = w / 2f
        val textY = h / 2f + textBounds.height() / 2f

        canvas.drawText(textToDraw, textX, textY, textPaint)
    }

    /**
     * 矢印と本体を一体化した、切れ目のない単一のPathを生成する
     */
    private fun updatePath(w: Float, h: Float) {
        backgroundPath.reset()

        val cornerRadius = 24f
        val pointerHeight = 35f
        val showPointer = currentDirection != FlickDirection.TAP
        val rectWidth = if (showPointer) w - pointerHeight else w

        // ▼▼▼ FIX: 角丸と矢印を滑らかに繋ぐ、新しい描画ロジック ▼▼▼
        backgroundPath.moveTo(cornerRadius, 0f)
        backgroundPath.lineTo(rectWidth - cornerRadius, 0f)
        backgroundPath.quadTo(rectWidth, 0f, rectWidth, cornerRadius)

        if (showPointer) {
            // 矢印の先端へ
            backgroundPath.lineTo(w, h / 2f)
            // 矢印の先端から戻る
            backgroundPath.lineTo(rectWidth, h - cornerRadius)
        } else {
            // タップ時
            backgroundPath.lineTo(rectWidth, h - cornerRadius)
        }

        backgroundPath.quadTo(rectWidth, h, rectWidth - cornerRadius, h)
        backgroundPath.lineTo(cornerRadius, h)
        backgroundPath.quadTo(0f, h, 0f, h - cornerRadius)
        backgroundPath.lineTo(0f, cornerRadius)
        backgroundPath.quadTo(0f, 0f, cornerRadius, 0f)

        backgroundPath.close()
    }
}
