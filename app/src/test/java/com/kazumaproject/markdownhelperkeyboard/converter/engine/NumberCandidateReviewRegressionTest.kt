package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import org.junit.Assert.*
import org.junit.Test

class NumberCandidateReviewRegressionTest {
    @Test fun userRequestedTextResultsReplaceNumberAndCommitProvenance() {
        val original = Candidate("3人", 1, 4u, 0, number = ValidatedNumber.parse("さんにん"),
            temporalSource = "さんにん" to "3人", conversionSegments = listOf(CandidateConversionSegment(0, 4, "3人", "さんにん")))
        for (type in listOf(41.toByte(), 42.toByte())) {
            val translated = original.withUserRequestedTextResult("さんにん", "3 people", type)
            assertEquals("3 people", translated.commitText)
            assertNull(translated.number)
            assertNull(translated.temporalSource)
            assertTrue(translated.conversionSegments.isEmpty())
            assertFalse(translated.generatedNumber)
            assertTrue(NumberCandidatePolicy.eligible("さんにん", translated))
            assertFalse(NumberCandidatePolicy.eligible("よにん", translated))
            assertFalse(NumberCandidatePolicy.eligible("さんにん", translated.copy(commitText = "4 people")))
        }
    }

    @Test fun floatingItemsKeepIdentityAndOriginalNumberMetadataThroughPhysicalKeyHandling() {
        val provenance = FloatingCandidateProvenance()
        val half = Candidate("1", 1, 1u, 0, number = ValidatedNumber.parseDigits("1"))
        val full = Candidate("1", 1, 1u, 0, number = ValidatedNumber.parseDigits("１"))
        val halfItem = provenance.present(half)
        val fullItem = provenance.present(full)
        assertEquals(halfItem, fullItem)
        assertSame(half, provenance.resolve(halfItem))
        assertSame(full, provenance.resolve(fullItem))
        assertNull(provenance.resolve(halfItem.copy()))
        assertTrue(NumberCandidatePolicy.eligible("１", requireNotNull(provenance.resolve(fullItem))))
        assertFalse(NumberCandidatePolicy.eligible("2", requireNotNull(provenance.resolve(halfItem))))
    }

    @Test fun restoredIndependentTextSegmentsDoNotLicenseAdjacentAutomaticNumbers() {
        val text = CandidateConversionSegment(0, 1, "8", "よ", nonNumericSource = "よ" to "8")
        assertTrue(NumberCandidatePolicy.eligible("よ", Candidate("8", 1, 1u, 0, yomi = "よ", conversionSegments = listOf(text))))
        assertFalse(NumberCandidatePolicy.eligible("よ", Candidate("4", 1, 1u, 0, yomi = "よ", conversionSegments = listOf(text.copy(output = "4")))))
        val combined = Candidate("85", 1, 2u, 0, yomi = "よご", conversionSegments = listOf(
            text, CandidateConversionSegment(1, 2, "5", "ご", leftId = 2044)))
        assertFalse(NumberCandidatePolicy.eligible("よご", combined))
    }

    @Test fun independentTextEvidenceCannotBeReboundOrUsedForNumericGeneration() {
        val candidate = Candidate("8", 1, 1u, 0, nonNumericSource = "よ" to "8")
        assertTrue(NumberCandidatePolicy.eligible("よ", candidate))
        assertFalse(NumberCandidatePolicy.eligible("ろ", candidate))
        assertFalse(NumberCandidatePolicy.eligible("よ", candidate.copy(string = "4", commitText = "4")))
        assertFalse(NumberCandidatePolicy.eligible("よ", candidate.copy(commitText = "4")))
        assertFalse(NumberCandidatePolicy.eligible("よ", candidate.copy(generatedNumber = true)))
    }

    @Test fun ordinaryNumeralSpellingsAreNotLimitedToFourExceptions() {
        for ((reading, output) in listOf("いっとき" to "一時", "ばんにん" to "万人", "いちぶ" to "一分")) {
            assertTrue("$reading -> $output", NumberCandidatePolicy.eligible(reading,
                Candidate(output, 1, reading.length.toUByte(), 0, yomi = reading)))
        }
    }

    @Test fun arbitrarilyLongDirectDigitsKeepTheirLiteralAndWidthConversion() {
        val input = "123456789012345678901234567890"
        for (output in listOf(input, input.map { it + 0xFEE0 }.joinToString(""))) {
            assertTrue(output, NumberCandidatePolicy.eligible(input,
                Candidate(output, 1, input.length.toUByte(), 0, yomi = input)))
        }
    }

