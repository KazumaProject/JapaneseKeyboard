package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.graph.*
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class FlickCorrectionSearchTest {
    private fun trie(vararg readings: String): LOUDSWithTermId {
        val tree = PrefixTreeWithTermId().apply { readings.forEach { insert(it) } }
        val converted = ConverterWithTermId().convert(tree.root).apply { convertListToBitSet() }
        return LOUDSWithTermId(converted.LBS, converted.getAllLabels(), converted.isLeaf, converted.termIds.toIntArray())
    }

    @Test fun allFiveErrorKindsConsumeOriginalInputLength() {
        val cases = listOf(
            Triple("かした", "あした", FlickCorrectionKind.KEY),
            Triple("あしと", "あした", FlickCorrectionKind.DIRECTION),
            Triple("こんちは", "こんにちは", FlickCorrectionKind.MISSING),
            Triple("こんにちちは", "こんにちは", FlickCorrectionKind.EXTRA),
            Triple("よしろく", "よろしく", FlickCorrectionKind.TRANSPOSE),
        )
        for ((input, reading, kind) in cases) {
            val dictionary = trie(reading)
            val results = dictionary.commonPrefixSearchWithFlickCorrection(
                input, 0, SuccinctBitVector(dictionary.LBS), FlickCorrectionInput(input), false,
            )
            val corrected = results.firstOrNull { it.yomi == reading && it.consumedLength == input.length }
            assertNotNull("$input -> $reading: $results", corrected)
            assertEquals(kind, corrected!!.edits.single().kind)
            assertTrue(corrected.edits.all { it.inputStart in 0..input.length && it.inputEnd in it.inputStart..input.length })
        }
    }

    @Test fun modifierOmissionCombinesWithCorrectionButDoesNotBecomeAnEdit() {
        val dictionary = trie("がっこう")
        fun search(input: String, omission: Boolean) = dictionary.commonPrefixSearchWithFlickCorrection(
            input, 0, SuccinctBitVector(dictionary.LBS), FlickCorrectionInput(input), omission)
        val mixed = search("かつこお", true).first { it.yomi == "がっこう" && it.consumedLength == 4 }
        assertEquals(1, mixed.edits.size)
        assertTrue(mixed.modifierOmissionOccurred)
        assertTrue(search("かつこお", false).none { it.yomi == "がっこう" && it.consumedLength == 4 })
        assertTrue(search("かつこう", true).none { it.yomi == "がっこう" && it.consumedLength == 4 })
    }

    @Test fun shortInputsAndPunctuationAreNotExpandedAcrossBoundaries() {
        val dictionary = trie("こんにちは", "よろしく")
        for (input in listOf("", "こ", "こん", "こ、んちは")) {
            assertTrue(dictionary.commonPrefixSearchWithFlickCorrection(input, 0,
                SuccinctBitVector(dictionary.LBS), FlickCorrectionInput(input), false).isEmpty())
        }
    }

    @Test fun twoSubstitutionsRequireSixConsumedCharacters() {
        val dictionary = trie("よろしく", "こんにちはね")
        fun search(input: String) = dictionary.commonPrefixSearchWithFlickCorrection(input, 0,
            SuccinctBitVector(dictionary.LBS), FlickCorrectionInput(input), false)
        assertTrue(search("よろそけ").none { it.yomi == "よろしく" && it.consumedLength == 4 })
        val result = search("こんにつはに").first { it.yomi == "こんにちはね" && it.consumedLength == 6 }
        assertEquals(2, result.edits.size)
    }

    @Test fun boundedSearchChecksCancellation() {
        val dictionary = trie("こんにちは")
        var checks = 0
        try {
            dictionary.commonPrefixSearchWithFlickCorrection("こんちは", 0,
                SuccinctBitVector(dictionary.LBS), FlickCorrectionInput("こんちは"), false,
                maxStates = 4, maxResults = 1, cancellationCheck = {
                    if (++checks == 2) throw CancellationException("superseded")
                })
            fail("Cancellation must escape the search")
        } catch (_: CancellationException) {
            assertEquals(2, checks)
        }
    }

    @Test fun cheaperSubstitutionsDoNotExhaustTheBudgetBeforeAWholeMissingCharacterMatch() {
        val input = "はるあきふゆ"
        val expected = "なはるあきふゆ"
        val characters = KanaFlickLayout.allGroups().flatMap { group ->
            FlickDir.values().mapNotNull { KanaFlickLayout.charOf(group, it) }
        }.filter { it in 'ぁ'..'ゖ' || it == 'ー' }
        val distractions = input.indices.flatMap { index -> characters.map { char ->
            input.replaceRange(index, index + 1, char.toString())
        } }
        val dictionary = trie(*(distractions + expected).toTypedArray())
        val results = dictionary.commonPrefixSearchWithFlickCorrection(input, 0,
            SuccinctBitVector(dictionary.LBS), FlickCorrectionInput(input), false)
        assertTrue(results.size <= 32)
        assertTrue(results.any { it.yomi == expected && it.consumedLength == input.length })
    }

    @Test fun appendFrontiersMatchColdSearchAcrossSwapAndInsertionBoundaries() {
        val dictionary = trie("こんにちは", "こんにちわ", "こんにちには", "がっこう", "よろしく",
            "きょう", "とうきょう", "はるあきふゆ", "なはるあきふゆ", "おはようございます")
        val vector = SuccinctBitVector(dictionary.LBS)
        for (omission in listOf(false, true)) for (text in listOf("こんにちちは", "こんちは",
            "よしろく", "かつこお", "ときょう", "はるあきふゆ", "おはようごいざます")) {
            var previous: LOUDSWithTermId.FlickSearchProgress? = null
            for (length in 1..text.length) {
                val input = text.take(length)
                val warm = dictionary.commonPrefixSearchWithFlickCorrectionProgress(input, 0, vector,
                    FlickCorrectionInput(input), omission, previous = previous)
                val cold = dictionary.commonPrefixSearchWithFlickCorrection(input, 0, vector,
                    FlickCorrectionInput(input), omission)
                assertEquals("$input / omission=$omission", cold, warm.results)
                previous = warm.flickProgress
            }
        }
    }

    @Test fun waKeyIsBelowYaAndMatchesRuntimeCharacters() {
        assertEquals(1, KanaFlickLayout.manhattan(KeyGroup.WA, KeyGroup.YA))
        assertEquals(2, KanaFlickLayout.manhattan(KeyGroup.WA, KeyGroup.MA))
        assertEquals('ん', KanaFlickLayout.charOf(KeyGroup.WA, FlickDir.UP))
    }
}
