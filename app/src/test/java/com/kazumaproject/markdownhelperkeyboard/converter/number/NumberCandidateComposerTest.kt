package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TEXT_MACRO
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.withUpdatedCandidatePaths
import org.junit.Assert.*
import org.junit.Test

class NumberCandidateComposerTest {
    @Test fun moneyCandidatesAreGroupedAndDeduplicatedInEveryOrder() {
        val expectedByFormat = mapOf(
            NumberCandidateFormat.HALF_WIDTH to listOf("10000円", "10,000円", "1万円"),
            NumberCandidateFormat.FULL_WIDTH to listOf("１００００円"),
            NumberCandidateFormat.KANJI to listOf("一万円"),
        )
        for (order in permutations(NumberCandidateFormat.entries.toList())) {
            val config = NumberCandidateConfig(order = order)
            val actual = NumberCandidateComposer.prepare("いちまんえん", listOf(candidate("一万円", 6)), config)
            assertEquals(order.flatMap { expectedByFormat.getValue(it) }, actual.map { it.string })
            assertEquals(actual, NumberCandidateComposer.reorder("いちまんえん", actual, config))
            assertTrue(actual.all { it.string == it.commitText })
        }
    }

    @Test fun daysAndIrregularCountersHaveAllThreeCanonicalForms() {
        val cases = mapOf(
            "ついたち" to "1日", "ふつか" to "2日", "みっか" to "3日", "よっか" to "4日",
            "いつか" to "5日", "むいか" to "6日", "なのか" to "7日", "ようか" to "8日",
            "ここのか" to "9日", "とおか" to "10日", "じゅうよっか" to "14日", "はつか" to "20日",
            "にじゅうよっか" to "24日", "はつかかん" to "20日間", "ひとり" to "1人", "ふたり" to "2人",
            "いっぽん" to "1本", "さんぼん" to "3本", "ろっぽん" to "6本", "はっぽん" to "8本",
            "いっぴき" to "1匹", "さんびき" to "3匹",
            "いっぱい" to "1杯", "さんばい" to "3杯", "さんがい" to "3階",
            "にじゅっぷん" to "20分", "にじゅうよじ" to "24時", "くじ" to "9時",
        )
        cases.forEach { (input, half) ->
            val candidates = NumberCandidateComposer.prepare(input, emptyList())
            assertTrue("$input: $candidates", candidates.any { it.string == half })
            assertEquals("$input", NumberCandidateFormat.entries.toSet(), candidates.map { it.numberVariant!!.format }.toSet())
        }
    }

    @Test fun allSupportedCountersAndAmbiguousKaiKeepCanonicalSurfaces() {
        val cases = mapOf("えん" to "円", "ほん" to "本", "まい" to "枚",
            "こ" to "個", "ひき" to "匹", "さつ" to "冊", "はい" to "杯", "かい" to "回",
            "ねん" to "年", "ねんかん" to "年間", "がつ" to "月", "かげつ" to "か月",
            "さい" to "歳", "ふん" to "分", "じ" to "時", "じかん" to "時間", "びょう" to "秒", "めい" to "名")
        cases.forEach { (reading, surface) ->
            assertTrue(reading, NumberCandidateComposer.prepare("に" + reading, emptyList()).any { it.string == "2$surface" })
        }
        assertTrue(NumberCandidateComposer.prepare("にかい", emptyList()).any { it.string == "2階" })
        assertTrue(NumberCandidateComposer.prepare("さんがい", emptyList()).any { it.string == "3階" })
    }

    @Test fun disabledEnhancementRetainsExistingRowsAndStillReordersNumbers() {
        val off = NumberCandidateConfig(enhanceCounterCandidates = false)
        assertTrue(NumberCandidateComposer.prepare("はつか", emptyList(), off).isEmpty())
        val original = listOf(candidate("一万円", 6), candidate("１００００円", 6), candidate("10000円", 6))
        assertEquals(listOf("10000円", "１００００円", "一万円"), NumberCandidateComposer.prepare("いちまんえん", original, off).map { it.string })
        assertEquals(listOf("10000", "１００００", "一万"), NumberCandidateComposer.prepare("いちまん",
            listOf(candidate("一万", 4), candidate("１００００", 4), candidate("10000", 4)), off).map { it.string })
    }

    @Test fun ordinaryWordsMalformedNumbersAndOverflowAreNotGenerated() {
        listOf("よしよし", "しせん", "くちょう", "ちょうせん", "ごご", "さんご", "しえん", "しじ",
            "いちいち", "さんさん", "ろくろく", "よせん", "くせん", "いちまんにまんえん",
            "いちまんえんです", "99999999999999999999999えん").forEach {
            assertTrue(it, NumberCandidateComposer.prepare(it, emptyList()).isEmpty())
        }
    }

