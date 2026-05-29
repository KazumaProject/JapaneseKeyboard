package com.kazumaproject.tenkey.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.setPadding
import androidx.core.widget.ImageViewCompat
import com.kazumaproject.core.domain.extensions.setBorder
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.key.Key

class SideKeySymbolModeContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val numberButton = createButton("number_mode")
    private val symbolButton = createButton("symbol")
    private var useThreeStateKeyboard: Boolean = true
    private var iconPadding: Int = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        background = null
        isClickable = false
        isFocusable = false
        clipToPadding = false
        clipChildren = false

        numberButton.setImageDrawable(
            AppCompatResources.getDrawable(
                context,
                com.kazumaproject.core.R.drawable.input_mode_number_select_custom
            )
        )
        symbolButton.setImageDrawable(
            AppCompatResources.getDrawable(context, com.kazumaproject.core.R.drawable.symbol)
        )

        addView(numberButton)
        addView(symbolButton)
        setUseThreeStateKeyboard(true)
    }

    private fun createButton(description: String): AppCompatImageButton {
        return AppCompatImageButton(context).apply {
            id = View.generateViewId()
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            background = AppCompatResources
                .getDrawable(context, com.kazumaproject.core.R.drawable.ten_keys_side_bg)
                ?.mutate()
            contentDescription = description
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            isClickable = false
            isFocusable = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusable = View.NOT_FOCUSABLE
            }
            ImageViewCompat.setImageTintList(
                this,
                AppCompatResources.getColorStateList(
                    context,
                    com.kazumaproject.core.R.color.keyboard_icon_color
                )
            )
        }
    }

    fun setUseThreeStateKeyboard(enabled: Boolean) {
        useThreeStateKeyboard = enabled
        numberButton.visibility = if (enabled) GONE else VISIBLE
        (numberButton.layoutParams as LayoutParams).apply {
            width = 0
            weight = if (enabled) 0f else 1f
            numberButton.layoutParams = this
        }
        (symbolButton.layoutParams as LayoutParams).apply {
            width = 0
            weight = if (enabled) 1f else 1f
            symbolButton.layoutParams = this
        }
        clearPressedKey()
        requestLayout()
        invalidate()
    }

    fun setImages(numberDrawable: Drawable?, symbolDrawable: Drawable?) {
        numberButton.setImageDrawable(numberDrawable)
        symbolButton.setImageDrawable(symbolDrawable)
    }

    fun setSymbolImageDrawable(drawable: Drawable?) {
        symbolButton.setImageDrawable(drawable)
    }

    fun setNumberImageDrawable(drawable: Drawable?) {
        numberButton.setImageDrawable(drawable)
    }

    fun setKeyBackground(drawable: Drawable?) {
        numberButton.background = drawable?.constantState?.newDrawable()?.mutate()
            ?: drawable?.mutate()
        symbolButton.background = drawable?.constantState?.newDrawable()?.mutate()
            ?: drawable
    }

    fun setKeyTint(tint: ColorStateList?) {
        ImageViewCompat.setImageTintList(numberButton, tint)
        ImageViewCompat.setImageTintList(symbolButton, tint)
    }

    fun setKeyDrawableAlpha(alpha: Int) {
        numberButton.setDrawableAlpha(alpha)
        symbolButton.setDrawableAlpha(alpha)
    }

    fun setKeySolidColor(color: Int) {
        numberButton.setDrawableSolidColor(color)
        symbolButton.setDrawableSolidColor(color)
    }

    fun setKeyBorder(color: Int, width: Int) {
        numberButton.setBorder(color, width)
        symbolButton.setBorder(color, width)
    }

    fun setIconPadding(paddingSize: Int) {
        iconPadding = paddingSize
        numberButton.setPadding(iconPadding)
        symbolButton.setPadding(iconPadding)
    }

    fun setPressedKey(key: Key?) {
        numberButton.isPressed = !useThreeStateKeyboard && key == Key.SideKeyNumberMode
        symbolButton.isPressed = key == Key.SideKeySymbol
    }

    fun clearPressedKey() {
        numberButton.isPressed = false
        symbolButton.isPressed = false
    }
}
