package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.google.android.material.R as MaterialR // MaterialR as alias to avoid conflict

/**
 * キーボードレイアウト編集専用のカスタムビュー
 * FlickKeyboardViewの見た目を再現しつつ、キーの選択機能に特化
 */
@SuppressLint("ClickableViewAccessibility")
class EditableFlickKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    interface OnKeyEditListener {
        fun onKeySelected(keyId: String)
    }

    private var listener: OnKeyEditListener? = null

    fun setOnKeyEditListener(listener: OnKeyEditListener?) {
        this.listener = listener
    }

    fun removeOnKeyEditListener() {
        this.listener = null
    }

    fun setKeyboard(layout: KeyboardLayout) {
        this.removeAllViews()

        this.columnCount = layout.columnCount
        this.rowCount = layout.rowCount
        this.isFocusable = false

        layout.keys.forEach { keyData ->
            val isDarkTheme = context.isDarkThemeOn()
            val keyView: View = if (keyData.isSpecialKey && keyData.drawableResId != null) {
                AppCompatImageButton(context).apply {
                    isFocusable = false
                    elevation = 2f
                    setImageResource(keyData.drawableResId!!)
                    contentDescription = keyData.label
                    setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
                }
            } else {
                Button(context).apply {
                    isFocusable = false
                    isAllCaps = false
                    // テキストのスタイリング (FlickKeyboardViewから移植)
                    if (keyData.label.contains("\n")) {
                        val parts = keyData.label.split("\n", limit = 2)
                        val primaryText = parts[0]
                        val secondaryText = parts.getOrNull(1) ?: ""
                        val spannable = SpannableString(keyData.label)
                        spannable.setSpan(
                            AbsoluteSizeSpan(spToPx(16f)),
                            0,
                            primaryText.length,
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        )
                        if (secondaryText.isNotEmpty()) {
                            spannable.setSpan(
                                AbsoluteSizeSpan(spToPx(10f)),
                                primaryText.length + 1,
                                keyData.label.length,
                                Spannable.SPAN_INCLUSIVE_INCLUSIVE
                            )
                        }
                        this.maxLines = 2
                        this.setLineSpacing(0f, 0.9f)
                        this.setPadding(0, dpToPx(4), 0, dpToPx(4))
                        this.gravity = Gravity.CENTER
                        this.text = spannable
                    } else {
                        text = keyData.label
                        gravity = Gravity.CENTER
                        val englishOnlyRegex = Regex("^[a-zA-Z@#/_'\"().,?! ]+$")
                        val symbolRegex = Regex("^[()\\[\\],./ -]+$")
                        if (englishOnlyRegex.matches(keyData.label)) {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        } else if (symbolRegex.matches(keyData.label)) {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        } else {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                        }
                    }

                    // 背景のスタイリング (FlickKeyboardViewから移植)
                    when {
                        keyData.isSpecialKey -> {
                            elevation = 2f
                            setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        }

                        keyData.keyType == KeyType.STANDARD_FLICK -> {
                            // SegmentedBackgroundDrawableを編集画面用に単純化して表示
                            val keyBaseColor =
                                if (isDarkTheme) context.getColorFromAttr(MaterialR.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                    MaterialR.attr.colorSurface
                                )
                            val keyHighlightColor =
                                context.getColorFromAttr(MaterialR.attr.colorSecondaryContainer)
                            val keyTextColor =
                                context.getColorFromAttr(MaterialR.attr.colorOnSurface)
                            val segmentedDrawable = SegmentedBackgroundDrawable(
                                label = keyData.label,
                                baseColor = keyBaseColor,
                                highlightColor = keyHighlightColor,
                                textColor = keyTextColor,
                                cornerRadius = 20f
                            )
                            background = segmentedDrawable
                            setTextColor(Color.TRANSPARENT) // Drawable側で描画するため
                        }

                        else -> {
                            setBackgroundResource(if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_center_bg_material else com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light)
                        }
                    }
                }
            }

            // レイアウトパラメータの設定 (FlickKeyboardViewから移植)
            val params = LayoutParams().apply {
                rowSpec = spec(keyData.row, keyData.rowSpan, FILL, 1f)
                columnSpec = spec(keyData.column, keyData.colSpan, FILL, 1f)
                width = 0
                height = 0
                elevation = 2f
                if (keyData.isSpecialKey) {
                    setMargins(6, 12, 6, 6)
                } else {
                    setMargins(6, 9, 6, 9)
                }
            }
            keyView.layoutParams = params

            // ★★★ 重要な違い ★★★
            // フリックコントローラーの代わりに、シンプルなクリックリスナーを設定
            keyView.setOnClickListener {
                keyData.keyId?.let { keyId ->
                    listener?.onKeySelected(keyId)
                }
            }
            // 長押しなどの複雑なイベントは編集画面では不要

            this.addView(keyView)
        }
    }

    // FlickKeyboardViewからヘルパー関数を移植
    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return ContextCompat.getColor(this, typedValue.resourceId)
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
            .toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
