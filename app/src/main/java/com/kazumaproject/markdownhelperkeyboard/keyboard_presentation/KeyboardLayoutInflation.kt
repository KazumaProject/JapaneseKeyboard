package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Docked IME views retain their inflated XML across rotation; previews must use that XML too. */
internal object KeyboardLayoutInflation {
    private var owner: WeakReference<Any>? = null
    private val orientationState = MutableStateFlow<Int?>(null)
    val mainOrientation: StateFlow<Int?> = orientationState

    fun recordMain(instance: Any, orientation: Int) {
        owner = WeakReference(instance)
        orientationState.value = orientation
    }

    fun clear(instance: Any) {
        if (owner?.get() !== instance) return
        owner = null
        orientationState.value = null
    }
}
