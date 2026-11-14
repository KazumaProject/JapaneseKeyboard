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
    val engine: String = "system",
    val modelPath: String = "",
    val backend: String = "cpu",
    val textReplacementEnabled: Boolean = false,
    val textReplacementRules: List<VoiceInputReplacementRule> = emptyList(),
) {
    private val orderedRules by lazy {
        textReplacementRules.filter { it.source.isNotEmpty() }
            .distinctBy { it.source }.sortedByDescending { it.source.length }
    }

    fun transform(text: String, recognitionLanguage: String): String {
        val spacedText = if (enabled && recognitionLanguage.substringBefore('-').equals("ja", ignoreCase = true)) {
            val separator = if (replacement == VoiceInputSpaceReplacement.CUSTOM) customText else replacement.text
            text.replace(" ", separator)
        } else {
            text
        }
        if (!textReplacementEnabled || orderedRules.isEmpty()) return spacedText
        // Match only the input: inserted text is never passed through another rule.
        // At each position the longest matching source wins, regardless of list order.
        return buildString {
            var position = 0
            while (position < spacedText.length) {
                val rule = orderedRules.firstOrNull { spacedText.startsWith(it.source, position) }
                if (rule == null) {
                    append(spacedText[position++])
                } else {
                    append(rule.replacement)
                    position += rule.source.length
                }
            }
        }
    }
}
