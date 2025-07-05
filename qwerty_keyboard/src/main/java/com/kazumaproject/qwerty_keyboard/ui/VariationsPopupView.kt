package com.kazumaproject.qwerty_keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.core.content.ContextCompat

class VariationsPopupView(context: Context) : View(context) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(
            context,
            com.kazumaproject.core.R.color.keyboard_icon_color
        )
        textSize = 60f
        textAlign = Paint.Align.CENTER
    }
    private val backgroundPaint = Paint().apply {
        color = ContextCompat.getColor(
            context,
            com.kazumaproject.core.R.color.enter_key_bg
        )
    }

    private val mainBackgroundPaint = Paint().apply {
        color = ContextCompat.getColor(
            context,
            com.kazumaproject.core.R.color.candidate_background
        )
    }

    private val cornerRadius = 30f
    private val clipPath = Path()

    private var chars: List<Char> = emptyList()
    private var selectedIndex = -1
    private var charWidth = 0f

    fun setChars(charList: List<Char>) {
        this.chars = charList
        if (width > 0 && chars.isNotEmpty()) {
            this.charWidth = width / chars.size.toFloat()
        }
        selectedIndex = if (charList.isNotEmpty()) 0 else -1

        invalidate() // 再描画を要求
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (chars.isEmpty()) return

        // ★ 1. 描画領域を丸い四角形にクリップする
        clipPath.reset()
        clipPath.addRoundRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            cornerRadius,
            cornerRadius,
            Path.Direction.CW
        )
        canvas.clipPath(clipPath)

        // ★ 2. クリップされた領域内で背景などを描画する (以前のコードと同じ)
        // まずビュー全体の背景を描画
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mainBackgroundPaint)

        charWidth = width / chars.size.toFloat()

        // 選択中の背景をハイライト描画
        if (selectedIndex != -1) {
            val start = charWidth * selectedIndex
            canvas.drawRect(start, 0f, start + charWidth, height.toFloat(), backgroundPaint)
        }

        // 各文字を描画
        chars.forEachIndexed { index, char ->
            val x = charWidth * index + charWidth / 2
            val y = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(char.toString(), x, y, textPaint)
        }
    }

    // タッチ座標から選択中の文字を更新
    fun updateSelection(x: Float): Boolean {
        if (chars.isEmpty()) return false
        val index = (x / charWidth).toInt()
        val newIndex = index.coerceIn(0, chars.size - 1)

        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
            invalidate() // ハイライトを更新するために再描画
            return true
        }
        return false
    }

    // 選択された文字を取得
    fun getSelectedChar(): Char? {
        return if (selectedIndex != -1) {
            chars[selectedIndex]
        } else {
            null
        }
    }
}
