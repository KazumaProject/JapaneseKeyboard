package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.ui

import android.view.View
import android.widget.AdapterView
import android.widget.Spinner

internal fun Spinner.setOnItemSelectedListenerCompat(onSelected: () -> Unit) {
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) = onSelected()

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}
