package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import androidx.activity.ComponentActivity
import androidx.hilt.navigation.HiltViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import com.kazumaproject.markdownhelperkeyboard.R

internal fun NavController.keyboardEditorViewModel(activity: ComponentActivity): KeyboardEditorViewModel {
    // Share the draft with the key editor for the lifetime of this layout editor entry.
    val entry = getBackStackEntry(R.id.keyboardEditorFragment)
    return ViewModelProvider(entry, HiltViewModelFactory(activity, entry))[KeyboardEditorViewModel::class.java]
}
