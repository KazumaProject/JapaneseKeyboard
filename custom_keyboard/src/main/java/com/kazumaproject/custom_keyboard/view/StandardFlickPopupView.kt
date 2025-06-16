package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.toColorInt

/**
 * フリック入力時に表示される単一の円形ポップアップビュー。
 * 内部のテキストはコントローラーによって動的に更新される。
 */
class StandardFlickPopupView(context: Context) : AppCompatTextView(context) {

    val viewSize = dpToPx(72) // ポップアップの直径
    private val backgroundDrawable: GradientDrawable = createBackground()

    init {
        width = viewSize
        height = viewSize
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK) // 初期色。setColorsで上書きされる
        maxLines = 2
        setLineSpacing(0f, 0.9f)
        background = backgroundDrawable
    }

    /**
     * ▼▼▼ NEW: ポップアップに動的な色を適用するメソッド ▼▼▼
     */
    fun setColors(backgroundColor: Int, textColor: Int, strokeColor: Int) {
        setTextColor(textColor)
        backgroundDrawable.setColor(backgroundColor)
        backgroundDrawable.setStroke(dpToPx(1), strokeColor)
        invalidate()
    }

    /**
     * ポップアップに表示するテキストを更新する。
     * @param text "主文字\n副文字" のような形式、または単一の文字列。
     */
    fun updateText(text: String?) {
        if (text.isNullOrEmpty()) {
            this.text = ""
            return
        }
        this.text = createSpannableText(text)
    }

    private fun createSpannableText(text: String): SpannableString {
        val spannable = SpannableString(text)
        if (text.contains("\n")) {
            val parts = text.split("\n", limit = 2)
            val primaryText = parts[0]
            // 主文字のスタイル
            spannable.setSpan(
                AbsoluteSizeSpan(spToPx(26f)),
                0,
                primaryText.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
            // 副文字のスタイル
            spannable.setSpan(
                AbsoluteSizeSpan(spToPx(14f)),
                primaryText.length,
                text.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        } else {
            // 1行のみの場合
            spannable.setSpan(
                AbsoluteSizeSpan(spToPx(26f)),
                0,
                text.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        return spannable
    }

    private fun createBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor("#FFFFFF".toColorInt())
            setStroke(dpToPx(1), Color.LTGRAY)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
            .toInt()
    }
}
