package com.kazumaproject.markdownhelperkeyboard.ime_service.models

import com.kazumaproject.core.data.clicked_symbol.SymbolMode

data class SymbolKeyboardState(
    val isShown: Boolean = false,
    val mode: SymbolMode? = null,
) {
    fun initialMode(preferred: SymbolMode?): SymbolMode = mode ?: preferred ?: SymbolMode.EMOJI
}
