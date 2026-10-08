package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideSorter
import org.junit.Assert.*
import org.junit.Test

class WholeInputTimeCandidateComposerTest {
    @Test fun completeTimeOverridesLexicalNbestWithoutChangingOtherCandidates() {
        val cases = mapOf(
            "くじごふん" to listOf("9時5分", "９時５分", "九時五分"),
            "よじごふん" to listOf("4時5分", "４時５分", "四時五分"),
            "よじきゅうふん" to listOf("4時9分", "４時９分", "四時九分"),
            "じゅうにじさんじゅっぷん" to listOf("12時30分", "１２時３０分", "十二時三十分"),
            "にじゅうよじごふん" to listOf("24時5分", "２４時５分", "二十四時五分"),
            "にじゅうくじごじゅうきゅうふん" to listOf("29時59分", "２９時５９分", "二十九時五十九分"),
            "れいじれいふん" to listOf("0時0分", "０時０分", "〇時〇分"),
        )
        cases.forEach { (input, texts) ->
            val original = listOf(candidate("くじ胡粉", input), candidate("九時五分以外", input, 5))
            val paths = linkedMapOf<String, List<CandidateConversionSegment>>()
            val splits = linkedMapOf<String, List<Int>>()
            val result = WholeInputTimeCandidateComposer.promote(input, original, NumberCandidateConfig(),
                NumberCandidateProvider.analyze(input).spans, paths, splits)
            assertEquals(input, texts, result.take(3).map { it.string })
            assertEquals(input, original, result.drop(3))
            result.take(3).forEach { time ->
                assertEquals(time.string, time.commitText)
                assertEquals(time.string, time.conversionSegments.joinToString("") { it.output })
                assertEquals(0, time.conversionSegments.first().inputStart)
                assertEquals(input.length, time.conversionSegments.last().inputEnd)
                assertEquals(time.conversionSegments[0].inputEnd, time.conversionSegments[1].inputStart)
                assertTrue(time.conversionSegments.all { it.leftId != null && it.rightId != null && it.numericIdentity != null })
                assertEquals(time.conversionSegments, paths[time.string])
                assertEquals(emptyList<Int>(), splits[time.string])
                assertNull(time.numberVariant)
            }
        }
    }

    @Test fun nonTimeIncompleteSentenceAndOutOfRangeInputsReturnSameList() {
        listOf("にほんご", "くじびき", "よじのぼる", "くじにあたる", "くじ", "ごふん", "よじご",
            "くじごふんにあう", "ごぜんくじごふん", "くじごふんいちびょう", "-くじごふん",
            "くじろくじゅっぷん", "さんじゅうじごふん", "いちじかんごふん", "いちてんごじごふん",
            "くじごじゅうきゅうふんです", "いちまんえん", "くじごふんごふん",
            "きゅうじごふん", "よんじごふん", "しじごふん").forEach { input ->
            val original = listOf(candidate("元の候補", input))
            assertSame(input, original, WholeInputTimeCandidateComposer.promote(input, original, NumberCandidateConfig()))
        }
    }

    @Test fun disabledEnhancementReturnsSameListAndDoesNotMutateCollectors() {
        val original = listOf(candidate("くじ胡粉", "くじごふん"))
        val paths = linkedMapOf<String, List<CandidateConversionSegment>>()
        val splits = linkedMapOf<String, List<Int>>()
        assertSame(original, WholeInputTimeCandidateComposer.promote("くじごふん", original, NumberCandidateConfig(false),
            segmentsByString = paths, splitPatternsByString = splits))
        assertTrue(paths.isEmpty())
        assertTrue(splits.isEmpty())
    }

    @Test fun existingTimeIsReusedAndRepeatedPromotionIsIdempotentForEveryFormatOrder() {
        val input = "くじごふん"
        val existing = candidate("九時五分", input).copy(score = 234, leftId = 11, rightId = 12)
        val original = listOf(candidate("くじ胡粉", input), existing, candidate("くじごふん", input, 3))
        permutations(NumberCandidateFormat.entries.toList()).forEach { order ->
            val config = NumberCandidateConfig(order = order)
            val promoted = WholeInputTimeCandidateComposer.promote(input, original, config)
            val expected = order.map { listOf("9時5分", "９時５分", "九時五分")[it.ordinal] }
            assertEquals(expected, promoted.take(3).map { it.string })
            assertSame(existing, promoted.first { it.string == "九時五分" })
            assertEquals(original.filterNot { it === existing }, promoted.drop(3))
            assertEquals(promoted, WholeInputTimeCandidateComposer.promote(input, promoted, config))
            val reversed = WholeInputTimeCandidateComposer.promote(input, promoted, config.copy(order = order.reversed()))
            assertEquals(expected.reversed(), reversed.take(3).map { it.string })
        }
    }

    @Test fun mergedLearnedTimeAndLearnedLexicalCandidateKeepTheirPriorityAndAttributes() {
        val input = "くじごふん"
        val engine = WholeInputTimeCandidateComposer.promote(input, listOf(candidate("くじ胡粉", input)), NumberCandidateConfig())
        for (text in listOf("9時5分", "九時五分", "くじ胡粉")) {
            val learned = candidate(text, input, CANDIDATE_TYPE_LEARNED_DICTIONARY).copy(score = -100, leftId = 3, rightId = 4)
            val merged = NumberCandidateComposer.inheritVariantIdentity(listOf(learned) + engine).distinctBy { it.string }
            val reordered = NumberCandidateComposer.reorder(input, merged, NumberCandidateConfig())
            assertSame(learned, reordered.first())
            assertEquals(1, reordered.count { it.string == text })
            assertEquals(listOf(learned) + engine.filterNot { it.string == text }, reordered)
        }
    }

    @Test fun actionWithIdenticalLabelIsRetainedAlongsideTheTimeCandidate() {
        val input = "くじごふん"
        val action = candidate("9時5分", input, CANDIDATE_TYPE_TEXT_MACRO).copy(sourceId = 7, commitText = "別の内容")
        val result = WholeInputTimeCandidateComposer.promote(input, listOf(action), NumberCandidateConfig())
        assertEquals("9時5分", result.first().commitText)
        assertSame(action, result.last())
    }

    @Test fun userSavedExactOrderOverridesEngineTimePriority() {
        val input = "くじごふん"
        val lexical = candidate("くじ胡粉", input)
        val promoted = WholeInputTimeCandidateComposer.promote(input, listOf(lexical), NumberCandidateConfig())
        val overrides = mapOf(input to listOf(CandidateOrderOverrideEntity(
            input = input, candidate = lexical.string, rank = 1, createdAt = 1, updatedAt = 1)))
        val ordered = CandidateOrderOverrideSorter.applyByConversionSegment(
            input, promoted, overrides, promoted.associate { it.string to it.conversionSegments })
        assertSame(lexical, ordered.first())
        assertEquals(promoted.filterNot { it === lexical }, ordered.drop(1))
    }

    private fun candidate(text: String, input: String, type: Byte = 1) = Candidate(text, type, input.length.toUByte(), 100,
        yomi = input)
    private fun <T> permutations(values: List<T>): List<List<T>> = if (values.isEmpty()) listOf(emptyList())
        else values.flatMap { first -> permutations(values - first).map { listOf(first) + it } }
}