    @Test fun dictionaryAffixesAndOtherCountersDelimitTheNumericInterval() {
        fun check(vararg parts: Triple<String, String, Short>) {
            val input = parts.joinToString("") { it.first }
            var offset = 0
            val segments = parts.map { (reading, output, id) ->
                CandidateConversionSegment(offset, offset + reading.length, output, reading,
                    leftId = id, rightId = id).also { offset += reading.length }
            }
            val output = segments.joinToString("") { it.output }
            assertTrue("$input -> $output", NumberCandidatePolicy.eligible(input,
                Candidate(output, 1, input.length.toUByte(), 0, yomi = input, conversionSegments = segments)))
        }
        check(Triple("やく", "約", 2630), Triple("さん", "3", 2044), Triple("にん", "人", 2011))
        check(Triple("さん", "3", 2044), Triple("にん", "人", 2011), Triple("いじょう", "以上", 2000))
        check(Triple("さん", "3", 2044), Triple("こ", "個", 2011))
    }

    @Test fun longDigitInputHonorsEveryWidthOrderWithoutNeedingALongValue() {
        val input = "000123456789012345678901234567890"
        val full = input.map { it + 0xFEE0 }.joinToString("")
        for (order in NumberCandidateOrder.entries) {
            val result = KanaKanjiEngine().getCandidatesEnglishKana(input,
                PredictionConfig(japaneseNumberCandidatesEnabled = false, numberCandidateOrder = order))
            assertEquals(order.indices.filter { it < 2 }.map { listOf(input, full)[it] },
                result.filter { it.string == input || it.string == full }.map { it.string }.distinct())
        }
    }

    @Test fun properNamePosCannotExcuseNumericAtomsInAnInvalidCompound() {
        val c = Candidate("全五", 1, 3u, 0, yomi = "ぜんご", conversionSegments = listOf(
            CandidateConversionSegment(0, 2, "全", "ぜん", leftId = 1851, rightId = 1851),
            CandidateConversionSegment(2, 3, "五", "ご", leftId = 1924, rightId = 1924)))
        assertFalse(NumberCandidatePolicy.eligible("ぜんご", c))
        val atom = NumberCandidatePolicy.dictionaryCandidate(Candidate("五", 1, 1u, 0,
            yomi = "ご", leftId = 1924, rightId = 1924))
        assertFalse(NumberCandidatePolicy.eligible("ごぜん", atom))
        assertFalse(NumberCandidatePolicy.eligible("ろく", atom))
        assertTrue(NumberCandidatePolicy.eligible("ご", atom))
    }

    @Test fun directlyEnteredNumericalSymbolsAreNotReinterpretedAsSpokenNumbers() {
        for (input in listOf("①", "Ⅰ", "〇", "¹", "2026年", "123456789012345678901")) {
            assertTrue(input, NumberCandidatePolicy.eligible(input, Candidate(input, 1, input.length.toUByte(), 0)))
        }
    }

    @Test fun genericNounPosDoesNotEvadeSupportedCounterGrammar() {
        val candidate = Candidate("10分", 1, 5u, 0, yomi = "じゅうふん", conversionSegments = listOf(
            CandidateConversionSegment(0, 3, "10", "じゅう", leftId = 2044, rightId = 2044),
            CandidateConversionSegment(3, 5, "分", "ふん", leftId = 1851, rightId = 1851)))
        assertFalse(NumberCandidatePolicy.eligible("じゅうふん", candidate))
    }

    @Test fun userDictionaryWordDoesNotLicenseABareNumericTail() {
        val candidate = Candidate("日本5", 1, 4u, 0, yomi = "にほんご", conversionSegments = listOf(
            CandidateConversionSegment(0, 3, "日本", "にほん", source = com.kazumaproject.graph.CandidateSource.USER_DICTIONARY),
            CandidateConversionSegment(3, 4, "5", "ご", leftId = 2044, rightId = 2044)))
        assertFalse(NumberCandidatePolicy.eligible("にほんご", candidate))
    }

    @Test fun aMixedNumericNodeDoesNotBypassTheCompleteReadingCheck() {
        val c = Candidate("全5", 1, 3u, 0, yomi = "ぜんご", conversionSegments = listOf(
            CandidateConversionSegment(0, 3, "全5", "ぜんご")))
        assertFalse(NumberCandidatePolicy.eligible("ぜんご", c))
    }

    @Test fun numericCommitTextCannotBorrowTheDisplayedTextSegments() {
        val c = Candidate("前後", 1, 3u, 0, yomi = "ぜんご", commitText = "全5", conversionSegments = listOf(
            CandidateConversionSegment(0, 3, "前後", "ぜんご")))
        assertFalse(NumberCandidatePolicy.eligible("ぜんご", c))
    }

