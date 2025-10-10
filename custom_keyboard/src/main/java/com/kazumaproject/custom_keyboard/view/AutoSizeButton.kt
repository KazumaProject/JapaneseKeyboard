package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatButton

class AutoSizeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {

    private var defaultTextSize = 14f

    private val textBounds = Rect()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            adjustTextSize(w, h)
        }
    }

    fun setDefaultTextSize(textSize: Float) {
        this.defaultTextSize = textSize
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
}
