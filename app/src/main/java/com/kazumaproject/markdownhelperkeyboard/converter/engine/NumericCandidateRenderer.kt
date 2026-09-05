package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.createValueBasedSymbolCandidates

private const val NUMERIC_POS_ID_COUNTER_GENERIC: Short = 2011
private const val NUMERIC_POS_ID_COUNTER_TIME: Short = 2015
private const val NUMERIC_POS_ID_NUMBER_ARABIC: Short = 2044
private const val NUMERIC_POS_ID_NUMBER_SEPARATED: Short = 2045
private const val NUMERIC_POS_ID_NUMBER_KANJI: Short = 2046

private const val NUMERIC_TYPE_KANJI_MIXED: Byte = 17
private const val NUMERIC_TYPE_EXPONENT_OR_SUBSCRIPT: Byte = 20
private const val NUMERIC_TYPE_SUPERSCRIPT: Byte = 21
private const val NUMERIC_TYPE_FULL_WIDTH: Byte = 30
private const val NUMERIC_TYPE_HALF_WIDTH: Byte = 31
private const val NUMERIC_TYPE_KANJI: Byte = 32
private const val NUMERIC_TYPE_SEPARATED: Byte = 19

private const val NUMERIC_BASE_SCORE = 700

data class RenderedNumericCandidate(
    val candidate: Candidate,
    val segments: List<CandidateConversionSegment>,
    val expression: NumericExpression,
)

