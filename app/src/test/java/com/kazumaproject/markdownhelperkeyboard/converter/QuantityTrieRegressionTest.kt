package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import java.io.*
import java.util.zip.ZipInputStream
import org.junit.Test
import org.junit.Assert.*

class QuantityTrieRegressionTest {
    @Test fun predictiveTraversalNeverReturnsToTheRoot() {
        val asset = listOf(File("app/src/main/assets/system/yomi.dat.zip"), File("src/main/assets/system/yomi.dat.zip")).first { it.exists() }
        val trie = ZipInputStream(asset.inputStream()).use { zip -> zip.nextEntry; ObjectInputStream(zip).use { LOUDSWithTermId().readExternalNotCompress(it) } }
        val compactFile = listOf(File("app/build/generated/compactSystemDictionary/assets/system/system.compact.kdict"), File("build/generated/compactSystemDictionary/assets/system/system.compact.kdict")).first { it.exists() }
        val compact = com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactSystemDictionaryReader.read(java.nio.ByteBuffer.wrap(compactFile.readBytes())).get(com.kazumaproject.markdownhelperkeyboard.converter.compact.CompactDictionaryKind.SYSTEM)
        assertEquals(trie.LBS, compact.yomiTrie.LBS)
        val expectedBits = SuccinctBitVector(compact.yomiTrie.LBS)
        val bits = compact.yomiLbsIndex
        for (i in 0 until bits.size() step 8) assertEquals("rank at $i", expectedBits.rank1(i), bits.rank1(i))
        val traverse = trie.javaClass.getDeclaredMethod("traverse", Int::class.javaPrimitiveType, Char::class.javaPrimitiveType, SuccinctBitVector::class.java).apply { isAccessible = true }
        var pos = 0
        for (char in "あとななこだけ") {
            val next = traverse.invoke(trie, pos, char, bits) as Int
            if (next < 0) break
            assertTrue("child $next must follow parent $pos", next > pos)
            pos = next
        }
        assertEquals(emptyList<String>(), compact.yomiTrie.predictiveSearch("あとななこだけ", bits))
        // Every actual node must have a forward-only child link or no children.
        val firstChild = trie.javaClass.getDeclaredMethod("firstChild", Int::class.javaPrimitiveType, SuccinctBitVector::class.java).apply { isAccessible = true }
        var at = trie.LBS.nextSetBit(0)
        while (at >= 0) {
            val selected = firstChild.invoke(trie, at, bits) as Int
            assertTrue("node=$at rank=${bits.rank1(at)} selected=$selected", selected < 0 || selected > at)
            at = trie.LBS.nextSetBit(at + 1)
        }
    }
}
