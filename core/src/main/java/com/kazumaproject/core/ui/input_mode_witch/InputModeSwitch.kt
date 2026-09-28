package com.kazumaproject.core.ui.input_mode_witch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.kazumaproject.core.R
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TwoStateNumberReturnTarget
import com.kazumaproject.core.ui.font.KeyboardFontAware
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontGlyphDrawable
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot

class InputModeSwitch(context: Context, attrs: AttributeSet) :
    AppCompatImageButton(context, attrs), KeyboardFontAware {

    private var currentInputMode: InputMode = InputMode.ModeJapanese
    private var isGojuonMode = false
    private var useThreeStateKeyboard = true
    private var numberReturnTarget = TwoStateNumberReturnTarget.Japanese
    private var fontSnapshot = KeyboardFontSnapshot()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

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

    override fun setKeyboardFont(snapshot: KeyboardFontSnapshot) {
        KeyboardFontApplicator.track(this)
        if (fontSnapshot == snapshot) return
        fontSnapshot = snapshot
        labelPaint.typeface = snapshot.typeface?.let { Typeface.create(it, Typeface.NORMAL) }
        renderModeIcon()
    }

    private fun renderModeIcon() {
        if (fontSnapshot.typeface != null) {
            setImageDrawable(null)
            invalidate()
            return
        }
        val resId = resolveInputModeSwitchIconResId(
            inputMode = currentInputMode,
            isGojuon = isGojuonMode,
            useThreeStateKeyboard = useThreeStateKeyboard,
            twoStateNumberReturnTarget = numberReturnTarget,
        )
        setImageDrawable(AppCompatResources.getDrawable(context, resId))
    }

    override fun onDraw(canvas: Canvas) {
        if (fontSnapshot.typeface == null) {
            super.onDraw(canvas)
            return
        }
        // Existing mode icons outline letters as paths. Render their semantic labels
        // with the selected typeface while keeping the same three-state control.
        super.onDraw(canvas)
        val iconWidth = width - paddingLeft - paddingRight
        val iconHeight = height - paddingTop - paddingBottom
        if (iconWidth <= 0 || iconHeight <= 0) return
        val fallbackColor = ContextCompat.getColor(context, R.color.keyboard_icon_color)
        val tint = imageTintList?.getColorForState(drawableState, fallbackColor)
        val selectedColor = tint ?: ContextCompat.getColor(context, R.color.keyboard_icon_color)
        val idleColor = tint ?: 0xff839096.toInt()
        val (labels, positions, selectedIndex) = when {
            useThreeStateKeyboard -> {
                val selected = when (currentInputMode) {
                    InputMode.ModeJapanese -> 0
                    InputMode.ModeEnglish -> 1
                    InputMode.ModeNumber -> 2
                }
                Triple(listOf("あ", "a", "1"), floatArrayOf(.28f, .5f, .72f), selected)
            }
            currentInputMode == InputMode.ModeNumber -> {
                val returnLabel = if (numberReturnTarget == TwoStateNumberReturnTarget.Japanese) "あ" else "a"
                Triple(listOf(returnLabel, "1"), floatArrayOf(.38f, .62f), 1)
            }
            else -> {
                val selected = if (currentInputMode == InputMode.ModeJapanese) 0 else 1
                Triple(listOf("あ", "a"), floatArrayOf(.38f, .62f), selected)
            }
        }
        labels.forEachIndexed { index, label ->
            val baseColor = if (index == selectedIndex) selectedColor else idleColor
            val color = Color.argb(
                Color.alpha(baseColor) * imageAlpha / 255,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor),
            )
            val centerX = paddingLeft + iconWidth * positions[index]
            KeyboardFontGlyphDrawable.drawGlyph(
                canvas = canvas,
                paint = labelPaint,
                text = label,
                region = RectF(
                    centerX - iconWidth * 0.11f,
                    paddingTop + iconHeight * 0.28f,
                    centerX + iconWidth * 0.11f,
                    paddingTop + iconHeight * 0.72f,
                ),
                color = color,
                typeface = fontSnapshot.typeface ?: Typeface.DEFAULT,
                style = if (!useThreeStateKeyboard && index == selectedIndex) {
                    Typeface.BOLD
                } else {
                    Typeface.NORMAL
                },
            )
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