/** Renders a semantic expression into the configured numeric notation variants. */
class NumericCandidateRenderer(
    private val catalog: NumericSuffixCatalog,
    private val readingResolver: NumericReadingResolver = NumericReadingResolver(),
) {

    init {
        require(catalog.validate().isEmpty()) { catalog.validate().joinToString() }
    }

    private data class PrimaryForm(
        val style: NumberStyle,
        val number: String,
    )

    fun render(
        input: String,
        expressions: List<NumericExpression>,
        notationPreference: NumericNotationPreference = NumericNotationPreference.HALF_WIDTH_FIRST,
        showSymbolCandidates: Boolean = true,
    ): List<RenderedNumericCandidate> {
        if (expressions.isEmpty()) return emptyList()

        val results = ArrayList<RenderedNumericCandidate>()
        val seen = LinkedHashSet<String>()
        expressions.forEach { expression ->
            val canonicalSuffixVariants = renderSuffixVariants(expression, canonicalOnly = true)
            val suffixVariants = renderSuffixVariants(expression, canonicalOnly = false)
                .filterNot { it in canonicalSuffixVariants }
            val primaryForms = primaryForms(expression.number, notationPreference)
            primaryForms.forEach { form ->
                canonicalSuffixVariants.forEach suffixLoop@{ suffixes ->
                    val string = form.number + suffixes.joinToString("")
                    if (!seen.add(string)) return@suffixLoop
                    results += renderedCandidate(
                        input = input,
                        expression = expression,
                        numberStyle = form.style,
                        string = string,
                        suffixOutputs = suffixes,
                        rank = results.size,
                    )
                }
            }
            primaryForms.forEach { form ->
                suffixVariants.forEach suffixLoop@{ suffixes ->
                    val string = form.number + suffixes.joinToString("")
                    if (!seen.add(string)) return@suffixLoop
                    results += renderedCandidate(
                        input = input,
                        expression = expression,
                        numberStyle = form.style,
                        string = string,
                        suffixOutputs = suffixes,
                        rank = results.size,
                    )
                }
            }

            if (expression.suffixes.isEmpty()) {
                renderExtendedNumberCandidates(
                    input = input,
                    expression = expression,
                    seen = seen,
                    results = results,
                    showSymbolCandidates = showSymbolCandidates,
                )
            }
        }
        return results
    }

    private fun primaryForms(
        number: NumericValue,
        notationPreference: NumericNotationPreference,
    ): List<PrimaryForm> {
        val digits = number.sourceDigits ?: number.value.toString()
        val forms = linkedMapOf<NumberStyle, String>()
        val orderedStyles = when (notationPreference) {
            NumericNotationPreference.HALF_WIDTH_FIRST -> listOf(
                NumberStyle.ASCII,
                NumberStyle.FULL_WIDTH,
                NumberStyle.KANJI,
                NumberStyle.MIXED_KANJI,
                NumberStyle.COMMA,
            )

            NumericNotationPreference.FULL_WIDTH_FIRST -> listOf(
                NumberStyle.FULL_WIDTH,
                NumberStyle.ASCII,
                NumberStyle.KANJI,
                NumberStyle.MIXED_KANJI,
                NumberStyle.COMMA,
            )

            NumericNotationPreference.KANJI_FIRST -> listOf(
                NumberStyle.KANJI,
                NumberStyle.ASCII,
                NumberStyle.FULL_WIDTH,
                NumberStyle.MIXED_KANJI,
                NumberStyle.COMMA,
            )
        }

        orderedStyles.forEach { style ->
            val rendered = when (style) {
                NumberStyle.ASCII -> digits
                NumberStyle.FULL_WIDTH -> NumericNumberFormatter.toFullWidthDigits(digits)
                NumberStyle.KANJI -> NumericNumberFormatter.toJapaneseKanji(number.value)
                NumberStyle.MIXED_KANJI -> NumericNumberFormatter.toMixedKanji(number.value)
                NumberStyle.COMMA -> NumericNumberFormatter.addDigitGrouping(digits)
            }
            forms.putIfAbsent(style, rendered)
        }
        return forms.map { (style, value) -> PrimaryForm(style, value) }
    }

    private fun renderSuffixVariants(
        expression: NumericExpression,
        canonicalOnly: Boolean,
    ): List<List<String>> {
        if (expression.suffixes.isEmpty()) return listOf(emptyList())

        var variants: List<List<String>> = listOf(emptyList())
        expression.suffixes.forEachIndexed suffixLoop@{ index, suffix ->
            val definition = catalog.definition(suffix.definitionId) ?: return@suffixLoop
            val surfaces = suffixSurfaces(
                suffix = suffix,
                definition = definition,
                canonicalOnly = canonicalOnly,
                number = expression.number,
                previousSuffixes = expression.suffixes.take(index),
            )
            variants = variants.flatMap { prefix -> surfaces.map { prefix + it } }
        }
        return variants.distinct()
    }

    private fun suffixSurfaces(
        suffix: NumericSuffix,
        definition: NumericSuffixDefinition,
        canonicalOnly: Boolean,
        number: NumericValue,
        previousSuffixes: List<NumericSuffix>,
    ): List<String> {
        val surfaces = LinkedHashSet<String>()
        if (SuffixStyle.CANONICAL in definition.allowedStyles) {
            definition.surfaces
                .filter { it.style == SuffixStyle.CANONICAL }
                .forEach { surfaces += it.surface }
        }
        if (canonicalOnly) return surfaces.toList()
        val validReadings = definition.readingRules
            .asSequence()
            .filter { it.wholeExpressionValue == null }
            .filter {
                readingResolver.matches(
                    condition = it.condition,
                    value = number,
                    previousSuffixes = previousSuffixes,
                )
            }
            .map { it.reading }
            .toList()
            .let { readings -> listOf(suffix.matchedReading) + readings }
            .distinct()
        if (SuffixStyle.HIRAGANA in definition.allowedStyles) {
            definition.surfaces
                .filter { it.style == SuffixStyle.HIRAGANA }
                .forEach { surfaces += it.surface }
            validReadings.forEach { reading -> surfaces += reading.toHiragana() }
        }
        if (SuffixStyle.KATAKANA in definition.allowedStyles) {
            definition.surfaces
                .filter { it.style == SuffixStyle.KATAKANA }
                .forEach { surfaces += it.surface }
            validReadings.forEach { reading -> surfaces += reading.toKatakana() }
        }
        return surfaces.toList()
    }

    private fun renderedCandidate(
        input: String,
        expression: NumericExpression,
        numberStyle: NumberStyle,
        string: String,
        suffixOutputs: List<String>,
        rank: Int,
    ): RenderedNumericCandidate {
        val timeLike = expression.suffixes.any { it.type == NumericSuffixType.TIME }
        val type = when (numberStyle) {
            NumberStyle.ASCII -> if (timeLike) CANDIDATE_TYPE_TIME else NUMERIC_TYPE_HALF_WIDTH
            NumberStyle.FULL_WIDTH -> NUMERIC_TYPE_FULL_WIDTH
            NumberStyle.KANJI -> NUMERIC_TYPE_KANJI
            NumberStyle.MIXED_KANJI -> NUMERIC_TYPE_KANJI_MIXED
            NumberStyle.COMMA -> NUMERIC_TYPE_SEPARATED
        }
        val leftId = when (numberStyle) {
            NumberStyle.COMMA -> NUMERIC_POS_ID_NUMBER_SEPARATED
            NumberStyle.KANJI, NumberStyle.MIXED_KANJI -> NUMERIC_POS_ID_NUMBER_KANJI
            else -> NUMERIC_POS_ID_NUMBER_ARABIC
        }
        val lastSuffixDefinition = expression.suffixes.lastOrNull()?.let {
            catalog.definition(it.definitionId)
        }
        val rightId = if (expression.suffixes.isNotEmpty()) {
            lastSuffixDefinition?.rightId ?: if (timeLike) {
                NUMERIC_POS_ID_COUNTER_TIME
            } else {
                NUMERIC_POS_ID_COUNTER_GENERIC
            }
        } else {
            when (numberStyle) {
                NumberStyle.COMMA -> NUMERIC_POS_ID_NUMBER_SEPARATED
                NumberStyle.KANJI, NumberStyle.MIXED_KANJI -> NUMERIC_POS_ID_NUMBER_KANJI
                else -> NUMERIC_POS_ID_NUMBER_ARABIC
            }
        }

        return RenderedNumericCandidate(
            candidate = Candidate(
                string = string,
                type = type,
                length = input.length.toUByte(),
                score = NUMERIC_BASE_SCORE + rank,
                leftId = leftId,
                rightId = rightId,
            ),
            segments = createSegments(expression, string, suffixOutputs),
            expression = expression,
        )
    }

    private fun createSegments(
        expression: NumericExpression,
        string: String,
        suffixOutputs: List<String>,
    ): List<CandidateConversionSegment> {
        val number = expression.number
        val suffixes = expression.suffixes
        if (
            suffixes.size == 1 &&
            number.inputStart == suffixes.first().inputStart &&
            number.inputEnd == suffixes.first().inputEnd
        ) {
            return listOf(
                CandidateConversionSegment(
                    inputStart = number.inputStart,
                    inputEnd = number.inputEnd,
                    output = string,
                ),
            )
        }

        val segments = mutableListOf<CandidateConversionSegment>()
        if (number.inputEnd > number.inputStart) {
            val numberOutputLength = string.length - suffixOutputs.sumOf { it.length }
            segments += CandidateConversionSegment(
                inputStart = number.inputStart,
                inputEnd = number.inputEnd,
                output = string.take(numberOutputLength),
            )
        }
        suffixes.forEachIndexed { index, suffix ->
            segments += CandidateConversionSegment(
                inputStart = suffix.inputStart,
                inputEnd = suffix.inputEnd,
                output = suffixOutputs.getOrElse(index) { suffix.surface },
            )
        }
        return segments
    }

    private fun renderExtendedNumberCandidates(
        input: String,
        expression: NumericExpression,
        seen: MutableSet<String>,
        results: MutableList<RenderedNumericCandidate>,
        showSymbolCandidates: Boolean,
    ) {
        val number = expression.number
        val digits = number.sourceDigits ?: number.value.toString()
        val exponent = NumericNumberFormatter.toExponentOrNull(number.value)
        if (exponent != null && seen.add(exponent)) {
            val candidate = Candidate(
                string = exponent,
                type = NUMERIC_TYPE_EXPONENT_OR_SUBSCRIPT,
                length = input.length.toUByte(),
                score = NUMERIC_BASE_SCORE + results.size,
                leftId = NUMERIC_POS_ID_NUMBER_ARABIC,
                rightId = NUMERIC_POS_ID_NUMBER_ARABIC,
            )
            results += RenderedNumericCandidate(
                candidate = candidate,
                segments = listOf(
                    CandidateConversionSegment(number.inputStart, number.inputEnd, exponent),
                ),
                expression = expression,
            )
        }

        listOf(
            NumericNumberFormatter.toSuperscript(digits) to NUMERIC_TYPE_SUPERSCRIPT,
            NumericNumberFormatter.toSubscript(digits) to NUMERIC_TYPE_EXPONENT_OR_SUBSCRIPT,
        ).forEach { (string, type) ->
            if (!seen.add(string)) return@forEach
            val candidate = Candidate(
                string = string,
                type = type,
                length = input.length.toUByte(),
                score = NUMERIC_BASE_SCORE + results.size,
                leftId = NUMERIC_POS_ID_NUMBER_ARABIC,
                rightId = NUMERIC_POS_ID_NUMBER_ARABIC,
            )
            results += RenderedNumericCandidate(
                candidate = candidate,
                segments = listOf(
                    CandidateConversionSegment(number.inputStart, number.inputEnd, string),
                ),
                expression = expression,
            )
        }

        if (showSymbolCandidates && number.value.bitLength() <= 63) {
            val value = number.value.toLong()
            createValueBasedSymbolCandidates(value, input.length.toUByte()).forEach { candidate ->
                if (seen.add(candidate.string)) {
                    results += RenderedNumericCandidate(
                        candidate = candidate,
                        segments = listOf(
                            CandidateConversionSegment(number.inputStart, number.inputEnd, candidate.string),
                        ),
                        expression = expression,
                    )
                }
            }
        }
    }

    private fun String.toHiragana(): String = map { character ->
        if (character in 'ァ'..'ヶ') (character.code - 0x60).toChar() else character
    }.joinToString("")

    private fun String.toKatakana(): String = map { character ->
        if (character in 'ぁ'..'ゖ') (character.code + 0x60).toChar() else character
    }.joinToString("")
}
