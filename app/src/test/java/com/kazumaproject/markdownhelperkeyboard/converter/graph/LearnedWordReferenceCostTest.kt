package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.Louds.Converter
import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedIntArray
import com.kazumaproject.markdownhelperkeyboard.converter.engine.TripleDictionaryData
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import com.kazumaproject.prefix.PrefixTree
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.BitSet

class LearnedWordReferenceCostTest {
    @Test
    fun partialNameUsesCheapestMatchingTokenAndIgnoresDifferentPartOfSpeech() = runBlocking {
        val system = dictionary(listOf(
            Word("慧", 5000), Word("慧", 4500), Word("慧", 10, contextId = 1984),
        ))
        assertEquals(4000, learnedNodeCost(system))
    }

    @Test
    fun activeSystemUserAndClassifiedReferencesParticipateInTheMinimum() = runBlocking {
        val system = dictionary(listOf(Word("慧", 5000)))
        val systemUser = dictionary(listOf(Word("慧", 4000)))
        val classified = dictionary(listOf(Word("慧", 3000)))
        assertEquals(2500, learnedNodeCost(system, systemUser, classified))
    }

    @Test
    fun classifiedLiteralOverridesAreDecodedLikeTheirConversionNodes() = runBlocking {
        val system = dictionary(listOf(Word("計", 5000)))
        val classified = dictionary(listOf(Word("慧", 3000)), literalOverride = true)
        assertEquals(2500, learnedNodeCost(system, classified = classified))
    }

    @Test
    fun legacyMissingPartOfSpeechUsesNounContextWithoutChangingTheRecord() = runBlocking {
        val system = dictionary(listOf(Word("慧", 4500, contextId = 1851)))
        val entry = LearnEntity("けい", "慧", score = 0, id = 3)
        assertEquals(4000, learnedNodeCost(system, entry = entry))
    }

    @Test
    fun manualUserDictionaryCostsUseTheirMappedContextIds() = runBlocking {
        val system = dictionary(listOf(Word("慧", 5000, contextId = 1851)))
        val entry = LearnEntity("けい", "慧", score = 0, leftId = 1851, rightId = 1851, id = 1)
        val manualWord = UserWord(word = "慧", reading = "けい", posIndex = 0, posScore = 3500)
        assertEquals(3000, learnedNodeCost(system, entry = entry, manualWord = manualWord))
    }

    private suspend fun learnedNodeCost(
        system: TripleDictionaryData,
        systemUser: TripleDictionaryData? = null,
        classified: TripleDictionaryData? = null,
        entry: LearnEntity = LearnEntity("けい", "慧", score = 0, leftId = 1922, rightId = 1922, id = 1),
        manualWord: UserWord? = null,
    ): Int {
        val repository = LearnRepository(LearnedConversionRegressionTest.InMemoryReadOnlyLearnDao(listOf(entry)))
        val userRepository = manualWord?.let { word ->
            mock<UserDictionaryRepository>().also { user ->
                whenever(user.commonPrefixSearchInUserDict(any())).thenAnswer { invocation ->
                    if (invocation.getArgument<String>(0).startsWith(word.reading)) listOf(word) else emptyList()
                }
            }
        }
        val builder = GraphBuilder()
        systemUser?.let {
            builder.updateSystemUserDictionary(it.yomiTrie, it.tangoTrie, it.tokenArray,
                it.succinctBitVectorLBSYomi, it.succinctBitVectorIsLeafYomi,
                it.succinctBitVectorTokenArray, it.succinctBitVectorTangoLBS)
        }
        val graph = builder.constructGraph(
            str = "けいさん", yomiTrie = system.yomiTrie, tangoTrie = system.tangoTrie,
            tokenArray = system.tokenArray, succinctBitVectorLBSYomi = system.succinctBitVectorLBSYomi,
            succinctBitVectorIsLeafYomi = system.succinctBitVectorIsLeafYomi,
            succinctBitVectorTokenArray = system.succinctBitVectorTokenArray,
            succinctBitVectorTangoLBS = system.succinctBitVectorTangoLBS,
            userDictionaryRepository = userRepository, learnRepository = repository,
            classifiedDictionaries = classified?.let {
                mapOf(DictionaryCategory.CLASSIFIED_PERSON_ACTOR to it)
            }.orEmpty(),
            isOmissionSearchEnable = false, typoCorrectionOffsetScore = 3000,
            omissionSearchOffSetScore = 1900,
        )
        assertEquals(listOf(entry), repository.allSuspend())
        return graph.values.flatten().single {
            it.tango == entry.out && it.candidateSource == CandidateSource.LEARNED_DICTIONARY
        }.score
    }

    private data class Word(val surface: String, val cost: Int, val contextId: Short = 1922)

    private fun dictionary(words: List<Word>, literalOverride: Boolean = false): TripleDictionaryData {
        val reading = "けい"
        val yomiTree = PrefixTreeWithTermId().apply { insert(reading) }
        val convertedYomi = ConverterWithTermId().convert(yomiTree.root).apply { convertListToBitSet() }
        val yomi = LOUDSWithTermId(convertedYomi.LBS, convertedYomi.getAllLabels(),
            convertedYomi.isLeaf, convertedYomi.termIds.toIntArray())
        val surfaceTree = PrefixTree().apply { words.map { it.surface }.distinct().sorted().forEach(::insert) }
        val tango = Converter().convert(surfaceTree.root).apply { convertListToBitSet() }
        val tokenBits = BitSet().apply { set(1, words.size + 1) }
        val tokens = TokenArray.fromPacked(
            ShortArray(words.size) { it.toShort() }, words.map { it.cost.toShort() }.toShortArray(),
            PackedIntArray.from(words.map {
                if (literalOverride) -2 else tango.getNodeIndex(it.surface)
            }.toIntArray()),
            tokenBits, words.map { it.contextId }.toShortArray(), words.map { it.contextId }.toShortArray(),
        )
        return TripleDictionaryData(tango, yomi, tokens, SuccinctBitVector(yomi.LBS),
            SuccinctBitVector(yomi.isLeaf), SuccinctBitVector(tokenBits), SuccinctBitVector(tango.LBS),
            literalSurfaces = if (literalOverride) mapOf("-2\t$reading" to words.single().surface) else emptyMap())
    }
}
