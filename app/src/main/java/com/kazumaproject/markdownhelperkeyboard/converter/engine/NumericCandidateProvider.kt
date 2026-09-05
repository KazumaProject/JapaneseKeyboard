package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/** Compatibility entry point for the engine while the numeric pipeline remains independently testable. */
internal object NumericCandidateProvider {

    private val parser = NumericExpressionParser(BundledNumericSuffixCatalog)
    private val renderer = NumericCandidateRenderer(BundledNumericSuffixCatalog)

    fun isDigitSequence(input: String): Boolean = NumericNumberParser.isDigitSequence(input)

    fun parse(input: String): List<NumericExpression> = parser.parse(input)

    fun generateRendered(
        input: String,
        notationPreference: NumericNotationPreference = NumericNotationPreference.HALF_WIDTH_FIRST,
        showSymbolCandidates: Boolean = true,
    ): List<RenderedNumericCandidate> = renderer.render(
        input = input,
        expressions = parser.parse(input),
        notationPreference = notationPreference,
        showSymbolCandidates = showSymbolCandidates,
    )

    fun generate(
        input: String,
        notationPreference: NumericNotationPreference = NumericNotationPreference.HALF_WIDTH_FIRST,
        showSymbolCandidates: Boolean = true,
    ): List<Candidate> = generateRendered(
        input = input,
        notationPreference = notationPreference,
        showSymbolCandidates = showSymbolCandidates,
    ).map { it.candidate }

    /**
     * Numeric preference is applied for explicit digits and unambiguous readings.  A very short
     * numeric prefix followed by a counter/unit is intentionally left to the lexical ranking;
     * this is a grammar-level ambiguity policy that applies equally to every catalog definition.
     */
    fun shouldPrioritize(input: String): Boolean {
        if (isDigitSequence(input)) return true
        val expressions = parser.parse(input)
        if (expressions.isEmpty()) return false
        return expressions.none { expression ->
            val sourceReading = expression.number.sourceReading ?: return@none false
            sourceReading.length <= 1 && expression.suffixes.any {
                it.type in setOf(
                    NumericSuffixType.COUNTER,
                    NumericSuffixType.TIME,
                    NumericSuffixType.ORDINAL,
                )
            }
        }
    }
}
