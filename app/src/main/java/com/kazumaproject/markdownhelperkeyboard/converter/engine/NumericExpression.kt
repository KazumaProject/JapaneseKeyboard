package com.kazumaproject.markdownhelperkeyboard.converter.engine

import java.math.BigInteger

/** The semantic category of a numeric suffix. */
enum class NumericSuffixType {
    COUNTER,
    UNIT,
    CURRENCY,
    TIME,
    DATE,
    ORDINAL,
    OTHER,
}

enum class SuffixStyle {
    CANONICAL,
    HIRAGANA,
    KATAKANA,
}

enum class NumberStyle {
    ASCII,
    FULL_WIDTH,
    KANJI,
    MIXED_KANJI,
    COMMA,
}

enum class NumericPhonologicalFeature {
    GEMINATE,
}

data class NumericValue(
    val value: BigInteger,
    val sourceReading: String? = null,
    val sourceDigits: String? = null,
    val phonologicalFeatures: Set<NumericPhonologicalFeature> = emptySet(),
    /** UTF-16 range occupied by the number in the original reading. */
    val inputStart: Int = 0,
    val inputEnd: Int = 0,
)

data class NumericSuffix(
    val definitionId: String,
    val matchedReading: String,
    val surface: String,
    val type: NumericSuffixType,
    val inputStart: Int,
    val inputEnd: Int,
)

data class NumericExpression(
    val number: NumericValue,
    val suffixes: List<NumericSuffix>,
)

data class NumericSurface(
    val style: SuffixStyle,
    val surface: String,
)

/** A data-only rule describing one accepted reading of a suffix. */
data class NumericReadingRule(
    val reading: String,
    val condition: NumericCondition = NumericCondition.Always,
    /** Non-null rules represent lexicalized readings such as 「ひとり」 or 「ふつか」. */
    val wholeExpressionValue: BigInteger? = null,
)

sealed interface NumericCondition {
    data object Always : NumericCondition

    data class ValueEquals(val value: BigInteger) : NumericCondition

    data class LastDigitIn(val digits: Set<Int>) : NumericCondition

    data class ModuloIn(
        val modulo: Int,
        val values: Set<Int>,
    ) : NumericCondition

    data class SourceReadingEndsWith(val readings: Set<String>) : NumericCondition

    data class FeatureIn(val features: Set<NumericPhonologicalFeature>) : NumericCondition

    data class AllOf(val conditions: List<NumericCondition>) : NumericCondition

    data class AnyOf(val conditions: List<NumericCondition>) : NumericCondition
}

/** Data-driven constraints for suffix chains such as 「時間」 + 「半」. */
data class NumericCompositionRule(
    val allowedPreviousTypes: Set<NumericSuffixType>? = null,
    val allowedPreviousIds: Set<String>? = null,
) {
    fun allows(previous: List<NumericSuffix>): Boolean {
        if (previous.isEmpty()) {
            return allowedPreviousTypes == null && allowedPreviousIds == null
        }
        val previousSuffix = previous.last()
        val typeAllowed = allowedPreviousTypes?.contains(previousSuffix.type) ?: true
        val idAllowed = allowedPreviousIds?.contains(previousSuffix.definitionId) ?: true
        return typeAllowed && idAllowed
    }

    companion object {
        fun none(): NumericCompositionRule = NumericCompositionRule()
    }
}

/** A suffix definition contains vocabulary and rules only; it contains no conversion logic. */
data class NumericSuffixDefinition(
    val id: String,
    val type: NumericSuffixType,
    val surfaces: List<NumericSurface>,
    val allowedStyles: Set<SuffixStyle>,
    val readingRules: List<NumericReadingRule>,
    val composition: NumericCompositionRule = NumericCompositionRule.none(),
    /** Optional dictionary POS ids used when the rendered candidate enters the main lattice. */
    val leftId: Short? = null,
    val rightId: Short? = null,
) {
    val canonicalSurfaces: List<String>
        get() = surfaces.filter { it.style == SuffixStyle.CANONICAL }.map { it.surface }
}

/**
 * The parser and renderer depend on this abstraction rather than on a fixed counter list.
 * A catalog is vocabulary data and can be replaced in tests or by a future dictionary loader
 * without changing the conversion algorithms.
 */
interface NumericSuffixCatalog {
    val definitions: List<NumericSuffixDefinition>

    fun definition(id: String): NumericSuffixDefinition? =
        definitions.firstOrNull { it.id == id }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        val ids = definitions.map { it.id }.toSet()
        val duplicateIds = definitions.groupingBy { it.id }.eachCount().filterValues { it > 1 }
        duplicateIds.keys.forEach { errors += "duplicate suffix id: $it" }
        definitions.forEach { definition ->
            if (definition.id.isBlank()) errors += "blank suffix id"
            if (definition.surfaces.isEmpty()) errors += "no surface for ${definition.id}"
            definition.surfaces.forEach { surface ->
                if (surface.surface.isBlank()) {
                    errors += "blank surface for ${definition.id}"
                }
                if (surface.style !in definition.allowedStyles) {
                    errors += "surface style ${surface.style} is not allowed for ${definition.id}"
                }
            }
            if (definition.surfaces.none { it.style == SuffixStyle.CANONICAL }) {
                errors += "no canonical surface for ${definition.id}"
            }
            if (definition.allowedStyles.isEmpty()) {
                errors += "no allowed suffix style for ${definition.id}"
            }
            if (SuffixStyle.CANONICAL !in definition.allowedStyles) {
                errors += "canonical style is not allowed for ${definition.id}"
            }
            if (definition.readingRules.isEmpty()) {
                errors += "no reading rule for ${definition.id}"
            }
            definition.readingRules.forEach { rule ->
                if (rule.reading.isBlank()) errors += "blank reading for ${definition.id}"
            }
            definition.composition.allowedPreviousIds.orEmpty().forEach { previousId ->
                if (previousId !in ids) {
                    errors += "unknown previous suffix $previousId for ${definition.id}"
                }
            }
        }
        return errors
    }
}
