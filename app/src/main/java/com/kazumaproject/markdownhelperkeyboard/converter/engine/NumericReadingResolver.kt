package com.kazumaproject.markdownhelperkeyboard.converter.engine

import java.math.BigInteger

/** Evaluates the data-defined phonological and value conditions for a suffix reading. */
class NumericReadingResolver {

    fun resolve(
        definition: NumericSuffixDefinition,
        value: NumericValue,
        matchedReading: String,
        previousSuffixes: List<NumericSuffix> = emptyList(),
    ): List<NumericReadingRule> = definition.readingRules.filter { rule ->
        rule.wholeExpressionValue == null &&
            rule.reading == matchedReading &&
            matches(rule.condition, value, previousSuffixes)
    }

    fun resolveWholeExpression(
        definition: NumericSuffixDefinition,
        reading: String,
    ): List<NumericReadingRule> = definition.readingRules.filter { rule ->
        val value = rule.wholeExpressionValue
        value != null &&
            rule.reading == reading &&
            matches(
                condition = rule.condition,
                value = NumericValue(value = value, sourceReading = reading),
            )
    }

    fun matches(
        condition: NumericCondition,
        value: NumericValue,
        previousSuffixes: List<NumericSuffix> = emptyList(),
    ): Boolean = when (condition) {
        NumericCondition.Always -> true
        is NumericCondition.ValueEquals -> value.value == condition.value
        is NumericCondition.LastDigitIn -> value.value.mod(BigInteger.TEN).toInt() in condition.digits
        is NumericCondition.ModuloIn -> {
            condition.modulo > 0 &&
                value.value.mod(BigInteger.valueOf(condition.modulo.toLong())).toInt() in condition.values
        }

        is NumericCondition.SourceReadingEndsWith ->
            condition.readings.any { value.sourceReading?.endsWith(it) == true }

        is NumericCondition.FeatureIn ->
            value.phonologicalFeatures.any { it in condition.features }

        is NumericCondition.AllOf -> condition.conditions.all { matches(it, value, previousSuffixes) }
        is NumericCondition.AnyOf -> condition.conditions.any { matches(it, value, previousSuffixes) }
    }
}
