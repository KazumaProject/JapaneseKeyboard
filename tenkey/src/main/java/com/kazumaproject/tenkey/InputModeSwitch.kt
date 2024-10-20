package com.kazumaproject.tenkey

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.kazumaproject.tenkey.state.InputMode

class InputModeSwitch(context: Context, attrs: AttributeSet) :
    androidx.appcompat.widget.AppCompatImageButton(context, attrs) {

    private var currentInputMode: InputMode = InputMode.ModeJapanese

    fun setInputMode(inputMode: InputMode) {
        currentInputMode = inputMode
        when (inputMode) {
            InputMode.ModeJapanese -> setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.input_mode_japanese_select
                )
            )

            InputMode.ModeEnglish -> setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.input_mode_english_select
                )
            )

            InputMode.ModeNumber -> setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.input_mode_number_select
                )
            )
        }
    }

    fun getCurrentInputMode(): InputMode {
        return currentInputMode
    }


}