package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme

class FlickGridPopupView(context: Context) : GridLayout(context) {

    private val buttons = mutableMapOf<FlickDirection, Button>()

    private var defaultColor = "#455A64".toColorInt()
    private var highlightColor = "#37474F".toColorInt()
    private var textColor = Color.WHITE

    init {
        columnCount = 3
        rowCount = 3
        background = Color.TRANSPARENT.toDrawable()
        alignmentMode = ALIGN_BOUNDS
    }

    fun setColors(theme: FlickPopupColorTheme) {
        this.defaultColor = theme.centerGradientStartColor
        this.highlightColor = theme.segmentHighlightGradientStartColor
        this.textColor = theme.textColor
    }

    /**
     * FIX: 元キーのサイズを受け取り、ポップアップ内の各ボタンのサイズをそれに合わせる
     */
    fun setCharacters(map: Map<FlickDirection, String>, keyWidth: Int, keyHeight: Int) {
        this.removeAllViews()
        buttons.clear()

        val gridPositions = mapOf(
            FlickDirection.UP to Pair(0, 1),
            FlickDirection.DOWN to Pair(2, 1),
            FlickDirection.UP_LEFT_FAR to Pair(1, 0),
            FlickDirection.UP_RIGHT_FAR to Pair(1, 2),
            FlickDirection.TAP to Pair(1, 1)
        )

        gridPositions.forEach { (direction, pos) ->
            val char = map[direction]
            if (!char.isNullOrEmpty()) {
                val button = createButton(char)
                // pos.firstが行、pos.secondが列
                val params = LayoutParams(spec(pos.first), spec(pos.second)).apply {
                    // 各ボタンのサイズを元のキーのサイズに設定
                    width = keyWidth
                    height = keyHeight
                    // ボタン間の余白を調整
                    val margin = (context.resources.displayMetrics.density * 1).toInt()
                    setMargins(margin, margin, margin, margin)
                }
                button.layoutParams = params
                addView(button)
                buttons[direction] = button
            }
        }
    }

    private fun createButton(text: String): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            setTextColor(this@FlickGridPopupView.textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            background = createButtonDrawable(false)
        }
    }

    fun highlightKey(direction: FlickDirection) {
        buttons.forEach { (dir, button) ->
            button.background = createButtonDrawable(dir == direction)
        }
    }

    private fun createButtonDrawable(isHighlighted: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            setColor(if (isHighlighted) highlightColor else defaultColor)
            cornerRadius = 24f
        }
    }
}