    @Test fun sentencesUpdateCommitTextPathsAndSplitsWithoutCartesianExpansion() {
        val input = "はつかにいちまんえんはらう"
        val text = "二十日に一万円払う"
        val segments = listOf(segment(0, 3, "二十日"), segment(3, 4, "に"), segment(4, 8, "一万", 2047),
            segment(8, 10, "円", 2011), segment(10, 13, "払う"))
        val collector = mutableMapOf(text to segments)
        val splits = mutableMapOf(text to listOf(4, 10))
        val original = candidate(text, input.length).copy(yomi = input)
        val result = NumberCandidateComposer.prepare(input, listOf(original), segmentsByString = collector, splitPatternsByString = splits)
        assertEquals(listOf("20日に10000円払う", "20日に10,000円払う", "20日に1万円払う", "２０日に１００００円払う", text), result.map { it.string })
        result.forEach {
            assertEquals(it.string, it.commitText)
            assertEquals(it.string, collector.getValue(it.string).joinToString("") { part -> part.output })
            assertEquals(listOf(4, 10), splits.getValue(it.string))
        }
        assertEquals(0, collector.getValue(result.first().string).first().inputStart)
        assertEquals(input.length, collector.getValue(result.first().string).last().inputEnd)
    }

    @Test fun properNamesUserAndLearnedNodesAndInexactPathsArePreserved() {
        val input = "いちまんえんはらう"
        val text = "一万円払う"
        val segments = listOf(segment(0, 4, "一万", 2047), segment(4, 6, "円", 2011), segment(6, 9, "払う"))
        val original = candidate(text, input.length).copy(yomi = input)
        val excluded = listOf(
            segments.map { it.copy(source = CandidateSource.USER_DICTIONARY) },
            segments.map { it.copy(source = CandidateSource.LEARNED_DICTIONARY) },
            segments.map { it.copy(isSystemUserDictionary = true) },
            segments.map { it.copy(leftId = 1923) },
            segments.map { it.copy(rightId = 1928) },
            segments.drop(1),
        )
        excluded.forEach {
            assertEquals(listOf(original), NumberCandidateComposer.prepare(input, listOf(original), segmentsByString = mutableMapOf(text to it)))
        }
        val corrected = original.copy(yomi = "いちまんえんはろう")
        assertEquals(listOf(corrected), NumberCandidateComposer.prepare(input, listOf(corrected), segmentsByString = mutableMapOf(text to segments)))
        val japan = candidate("日本", 3)
        assertTrue(NumberCandidateComposer.prepare("にほん", listOf(japan)).contains(japan))
    }

    @Test fun reorderDoesNotRegenerateBlockedRowsOrMergeActionsWithIdenticalText() {
        val prepared = NumberCandidateComposer.prepare("いちまんえん", emptyList())
        val filtered = prepared.filterNot { it.string == "10000円" }
        assertFalse(NumberCandidateComposer.reorder("いちまんえん", filtered, NumberCandidateConfig()).any { it.string == "10000円" })
        val macro = candidate("10000円", 6).copy(type = CANDIDATE_TYPE_TEXT_MACRO, sourceId = 10)
        val result = NumberCandidateComposer.prepare("いちまんえん", listOf(macro))
        assertEquals(macro, result.first())
        assertEquals(2, result.count { it.string == "10000円" })
    }

    @Test fun learnedAndTemplateDuplicatesRetainFormatOrderWithoutChangingTheirIdentity() {
        val core = NumberCandidateComposer.prepare("いちまんえん", emptyList())
        val learned = candidate("一万円", 6).copy(type = 34, score = 50)
        val merged = NumberCandidateComposer.inheritVariantIdentity(listOf(learned) + core).distinctBy { it.string }
        val result = NumberCandidateComposer.reorder("いちまんえん", merged, NumberCandidateConfig())
        assertEquals("10000円", result.first().string)
        val preserved = result.first { it.string == "一万円" }
        assertEquals(learned.type, preserved.type)
        assertEquals(learned.score, preserved.score)
        assertEquals(learned.commitText, preserved.commitText)
    }

    @Test fun maximalNumericSurfaceCannotBeReinterpretedFromItsSuffix() {
        val input = "にじゅっぷんまって"
        val text = "2十分待って"
        val segments = listOf(segment(0, 1, "2"), segment(1, 4, "十"), segment(4, 6, "分"), segment(6, 9, "待って"))
        val paths = mutableMapOf(text to segments)
        val splits = mutableMapOf(text to listOf(1, 4, 6))
        val result = NumberCandidateComposer.prepare(input, listOf(candidate(text, input.length).copy(yomi = input)),
            segmentsByString = paths, splitPatternsByString = splits)
        assertTrue(result.any { it.string == "20分待って" })
        assertFalse(result.any { it.string == "210分待って" })
        result.forEach { assertEquals(listOf(6), splits.getValue(it.string)) }
        assertEquals(20L, paths.getValue(text).first().numericIdentity?.value)
    }

