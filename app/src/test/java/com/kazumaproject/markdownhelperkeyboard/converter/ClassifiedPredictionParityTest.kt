package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedCharArray
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedIntArray
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionAggressiveness
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.engine.TripleDictionaryData
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.BitSet

class ClassifiedPredictionParityTest {
    private val readings = listOf("あいあ", "あいあい", "あいあう", "あいあえお", "あいあえおかきくけこ", "あいあ😀", "あせんやく").sorted()

    private fun trie(packed: Boolean): LOUDSWithTermId {
        val tree = PrefixTreeWithTermId().apply { readings.forEach(::insert) }
        val converted = ConverterWithTermId().convert(tree.root).apply { convertListToBitSet() }
        return if (packed) LOUDSWithTermId.fromPacked(
            converted.LBS, PackedCharArray.from(converted.getAllLabels()), converted.isLeaf,
            PackedIntArray.from(converted.termIds.toIntArray()),
        ) else LOUDSWithTermId(converted.LBS, converted.getAllLabels(), converted.isLeaf, converted.termIds.toIntArray())
    }

    @Test fun boundedVisitorPreservesDfsOrderAndTerminalIdsForDenseAndPackedTries() {
        for (packed in listOf(false, true)) {
            val trie = trie(packed)
            val lbs = SuccinctBitVector(trie.LBS)
            val leaf = SuccinctBitVector(trie.isLeaf)
            for (prefix in listOf("", "あ", "あい", "あいあ", "あいあえ", "あいあ😀", "ない")) {
                for (limit in 0..20) {
                    val expected = trie.predictiveSearch(prefix, lbs).filter { it.length <= limit }
                        .map { it to trie.getTermId(trie.getNodeIndex(it, lbs), leaf) }
                    val actual = mutableListOf<Pair<String, Int>>()
                    trie.forEachPredictiveMatch(prefix, limit, lbs, leaf) { reading, term -> actual.add(reading to term) }
                    assertEquals("packed=$packed prefix=$prefix limit=$limit", expected, actual)
                }
            }
        }
    }

    private fun dictionary(packed: Boolean): TripleDictionaryData {
        val yomi = trie(packed)
        val bits = BitSet()
        val costs = mutableListOf<Short>()
        val positions = mutableListOf<Short>()
        var bit = 0
        for (reading in readings) {
            bit++ // token-list separator, term IDs start at one
            for ((index, cost) in listOf<Short>(2_000, 1_000, 1_000, 0, 1_000, -3_000).withIndex()) {
                bits.set(bit++)
                costs.add(if (reading.length > 6) Short.MIN_VALUE else cost)
                positions.add(index.toShort())
            }
        }
        val tokens = TokenArray.fromPacked(
            positions.toShortArray(), costs.toShortArray(), PackedIntArray.from(IntArray(costs.size) { -2 }), bits,
            ShortArray(6) { (100 + it).toShort() }, ShortArray(6) { (200 + it).toShort() },
        )
        val tango = LOUDS()
        return TripleDictionaryData(tango, yomi, tokens, SuccinctBitVector(yomi.LBS),
            SuccinctBitVector(yomi.isLeaf), SuccinctBitVector(bits), SuccinctBitVector(tango.LBS))
    }

    private fun reference(input: String, data: TripleDictionaryData, config: PredictionConfig): List<Candidate> =
        data.yomiTrie.predictiveSearch(input, data.succinctBitVectorLBSYomi)
            .filter { config.acceptsJapaneseReading(input.length, it.length) }.flatMap { reading ->
                val term = data.yomiTrie.getTermId(data.yomiTrie.getNodeIndex(reading, data.succinctBitVectorLBSYomi), data.succinctBitVectorIsLeafYomi)
                data.tokenArray.getListDictionaryByYomiTermId(term, data.succinctBitVectorTokenArray).map { token ->
                    Candidate(data.surfaceFor(token.nodeId, reading), 9,
                        reading.length.toUByte(), token.wordCost.toInt() + config.completionPenalty(input.length,
                            reading.length, 1_500, null), reading, data.tokenArray.leftIds[token.posTableIndex.toInt()],
                        data.tokenArray.rightIds[token.posTableIndex.toInt()])
                }
            }.sortedBy { it.score }.take(4)

    @Test fun streamingSelectionMatchesOriginalStableSortIncludingTiesAndLongLowCostReadings() {
        val method = KanaKanjiEngine::class.java.getDeclaredMethod("predictedClassifiedCandidates", String::class.java,
            TripleDictionaryData::class.java, PredictionConfig::class.java).apply { isAccessible = true }
        for (packed in listOf(false, true)) {
            val data = dictionary(packed)
            for (input in listOf("あいあ", "あいあえ", "あせん", "ない")) {
                for (lookahead in listOf(1, 3, 6)) for (aggressiveness in PredictionAggressiveness.entries) {
                    val config = PredictionConfig(lookaheadCharacterCount = lookahead, aggressiveness = aggressiveness)
                    assertEquals("$packed/$input/$lookahead/$aggressiveness", reference(input, data, config),
                        method.invoke(KanaKanjiEngine(), input, data, config))
                }
            }
        }
    }

    @Test fun mergedLabelsAndPredictionGatesMatchExistingBehavior() {
        val data = dictionary(true)
        val categories = linkedSetOf(DictionaryCategory.CLASSIFIED_PERSON_ACTOR, DictionaryCategory.CLASSIFIED_PLACE)
        val engine = KanaKanjiEngine()
        KanaKanjiEngine::class.java.getDeclaredField("classifiedDictionaries").apply { isAccessible = true }
            .set(engine, categories.associateWith { data })
        val method = KanaKanjiEngine::class.java.getDeclaredMethod("getClassifiedCandidates", String::class.java,
            Set::class.java, PredictionConfig::class.java).apply { isAccessible = true }
        val config = PredictionConfig()
        val expected = reference("あいあ", data, config).distinct().map { it.copy(dictionaryCategories = categories) }
        assertEquals(expected, method.invoke(engine, "あいあ", categories, config))
        for ((input, gate) in listOf("あい" to config, "あいうえおかきくけこさしすせそたち" to config,
            "あいあ" to config.copy(japanesePredictionEnabled = false), "あいあ" to config.copy(externalMozcEnabled = false))) {
            assertEquals(emptyList<Candidate>(), method.invoke(engine, input, categories, gate))
        }
    }
}
