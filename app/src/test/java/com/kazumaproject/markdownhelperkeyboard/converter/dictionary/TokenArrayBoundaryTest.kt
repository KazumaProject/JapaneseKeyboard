package com.kazumaproject.markdownhelperkeyboard.converter.dictionary

import com.kazumaproject.bitset.rank0GetIntArray
import com.kazumaproject.bitset.rank0GetShortArray
import com.kazumaproject.bitset.rank1GetIntArray
import com.kazumaproject.bitset.rank1GetShortArray
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.dictionary.models.TokenEntry
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.BitSet
import org.junit.Assert.*
import org.junit.Test

class TokenArrayBoundaryTest {
    private fun fixture(bitCount: Int): TokenArray {
        val tokens = bitCount - 2
        val bits = BitSet().apply { set(1); set(3, bitCount) }
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use {
                it.writeObject(ShortArray(tokens))
                it.writeObject(ShortArray(tokens) { index -> (100 - index).toShort() })
                it.writeObject(IntArray(tokens) { index -> 1000 + index })
                it.writeObject(bits)
            }
        }.toByteArray()
        return TokenArray().also { it.readExternal(ObjectInputStream(ByteArrayInputStream(bytes))) }
    }

    @Test fun finalGroupSurvivesWordBoundaryAndPaddingAcrossAllAccessors() {
        for (bits in listOf(63, 64, 65, 127, 128, 129)) {
            val tokens = fixture(bits)
            val index = SuccinctBitVector(tokens.bitvector)
            val expected = (1 until bits - 2).map { TokenEntry(0, (100 - it).toShort(), 1000 + it) }
            assertEquals("succinct/$bits", expected, tokens.getListDictionaryByYomiTermId(2, index))
            assertEquals("short succinct/$bits", expected, tokens.getListDictionaryByYomiTermIdShortArray(2, index))
            assertEquals("int rank/$bits", expected, tokens.getListDictionaryByYomiTermId(2,
                tokens.bitvector.rank0GetIntArray(), tokens.bitvector.rank1GetIntArray()))
            assertEquals("short rank/$bits", expected, tokens.getListDictionaryByYomiTermIdShortArray(2,
                tokens.bitvector.rank0GetShortArray(), tokens.bitvector.rank1GetShortArray()))
            val visited = mutableListOf<TokenEntry>()
            tokens.forEachDictionaryByYomiTermId(2, index) { pos, cost, node -> visited.add(TokenEntry(pos, cost, node)) }
            assertEquals(expected, visited)
            visited.clear()
            tokens.forEachDictionaryByYomiTermIdShortArray(2, index) { pos, cost, node -> visited.add(TokenEntry(pos, cost, node)) }
            assertEquals(expected, visited)
            visited.clear()
            tokens.forEachLowestCostDictionaryByYomiTermId(2, index, IntArray(4)) { pos, cost, node -> visited.add(TokenEntry(pos, cost, node)) }
            assertEquals(expected.sortedBy { it.wordCost }.take(4), visited)
            assertEquals(listOf(TokenEntry(0, 100, 1000)), tokens.getListDictionaryByYomiTermId(1, index))
        }
    }

    @Test fun absentGroupsAndEmptyDictionaryDoNotReturnUnrelatedTokens() {
        for (tokens in listOf(fixture(64), TokenArray())) {
            val index = SuccinctBitVector(tokens.bitvector)
            for (id in listOf(0, 500)) {
                assertTrue(tokens.getListDictionaryByYomiTermId(id, index).isEmpty())
                assertTrue(tokens.getListDictionaryByYomiTermIdShortArray(id.toShort(), index).isEmpty())
                assertTrue(tokens.getListDictionaryByYomiTermId(id,
                    tokens.bitvector.rank0GetIntArray(), tokens.bitvector.rank1GetIntArray()).isEmpty())
                assertTrue(tokens.getListDictionaryByYomiTermIdShortArray(id.toShort(),
                    tokens.bitvector.rank0GetShortArray(), tokens.bitvector.rank1GetShortArray()).isEmpty())
                tokens.forEachDictionaryByYomiTermId(id, index) { _, _, _ -> fail("Visited an absent group") }
                tokens.forEachDictionaryByYomiTermIdShortArray(id.toShort(), index) { _, _, _ -> fail("Visited an absent group") }
                tokens.forEachLowestCostDictionaryByYomiTermId(id, index, IntArray(4)) { _, _, _ -> fail("Visited an absent group") }
            }
        }
    }
}
