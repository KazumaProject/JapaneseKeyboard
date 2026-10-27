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
import com.kazumaproject.preprocessLBSIntoBooleanArray
import com.kazumaproject.toBooleanArray
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId as EnglishLOUDSWithTermId
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

    @Test fun englishPredictionStopsAcrossAllIndexesAndLimits() {
        val common = dictionary(false)
        val trie = EnglishLOUDSWithTermId(common.LBS, common.getAllLabels(), common.isLeaf, common.getAllTermIds())
        val bits = trie.LBS
        val lbs = SuccinctBitVector(bits)
        for (prefix in listOf("ちゅう", words.last())) {
            val expected = words.filter { it.startsWith(prefix) }
            assertEquals(expected, trie.predictiveSearch(prefix, lbs))
            assertEquals(expected, trie.predictiveSearch(prefix, bits.rank0GetIntArray(), bits.rank1GetIntArray()))
            assertEquals(expected, trie.predictiveSearch(prefix, bits.rank0GetShortArray(), bits.rank1GetShortArray()))
            for (limit in listOf(0, 1, 6, 100)) {
                assertEquals(expected.take(limit), trie.predictiveSearch(prefix, lbs, limit))
            }
        }
    }

    @Test fun dictionaryIteratorsEndWithoutRepeatingOrInventingWords() {
        for (packed in listOf(false, true)) {
            val common = dictionary(packed)
            val english = EnglishLOUDSWithTermId(common.LBS, common.getAllLabels(), common.isLeaf, common.getAllTermIds())
            val lbs = SuccinctBitVector(common.LBS)
            val leaves = SuccinctBitVector(common.isLeaf)
            for (iterator in listOf(common.termIterator(lbs, leaves), english.termIterator(lbs, leaves))) {
                val actual = mutableListOf<String>()
                repeat(words.size) {
                    assertTrue(iterator.hasNext())
                    assertTrue(iterator.hasNext())
                    actual.add(iterator.next().first)
                }
                assertEquals(words, actual)
                assertEquals(false, iterator.hasNext())
            }
        }
        val empty = LOUDSWithTermId()
        assertEquals(false, empty.termIterator(SuccinctBitVector(empty.LBS), SuccinctBitVector(empty.isLeaf)).hasNext())
    }

    @Test fun exactLookupCannotContinueFromFinalLeafViaRootSentinel() {
        val common = dictionary(true)
        val english = EnglishLOUDSWithTermId(common.LBS, common.getAllLabels(), common.isLeaf, common.getAllTermIds())
        val lbs = SuccinctBitVector(common.LBS)
        val ints = common.LBS.rank1GetIntArray()
        val shorts = common.LBS.rank1GetShortArray()
        val preprocess = common.LBS.toBooleanArray().preprocessLBSIntoBooleanArray()
        val lookups = listOf<(String) -> Int>(
            { common.getNodeIndex(it, lbs) }, { english.getNodeIndex(it, lbs) },
            { common.getNodeIndex(it, ints, preprocess) }, { english.getNodeIndex(it, ints, preprocess) },
            { common.getNodeIndex(it, shorts, preprocess) }, { english.getNodeIndex(it, shorts, preprocess) },
        )
        for (lookup in lookups) {
            assertTrue(lookup(words.last()) > 0)
            for (missing in listOf(words.last() + "!", words.last() + " " + words.first())) {
                assertEquals(missing, -1, lookup(missing))
            }
        }
    }

    @Test fun surfaceTriesStopAtFinalLeaf() {
        val common = dictionary(false)
        val japanese = com.kazumaproject.Louds.LOUDS(common.LBS, common.getAllLabels(), common.isLeaf)
        val english = com.kazumaproject.markdownhelperkeyboard.converter.english.louds.LOUDS(
            common.LBS, common.getAllLabels(), common.isLeaf)
        for (search in listOf(japanese::commonPrefixSearch, english::commonPrefixSearch)) {
            assertEquals(listOf(words.last()), search(words.last() + " " + words.first()))
        }
        for (lookup in listOf(japanese::getNodeIndex, english::getNodeIndex)) {
            assertTrue(lookup(words.last()) > 0)
            assertEquals(-1, lookup(words.last() + " " + words.first()))
        }
    }
}
