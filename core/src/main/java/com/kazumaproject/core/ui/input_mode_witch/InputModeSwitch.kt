package com.kazumaproject.core.ui.input_mode_witch

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import com.kazumaproject.core.R
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TwoStateNumberReturnTarget
import com.kazumaproject.core.ui.ScalableKeyLabelButton
import com.kazumaproject.core.ui.font.KeyboardFontAware
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot

class InputModeSwitch(context: Context, attrs: AttributeSet) :
    ScalableKeyLabelButton(context, attrs), KeyboardFontAware {

    private var currentInputMode: InputMode = InputMode.ModeJapanese
    private var isGojuonMode = false
    private var useThreeStateKeyboard = true
    private var numberReturnTarget = TwoStateNumberReturnTarget.Japanese
    private var fontSnapshot = KeyboardFontSnapshot()
    private var skinSelectedLabelColor: Int? = null
    private var skinIdleLabelColor: Int? = null

    fun setInputMode(inputMode: InputMode, isGojuon: Boolean) {
        setInputMode(inputMode, isGojuon, useThreeStateKeyboard = true)
    }

    fun setInputMode(
        inputMode: InputMode,
        isGojuon: Boolean,
        useThreeStateKeyboard: Boolean,
        twoStateNumberReturnTarget: TwoStateNumberReturnTarget = TwoStateNumberReturnTarget.Japanese
    ) {
        currentInputMode = inputMode
        this.isGojuonMode = isGojuon
        this.useThreeStateKeyboard = useThreeStateKeyboard
        this.numberReturnTarget = twoStateNumberReturnTarget
        renderModeIcon()
    }

    /** Uses separately colored mode labels while a keyboard skin is active. */
    fun setSkinModeLabelColors(selectedColor: Int?, idleColor: Int?) {
        require((selectedColor == null) == (idleColor == null)) {
            "Selected and idle label colors must both be set or both be cleared"
        }
        if (skinSelectedLabelColor == selectedColor && skinIdleLabelColor == idleColor) return
        skinSelectedLabelColor = selectedColor
        skinIdleLabelColor = idleColor
        renderModeIcon()
    }

    override fun setKeyboardFont(snapshot: KeyboardFontSnapshot) {
        KeyboardFontApplicator.track(this)
        if (fontSnapshot == snapshot) return
        fontSnapshot = snapshot
        renderModeIcon()
    }

    private fun renderModeIcon() {
        val resId = resolveInputModeSwitchIconResId(
            inputMode = currentInputMode,
            isGojuon = isGojuonMode,
            useThreeStateKeyboard = useThreeStateKeyboard,
            twoStateNumberReturnTarget = numberReturnTarget,
        )
        val icon = if (fontSnapshot.typeface == null && skinSelectedLabelColor == null) {
            requireNotNull(AppCompatResources.getDrawable(context, resId))
        } else {
            InputModeSwitchIconDrawable(context, resId, fontSnapshot.typeface,
                skinSelectedLabelColor, skinIdleLabelColor, currentInputMode)
        }
        val tenKeyKanaReturn = !isGojuonMode && resId == R.drawable.input_mode_japanese_select_custom
        if (!isGojuonMode) {
            // Centered ink bounds for the mode vectors and their font-rendered equivalents.
            when {
                tenKeyKanaReturn -> setLabelInkFractions(.74f, .76f)
                useThreeStateKeyboard -> setLabelInkFractions(.85f, .4f)
                resId == R.drawable.input_mode_english_custom -> setLabelInkFractions(.74f, .48f)
                else -> setLabelInkFractions(.96f, .6f)
            }
        }
        setImageDrawable(if (tenKeyKanaReturn) {
            TenKeyKanaReturnIcon(icon)
        } else {
            icon
        })
    }

    private class TenKeyKanaReturnIcon(private val icon: Drawable) : DrawableWrapper(icon) {
        // Scale the 24dp artwork to 34dp and trim its vertical margins to a 17dp box.
        override fun getIntrinsicWidth() = icon.intrinsicWidth * 34 / 24
        override fun getIntrinsicHeight() = intrinsicWidth / 2

        override fun onBoundsChange(bounds: Rect) {
            val top = bounds.top - (bounds.width() - bounds.height()) / 2
            icon.setBounds(bounds.left, top, bounds.right, top + bounds.width())
        }
    }
}

fun resolveInputModeSwitchIconResId(
    inputMode: InputMode,
    isGojuon: Boolean,
    useThreeStateKeyboard: Boolean,
    twoStateNumberReturnTarget: TwoStateNumberReturnTarget = TwoStateNumberReturnTarget.Japanese
): Int {
    return if (useThreeStateKeyboard) {
        when (inputMode) {
            InputMode.ModeJapanese -> if (isGojuon) {
                R.drawable.input_mode_japanese_select_gojuon
            } else {
                R.drawable.input_mode_japanese_select
            }

            InputMode.ModeEnglish -> if (isGojuon) {
                R.drawable.input_mode_english_select_gojuon
            } else {
                R.drawable.input_mode_english_select
            }

            InputMode.ModeNumber -> if (isGojuon) {
                R.drawable.input_mode_number_select_gojuon
            } else {
                R.drawable.input_mode_number_select
            }
        }
    } else {
        when (inputMode) {
            InputMode.ModeJapanese -> R.drawable.language_japanese_kana_left_bold_24px
            InputMode.ModeEnglish -> R.drawable.language_japanese_kana_right_bold_24px
            InputMode.ModeNumber -> when (twoStateNumberReturnTarget) {
                TwoStateNumberReturnTarget.Japanese -> R.drawable.input_mode_japanese_select_custom
                TwoStateNumberReturnTarget.English -> R.drawable.input_mode_english_custom
            }
        }
    }
}
