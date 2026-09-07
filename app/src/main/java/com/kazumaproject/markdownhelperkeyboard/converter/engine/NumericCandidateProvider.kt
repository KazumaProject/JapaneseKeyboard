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

    /** Explicit numeric spelling, including a following counter, opts into notation ordering. */
    fun shouldPrioritize(input: String): Boolean =
        input.firstOrNull()?.let { it in '0'..'9' || it in '０'..'９' } == true &&
            parser.parse(input).isNotEmpty()

    fun isLearnableConversion(input: String, output: String): Boolean =
        generate(input, showSymbolCandidates = false).any { it.string == output }
}
