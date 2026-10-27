package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.bitset.rank0GetIntArray
import com.kazumaproject.bitset.rank0GetShortArray
import com.kazumaproject.bitset.rank1GetIntArray
import com.kazumaproject.bitset.rank1GetShortArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedCharArray
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedIntArray
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import java.util.BitSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LOUDSWithTermIdPredictionBoundaryTest {
    private val words = (0 until 32).map { "ちゅう${('あ'.code + it).toChar()}" }

    private fun dictionary(packed: Boolean): LOUDSWithTermId {
        val tree = PrefixTreeWithTermId().apply { words.forEach(::insert) }
        val converted = ConverterWithTermId().convert(tree.root).apply { convertListToBitSet() }
        // Match compact asset loading: valueOf drops unused trailing zero words.
        val bits = BitSet.valueOf(converted.LBS.toLongArray())
        assertTrue(bits.size() - bits.cardinality() < bits.cardinality())
        return if (packed) LOUDSWithTermId.fromPacked(
            bits, PackedCharArray.from(converted.getAllLabels()), converted.isLeaf,
            PackedIntArray.from(converted.termIds.toIntArray()),
        ) else LOUDSWithTermId(bits, converted.getAllLabels(), converted.isLeaf, converted.termIds.toIntArray())
    }

    @Test fun predictionStopsAtFinalLeavesAcrossAllIndexes() {
        for (packed in listOf(false, true)) {
            val trie = dictionary(packed)
            val bits = trie.LBS
            val index = SuccinctBitVector(bits)
            for (prefix in listOf("ちゅう", words.last())) {
                val expected = words.filter { it.startsWith(prefix) }
                assertEquals(expected, trie.predictiveSearch(prefix, index))
                assertEquals(expected, trie.predictiveSearch(prefix, bits.rank0GetIntArray(), bits.rank1GetIntArray()))
                assertEquals(expected, trie.predictiveSearch(prefix, bits.rank0GetShortArray(), bits.rank1GetShortArray()))
            }
        }
    }

    @Test fun boundedPredictionDoesNotReenterRootOrInventReadings() {
        for (packed in listOf(false, true)) {
            val trie = dictionary(packed)
            val lbs = SuccinctBitVector(trie.LBS)
            val leaf = SuccinctBitVector(trie.isLeaf)
            val matches = mutableListOf<Pair<String, Int>>()
            trie.forEachPredictiveMatch("ちゅう", 12, lbs, leaf) { reading, term ->
                matches.add(reading to term)
            }
            assertEquals(words, matches.map { it.first })
            matches.forEach { (reading, term) ->
                assertEquals(trie.getTermId(trie.getNodeIndex(reading, lbs), leaf), term)
            }
        }
    }
}
