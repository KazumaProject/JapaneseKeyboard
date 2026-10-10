package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_dictionary

import android.annotation.SuppressLint
import android.content.Context
import android.widget.EditText

/** Local editors do not receive the application's IME selection callbacks. */
// This editor lives in an IME window with a platform theme, outside AppCompat activities.
@SuppressLint("AppCompatCustomView")
internal class FloatingDictionaryEditText(context: Context) : EditText(context) {
    var onSelectionChangedListener: ((EditText, Int, Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(this, selStart, selEnd)
    }
}
