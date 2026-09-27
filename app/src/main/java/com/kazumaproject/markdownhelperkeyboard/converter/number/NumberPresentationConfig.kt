package com.kazumaproject.markdownhelperkeyboard.converter.number

enum class NumberStyle {
    HALF_WIDTH,
    FULL_WIDTH,
    KANJI,
}

data class NumberPresentationConfig(
    val additionsEnabled: Boolean = true,
    val styleOrder: List<NumberStyle> = DEFAULT_STYLE_ORDER,
) {
    fun normalized(): NumberPresentationConfig = copy(
        styleOrder = styleOrder.distinct().let { distinct ->
            distinct + DEFAULT_STYLE_ORDER.filterNot(distinct::contains)
        },
    )

    companion object {
        val DEFAULT_STYLE_ORDER = listOf(
            NumberStyle.HALF_WIDTH,
            NumberStyle.FULL_WIDTH,
            NumberStyle.KANJI,
        )

        const val DEFAULT_STYLE_ORDER_KEY = "half_full_kanji"

        fun parseStyleOrder(value: String?): List<NumberStyle> = when (value) {
            "half_full_kanji" -> listOf(NumberStyle.HALF_WIDTH, NumberStyle.FULL_WIDTH, NumberStyle.KANJI)
            "half_kanji_full" -> listOf(NumberStyle.HALF_WIDTH, NumberStyle.KANJI, NumberStyle.FULL_WIDTH)
            "full_half_kanji" -> listOf(NumberStyle.FULL_WIDTH, NumberStyle.HALF_WIDTH, NumberStyle.KANJI)
            "full_kanji_half" -> listOf(NumberStyle.FULL_WIDTH, NumberStyle.KANJI, NumberStyle.HALF_WIDTH)
            "kanji_half_full" -> listOf(NumberStyle.KANJI, NumberStyle.HALF_WIDTH, NumberStyle.FULL_WIDTH)
            "kanji_full_half" -> listOf(NumberStyle.KANJI, NumberStyle.FULL_WIDTH, NumberStyle.HALF_WIDTH)
            else -> DEFAULT_STYLE_ORDER
        }
    }
}

enum class NumberCandidateOrigin {
    SYSTEM_PATH,
    ENGINE_SUPPLEMENT,
    PRESENTATION_VARIANT,
}

data class NumberCandidateMetadata(
    val familyKey: String,
    val origin: NumberCandidateOrigin,
    val style: NumberStyle? = null,
    val numericSpans: List<NumberSpan> = emptyList(),
    val isFallback: Boolean = false,
)

data class NumberSpan(
    val inputStart: Int,
    val inputEnd: Int,
    val outputStart: Int,
    val outputEnd: Int,
    val valueDigits: String,
    val digitSequence: Boolean,
    val commaSeparated: Boolean = false,
)

/** Shared family identity for a direct digit input and its numeric graph path. */
object NumberCandidateFamilyKey {
    fun directDigits(valueDigits: String, inputStart: Int = 0): String {
        val digitSequence = valueDigits.length > 1 && valueDigits.startsWith('0')
        return "numeric:<N:$valueDigits:${if (digitSequence) "digits" else "value"}>" +
            "<span:$inputStart-${inputStart + valueDigits.length}>"
    }
}
