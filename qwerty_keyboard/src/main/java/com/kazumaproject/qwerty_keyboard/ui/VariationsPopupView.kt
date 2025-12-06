package com.kazumaproject.qwerty_keyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.ceil

class VariationsPopupView(context: Context) : View(context) {

    // ★ 定数設定：1行あたりの最大文字数
    private val MAX_COLUMNS = 3

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

    // ★ 幅と高さの管理用変数
    private var itemWidth = 0f
    private var itemHeight = 0f
    private var numColumns = 1
    private var numRows = 1

    fun setChars(charList: List<Char>) {
        this.chars = charList

        // ★ リストのサイズと最大列数から、実際の列数と行数を計算
        if (chars.isNotEmpty()) {
            numColumns = if (chars.size < MAX_COLUMNS) chars.size else MAX_COLUMNS
            numRows = ceil(chars.size.toFloat() / MAX_COLUMNS).toInt()
        }

        selectedIndex = if (charList.isNotEmpty()) 0 else -1
        requestLayout() // サイズ変更の可能性があるため
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // ★ 1セルあたりのサイズを計算
        if (numColumns > 0 && numRows > 0) {
            itemWidth = width / numColumns.toFloat()
            itemHeight = height / numRows.toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (chars.isEmpty()) return

        // 描画領域をクリップ
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

        // 背景描画
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mainBackgroundPaint)

        // ★ グリッドベースでの描画
        chars.forEachIndexed { index, char ->
            val col = index % MAX_COLUMNS
            val row = index / MAX_COLUMNS

            val left = col * itemWidth
            val top = row * itemHeight
            val right = left + itemWidth
            val bottom = top + itemHeight

            // 選択中のハイライト描画
            if (index == selectedIndex) {
                canvas.drawRect(left, top, right, bottom, backgroundPaint)
            }

            // 文字描画位置の計算（セルの中心）
            val cx = left + itemWidth / 2f
            val cy = top + (itemHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

            canvas.drawText(char.toString(), cx, cy, textPaint)
        }
    }

    // ★ タッチ判定をXY座標で行うように変更
    fun updateSelection(x: Float, y: Float): Boolean {
        if (chars.isEmpty()) return false

        // 範囲外チェック
        if (x < 0 || x > width || y < 0 || y > height) {
            return false
        }

        val col = (x / itemWidth).toInt()
        val row = (y / itemHeight).toInt()

        // 列と行が有効範囲内かチェック
        if (col in 0 until numColumns && row in 0 until numRows) {
            val newIndex = row * MAX_COLUMNS + col

            // 計算したインデックスがリストの範囲内かチェック (最終行が埋まっていない場合など)
            if (newIndex in chars.indices) {
                if (newIndex != selectedIndex) {
                    selectedIndex = newIndex
                    invalidate()
                    return true
                }
            }
        }
        return false
    }

    fun getSelectedChar(): Char? {
        return if (selectedIndex != -1 && selectedIndex < chars.size) {
            chars[selectedIndex]
        } else {
            null
        }
    }
}
