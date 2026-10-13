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
import com.kazumaproject.core.ui.font.KeyboardFontAware
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontGlyphDrawable
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot

class SideKeySymbolModeContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), KeyboardFontAware {

    private val numberButton = createButton("number_mode")
    private val symbolButton = createButton("symbol")
    private var useThreeStateKeyboard: Boolean = true
    private var iconPadding: Int = 0
    private var keyboardFontSnapshot = KeyboardFontApplicator.processSnapshot

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        background = null
        isClickable = false
        isFocusable = false
        isPressed = false
        setDuplicateParentStateEnabled(false)
        setAddStatesFromChildren(false)
        clipToPadding = false
        clipChildren = false

        setKeyboardFont(keyboardFontSnapshot)

        addView(numberButton)
        addView(symbolButton)
        setUseThreeStateKeyboard(true)
    }

    override fun setKeyboardFont(snapshot: KeyboardFontSnapshot) {
        KeyboardFontApplicator.track(this)
        keyboardFontSnapshot = snapshot
        KeyboardFontGlyphDrawable.setImageResource(
            numberButton,
            com.kazumaproject.core.R.drawable.input_mode_number_select_custom,
            snapshot,
        )
        KeyboardFontGlyphDrawable.setImageResource(
            symbolButton,
            com.kazumaproject.core.R.drawable.symbol,
            snapshot,
        )
    }

    private fun createButton(description: String): AppCompatImageButton {
        return AppCompatImageButton(context).apply {
            id = View.generateViewId()
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            background = AppCompatResources
                .getDrawable(context, com.kazumaproject.core.R.drawable.ten_keys_side_bg)
                .newIndependentDrawable()
            contentDescription = description
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            isClickable = false
            isFocusable = false
            isPressed = false
            setDuplicateParentStateEnabled(false)
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

    private fun Drawable?.newIndependentDrawable(): Drawable? {
        val source = this ?: return null
        return source.constantState?.newDrawable()?.mutate() ?: source.mutate()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(false)
    }

    override fun dispatchSetPressed(pressed: Boolean) {
        // Pressed state is controlled only by setPressedKey().
    }

    fun setUseThreeStateKeyboard(enabled: Boolean, numberSymbolKeyGapDp: Int = 4) {
        useThreeStateKeyboard = enabled
        numberButton.visibility = if (enabled) GONE else VISIBLE
        val symbolParams = symbolButton.layoutParams as LayoutParams
        symbolParams.marginStart = if (enabled) 0 else {
            (numberSymbolKeyGapDp.coerceIn(0, 16) * resources.displayMetrics.density).toInt()
        }
        symbolButton.layoutParams = symbolParams
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
        KeyboardFontGlyphDrawable.setImageDrawable(numberButton, numberDrawable, keyboardFontSnapshot)
        KeyboardFontGlyphDrawable.setImageDrawable(symbolButton, symbolDrawable, keyboardFontSnapshot)
    }

    fun setSymbolImageDrawable(drawable: Drawable?) {
        KeyboardFontGlyphDrawable.setImageDrawable(symbolButton, drawable, keyboardFontSnapshot)
    }

    fun setNumberImageDrawable(drawable: Drawable?) {
        KeyboardFontGlyphDrawable.setImageDrawable(numberButton, drawable, keyboardFontSnapshot)
    }

    fun setKeyBackground(drawable: Drawable?) {
        numberButton.background = drawable.newIndependentDrawable()
        symbolButton.background = drawable.newIndependentDrawable()
        clearPressedKey()
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
        clearPressedKey()
    }

    fun setKeyBorder(color: Int, width: Int) {
        numberButton.setBorder(color, width)
        symbolButton.setBorder(color, width)
        clearPressedKey()
    }

    fun setIconPadding(paddingSize: Int) {
        iconPadding = paddingSize
        numberButton.setPadding(iconPadding)
        symbolButton.setPadding(iconPadding)
    }

    fun setPressedKey(key: Key?) {
        isPressed = false
        numberButton.isPressed = !useThreeStateKeyboard && key == Key.SideKeyNumberMode
        symbolButton.isPressed = key == Key.SideKeySymbol
    }

    fun clearPressedKey() {
        isPressed = false
        numberButton.isPressed = false
        symbolButton.isPressed = false
    }
}
