package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberCandidatePresenterTest {

    @Test
    fun completedPathSearchExpansionIsLimitedToInputsWithNumericAlternatives() {
        assertEquals(4, NumberCandidatePresenter.expandedSearchCount("ねこ", 4))
        assertEquals(4, NumberCandidatePresenter.expandedSearchCount("こんにちは", 4))
        assertFalse(NumberCandidatePresenter.shouldCollectSegments("こんにちは"))
        assertEquals(4, NumberCandidatePresenter.expandedSearchCount("とおり", 4))
        assertFalse(NumberCandidatePresenter.shouldCollectSegments("とおり"))
        assertEquals(4, NumberCandidatePresenter.expandedSearchCount("たくさんまいにち", 4))
        assertFalse(NumberCandidatePresenter.shouldCollectSegments("たくさんまいにち"))
        assertEquals(32, NumberCandidatePresenter.expandedSearchCount("いち", 4))
        assertEquals(32, NumberCandidatePresenter.expandedSearchCount("ごかい", 4))
        assertEquals(32, NumberCandidatePresenter.expandedSearchCount("さんじゅうにとねこ", 4))
        assertEquals(32, NumberCandidatePresenter.expandedSearchCount("さんまいとにまいをかう", 4))
        assertEquals(32, NumberCandidatePresenter.expandedSearchCount("さんまいから", 4))
        assertEquals(32, NumberCandidatePresenter.expandedSearchCount("にまいまで", 4))
        assertTrue(NumberCandidatePresenter.shouldCollectSegments("さんまいから"))
        assertEquals(32, NumberCandidatePresenter.expandedSearchCount("1２ねこ", 4))
        assertTrue(NumberCandidatePresenter.shouldCollectSegments("1２ねこ"))
        assertEquals(96, NumberCandidatePresenter.expandedSearchCount("123456789", 20))
        assertEquals(4, NumberCandidatePresenter.expandedSearchCount("ごかい", 4, additionsEnabled = false))
    }

    @Test
    fun allSixStyleOrdersKeepMeaningRepresentativesAheadOfStyleAlternatives() {
        val expectedOrders = listOf(
            listOf(NumberStyle.HALF_WIDTH, NumberStyle.FULL_WIDTH, NumberStyle.KANJI),
            listOf(NumberStyle.HALF_WIDTH, NumberStyle.KANJI, NumberStyle.FULL_WIDTH),
            listOf(NumberStyle.FULL_WIDTH, NumberStyle.HALF_WIDTH, NumberStyle.KANJI),
            listOf(NumberStyle.FULL_WIDTH, NumberStyle.KANJI, NumberStyle.HALF_WIDTH),
            listOf(NumberStyle.KANJI, NumberStyle.HALF_WIDTH, NumberStyle.FULL_WIDTH),
            listOf(NumberStyle.KANJI, NumberStyle.FULL_WIDTH, NumberStyle.HALF_WIDTH),
        )

        expectedOrders.forEach { order ->
            val candidates = listOf(
                candidate("誤解", "ごかい"),
                candidate("5回", "ごかい"),
                candidate("5階", "ごかい"),
            )
            val segments = mapOf(
                "5回" to counterPath("5", "回"),
                "5階" to counterPath("5", "階"),
            )

            val result = NumberCandidatePresenter.present(
                candidates = candidates,
                segmentsByCandidateString = segments,
                config = NumberPresentationConfig(styleOrder = order),
            )

            // The first alternative list contains each style for both meanings in style-rank order.
            val expectedByDisplayClass = listOf("誤解", styleSurface(order[0], "回"), styleSurface(order[0], "階")) +
                order.drop(1).flatMap { style ->
                    listOf(styleSurface(style, "回"), styleSurface(style, "階"))
                }
            assertEquals(expectedByDisplayClass, result.candidates.map { it.string })
            assertNotEquals(styleSurface(order[0], "回"), styleSurface(order[0], "階"))
            result.candidates.filter { it.string != "誤解" }.forEach { styled ->
                assertEquals(styled.string, styled.commitText)
                assertNotNull(styled.numberMetadata)
                assertEquals(
                    styled.string,
                    result.segmentsByCandidateString.getValue(styled.string).joinToString("") { it.output },
                )
            }
        }
    }

    @Test
    fun displayLimitKeepsMeaningRepresentativesBeforeSpellingAlternatives() {
        val candidates = listOf(
            candidate("誤解", "ごかい"),
            candidate("5回", "ごかい"),
            candidate("5階", "ごかい"),
        )
        val result = NumberCandidatePresenter.present(
            candidates = candidates,
            segmentsByCandidateString = mapOf(
                "5回" to counterPath("5", "回"),
                "5階" to counterPath("5", "階"),
            ),
            config = NumberPresentationConfig(),
            maxDisplayedCandidates = 4,
        )

        assertEquals(listOf("誤解", "5回", "5階", "５回"), result.candidates.map(Candidate::string))
        val meaningLimited = NumberCandidatePresenter.present(
            candidates = candidates,
            segmentsByCandidateString = mapOf(
                "5回" to counterPath("5", "回"),
                "5階" to counterPath("5", "階"),
            ),
            config = NumberPresentationConfig(),
            maxRepresentedMeanings = 2,
            maxDisplayedCandidates = 6,
        )
        assertEquals(listOf("誤解", "5回", "５回", "五回"), meaningLimited.candidates.map(Candidate::string))
        assertEquals(
            2,
            NumberCandidatePresenter.present(
                candidates = listOf(candidate("猫", "ねこ"), candidate("寝子", "ねこ")),
                segmentsByCandidateString = emptyMap(),
                config = NumberPresentationConfig(),
                maxDisplayedCandidates = 1,
            ).candidates.size,
        )
    }

    @Test
    fun displayCapRunsAfterAnExplicitOrderAndDoesNotTrimWhenAdditionsAreDisabled() {
        val overrideOrder = listOf(candidate("５階", "ごかい")) +
            (0 until 12).map { candidate("候補$it", "ごかい") }
        val limited = NumberCandidatePresenter.limitForDisplay(
            candidates = overrideOrder,
            config = NumberPresentationConfig(),
            requestedMeanings = 1,
            hasNumericFamilies = true,
        )
        assertEquals("５階", limited.first().string)
        assertEquals(3, limited.size)

        val noNumericFamily = NumberCandidatePresenter.limitForDisplay(
            candidates = overrideOrder,
            config = NumberPresentationConfig(),
            requestedMeanings = 1,
        )
        assertEquals(overrideOrder, noNumericFamily)

        val additionsDisabled = NumberCandidatePresenter.limitForDisplay(
            candidates = overrideOrder,
            config = NumberPresentationConfig(additionsEnabled = false),
            requestedMeanings = 1,
        )
        assertEquals(overrideOrder, additionsDisabled)
    }

    @Test
    fun semanticKeysMergeOnlySameCounterMeaningAcrossDigitStyles() {
        val halfWidth = NumberCandidatePresenter.semanticFamilyKey(counterPath("5", "回"))
        val fullWidth = NumberCandidatePresenter.semanticFamilyKey(counterPath("５", "回"))
        val kanji = NumberCandidatePresenter.semanticFamilyKey(counterPath("五", "回"))
        val floor = NumberCandidatePresenter.semanticFamilyKey(counterPath("5", "階"))

        assertEquals(halfWidth, fullWidth)
        assertEquals(halfWidth, kanji)
        assertNotEquals(halfWidth, floor)
    }

    @Test
    fun selectedStyleCanReplaceTheTypedSurfaceForAnExplicitNumericInput() {
        val halfWidth = candidate("123", "123").copy(
            numberMetadata = NumberCandidateMetadata(
                familyKey = "number:123",
                origin = NumberCandidateOrigin.SYSTEM_PATH,
                style = NumberStyle.HALF_WIDTH,
                numericSpans = listOf(NumberSpan(0, 3, 0, 3, "123", false)),
            ),
        )
        val fullWidth = candidate("１２３", "123").copy(
            numberMetadata = NumberCandidateMetadata(
                familyKey = "number:123",
                origin = NumberCandidateOrigin.ENGINE_SUPPLEMENT,
                style = NumberStyle.FULL_WIDTH,
                numericSpans = listOf(NumberSpan(0, 3, 0, 3, "123", false)),
                isFallback = true,
            ),
        )

        val result = NumberCandidatePresenter.present(
            candidates = listOf(halfWidth, fullWidth),
            segmentsByCandidateString = emptyMap(),
            config = NumberPresentationConfig(
                styleOrder = listOf(NumberStyle.FULL_WIDTH, NumberStyle.HALF_WIDTH, NumberStyle.KANJI),
            ),
        )

        assertEquals("１２３", result.candidates.first().string)
        assertEquals("123", result.candidates[1].string)
        assertEquals("百二十三", result.candidates[2].string)
    }

    @Test
    fun multiBunsetsuSentenceRendersAllNumericSpansWithOneGlobalStyle() {
        val input = "さんまいとにまいをかう"
        val source = candidate("3枚と2枚を買う", input)
        val segments = listOf(
            CandidateConversionSegment(0, 2, "3", "さん", 2044, 2044),
            CandidateConversionSegment(2, 4, "枚", "まい", 2011, 2011),
            CandidateConversionSegment(4, 5, "と", "と", 20, 20),
            CandidateConversionSegment(5, 6, "2", "に", 2044, 2044),
            CandidateConversionSegment(6, 8, "枚", "まい", 2011, 2011),
            CandidateConversionSegment(8, 11, "を買う", "をかう", 20, 20),
        )

        val result = NumberCandidatePresenter.present(
            candidates = listOf(source),
            segmentsByCandidateString = mapOf(source.string to segments),
            config = NumberPresentationConfig(),
        )

        assertEquals(
            listOf("3枚と2枚を買う", "３枚と２枚を買う", "三枚と二枚を買う"),
            result.candidates.map { it.string },
        )
        assertFalse(result.candidates.any { it.string in setOf("3枚と２枚を買う", "三枚と2枚を買う") })
        assertEquals(
            listOf(0 to 4, 5 to 8),
            NumberCandidatePresenter.numericInputSpans(
                result.segmentsByCandidateString.getValue("三枚と二枚を買う"),
            ),
        )
        result.candidates.forEach { candidate ->
            assertEquals(
                candidate.string,
                result.segmentsByCandidateString.getValue(candidate.string).joinToString("") { it.output },
            )
            assertEquals(segments.map { it.inputStart to it.inputEnd },
                result.segmentsByCandidateString.getValue(candidate.string).map { it.inputStart to it.inputEnd })
        }
    }

    @Test
    fun splitPositionsUseInputOffsetsAndNeverCutInsideAParsedNumber() {
        val segments = listOf(
            CandidateConversionSegment(0, 4, "三十", "さんじゅう", 2046, 2046),
            CandidateConversionSegment(4, 6, "二", "に", 2046, 2046),
            CandidateConversionSegment(6, 7, "と", "と", 20, 20),
            CandidateConversionSegment(7, 9, "猫", "ねこ", 12, 12),
        )

        val splitPositions = NumberCandidatePresenter.splitPositionsFromSegments(
            segments,
            isIndependentWordPos = { it == 12.toShort() || it == 2046.toShort() },
        )

        // The numeric output is three characters long, but its input reading occupies six.
        assertEquals(listOf(7), splitPositions)
        assertEquals(
            listOf(6, 7),
            NumberCandidatePresenter.filterSplitPositionsInsideNumericSpans(
                splitPositions = listOf(4, 6, 7),
                numericSpans = listOf(0 to 6),
            ),
        )
    }

    @Test
    fun ngPreferredSurfacePromotesNextAllowedStyleAndNeverLeaksBlockedVariant() {
        val result = NumberCandidatePresenter.present(
            candidates = listOf(candidate("5回", "ごかい")),
            segmentsByCandidateString = mapOf("5回" to counterPath("5", "回")),
            config = NumberPresentationConfig(),
            isNgWord = { it.string == "5回" },
        )

        assertEquals(listOf("５回", "五回"), result.candidates.map { it.string })
        assertFalse(result.candidates.any { it.string == "5回" })
    }

    @Test
    fun disablingAdditionsKeepsExistingCandidatesButRemovesGeneratedForms() {
        val dictionaryCandidate = candidate("5回", "ごかい").copy(
            type = CANDIDATE_TYPE_USER_DICTIONARY,
        )
        val generated = candidate("５回", "ごかい").copy(
            numberMetadata = NumberCandidateMetadata(
                familyKey = "counter:回:回:5",
                origin = NumberCandidateOrigin.PRESENTATION_VARIANT,
                style = NumberStyle.FULL_WIDTH,
            ),
        )
        val fallback = candidate("5階", "ごかい").copy(
            numberMetadata = NumberCandidateMetadata(
                familyKey = "counter:階:階:5",
                origin = NumberCandidateOrigin.ENGINE_SUPPLEMENT,
                style = NumberStyle.HALF_WIDTH,
                isFallback = true,
            ),
        )

        val result = NumberCandidatePresenter.present(
            candidates = listOf(dictionaryCandidate, generated, fallback),
            segmentsByCandidateString = emptyMap(),
            config = NumberPresentationConfig(additionsEnabled = false),
        )

        assertEquals(listOf("5回"), result.candidates.map { it.string })
    }

    @Test
    fun userDictionarySurfacesStayFixedWhileLearnedMeaningUsesSelectedNumberStyles() {
        val segments = mapOf("5回" to counterPath("5", "回"))

        val fixed = NumberCandidatePresenter.present(
            listOf(candidate("5回", "ごかい").copy(type = CANDIDATE_TYPE_USER_DICTIONARY)),
            segments,
            NumberPresentationConfig(),
        )
        assertEquals(listOf("5回"), fixed.candidates.map { it.string })

        val learned = NumberCandidatePresenter.present(
            listOf(candidate("5回", "ごかい").copy(type = CANDIDATE_TYPE_LEARNED_DICTIONARY)),
            segments,
            NumberPresentationConfig(
                styleOrder = listOf(NumberStyle.KANJI, NumberStyle.FULL_WIDTH, NumberStyle.HALF_WIDTH),
            ),
        )
        assertEquals(listOf("五回", "５回", "5回"), learned.candidates.map { it.string })
    }

    @Test
    fun learnedNumericCandidateUsesItsVerifiedReadingWhenNoPathMetadataExists() {
        val learned = candidate("5回", "ごかい").copy(type = CANDIDATE_TYPE_LEARNED_DICTIONARY)
        val result = NumberCandidatePresenter.present(
            candidates = listOf(learned),
            segmentsByCandidateString = emptyMap(),
            config = NumberPresentationConfig(
                styleOrder = listOf(NumberStyle.KANJI, NumberStyle.FULL_WIDTH, NumberStyle.HALF_WIDTH),
            ),
        )

        assertEquals(listOf("五回", "５回", "5回"), result.candidates.map { it.string })
        result.candidates.forEach { candidate ->
            assertEquals(candidate.string, candidate.commitText)
            assertEquals(
                candidate.string,
                result.segmentsByCandidateString.getValue(candidate.string).joinToString("") { it.output },
            )
        }
    }

    @Test
    fun learnedNumericSurfaceInheritsEngineMeaningAndSurvivesDisabledAdditions() {
        val learned = candidate("5回", "ごかい").copy(type = CANDIDATE_TYPE_LEARNED_DICTIONARY)
        val engineSupplement = candidate("5回", "ごかい").copy(
            numberMetadata = NumberCandidateMetadata(
                familyKey = "counter:回:回:5",
                origin = NumberCandidateOrigin.ENGINE_SUPPLEMENT,
                style = NumberStyle.HALF_WIDTH,
                numericSpans = listOf(NumberSpan(0, 2, 0, 1, "5", false)),
                isFallback = true,
            ),
        )
        val merged = NumberCandidateMetadataInheritance.attachToLearnedDuplicates(
            listOf(learned, engineSupplement),
        )

        assertEquals(CANDIDATE_TYPE_LEARNED_DICTIONARY, merged.first().type)
        assertEquals(NumberCandidateOrigin.SYSTEM_PATH, merged.first().numberMetadata?.origin)
        assertEquals(false, merged.first().numberMetadata?.isFallback)

        val enabled = NumberCandidatePresenter.present(
            candidates = merged,
            segmentsByCandidateString = emptyMap(),
            config = NumberPresentationConfig(),
        )
        assertEquals(listOf("5回", "５回", "五回"), enabled.candidates.map { it.string })

        val disabled = NumberCandidatePresenter.present(
            candidates = merged,
            segmentsByCandidateString = emptyMap(),
            config = NumberPresentationConfig(additionsEnabled = false),
        )
        assertEquals(listOf("5回"), disabled.candidates.map { it.string })
    }

    @Test
    fun renderedWidthVariantsCarryMatchingCandidatePresentationTypes() {
        val source = candidate("５回", "ごかい").copy(type = 22)
        val result = NumberCandidatePresenter.present(
            candidates = listOf(source),
            segmentsByCandidateString = mapOf(source.string to counterPath("５", "回")),
            config = NumberPresentationConfig(),
        )

        assertEquals(listOf("5回", "５回", "五回"), result.candidates.map { it.string })
        assertEquals(31.toByte(), result.candidates[0].type)
        assertEquals(22.toByte(), result.candidates[1].type)
        assertEquals(17.toByte(), result.candidates[2].type)
        result.candidates.forEach { assertEquals(it.string, it.commitText) }
    }

    @Test
    fun digitSequencesDecimalsSignsAndOutOfRangeValuesDoNotLoseDigits() {
        val leadingZeroResult = presentNumber("0012")
        assertEquals(listOf("0012", "００１２", "〇〇一二"), leadingZeroResult.candidates.map { it.string })

        val decimalResult = presentNumber("-12.05")
        assertEquals("-12.05", decimalResult.candidates[0].string)
        assertEquals("－１２．０５", decimalResult.candidates[1].string)
        assertEquals("−一二・〇五", decimalResult.candidates[2].string)

        val tooLarge = presentNumber("9223372036854775808", order = listOf(
            NumberStyle.KANJI,
            NumberStyle.HALF_WIDTH,
            NumberStyle.FULL_WIDTH,
        ))
        assertFalse(tooLarge.candidates.any { it.string.any { char -> char in "〇零一二三四五六七八九十百千万億兆京" } })
        assertTrue(tooLarge.candidates.any { it.string == "9223372036854775808" })
    }

    @Test
    fun nonNumericKanjiWordsAreUntouchedAndPresentationIsIdempotent() {
        val word = candidate("一生", "いっしょう")
        val once = NumberCandidatePresenter.present(
            listOf(word),
            mapOf(
                "一生" to listOf(
                    CandidateConversionSegment(0, 5, "一生", "いっしょう", 100, 100),
                ),
            ),
            NumberPresentationConfig(),
        )
        assertEquals(listOf("一生"), once.candidates.map { it.string })

        val source = candidate("5回", "ごかい")
        val first = NumberCandidatePresenter.present(
            listOf(source),
            mapOf("5回" to counterPath("5", "回")),
            NumberPresentationConfig(),
        )
        val second = NumberCandidatePresenter.present(
            first.candidates,
            first.segmentsByCandidateString,
            NumberPresentationConfig(),
        )
        assertEquals(first.candidates.map { it.string }, second.candidates.map { it.string })
    }

    private fun presentNumber(
        surface: String,
        order: List<NumberStyle> = NumberPresentationConfig.DEFAULT_STYLE_ORDER,
    ) = NumberCandidatePresenter.present(
        candidates = listOf(candidate(surface, "number")),
        segmentsByCandidateString = mapOf(
            surface to listOf(
                CandidateConversionSegment(
                    inputStart = 0,
                    inputEnd = surface.length,
                    output = surface,
                    reading = "number",
                    leftId = 2044,
                    rightId = 2044,
                ),
            ),
        ),
        config = NumberPresentationConfig(styleOrder = order),
    )

    private fun candidate(surface: String, reading: String) = Candidate(
        string = surface,
        type = 1,
        length = reading.length.toUByte(),
        score = 0,
        yomi = reading,
    )

    private fun counterPath(number: String, counter: String): List<CandidateConversionSegment> {
        val numericId: Short = when (number) {
            "五" -> 2046
            else -> 2044
        }
        return listOf(
            CandidateConversionSegment(0, 1, number, "ご", numericId, numericId),
            CandidateConversionSegment(1, 3, counter, "かい", 2011, 2011),
        )
    }

    private fun styleSurface(style: NumberStyle, counter: String) = when (style) {
        NumberStyle.HALF_WIDTH -> "5$counter"
        NumberStyle.FULL_WIDTH -> "５$counter"
        NumberStyle.KANJI -> "五$counter"
    }
}
