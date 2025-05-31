package com.kazumaproject.core.ui.input_mode_witch

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import com.kazumaproject.core.R
import com.kazumaproject.core.domain.state.InputMode

class InputModeSwitch(context: Context, attrs: AttributeSet) :
    AppCompatImageButton(context, attrs) {

    private var currentInputMode: InputMode = InputMode.ModeJapanese

    fun setInputMode(inputMode: InputMode, isTablet: Boolean) {
        currentInputMode = inputMode
        val resId = when (inputMode) {
            InputMode.ModeJapanese -> if (isTablet) R.drawable.input_mode_japanese_select_tablet else R.drawable.input_mode_japanese_select
            InputMode.ModeEnglish -> if (isTablet) R.drawable.input_mode_english_select_tablet else R.drawable.input_mode_english_select
            InputMode.ModeNumber -> if (isTablet) R.drawable.input_mode_number_select_tablet else R.drawable.input_mode_number_select
        }
        val drawable = AppCompatResources.getDrawable(context, resId)
        setImageDrawable(drawable)
    }
}