package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme

class FlickGridPopupView(context: Context) : GridLayout(context) {

    private val buttons = mutableMapOf<FlickDirection, Button>()

    // ▼▼▼ 変更点: グリッド全体の背景Drawableは不要になったため削除 ▼▼▼
    // private val backgroundDrawable = GradientDrawable()

    private var defaultColor = "#455A64".toColorInt()
    private var highlightColor = "#37474F".toColorInt()
    private var textColor = Color.WHITE

    // ▼▼▼ 追加: 枠線用の色を保持するプロパティ ▼▼▼
    private var separatorColor = Color.LTGRAY

    init {
        columnCount = 3
        rowCount = 3
        alignmentMode = ALIGN_BOUNDS

        // ▼▼▼ 変更点: グリッド全体の背景を透明に戻す ▼▼▼
        background = Color.TRANSPARENT.toDrawable()
    }

    /**
     * ▼▼▼ 変更点: 枠線用の色もテーマから受け取るようにし、グリッド背景の設定は削除 ▼▼▼
     */
    fun setColors(theme: FlickPopupColorTheme) {
        this.defaultColor = theme.centerGradientStartColor
        this.highlightColor = theme.segmentHighlightGradientStartColor
        this.textColor = theme.textColor
        this.separatorColor = theme.separatorColor
    }

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

            val params = LayoutParams(spec(pos.first), spec(pos.second)).apply {
                width = keyWidth
                height = keyHeight
                val margin = dpToPx(1f)
                setMargins(margin, margin, margin, margin)
            }

            if (!char.isNullOrEmpty()) {
                val button = createButton(char)
                button.layoutParams = params
                addView(button)
                buttons[direction] = button
            } else {
                val placeholder = View(context)
                placeholder.layoutParams = params
                addView(placeholder)
            }
        }
    }

    private fun createButton(text: String): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            setTextColor(this@FlickGridPopupView.textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            background = createButtonDrawable(false)
        }
    }

    fun highlightKey(direction: FlickDirection) {
        buttons.forEach { (dir, button) ->
            button.background = createButtonDrawable(dir == direction)
        }
    }

    /**
     * ▼▼▼ 変更点: ここで各ボタンのDrawableに枠線を追加する ▼▼▼
     */
    private fun createButtonDrawable(isHighlighted: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            // 塗りつぶしの色をハイライト状態に応じて設定
            setColor(if (isHighlighted) highlightColor else defaultColor)
            // 角の丸みを設定
            cornerRadius = 24f
            // 枠線（アウトライン）を設定
            setStroke(dpToPx(1f), separatorColor)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
