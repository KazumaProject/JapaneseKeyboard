package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

data class GemmaHandwritingSettings(
    val autoRecognitionDelayMs: Long = DEFAULT_AUTO_RECOGNITION_DELAY_MS,
    val recognitionLanguage: GemmaHandwritingLanguage = GemmaHandwritingLanguage.AUTO,
    val additionalInstruction: String = "",
    val penSizeDp: Int = DEFAULT_PEN_SIZE_DP,
    val penColorArgb: Int = AUTOMATIC_PEN_COLOR,
) {
    val recognitionPrompt: String
        get() = GemmaHandwritingPrompt.build(
            language = recognitionLanguage,
            additionalInstruction = additionalInstruction,
        )

    val singleCharacterRecognitionPrompt: String
        get() = GemmaHandwritingPrompt.buildSingleCharacter(
            language = recognitionLanguage,
            additionalInstruction = additionalInstruction,
        )

    fun resolvedPenColor(darkMode: Boolean): Int {
        return when {
            penColorArgb != AUTOMATIC_PEN_COLOR -> penColorArgb
            darkMode -> DARK_MODE_AUTOMATIC_PEN_COLOR
            else -> LIGHT_MODE_AUTOMATIC_PEN_COLOR
        }
    }

    fun resolvedRecognitionPenColor(): Int = resolvedPenColor(darkMode = false)

    companion object {
        const val MIN_AUTO_RECOGNITION_DELAY_MS = 300
        const val MAX_AUTO_RECOGNITION_DELAY_MS = 5_000
        const val DEFAULT_AUTO_RECOGNITION_DELAY_MS = 900L
        const val MAX_ADDITIONAL_INSTRUCTION_LENGTH = 1_000
        const val MIN_PEN_SIZE_DP = 1
        const val MAX_PEN_SIZE_DP = 20
        const val DEFAULT_PEN_SIZE_DP = 5
        const val AUTOMATIC_PEN_COLOR = 0
        const val LIGHT_MODE_AUTOMATIC_PEN_COLOR = -0x1000000
        const val DARK_MODE_AUTOMATIC_PEN_COLOR = -0x1

        fun normalized(
            autoRecognitionDelayMs: Int,
            recognitionLanguage: String?,
            additionalInstruction: String?,
            penSizeDp: Int = DEFAULT_PEN_SIZE_DP,
            penColorArgb: Int = AUTOMATIC_PEN_COLOR,
        ): GemmaHandwritingSettings {
            return GemmaHandwritingSettings(
                autoRecognitionDelayMs = autoRecognitionDelayMs
                    .coerceIn(
                        MIN_AUTO_RECOGNITION_DELAY_MS,
                        MAX_AUTO_RECOGNITION_DELAY_MS,
                    )
                    .toLong(),
                recognitionLanguage =
                    GemmaHandwritingLanguage.fromPreference(recognitionLanguage),
                additionalInstruction = additionalInstruction
                    .orEmpty()
                    .trim()
                    .take(MAX_ADDITIONAL_INSTRUCTION_LENGTH),
                penSizeDp = penSizeDp.coerceIn(MIN_PEN_SIZE_DP, MAX_PEN_SIZE_DP),
                penColorArgb = normalizePenColor(penColorArgb),
            )
        }

        fun normalizePenColor(color: Int): Int {
            return if (color == AUTOMATIC_PEN_COLOR) {
                AUTOMATIC_PEN_COLOR
            } else {
                color or LIGHT_MODE_AUTOMATIC_PEN_COLOR
            }
        }
    }
}
