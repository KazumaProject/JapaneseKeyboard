package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.Converter
import com.kazumaproject.prefix.PrefixTree
import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedCharArray
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedIntArray
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath.ScoredWholeInputCandidate
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
        val surfaces = PrefixTree().apply { words.map { it.surface }.distinct().sorted().forEach(::insert) }
        val tango = Converter().convert(surfaces.root).apply { convertListToBitSet() }
        val nodeIds = mutableListOf<Int>()
        var bit = 0
        byReading.values.forEach { entries ->
            bit++
            entries.forEach { word ->
                val pos = positions.size
                positions.add(pos.toShort()); costs.add(word.cost); bits.set(bit++)
                nodeIds.add(tango.getNodeIndex(word.surface))
            }
        }
        val tokens = TokenArray.fromPacked(positions.toShortArray(), costs.toShortArray(),
            PackedIntArray.from(nodeIds.toIntArray()), bits,
            ShortArray(words.size) { (100 + it).toShort() }, ShortArray(words.size) { (200 + it).toShort() })
        return TripleDictionaryData(tango, yomi, tokens, SuccinctBitVector(yomi.LBS),
            SuccinctBitVector(yomi.isLeaf), SuccinctBitVector(bits), SuccinctBitVector(tango.LBS))
    }

    private fun merge(
        input: String,
        candidates: List<Candidate>,
        dictionaries: Map<DictionaryCategory, TripleDictionaryData>,
        enabled: Set<DictionaryCategory>,
        collector: MutableMap<String, List<CandidateConversionSegment>>? = null,
        primaryCount: Int = candidates.size,
        score: (Candidate) -> Candidate = { it },
    ) = ClassifiedExactCandidates.merge(input, candidates, dictionaries, enabled,
        { ScoredWholeInputCandidate(score(it)) },
        collector, primaryCount)

    private fun merge(
        input: String,
        result: BunsetsuCandidateResult,
        dictionaries: Map<DictionaryCategory, TripleDictionaryData>,
        enabled: Set<DictionaryCategory>,
        collector: MutableMap<String, List<CandidateConversionSegment>>? = null,
    ) = ClassifiedExactCandidates.merge(input, result, dictionaries, enabled,
        { ScoredWholeInputCandidate(it) }, collector)

    @Test fun keepsEveryHomophoneAndRanksWithMainCandidates() {
        val data = dictionary((0 until 12).map { Word("よみ", "語${it.toString().padStart(2, '0')}", (11 - it).toShort()) })
        val original = listOf(Candidate("既存", 1, 2u, -100))
        val result = merge("よみ", original, mapOf(person to data), setOf(person))
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
                assertEquals(listOf("表記$reading"), merge(reading, emptyList(),
                    mapOf(person to data), setOf(person)).map { it.string })
            }
            for (reading in listOf("", "かなか", "ない")) {
                val original = listOf(Candidate("元", 1, 1u, 0))
                assertSame(original, merge(reading, original, mapOf(person to data), setOf(person)))
            }
        }
    }

    @Test fun combinesEnabledCategoryLabelsAndKeepsLowestCostPosForNewWords() {
        val dictionaries = linkedMapOf(
            person to dictionary(listOf(Word("よみ", "共通", 100))),
            character to dictionary(listOf(Word("よみ", "別", 1), Word("よみ", "共通", 50))),
        )
        val result = merge("よみ", emptyList(), dictionaries, dictionaries.keys)
        val common = result.single { it.string == "共通" }
        assertEquals(setOf(person, character), common.dictionaryCategories)
        assertEquals(50, common.score)
        assertEquals(101.toShort(), common.leftId)
        assertEquals(201.toShort(), common.rightId)
        val oneCategory = merge("よみ", emptyList(), dictionaries, setOf(person))
        assertEquals(listOf("共通"), oneCategory.map { it.string })
        assertEquals(setOf(person), oneCategory.single().dictionaryCategories)
    }

    @Test fun sortsEqualCostAdditionsBySurfaceAfterLowerCostWords() {
        val data = dictionary(listOf(Word("よみ", "甲", 10), Word("よみ", "乙", 10), Word("よみ", "丙", 5)))
        val result = merge("よみ", emptyList(), mapOf(person to data), setOf(person))
        assertEquals(listOf("丙", "乙", "甲"), result.map { it.string })
        assertEquals(listOf(5, 10, 10), result.map { it.score })
    }

    @Test fun retainsExistingCandidateBehaviorOrderAndSegmentsWhenAddingLabels() {
        val data = dictionary(listOf(Word("よみ", "学習", 100)))
        val learned = Candidate("学習", CANDIDATE_TYPE_LEARNED_DICTIONARY, 2u, -500, "よみ", 3, 4,
            conversionSegments = listOf(CandidateConversionSegment(0, 2, "学習")), standaloneUnigramMatched = true)
        val other = Candidate("先頭", 1, 2u, 1000)
        val collector = linkedMapOf("学習" to learned.conversionSegments)
        val result = merge("よみ", listOf(other, learned), mapOf(person to data), setOf(person), collector)
        assertEquals(listOf(learned.copy(dictionaryCategories = setOf(person)), other), result)
        assertEquals(mapOf("学習" to learned.conversionSegments), collector)
        assertEquals(result, merge("よみ", result, mapOf(person to data), setOf(person)))
    }

    @Test fun actionOrDifferentCommitTextDoesNotConsumeAnExactWord() {
        val data = dictionary(listOf(Word("よみ", "表記", 100)))
        val macro = Candidate("表記", CANDIDATE_TYPE_TEXT_MACRO, 2u, 0, sourceId = 42)
        val differentCommit = Candidate("表記", 1, 2u, 0, commitText = "別の確定文字列")
        val result = merge("よみ", listOf(macro, differentCommit), mapOf(person to data), setOf(person))
        assertEquals(listOf(macro, differentCommit), result.take(2))
        assertEquals("表記", result.last().commitText)
        assertEquals(setOf(person), result.last().dictionaryCategories)
    }

    @Test fun marksNewWholeInputWordsAsUnsplitWithoutChangingExistingBunsetsuPatterns() {
        val data = dictionary(listOf(Word("よみ", "追加", 10)))
        val original = BunsetsuCandidateResult(listOf(Candidate("既存", 1, 2u, 0)),
            listOf(listOf(1)), mapOf("既存" to listOf(1)), setOf("既存"))
        val collector = linkedMapOf("追加" to listOf(CandidateConversionSegment(0, 1, "探索で未採用の経路")))
        val result = merge("よみ", original, mapOf(person to data), setOf(person), collector)
        assertEquals(original.splitPatterns, result.splitPatterns)
        assertEquals(original.systemNgramMatchedCandidates, result.systemNgramMatchedCandidates)
        assertEquals(mapOf("既存" to listOf(1), "追加" to emptyList()), result.splitPatternByCandidateString)
        assertEquals(listOf(CandidateConversionSegment(0, 2, "追加")), collector["追加"])
        assertSame(original, merge("よみ", original, mapOf(person to data), emptySet()))
    }

    @Test fun insertsBeforeAuxiliaryCandidatesAndDoesNotLetAnAuxiliaryDuplicateHideTheWord() {
        val data = dictionary(listOf(Word("よみ", "完全一致", 50)))
        val main = Candidate("主候補", 1, 2u, 100)
        val auxiliary = Candidate("完全一致", 7, 2u, 0)
        val result = merge("よみ", listOf(main, auxiliary), mapOf(person to data), setOf(person), primaryCount = 1)
        assertEquals(listOf("完全一致", "主候補", "完全一致"), result.map { it.string })
        assertEquals(1.toByte(), result.first().type)
        assertEquals(50, result.first().score)
        assertSame(auxiliary, result.last())
    }

    @Test fun choosesDuplicatePosByFinalPathCostRatherThanRawWordCost() {
        val data = dictionary(listOf(Word("よみ", "共通", 10), Word("よみ", "共通", 50)))
        val result = merge("よみ", emptyList(), mapOf(person to data), setOf(person), score = {
            it.copy(score = it.score + if (it.leftId == 100.toShort()) 500 else 0)
        })
        assertEquals(50, result.single().score)
        assertEquals(101.toShort(), result.single().leftId)
    }

    @Test fun lowerCostWholeWordReplacesAnExistingPathAndClearsItsSplitMetadata() {
        val data = dictionary(listOf(Word("よみ", "既存", 10)))
        val old = Candidate("既存", 1, 2u, 100, yomi = "よみ")
        val original = BunsetsuCandidateResult(listOf(old), listOf(listOf(1)), mapOf("既存" to listOf(1)))
        val collector = linkedMapOf("既存" to listOf(CandidateConversionSegment(0, 1, "既"), CandidateConversionSegment(1, 2, "存")))
        val result = merge("よみ", original, mapOf(person to data), setOf(person), collector)
        assertEquals(10, result.candidates.single().score)
        assertEquals(emptyList<Int>(), result.splitPatternByCandidateString["既存"])
        assertEquals(listOf(CandidateConversionSegment(0, 2, "既存")), collector["既存"])
    }

    @Test fun preservesExistingStandalonePriorityOverALowerCostExactWord() {
        val data = dictionary(listOf(Word("よみ", "完全一致", 10)))
        val preferred = Candidate("既存", 1, 2u, 100, standaloneUnigramMatched = true)
        val result = merge("よみ", listOf(preferred), mapOf(person to data), setOf(person))
        assertSame(preferred, result.first())
        assertEquals(10, result.last().score)
    }

    @Test fun carriesSingleNodeSystemRulePriorityIntoBunsetsuMetadataWithoutChangingScore() {
        val data = dictionary(listOf(Word("よみ", "完全一致", 100)))
        val original = BunsetsuCandidateResult(listOf(Candidate("既存", 1, 2u, 0)), emptyList())
        val result = ClassifiedExactCandidates.merge("よみ", original, mapOf(person to data), setOf(person),
            { ScoredWholeInputCandidate(it, true) })
        assertEquals("完全一致", result.candidates.first().string)
        assertEquals(100, result.candidates.first().score)
        assertFalse(result.candidates.first().standaloneUnigramMatched)
        assertEquals(setOf("完全一致"), result.systemNgramMatchedCandidates)
    }

    @Test fun doesNotPromoteAWordBecauseOnlyADiscardedHigherCostPosMatchesASystemRule() {
        val data = dictionary(listOf(Word("よみ", "完全一致", 100), Word("よみ", "完全一致", 50)))
        val original = BunsetsuCandidateResult(listOf(Candidate("既存", 1, 2u, 0)), emptyList())
        val result = ClassifiedExactCandidates.merge("よみ", original, mapOf(person to data), setOf(person),
            { ScoredWholeInputCandidate(it, it.leftId == 100.toShort()) })
        assertEquals("既存", result.candidates.first().string)
        assertEquals(50, result.candidates.last().score)
        assertEquals(emptySet<String>(), result.systemNgramMatchedCandidates)
    }
}
