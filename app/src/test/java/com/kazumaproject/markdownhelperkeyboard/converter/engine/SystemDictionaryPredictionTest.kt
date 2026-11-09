package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedIntArray
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.BitSet

class SystemDictionaryPredictionTest {
    private val input = "ちゅう"
    private val words = listOf("ちゅう", "ちゅうお", "ちゅうが", "ちゅうがく", "ちゅうがくせい")

    @Test
    fun predictionMatchesStableFullSortAcrossBothDictionaries() {
        val system = dictionary(10)
        val user = dictionary(20)
        for (limit in listOf(1, 4, 16)) {
            for (lookahead in listOf(1, 3, 6)) {
                for (aggressiveness in PredictionAggressiveness.entries) {
                    val config = PredictionConfig(
                        systemCandidateLimit = limit,
                        lookaheadCharacterCount = lookahead,
                        aggressiveness = aggressiveness,
                    )
                    val expected = (reference(system, config) + reference(user, config))
                        .sortedBy { it.score }
                        .take(limit)
                    assertEquals(
                        "limit=$limit lookahead=$lookahead aggressiveness=$aggressiveness",
                        expected,
                        predictSystemDictionaryCandidates(input, config, system, user),
                    )
                }
            }
        }
    }

    @Test
    fun predictionHonorsIndependentDictionarySwitchesAndInputLength() {
        val system = dictionary(10)
        val user = dictionary(20)
        val config = PredictionConfig()
        assertEquals(
            reference(user, config).sortedBy { it.score }.take(config.systemCandidateLimit),
            predictSystemDictionaryCandidates(input, config.copy(systemDictionaryEnabled = false), system, user),
        )
        assertEquals(
            reference(system, config).sortedBy { it.score }.take(config.systemCandidateLimit),
            predictSystemDictionaryCandidates(input, config.copy(systemUserDictionaryEnabled = false), system, user),
        )
        assertEquals(emptyList<Candidate>(), predictSystemDictionaryCandidates(
            input, config.copy(japanesePredictionEnabled = false), system, user,
        ))
        assertEquals(emptyList<Candidate>(), predictSystemDictionaryCandidates(
            input.take(2), config, system, user,
        ))
    }

    private fun dictionary(posOffset: Int): TripleDictionaryData {
        val tree = PrefixTreeWithTermId().apply { words.forEach(::insert) }
        val converted = ConverterWithTermId().convert(tree.root).apply { convertListToBitSet() }
        val yomi = LOUDSWithTermId(
            converted.LBS, converted.getAllLabels(), converted.isLeaf, converted.termIds.toIntArray(),
        )
        val bits = BitSet()
        val costs = ShortArray(words.size * 2)
        var bit = 0
        words.forEachIndexed { index, word ->
            bit++ // Start of the token range for this reading.
            bits.set(bit++)
            bits.set(bit++)
            costs[index * 2] = if (word == input || word == words.last()) Short.MIN_VALUE else 1_000
            costs[index * 2 + 1] = if (word.length == 5) -7_000 else 1_000
        }
        val tokens = TokenArray.fromPacked(
            ShortArray(costs.size) { (it % 2).toShort() },
            costs,
            PackedIntArray.from(IntArray(costs.size) { if (it % 2 == 0) -2 else -1 }),
            bits,
            shortArrayOf(posOffset.toShort(), (posOffset + 1).toShort()),
            shortArrayOf((posOffset + 2).toShort(), (posOffset + 3).toShort()),
        )
        val tango = LOUDS()
        return TripleDictionaryData(
            tango, yomi, tokens, SuccinctBitVector(yomi.LBS), SuccinctBitVector(yomi.isLeaf),
            SuccinctBitVector(bits), SuccinctBitVector(tango.LBS),
        )
    }

    private fun reference(data: TripleDictionaryData, config: PredictionConfig): List<Candidate> =
        data.yomiTrie.predictiveSearch(input, data.succinctBitVectorLBSYomi)
            .filter { config.acceptsJapaneseReading(input.length, it.length) }
            .flatMap { reading ->
                val node = data.yomiTrie.getNodeIndex(reading, data.succinctBitVectorLBSYomi)
                val termId = data.yomiTrie.getTermId(node, data.succinctBitVectorIsLeafYomi)
                data.tokenArray.getListDictionaryByYomiTermId(termId, data.succinctBitVectorTokenArray)
                    .map { token ->
                        Candidate(
                            string = if (token.nodeId == -1) reading.hiraToKata() else reading,
                            type = 9,
                            length = reading.length.toUByte(),
                            score = token.wordCost.toInt() + config.completionPenalty(input.length, reading.length),
                            leftId = data.tokenArray.leftIds[token.posTableIndex.toInt()],
                            rightId = data.tokenArray.rightIds[token.posTableIndex.toInt()],
                        )
                    }
            }
}
