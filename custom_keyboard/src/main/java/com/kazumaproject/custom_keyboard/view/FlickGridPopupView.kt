package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import com.kazumaproject.custom_keyboard.data.FlickDirection

/**
 * ロングプレス時に表示される、フリック候補をグリッド状に表示するポップアップ
 */
class FlickGridPopupView(context: Context) : GridLayout(context) {

    private val buttons = mutableMapOf<FlickDirection, Button>()
    private val defaultColor = Color.parseColor("#455A64")
    private val highlightColor = Color.parseColor("#37474F")

    init {
        columnCount = 3
        rowCount = 3
        background = ColorDrawable(Color.TRANSPARENT)
        val padding = (context.resources.displayMetrics.density * 8).toInt()
        setPadding(padding, padding, padding, padding)
        // ▼▼▼ FIX: グリッドレイアウトが子Viewを均等に配置するように設定 ▼▼▼
        alignmentMode = ALIGN_BOUNDS
    }

    fun setCharacters(map: Map<FlickDirection, String>) {
        this.removeAllViews()
        buttons.clear()

        // ▼▼▼ FIX: グリッドの各位置と方向を明確に対応させる ▼▼▼
        val gridPositions = mapOf(
            FlickDirection.UP to Pair(0, 1),           // Top-center
            FlickDirection.DOWN to Pair(2, 1),         // Bottom-center
            FlickDirection.UP_LEFT_FAR to Pair(1, 0),  // Middle-left
            FlickDirection.UP_RIGHT_FAR to Pair(1, 2), // Middle-right
            FlickDirection.TAP to Pair(1, 1)           // Center
        )

        // 空のボタンをプレースホルダーとして追加し、グリッドの形を整える
        for (i in 0 until 9) {
            val placeholder = View(context)
            val params = LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = spec(i % 3, 1f)
                rowSpec = spec(i / 3, 1f)
            }
            addView(placeholder, params)
        }

        gridPositions.forEach { (direction, pos) ->
            val char = map[direction]
            if (!char.isNullOrEmpty()) {
                val button = createButton(char)
                val params = LayoutParams(spec(pos.first, 1f), spec(pos.second, 1f)).apply {
                    width = (context.resources.displayMetrics.density * 72).toInt()
                    height = (context.resources.displayMetrics.density * 72).toInt()
                    val margin = (context.resources.displayMetrics.density * 4).toInt()
                    setMargins(margin, margin, margin, margin)
                }
                // グリッド内の特定の位置にボタンを配置
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
            setTextColor(Color.WHITE)
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
