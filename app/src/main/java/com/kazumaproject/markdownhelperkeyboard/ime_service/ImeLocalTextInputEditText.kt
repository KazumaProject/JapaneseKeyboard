package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import com.google.android.material.textfield.TextInputEditText

/** Selection changes in IME-local fields do not arrive through the application's callbacks. */
internal class ImeLocalTextInputEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : TextInputEditText(context, attrs) {
    var onSelectionChangedListener: ((EditText, Int, Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(this, selStart, selEnd)
    }
}
