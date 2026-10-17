package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedCharArray
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedIntArray
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import org.junit.Assert.*
import org.junit.Test
import java.util.BitSet

class ClassifiedExactCandidatesTest {
    private val person = DictionaryCategory.CLASSIFIED_PERSON
    private val character = DictionaryCategory.CLASSIFIED_CHARACTER
    private data class Word(val reading: String, val surface: String, val cost: Short)

    private fun dictionary(words: List<Word>, packed: Boolean = false): TripleDictionaryData {
        val byReading = words.groupBy { it.reading }.toSortedMap()
        val tree = PrefixTreeWithTermId().apply { byReading.keys.forEach(::insert) }
        val converted = ConverterWithTermId().convert(tree.root).apply { convertListToBitSet() }
        val yomi = if (packed) LOUDSWithTermId.fromPacked(
            converted.LBS, PackedCharArray.from(converted.getAllLabels()), converted.isLeaf,
            PackedIntArray.from(converted.termIds.toIntArray()),
        ) else LOUDSWithTermId(converted.LBS, converted.getAllLabels(), converted.isLeaf, converted.termIds.toIntArray())
        val bits = BitSet()
        val positions = mutableListOf<Short>()
        val costs = mutableListOf<Short>()
        val overrides = mutableMapOf<String, String>()
        var bit = 0
        byReading.forEach { (reading, entries) ->
            bit++
            entries.forEach { word ->
                val pos = positions.size
                positions.add(pos.toShort()); costs.add(word.cost); bits.set(bit++)
                overrides["-2\t$reading\t${100 + pos}\t${200 + pos}\t${word.cost}"] = word.surface
            }
        }
        val tokens = TokenArray.fromPacked(positions.toShortArray(), costs.toShortArray(),
            PackedIntArray.from(IntArray(words.size) { -2 }), bits,
            ShortArray(words.size) { (100 + it).toShort() }, ShortArray(words.size) { (200 + it).toShort() })
        val tango = LOUDS()
        return TripleDictionaryData(tango, yomi, tokens, SuccinctBitVector(yomi.LBS),
            SuccinctBitVector(yomi.isLeaf), SuccinctBitVector(bits), SuccinctBitVector(tango.LBS), overrides)
    }

    @Test fun keepsEveryHomophoneAndAppendsInCostThenSurfaceOrder() {
        val data = dictionary((0 until 12).map { Word("よみ", "語${it.toString().padStart(2, '0')}", (11 - it).toShort()) })
        val original = listOf(Candidate("既存", 1, 2u, -100))
        val result = ClassifiedExactCandidates.append("よみ", original, mapOf(person to data), setOf(person))
        assertSame(original.first(), result.first())
        assertEquals((11 downTo 0).map { "語${it.toString().padStart(2, '0')}" }, result.drop(1).map { it.string })
        assertTrue(result.drop(1).all { it.yomi == "よみ" && it.length.toInt() == 2 && it.type == 1.toByte() })
    }

    @Test fun acceptsShortLongAndSupplementaryReadingsButRejectsNonterminalPrefixes() {
        for (packed in listOf(false, true)) {
            val readings = listOf("あ", "かな", "かなかき", "あ".repeat(17), "あ😀")
            val data = dictionary(readings.map { Word(it, "表記$it", 10) }, packed)
            for (reading in readings) {
                val chars = reading.toCharArray()
                assertEquals(data.yomiTrie.getNodeIndex(reading, data.succinctBitVectorLBSYomi),
                    data.yomiTrie.getNodeIndex(chars, data.succinctBitVectorLBSYomi))
                assertEquals(listOf("表記$reading"), ClassifiedExactCandidates.append(reading, emptyList(),
                    mapOf(person to data), setOf(person)).map { it.string })
            }
            for (reading in listOf("", "かなか", "ない")) {
                val original = listOf(Candidate("元", 1, 1u, 0))
                assertSame(original, ClassifiedExactCandidates.append(reading, original, mapOf(person to data), setOf(person)))
            }
        }
    }

