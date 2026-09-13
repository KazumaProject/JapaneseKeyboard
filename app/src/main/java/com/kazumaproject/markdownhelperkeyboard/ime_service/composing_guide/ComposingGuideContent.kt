package com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide

internal data class ComposingGuideContent(
    val text: String = "",
    val reading: String = "",
    val liveConversion: Boolean = false,
) {
    fun visibleReading(showComposing: Boolean, showReading: Boolean): String =
        if (showComposing && showReading && liveConversion && text.isNotEmpty()) reading else ""
}
