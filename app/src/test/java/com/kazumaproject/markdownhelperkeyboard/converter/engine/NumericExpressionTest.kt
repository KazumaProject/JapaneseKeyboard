package com.kazumaproject.markdownhelperkeyboard.converter.engine

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericExpressionTest {

    private val parser = NumericExpressionParser(BundledNumericSuffixCatalog)
    private val renderer = NumericCandidateRenderer(BundledNumericSuffixCatalog)

    @Test
    fun everyBundledDefinitionHasAParseableReading() {
        assertTrue(BundledNumericSuffixCatalog.validate().isEmpty())

        BundledNumericSuffixCatalog.definitions.forEach { definition ->
            definition.readingRules.forEach { rule ->
                val found = if (rule.wholeExpressionValue != null) {
                    parser.parse(rule.reading).any { expression ->
                        expression.suffixes.any { it.definitionId == definition.id }
                    }
                } else {
                    val previousIds = definition.composition.allowedPreviousIds
                    val previousTypes = definition.composition.allowedPreviousTypes
                    val precedingReadings = when {
                        previousIds != null ->
                            previousIds.mapNotNull { id ->
                                BundledNumericSuffixCatalog.definition(id)
                                    ?.readingRules
                                    ?.firstOrNull { it.wholeExpressionValue == null }
                                    ?.reading
                            }

                        previousTypes != null ->
                            BundledNumericSuffixCatalog.definitions
                                .filter { it.type in previousTypes }
                                .mapNotNull { candidate ->
                                    candidate.readingRules
                                        .firstOrNull { it.wholeExpressionValue == null }
                                        ?.reading
                                }

                        else -> listOf("")
                    }
                    precedingReadings.any { precedingReading ->
                        (1..100).any { value ->
                            parser.parse("$value$precedingReading${rule.reading}").any { expression ->
                                expression.suffixes.any { it.definitionId == definition.id }
                            }
                        }
                    }
                }
                assertTrue(
                    "${definition.id}/${rule.reading} was not accepted by the generic parser",
                    found,
                )
            }
        }
    }

    @Test
    fun homophonousSuffixDefinitionsProduceSeparateExpressions() {
        val expressions = parser.parse("ごかい")
        val suffixIds = expressions.mapNotNull { it.suffixes.singleOrNull()?.definitionId }.toSet()

        assertTrue("counter.building.floor" in suffixIds)
        assertTrue("counter.times" in suffixIds)

        val strings = renderer.render("ごかい", expressions, showSymbolCandidates = false)
            .map { it.candidate.string }
        assertTrue(strings.contains("5階"))
        assertTrue(strings.contains("5回"))
    }

    @Test
    fun everyBundledDefinitionRendersCanonicalMetadataThroughTheSamePipeline() {
        BundledNumericSuffixCatalog.definitions
            .filter { definition ->
                definition.readingRules.any { it.wholeExpressionValue == null }
            }
            .forEach { definition ->
                val baseRule = definition.readingRules.first { it.wholeExpressionValue == null }
                val input = if (definition.id == "modifier.half") {
                    "2じ${baseRule.reading}"
                } else {
                    "2${baseRule.reading}"
                }
                val expression = parser.parse(input).first { candidate ->
                    candidate.suffixes.lastOrNull()?.definitionId == definition.id
                }
                val expectedString = "2" + expression.suffixes.joinToString("") { suffix ->
                    BundledNumericSuffixCatalog.definition(suffix.definitionId)!!
                        .canonicalSurfaces.first()
                }
                val rendered = renderer.render(
                    input = input,
                    expressions = listOf(expression),
                    showSymbolCandidates = false,
                ).firstOrNull { candidate ->
                    candidate.expression.suffixes.lastOrNull()?.definitionId == definition.id &&
                        candidate.candidate.string == expectedString
                }

                assertTrue("${definition.id}/${baseRule.reading}: $input", rendered != null)
                rendered ?: return@forEach
                assertEquals(input.length.toUByte(), rendered.candidate.length)
                assertEquals(
                    if (expression.suffixes.any { it.type == NumericSuffixType.TIME }) {
                        com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
                    } else {
                        31.toByte()
                    },
                    renderer.render(
                        input = input,
                        expressions = listOf(rendered.expression),
                        showSymbolCandidates = false,
                    ).first { it.candidate.string == rendered.candidate.string }.candidate.type,
                )
                assertEquals(
                    if (expression.suffixes.any { it.type == NumericSuffixType.TIME }) {
                        2015.toShort()
                    } else {
                        2011.toShort()
                    },
                    rendered.candidate.rightId,
                )
                assertEquals(
                    listOf(Triple(0, 1, "2")) + expression.suffixes.map { suffix ->
                        Triple(
                            suffix.inputStart,
                            suffix.inputEnd,
                            BundledNumericSuffixCatalog.definition(suffix.definitionId)!!
                                .canonicalSurfaces.first(),
                        )
                    },
                    rendered.segments.map { Triple(it.inputStart, it.inputEnd, it.output) },
                )
            }
    }

    @Test
    fun soundChangesAreResolvedFromRulesRatherThanSuffixBranches() {
        val cases = mapOf(
            "1ぽん" to "1本",
            "2ほん" to "2本",
            "3ぼん" to "3本",
            "1ぴき" to "1匹",
            "3びき" to "3匹",
            "いっぱい" to "1杯",
            "3ばい" to "3杯",
            "1ぷん" to "1分",
            "3ぷん" to "3分",
            "3がい" to "3階",
        )

        cases.forEach { (input, expected) ->
            assertTrue(
                "$input -> ${renderer.render(input, parser.parse(input), showSymbolCandidates = false)}",
                renderer.render(input, parser.parse(input), showSymbolCandidates = false)
                    .any { it.candidate.string == expected },
            )
        }
    }

    @Test
    fun suffixNotationVariantsUseOnlyReadingsValidForTheNumericValue() {
        val twoHourStrings = renderer.render(
            input = "2時",
            expressions = parser.parse("2時"),
            showSymbolCandidates = false,
        ).map { it.candidate.string }
        assertTrue(twoHourStrings.containsAll(listOf("2時", "2じ", "2ジ")))

        val twoHonStrings = renderer.render(
            input = "2ほん",
            expressions = parser.parse("2ほん"),
            showSymbolCandidates = false,
        ).map { it.candidate.string }
        assertTrue(twoHonStrings.containsAll(listOf("2本", "2ほん", "2ホン")))
        assertTrue(twoHonStrings.none { it == "2ぽん" || it == "2ポン" })
    }

    @Test
    fun suffixChainsAndSegmentsPreserveTheExpressionStructure() {
        val input = "にじかんはん"
        val rendered = renderer.render(input, parser.parse(input), showSymbolCandidates = false)
            .first { it.candidate.string == "2時間半" }

        assertEquals(
            listOf(
                Triple(0, 1, "2"),
                Triple(1, 4, "時間"),
                Triple(4, 6, "半"),
            ),
            rendered.segments.map { Triple(it.inputStart, it.inputEnd, it.output) },
        )
        assertEquals(NumericSuffixType.TIME, rendered.expression.suffixes.first().type)
        assertEquals("modifier.half", rendered.expression.suffixes.last().definitionId)
    }

    @Test
    fun syntheticCatalogCanAddAUnitWithoutParserChanges() {
        val catalog = object : NumericSuffixCatalog {
            override val definitions = listOf(
                NumericSuffixDefinition(
                    id = "test.synthetic.measure",
                    type = NumericSuffixType.UNIT,
                    surfaces = listOf(NumericSurface(SuffixStyle.CANONICAL, "¤")),
                    allowedStyles = setOf(
                        SuffixStyle.CANONICAL,
                        SuffixStyle.HIRAGANA,
                        SuffixStyle.KATAKANA,
                    ),
                    readingRules = listOf(NumericReadingRule("ふー")),
                ),
            )
        }
        val syntheticParser = NumericExpressionParser(catalog)
        val syntheticRenderer = NumericCandidateRenderer(catalog)

        val expressions = syntheticParser.parse("12ふー")
        val candidates = syntheticRenderer.render(
            input = "12ふー",
            expressions = expressions,
            showSymbolCandidates = false,
        ).map { it.candidate.string }

        assertEquals(listOf("12¤", "１２¤", "十二¤"), candidates.take(3))
        assertEquals("test.synthetic.measure", expressions.single().suffixes.single().definitionId)
    }

    @Test
    fun bigIntegerAndLeadingZeroInputsRemainStructured() {
        val input = "000000000000000000000000000000000000000001えん"
        val expression = parser.parse(input).single()

        assertEquals(BigInteger.ONE, expression.number.value)
        val candidates = renderer.render(input, listOf(expression), showSymbolCandidates = false)
            .map { it.candidate.string }
        assertTrue(candidates.contains("000000000000000000000000000000000000000001円"))
        assertTrue(candidates.contains("${NumericNumberFormatter.toFullWidthDigits("000000000000000000000000000000000000000001")}円"))
        assertTrue(candidates.contains("一円"))
    }

    @Test
    fun irregularCalendarReadingsAreCatalogData() {
        val cases = mapOf(
            "ついたち" to "1日",
            "ふつか" to "2日",
            "みっか" to "3日",
            "はつか" to "20日",
        )
        cases.forEach { (input, expected) ->
            assertTrue(
                input,
                renderer.render(input, parser.parse(input), showSymbolCandidates = false)
                    .any { it.candidate.string == expected },
            )
        }
    }
}
