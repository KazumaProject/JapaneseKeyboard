package com.kazumaproject.markdownhelperkeyboard.converter.engine

/**
 * Parses a reading into every semantic numeric interpretation available in a suffix catalog.
 *
 * The parser knows only the number grammar and the catalog protocol.  In particular, it never
 * names a counter, unit, or reading exception; those are supplied by [NumericSuffixDefinition].
 */
class NumericExpressionParser(
    private val catalog: NumericSuffixCatalog,
    private val readingResolver: NumericReadingResolver = NumericReadingResolver(),
) {

    init {
        require(catalog.validate().isEmpty()) { catalog.validate().joinToString() }
    }

    private val definitionsByReadingLength = catalog.definitions
        .sortedByDescending { definition ->
            definition.readingRules.maxOfOrNull { it.reading.length } ?: 0
        }

    fun parse(input: String): List<NumericExpression> {
        if (input.isEmpty()) return emptyList()

        val expressions = LinkedHashSet<NumericExpression>()
        addWholeExpressionInterpretations(input, expressions)

        NumericNumberParser.parsePrefixes(input).forEach { prefix ->
            val number = prefix.number.toValue(0, prefix.end)

            // A final 「し」 is a valid standalone cardinal reading, but is not a reliable
            // counter prefix.  This is a grammar-level ambiguity rule and does not single out a
            // suffix or lexical word.
            if (prefix.end < input.length && number.sourceReading?.endsWith("し") == true) {
                return@forEach
            }

            if (prefix.end == input.length) {
                expressions += NumericExpression(number = number, suffixes = emptyList())
            } else {
                parseSuffixes(
                    input = input,
                    offset = prefix.end,
                    number = number,
                    previousSuffixes = emptyList(),
                ).forEach { suffixes ->
                    expressions += NumericExpression(number = number, suffixes = suffixes)
                }
            }
        }

        return expressions.toList()
    }

    private fun addWholeExpressionInterpretations(
        input: String,
        expressions: MutableSet<NumericExpression>,
    ) {
        catalog.definitions.forEach definitionLoop@{ definition ->
            readingResolver.resolveWholeExpression(definition, input).forEach ruleLoop@{ rule ->
                val value = rule.wholeExpressionValue ?: return@ruleLoop
                val number = NumericValue(
                    value = value,
                    sourceReading = input,
                    inputStart = 0,
                    inputEnd = input.length,
                )
                expressions += NumericExpression(
                    number = number,
                    suffixes = listOf(
                        NumericSuffix(
                            definitionId = definition.id,
                            matchedReading = input,
                            surface = definition.canonicalSurfaces.first(),
                            type = definition.type,
                            inputStart = 0,
                            inputEnd = input.length,
                        ),
                    ),
                )
            }
        }
    }

    private fun parseSuffixes(
        input: String,
        offset: Int,
        number: NumericValue,
        previousSuffixes: List<NumericSuffix>,
    ): List<List<NumericSuffix>> {
        if (offset == input.length) return listOf(previousSuffixes)

        val results = LinkedHashSet<List<NumericSuffix>>()
        definitionsByReadingLength.forEach definitionLoop@{ definition ->
            if (!definition.composition.allows(previousSuffixes)) return@definitionLoop

            definition.readingRules
                .asSequence()
                .filter { it.wholeExpressionValue == null }
                .sortedByDescending { it.reading.length }
                .forEach ruleLoop@{ rule ->
                    if (!input.startsWith(rule.reading, offset)) return@ruleLoop
                    val end = offset + rule.reading.length
                    val suffix = NumericSuffix(
                        definitionId = definition.id,
                        matchedReading = rule.reading,
                        surface = definition.canonicalSurfaces.first(),
                        type = definition.type,
                        inputStart = offset,
                        inputEnd = end,
                    )
                    val value = readingResolver.resolve(
                        definition = definition,
                        value = number,
                        matchedReading = rule.reading,
                        previousSuffixes = previousSuffixes,
                    )
                    if (value.isEmpty()) return@ruleLoop

                    parseSuffixes(
                        input = input,
                        offset = end,
                        number = number,
                        previousSuffixes = previousSuffixes + suffix,
                    ).forEach { suffixes -> results += suffixes }
                }
        }
        return results.toList()
    }
}
