package com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input

enum class VoiceInputSpaceReplacement(val preferenceValue: String, val text: String) {
    REMOVE("remove", ""),
    COMMA("comma", "、"),
    PERIOD("period", "。"),
    NEWLINE("newline", "\n"),
    FULL_WIDTH_SPACE("full_width_space", "　"),
    CUSTOM("custom", "");

    companion object {
        fun fromPreference(value: String?): VoiceInputSpaceReplacement =
            entries.firstOrNull { it.preferenceValue == value } ?: REMOVE
    }
}

data class VoiceInputSettings(
    val enabled: Boolean = false,
    val replacement: VoiceInputSpaceReplacement = VoiceInputSpaceReplacement.REMOVE,
    val customText: String = "",
) {
    fun transform(text: String, recognitionLanguage: String): String {
        if (!enabled || !recognitionLanguage.substringBefore('-').equals("ja", ignoreCase = true)) {
            return text
        }
        val separator = if (replacement == VoiceInputSpaceReplacement.CUSTOM) {
            customText
        } else {
            replacement.text
        }
        return text.replace(" ", separator)
    }
}