    @Test fun anExplicitSegmentDoesNotExemptUnrelatedNumericCommitText() {
        val c = Candidate("登録語", 1, 3u, 0, yomi = "ぜんご", commitText = "1005", conversionSegments = listOf(
            CandidateConversionSegment(0, 3, "登録語", "ぜんご", source = com.kazumaproject.graph.CandidateSource.USER_DICTIONARY)))
        assertFalse(NumberCandidatePolicy.eligible("ぜんご", c))
        assertTrue(NumberCandidatePolicy.eligible("ぜんご", c.copy(commitText = "登録語")))
    }

    @Test fun oldDictionaryNumbersCannotBeReboundToAnotherInput() {
        val c = Candidate("3", 1, 2u, 0, yomi = "さん", conversionSegments = listOf(
            CandidateConversionSegment(0, 2, "3", "さん")))
        assertFalse(NumberCandidatePolicy.eligible("ろく", c))
        assertFalse(NumberCandidatePolicy.eligible("ろく", c.copy(yomi = null)))
        assertTrue(NumberCandidatePolicy.eligible("さん", c))
    }

    @Test fun userDictionaryPathsStillValidateAutomaticNeighbours() {
        val prefix = CandidateConversionSegment(0, 1, "登録", "あ", source = com.kazumaproject.graph.CandidateSource.USER_DICTIONARY)
        val bad = Candidate("登録全5", 1, 4u, 0, yomi = "あぜんご", conversionSegments = listOf(
            prefix, CandidateConversionSegment(1, 4, "全5", "ぜんご")))
        assertFalse(NumberCandidatePolicy.eligible("あぜんご", bad))
        val good = Candidate("登録4時", 1, 3u, 0, yomi = "あよじ", conversionSegments = listOf(
            prefix, CandidateConversionSegment(1, 2, "4", "よ"), CandidateConversionSegment(2, 3, "時", "じ")))
        assertTrue(NumberCandidatePolicy.eligible("あよじ", good))
    }

    @Test fun malformedNumericReadingsCannotHideInMixedKanjiHistory() {
        for (surface in listOf("全五", "全５", "全5")) {
            val history = Candidate(surface, CANDIDATE_TYPE_LEARNED_DICTIONARY, 3u, 0, yomi = "ぜんご")
            assertFalse(surface, NumberCandidatePolicy.eligible("ぜんご", history))
            val path = history.copy(conversionSegments = listOf(CandidateConversionSegment(0, 3, surface, "ぜんご")))
            assertFalse(surface, NumberCandidatePolicy.eligible("ぜんご", path))
        }
        assertTrue(NumberCandidatePolicy.eligible("ごかん", Candidate("五感", 1, 3u, 0, yomi = "ごかん")))
    }

    @Test fun incompleteInputCannotBorrowACompletedPredictionReading() {
        val raw = Candidate("10", 1, 3u, 0, yomi = "じゅう")
        assertFalse(NumberCandidatePolicy.eligible("じゅ", raw))
        val cached = NumberCandidatePolicy.filter("じゅう", listOf(raw), PredictionConfig()).single()
        assertFalse(NumberCandidatePolicy.eligible("じゅ", cached))
        assertFalse(NumberCandidatePolicy.eligible("", raw))
        assertTrue(NumberCandidatePolicy.eligible("じゅう", cached))
    }

    @Test fun selectingANumericNotationCommitsThatSameNotation() {
        val proof = requireNotNull(ValidatedNumber.parse("さん"))
        val generated = Candidate("3", 18, 2u, 0, yomi = "さん", number = proof, commitText = "三")
        assertFalse(NumberCandidatePolicy.eligible("さん", generated))
        assertFalse(NumberCandidatePolicy.eligible("さん", generated.copy(number = null, generatedNumber = false)))
        assertTrue(NumberCandidatePolicy.eligible("さん", generated.copy(commitText = "3")))
    }

    @Test fun theFestivalNameIsNotAnInvalidNumber() {
        // 七五三 names a festival, not 753. Kanagawa public cultural material:
        // https://www.pref.kanagawa.jp/documents/79520/culture11.pdf
        val c = Candidate("七五三", 1, 5u, 0, yomi = "しちごさん", conversionSegments = listOf(
            CandidateConversionSegment(0, 5, "七五三", "しちごさん", leftId = 1851, rightId = 1851)))
        assertTrue(NumberCandidatePolicy.eligible("しちごさん", c))
    }
}
