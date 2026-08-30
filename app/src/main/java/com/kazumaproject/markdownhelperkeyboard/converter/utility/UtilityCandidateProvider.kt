package com.kazumaproject.markdownhelperkeyboard.converter.utility

import java.math.MathContext
import java.math.RoundingMode

class UtilityCandidateProvider(
    private val calculationParser: CalculationParser = CalculationParser(),
    private val unitRegistry: UnitRegistry = UnitRegistry.Default,
    private val unitExpressionParser: UnitExpressionParser = UnitExpressionParser(unitRegistry),
) {
    private val conversionContext = MathContext(50, RoundingMode.HALF_EVEN)

    fun provide(
        input: String,
        config: UtilityCandidateConfig = UtilityCandidateConfig(),
    ): UtilityCandidateResult {
        if (!isWithinInputLimits(input)) return UtilityCandidateResult.Empty
        val normalized = UtilityInputNormalizer.normalize(input)
        if (normalized.isBlank()) return UtilityCandidateResult.Empty

        val calculation = calculationTrigger(normalized)
        if (calculation != null) {
            if (!config.calculationEnabled) return UtilityCandidateResult.Empty
            return provideCalculation(calculation, config)
        }
        if (containsBoundaryEquals(normalized)) return UtilityCandidateResult.Empty
        if (!config.unitConversionEnabled) return UtilityCandidateResult.Empty
        return provideUnitConversion(normalized, config)
    }

    private data class CalculationTrigger(val expression: String, val prefix: Boolean)

    private fun calculationTrigger(input: String): CalculationTrigger? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val prefix = trimmed.first() == '='
        val suffix = trimmed.last() == '='
        if (prefix == suffix) return null
        val expression = if (prefix) trimmed.drop(1).trim() else trimmed.dropLast(1).trim()
        if (expression.isEmpty() || '=' in expression) return null
        return CalculationTrigger(expression, prefix)
    }

    private fun containsBoundaryEquals(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.startsWith('=') || trimmed.endsWith('=') || '=' in trimmed
    }

    private fun provideCalculation(
        trigger: CalculationTrigger,
        config: UtilityCandidateConfig,
    ): UtilityCandidateResult {
        val value = calculationParser.calculate(trigger.expression, config.angleMode)
            ?: return UtilityCandidateResult.Empty
        val result = ResultFormatter.format(value, config.calculationPrecision)
        val candidates = buildList {
            add(UtilityCandidate(result, UtilityCandidateKind.CALCULATION))
            if (config.includeExpressionCandidate) {
                val displayExpression = trigger.expression
                    .replace("*", "×")
                    .replace("/", "÷")
                val expressionCandidate = if (trigger.prefix) {
                    "$result=$displayExpression"
                } else {
                    "$displayExpression=$result"
                }
                if (expressionCandidate != result) {
                    add(UtilityCandidate(expressionCandidate, UtilityCandidateKind.CALCULATION))
                }
            }
        }
        return UtilityCandidateResult(candidates, UtilityTrigger.EXPLICIT_CALCULATION)
    }

    private fun provideUnitConversion(
        normalizedInput: String,
        config: UtilityCandidateConfig,
    ): UtilityCandidateResult {
        val split = splitExplicitConversion(normalizedInput)
        if (split != null) {
            val parsed = unitExpressionParser.parse(split.source, config.regionalUnitProfile)
                ?: return UtilityCandidateResult.Empty
            val target = unitRegistry.findExact(
                split.target,
                parsed.category,
                config.regionalUnitProfile,
            ) ?: return UtilityCandidateResult.Empty
            val precision = config.unitTargets[parsed.category]
                ?.firstOrNull { it.unitId == target.id }
                ?.precision
                ?: Precision.Auto
            val text = formatConversion(parsed, target, precision) ?: return UtilityCandidateResult.Empty
            return UtilityCandidateResult(
                listOf(UtilityCandidate(text, UtilityCandidateKind.UNIT_CONVERSION)),
                UtilityTrigger.EXPLICIT_UNIT_CONVERSION,
            )
        }
        if (hasExplicitConversionSyntax(normalizedInput)) return UtilityCandidateResult.Empty

        val parsed = unitExpressionParser.parse(normalizedInput.trim(), config.regionalUnitProfile)
            ?: return UtilityCandidateResult.Empty
        val targets = config.unitTargets[parsed.category].orEmpty().take(
            UtilityCandidateConfig.MAX_TARGETS_PER_CATEGORY
        )
        if (targets.isEmpty()) return UtilityCandidateResult.Empty
        val onlySourceId = parsed.terms.singleOrNull()?.unit?.id
        val candidates = targets.mapNotNull { setting ->
            val target = unitRegistry.findById(setting.unitId) ?: return@mapNotNull null
            if (target.category != parsed.category || target.id == onlySourceId) return@mapNotNull null
            formatConversion(parsed, target, setting.precision)?.let {
                UtilityCandidate(it, UtilityCandidateKind.UNIT_CONVERSION)
            }
        }.distinctBy(UtilityCandidate::text)
        return if (candidates.isEmpty()) {
            UtilityCandidateResult.Empty
        } else {
            UtilityCandidateResult(candidates, UtilityTrigger.AUTOMATIC_UNIT_CONVERSION)
        }
    }

    private data class ExplicitConversion(val source: String, val target: String)

    private fun splitExplicitConversion(input: String): ExplicitConversion? {
        val trimmed = input.trim()
        if (trimmed.count { it == '>' } == 1) {
            val position = trimmed.indexOf('>')
            val source = trimmed.substring(0, position).trim()
            val target = trimmed.substring(position + 1).trim()
            return if (source.isNotEmpty() && target.isNotEmpty()) {
                ExplicitConversion(source, target)
            } else null
        }
        val matches = TO_SEPARATOR.findAll(trimmed).toList()
        if (matches.size != 1) return null
        val match = matches.single()
        val source = trimmed.substring(0, match.range.first).trim()
        val target = trimmed.substring(match.range.last + 1).trim()
        return if (source.isNotEmpty() && target.isNotEmpty()) {
            ExplicitConversion(source, target)
        } else null
    }

    private fun hasExplicitConversionSyntax(input: String): Boolean =
        '>' in input || TO_SEPARATOR.containsMatchIn(input)

    private fun formatConversion(
        parsed: ParsedUnitExpression,
        target: UnitDefinition,
        precision: Precision,
    ): String? = try {
        val converted = target.fromBase(parsed.baseValue, conversionContext)
        ResultFormatter.format(converted, precision) + target.symbol
    } catch (_: ArithmeticException) {
        null
    }

    private fun isWithinInputLimits(input: String): Boolean =
        input.length <= MAX_UTF16_LENGTH &&
            input.codePointCount(0, input.length) <= MAX_CODE_POINTS

    private companion object {
        const val MAX_UTF16_LENGTH = 255
        const val MAX_CODE_POINTS = 200
        val TO_SEPARATOR = Regex("\\s+to\\s+", RegexOption.IGNORE_CASE)
    }
}
