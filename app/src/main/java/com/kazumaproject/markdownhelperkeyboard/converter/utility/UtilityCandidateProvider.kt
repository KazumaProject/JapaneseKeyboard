package com.kazumaproject.markdownhelperkeyboard.converter.utility

import java.math.MathContext
import java.math.RoundingMode

class UtilityCandidateProvider(
    private val calculationParser: CalculationParser = CalculationParser(),
    private val formulaParser: FormulaParser = FormulaParser(),
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
        if (config.formulaCandidateEnabled && !looksLikeUnitExpression(normalized, config)) {
            val formulaInput = UtilityInputNormalizer.normalizeForFormula(input)
            formulaParser.parse(formulaInput)
                ?.takeIf {
                    it.unicodeText != formulaInput.trim() ||
                        it.normalizedTex != formulaInput.trim()
                }
                ?.let { formula ->
                return provideFormula(formula)
            }
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

    /** Keep quantities and conversion syntax on the existing unit-conversion path. */
    private fun looksLikeUnitExpression(
        input: String,
        config: UtilityCandidateConfig,
    ): Boolean {
        val explicit = splitExplicitConversion(input)
        if (explicit != null) {
            val parsed = unitExpressionParser.parse(explicit.source, config.regionalUnitProfile)
            if (parsed != null && unitRegistry.findExact(
                    explicit.target,
                    parsed.category,
                    config.regionalUnitProfile,
                ) != null
            ) {
                return true
            }
        }

        var offset = 0
        while (offset < input.length) {
            if (!input[offset].isDigit() && input[offset] != '.') {
                offset++
                continue
            }
            while (input.getOrNull(offset)?.isDigit() == true ||
                input.getOrNull(offset)?.let { it == '.' || it == ',' } == true
            ) {
                offset++
            }
            while (input.getOrNull(offset)?.isWhitespace() == true) offset++
            if (unitRegistry.matchAt(input, offset, profile = config.regionalUnitProfile) != null) {
                return true
            }
        }
        return false
    }

    private fun shouldUseFormattedCalculationExpression(expression: String): Boolean =
        expression.any { it == '^' || it == '_' || it == '√' || it == '|' || it == '‖' } ||
            expression.contains('\\') ||
            Regex("(?i)\\b(?:sqrt|root|nroot|sum|sigma|prod|product|int|integral|lim|abs|norm|floor|ceil|hat|bar|vec)\\b")
                .containsMatchIn(expression)

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
                val parsedFormula = if (config.formulaCandidateEnabled) {
                    formulaParser.parse(trigger.expression)
                } else {
                    null
                }
                val useFormattedExpression = parsedFormula != null &&
                    shouldUseFormattedCalculationExpression(trigger.expression)
                val displayExpression = parsedFormula
                    ?.takeIf { useFormattedExpression }
                    ?.unicodeText
                    ?: trigger.expression
                        .replace("*", "×")
                        .replace("/", "÷")
                val expressionCandidate = if (trigger.prefix) {
                    "$result=$displayExpression"
                } else {
                    "$displayExpression=$result"
                }
                if (expressionCandidate != result) {
                    val formulaPresentation = parsedFormula
                        ?.takeIf { useFormattedExpression }
                        ?.let { formula ->
                        val expressionUnicode = if (trigger.prefix) {
                            "$result=${formula.unicodeText}"
                        } else {
                            "${formula.unicodeText}=$result"
                        }
                        val expressionTex = if (trigger.prefix) {
                            "$result=${formula.normalizedTex}"
                        } else {
                            "${formula.normalizedTex}=$result"
                        }
                        FormulaCandidatePresentation(
                            ast = formulaRow(
                                listOf(
                                    FormulaNode.Number(result),
                                    FormulaNode.Symbol("="),
                                    formula.ast,
                                ).let { parts ->
                                    if (trigger.prefix) parts else listOf(formula.ast, FormulaNode.Symbol("="), FormulaNode.Number(result))
                                }
                            ),
                            unicodeText = expressionUnicode,
                            normalizedTex = expressionTex,
                            type = FormulaCandidateType.UNICODE,
                        )
                    }
                    add(
                        UtilityCandidate(
                            text = expressionCandidate,
                            kind = UtilityCandidateKind.CALCULATION,
                            formulaPresentation = formulaPresentation,
                        )
                    )
                }
            }
        }
        return UtilityCandidateResult(candidates, UtilityTrigger.EXPLICIT_CALCULATION)
    }

    private fun provideFormula(formula: ParsedFormula): UtilityCandidateResult {
        val candidates = listOf(
            UtilityCandidate(
                text = formula.unicodeText,
                kind = UtilityCandidateKind.FORMULA_UNICODE,
                formulaPresentation = formula.presentation(FormulaCandidateType.UNICODE),
            ),
            UtilityCandidate(
                text = formula.normalizedTex,
                kind = UtilityCandidateKind.FORMULA_TEX,
                formulaPresentation = formula.presentation(FormulaCandidateType.TEX),
            ),
        )
        return UtilityCandidateResult(
            candidates = candidates,
            trigger = UtilityTrigger.FORMULA,
        )
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
            UtilityCandidateResult(
                candidates = candidates,
                trigger = UtilityTrigger.AUTOMATIC_UNIT_CONVERSION,
                preferredSourceText = parsed.canonicalSourceText.takeIf {
                    parsed.prefersCanonicalSource && it != normalizedInput.trim()
                },
            )
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