    @Test fun numericCoalescingRetainsIndependentlySearchedSplitAlternatives() {
        val input = "にじゅっぷんまって"
        val text = "2十分待って"
        val original = candidate(text, input.length).copy(yomi = input)
        val paths = mutableMapOf(text to listOf(segment(0, 1, "2"), segment(1, 4, "十"),
            segment(4, 6, "分"), segment(6, 9, "待って")))
        val raw = BunsetsuCandidateResult(listOf(original),
            listOf(listOf(1, 4, 6), emptyList(), listOf(1, 6)), mapOf(text to listOf(1, 4, 6)))
        val splits = raw.splitPatternByCandidateString.toMutableMap()
        val candidates = NumberCandidateComposer.prepare(input, raw.candidates,
            segmentsByString = paths, splitPatternsByString = splits)
        val result = raw.withUpdatedCandidatePaths(candidates, splits)
        assertEquals(listOf(listOf(6), emptyList(), listOf(1, 6)), result.splitPatterns)
        candidates.forEach { assertEquals(listOf(6), result.splitPatternByCandidateString.getValue(it.string)) }
    }

    @Test fun rewritingOnePathKeepsUnchangedRowsAndTheSplitSearchLimit() {
        val old = listOf(1, 4, 6)
        val raw = BunsetsuCandidateResult(emptyList(), listOf(old, emptyList(), listOf(1, 6)),
            mapOf("numeric" to old, "protected" to old))
        val updated = raw.splitPatternByCandidateString + mapOf("numeric" to listOf(6),
            "supplemental" to listOf(7), "another" to listOf(8))
        val result = raw.withUpdatedCandidatePaths(emptyList(), updated)
        assertEquals(listOf(listOf(6), old, emptyList(), listOf(1, 6)), result.splitPatterns)
        assertEquals(updated, result.splitPatternByCandidateString)

        val unchanged = raw.copy(splitPatternByCandidateString =
            (0..31).associate { "candidate-$it" to listOf(it + 1) })
        assertEquals(unchanged.splitPatterns,
            unchanged.withUpdatedCandidatePaths(emptyList(), unchanged.splitPatternByCandidateString).splitPatterns)
    }

    @Test fun monthAliasesPreserveTheirSpellingAndRespectDisabledFormatOrder() {
        val input = "にかげつかかる"
        val texts = listOf("二ヶ月かかる", "2ヶ月かかる", "２か月かかる")
        val paths = texts.associateWith { text -> listOf(segment(0, 4, text.removeSuffix("かかる")), segment(4, 7, "かかる")) }.toMutableMap()
        val candidates = texts.map { candidate(it, input.length).copy(yomi = input) }
        val config = NumberCandidateConfig(false, listOf(NumberCandidateFormat.FULL_WIDTH, NumberCandidateFormat.HALF_WIDTH, NumberCandidateFormat.KANJI))
        assertEquals(listOf(texts[2], texts[1], texts[0]), NumberCandidateComposer.prepare(input, candidates, config, paths).map { it.string })
        val enhanced = NumberCandidateComposer.prepare(input, candidates, config.copy(enhanceCounterCandidates = true), paths)
        assertTrue(enhanced.any { it.string == "２ヶ月かかる" })
        assertTrue(enhanced.any { it.string == "２か月かかる" })
    }

    @Test fun financialNumeralsAndStandalonePathsAreCoalescedAtomically() {
        val input = "さんじゅっぷん"
        val text = "参拾分"
        val paths = mutableMapOf(text to listOf(segment(0, 2, "参"), segment(2, 5, "拾"), segment(5, 7, "分")))
        val splits = mutableMapOf(text to listOf(2, 5))
        val result = NumberCandidateComposer.prepare(input, listOf(candidate(text, input.length)), segmentsByString = paths, splitPatternsByString = splits)
        assertTrue(result.any { it.string == "30分" })
        result.forEach {
            assertTrue(splits.getValue(it.string).isEmpty())
            assertEquals(1, paths.getValue(it.string).size)
            assertEquals(it.conversionSegments, paths.getValue(it.string))
        }
    }

    @Test fun mixedSentenceFormatsAndDifferentPredictionReadingsDoNotBorrowAnIdentity() {
        val input = "さんにんとよにん"
        val text = "3人と四人"
        val paths = mutableMapOf(text to listOf(segment(0, 4, "3人"), segment(4, 5, "と"), segment(5, 8, "四人")))
        val result = NumberCandidateComposer.prepare(input, listOf(candidate(text, input.length).copy(yomi = input)), segmentsByString = paths)
        assertNull(result.first { it.string == text }.numberVariant)
        assertTrue(result.any { it.string == "3人と4人" })
        val donor = result.first { it.string == "3人と4人" }
        val prediction = donor.copy(yomi = "さんにんとよにんで", length = 9u, numberVariant = null)
        assertNull(NumberCandidateComposer.inheritVariantIdentity(listOf(prediction, donor)).first().numberVariant)
    }

    private fun candidate(text: String, length: Int) = Candidate(text, 1, length.toUByte(), 1000)
    private fun segment(start: Int, end: Int, text: String, pos: Short = 1851) =
        CandidateConversionSegment(start, end, text, pos, pos, CandidateSource.SYSTEM)
    private fun <T> permutations(values: List<T>): List<List<T>> = if (values.isEmpty()) listOf(emptyList())
        else values.flatMap { first -> permutations(values - first).map { listOf(first) + it } }
}