    @Test fun combinesEnabledCategoryLabelsAndKeepsLowestCostPosForNewWords() {
        val dictionaries = linkedMapOf(
            person to dictionary(listOf(Word("よみ", "共通", 100))),
            character to dictionary(listOf(Word("よみ", "別", 1), Word("よみ", "共通", 50))),
        )
        val result = ClassifiedExactCandidates.append("よみ", emptyList(), dictionaries, dictionaries.keys)
        val common = result.single { it.string == "共通" }
        assertEquals(setOf(person, character), common.dictionaryCategories)
        assertEquals(50, common.score)
        assertEquals(101.toShort(), common.leftId)
        assertEquals(201.toShort(), common.rightId)
        val oneCategory = ClassifiedExactCandidates.append("よみ", emptyList(), dictionaries, setOf(person))
        assertEquals(listOf("共通"), oneCategory.map { it.string })
        assertEquals(setOf(person), oneCategory.single().dictionaryCategories)
    }

    @Test fun sortsEqualCostAdditionsBySurfaceAfterLowerCostWords() {
        val data = dictionary(listOf(Word("よみ", "甲", 10), Word("よみ", "乙", 10), Word("よみ", "丙", 5)))
        val result = ClassifiedExactCandidates.append("よみ", emptyList(), mapOf(person to data), setOf(person))
        assertEquals(listOf("丙", "乙", "甲"), result.map { it.string })
        assertEquals(listOf(5, 10, 10), result.map { it.score })
    }

    @Test fun retainsExistingCandidateBehaviorOrderAndSegmentsWhenAddingLabels() {
        val data = dictionary(listOf(Word("よみ", "学習", 100)))
        val learned = Candidate("学習", CANDIDATE_TYPE_LEARNED_DICTIONARY, 2u, -500, "よみ", 3, 4,
            conversionSegments = listOf(CandidateConversionSegment(0, 2, "学習")), standaloneUnigramMatched = true)
        val other = Candidate("先頭", 1, 2u, 1000)
        val collector = linkedMapOf("学習" to learned.conversionSegments)
        val result = ClassifiedExactCandidates.append("よみ", listOf(other, learned), mapOf(person to data), setOf(person), collector)
        assertEquals(listOf(other, learned.copy(dictionaryCategories = setOf(person))), result)
        assertEquals(mapOf("学習" to learned.conversionSegments), collector)
        assertSame(result, ClassifiedExactCandidates.append("よみ", result, mapOf(person to data), setOf(person)))
    }

    @Test fun actionOrDifferentCommitTextDoesNotConsumeAnExactWord() {
        val data = dictionary(listOf(Word("よみ", "表記", 100)))
        val macro = Candidate("表記", CANDIDATE_TYPE_TEXT_MACRO, 2u, 0, sourceId = 42)
        val differentCommit = Candidate("表記", 1, 2u, 0, commitText = "別の確定文字列")
        val result = ClassifiedExactCandidates.append("よみ", listOf(macro, differentCommit), mapOf(person to data), setOf(person))
        assertEquals(listOf(macro, differentCommit), result.take(2))
        assertEquals("表記", result.last().commitText)
        assertEquals(setOf(person), result.last().dictionaryCategories)
    }

    @Test fun marksNewWholeInputWordsAsUnsplitWithoutChangingExistingBunsetsuPatterns() {
        val data = dictionary(listOf(Word("よみ", "追加", 10)))
        val original = BunsetsuCandidateResult(listOf(Candidate("既存", 1, 2u, 0)),
            listOf(listOf(1)), mapOf("既存" to listOf(1)), setOf("既存"))
        val collector = linkedMapOf("追加" to listOf(CandidateConversionSegment(0, 1, "探索で未採用の経路")))
        val result = ClassifiedExactCandidates.append("よみ", original, mapOf(person to data), setOf(person), collector)
        assertEquals(original.splitPatterns, result.splitPatterns)
        assertEquals(original.systemNgramMatchedCandidates, result.systemNgramMatchedCandidates)
        assertEquals(mapOf("既存" to listOf(1), "追加" to emptyList()), result.splitPatternByCandidateString)
        assertEquals(listOf(CandidateConversionSegment(0, 2, "追加")), collector["追加"])
        assertSame(original, ClassifiedExactCandidates.append("よみ", original, mapOf(person to data), emptySet()))
    }
}
