package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.bitset.rank1GetIntArray
import com.kazumaproject.bitset.rank1GetShortArray
import com.kazumaproject.preprocessLBSIntoBooleanArray
import com.kazumaproject.toBooleanArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId as EnglishLOUDSWithTermId
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LOUDSWithTermIdExactMatchTest {

    @Test
    fun commonDictionaryLookupRequiresTerminalNodeAcrossAllIndexes() {
        val louds = buildCommonLouds()

        assertExactLookupContract(
            lbs = louds.LBS,
            isLeaf = louds.isLeaf,
            getLetter = louds::getLetter,
            getNodeWithIntIndex = { input, rank, preprocess ->
                louds.getNodeIndex(input, rank, preprocess)
            },
            getNodeWithShortIndex = { input, rank, preprocess ->
                louds.getNodeIndex(input, rank, preprocess)
            },
            getNodeWithSuccinctIndex = louds::getNodeIndex,
            getTermWithIntIndex = louds::getTermId,
            getTermWithShortIndex = louds::getTermIdShortArray,
            getTermWithSuccinctIndex = louds::getTermId,
            getTermShortWithSuccinctIndex = louds::getTermIdShortArray,
        )
    }

    @Test
    fun englishDictionaryLookupRequiresTerminalNodeAcrossAllIndexes() {
        val common = buildCommonLouds()
        val louds = EnglishLOUDSWithTermId(
            common.LBS,
            common.getAllLabels(),
            common.isLeaf,
            common.getAllTermIds(),
        )

        assertExactLookupContract(
            lbs = louds.LBS,
            isLeaf = louds.isLeaf,
            getLetter = louds::getLetter,
            getNodeWithIntIndex = { input, rank, preprocess ->
                louds.getNodeIndex(input, rank, preprocess)
            },
            getNodeWithShortIndex = { input, rank, preprocess ->
                louds.getNodeIndex(input, rank, preprocess)
            },
            getNodeWithSuccinctIndex = louds::getNodeIndex,
            getTermWithIntIndex = louds::getTermId,
            getTermWithShortIndex = louds::getTermIdShortArray,
            getTermWithSuccinctIndex = louds::getTermId,
            getTermShortWithSuccinctIndex = louds::getTermIdShortArray,
        )
    }

    private fun assertExactLookupContract(
        lbs: java.util.BitSet,
        isLeaf: java.util.BitSet,
        getLetter: (Int) -> String,
        getNodeWithIntIndex: (String, IntArray, IntArray) -> Int,
        getNodeWithShortIndex: (String, ShortArray, IntArray) -> Int,
        getNodeWithSuccinctIndex: (String, SuccinctBitVector) -> Int,
        getTermWithIntIndex: (Int, IntArray) -> Int,
        getTermWithShortIndex: (Int, ShortArray) -> Short,
        getTermWithSuccinctIndex: (Int, SuccinctBitVector) -> Int,
        getTermShortWithSuccinctIndex: (Int, SuccinctBitVector) -> Short,
    ) {
        val lbsRankInt = lbs.rank1GetIntArray()
        val lbsRankShort = lbs.rank1GetShortArray()
        val lbsPreprocess = lbs.toBooleanArray().preprocessLBSIntoBooleanArray()
        val lbsSuccinct = SuccinctBitVector(lbs)
        val leafRankInt = isLeaf.rank1GetIntArray()
        val leafRankShort = isLeaf.rank1GetShortArray()
        val leafSuccinct = SuccinctBitVector(isLeaf)

        val exactInt = getNodeWithIntIndex("car", lbsRankInt, lbsPreprocess)
        val exactShort = getNodeWithShortIndex("car", lbsRankShort, lbsPreprocess)
        val exactSuccinct = getNodeWithSuccinctIndex("car", lbsSuccinct)
        assertTrue(exactInt > 0)
        assertEquals(exactInt, exactShort)
        assertEquals(exactInt, exactSuccinct)
        assertTrue(getTermWithIntIndex(exactInt, leafRankInt) > 0)
        assertEquals(
            getTermWithIntIndex(exactInt, leafRankInt),
            getTermWithShortIndex(exactInt, leafRankShort).toInt(),
        )
        assertEquals(
            getTermWithIntIndex(exactInt, leafRankInt),
            getTermWithSuccinctIndex(exactInt, leafSuccinct),
        )
        assertEquals(
            getTermWithIntIndex(exactInt, leafRankInt),
            getTermShortWithSuccinctIndex(exactInt, leafSuccinct).toInt(),
        )

        listOf("", "c", "ca", "cab").forEach { input ->
            assertEquals(-1, getNodeWithIntIndex(input, lbsRankInt, lbsPreprocess))
            assertEquals(-1, getNodeWithShortIndex(input, lbsRankShort, lbsPreprocess))
            assertEquals(-1, getNodeWithSuccinctIndex(input, lbsSuccinct))
        }

        val internalNode = (2 until lbs.size()).first { nodeIndex ->
            lbs[nodeIndex] && !isLeaf[nodeIndex] && getLetter(nodeIndex) == "ca"
        }
        listOf(-1, internalNode, lbs.size()).forEach { invalidNode ->
            assertEquals(-1, getTermWithIntIndex(invalidNode, leafRankInt))
            assertEquals(-1, getTermWithShortIndex(invalidNode, leafRankShort).toInt())
            assertEquals(-1, getTermWithSuccinctIndex(invalidNode, leafSuccinct))
            assertEquals(-1, getTermShortWithSuccinctIndex(invalidNode, leafSuccinct).toInt())
        }
    }

    private fun buildCommonLouds(): LOUDSWithTermId {
        val tree = PrefixTreeWithTermId().apply {
            insert("car")
            insert("cart")
        }
        val converted = ConverterWithTermId().convert(tree.root).apply {
            convertListToBitSet()
        }
        return LOUDSWithTermId(
            converted.LBS,
            converted.getAllLabels(),
            converted.isLeaf,
            converted.termIds.toIntArray(),
        )
    }
}
