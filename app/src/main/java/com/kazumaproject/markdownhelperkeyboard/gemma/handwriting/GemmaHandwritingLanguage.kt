package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

enum class GemmaHandwritingLanguage(
    val preferenceValue: String,
    val promptLanguageName: String?,
) {
    AUTO("auto", null),
    ENGLISH("en", "English"),
    JAPANESE("ja", "Japanese"),
    KOREAN("ko", "Korean"),
    CHINESE_SIMPLIFIED("zh-Hans", "Simplified Chinese"),
    CHINESE_TRADITIONAL("zh-Hant", "Traditional Chinese"),
    SPANISH("es", "Spanish"),
    FRENCH("fr", "French"),
    GERMAN("de", "German"),
    ITALIAN("it", "Italian"),
    PORTUGUESE("pt", "Portuguese"),
    RUSSIAN("ru", "Russian"),
    ARABIC("ar", "Arabic"),
    HINDI("hi", "Hindi"),
    INDONESIAN("id", "Indonesian"),
    THAI("th", "Thai"),
    VIETNAMESE("vi", "Vietnamese"),
    TURKISH("tr", "Turkish"),
    POLISH("pl", "Polish"),
    DUTCH("nl", "Dutch"),
    UKRAINIAN("uk", "Ukrainian"),
    ;

    fun promptInstruction(): String {
        val languageName = promptLanguageName
        return if (languageName == null) {
            """
                Infer the language or script only from the visible handwriting.
                Preserve the apparent language; never translate it.
            """.trimIndent()
        } else {
            """
                Use $languageName as the recognition context.
                Prefer characters and spelling from $languageName when a shape is ambiguous,
                but still preserve clearly visible digits, punctuation, and common symbols.
                Transcribe the visible text; never translate it.
            """.trimIndent()
        }
    }

    companion object {
        fun fromPreference(value: String?): GemmaHandwritingLanguage {
            return entries.firstOrNull { language -> language.preferenceValue == value } ?: AUTO
        }
    }
}
