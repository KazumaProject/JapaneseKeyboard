package com.kazumaproject.markdownhelperkeyboard.ime_service.state

data class ComposingTextState(
    val inputString: String = "",
    val cursorPosition: Int = 0
)
