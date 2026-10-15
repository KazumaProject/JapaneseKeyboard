package com.kazumaproject.markdownhelperkeyboard.converter.ngram

import com.kazumaproject.graph.Node
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PackedStandaloneUnigramDictionaryTest {
    private fun bytes(): ByteArray = File("src/main/assets/ngram/system_ngram_standalone_unigram.dat").readBytes()

    @Test fun standaloneMatchesOnlyWholeInputLiteralReadingNodes() {
        val dictionary = PackedSystemNgramDictionary.readStandalone(bytes())
        assertEquals(5, dictionary.ruleCount)
        for (word in listOf("しろ", "ごと", "したい", "はいる", "みた")) {
            val node = node(word)
            assertTrue(dictionary.matchesStandaloneNode(node, word))
            assertFalse(dictionary.matchesSingleNode(node))
            assertFalse(dictionary.mayMatchFirstNode(node))
            assertFalse(dictionary.mayMatchFirstPair(node, node))
            assertFalse(dictionary.matchesStandaloneNode(node.copy(sPos = 1), word))
            assertFalse(dictionary.matchesStandaloneNode(node, word + "い"))
            assertFalse(dictionary.matchesStandaloneNode(node.copy(yomiUsed = "別の読み"), word))
        }
        assertFalse(dictionary.matchesStandaloneNode(node("ごと"), "こと"))
        assertFalse(dictionary.matchesStandaloneNode(node("はいる"), "ひいる"))
        assertFalse(dictionary.matchesStandaloneNode(node("しろ"), null))
        assertFalse(dictionary.matchesStandaloneNode(node("しろい"), "しろい"))
        assertFalse(dictionary.matchesStandaloneNode(node("城").copy(yomiUsed = "しろ"), "しろ"))
    }

    @Test fun readersRejectInterchangedVersionsAndCorruption() {
        assertThrows(IllegalArgumentException::class.java) { PackedSystemNgramDictionary.read(bytes()) }
        val v4 = bytes().also { it[4] = 4 }
        assertThrows(IllegalArgumentException::class.java) { PackedSystemNgramDictionary.readStandalone(v4) }
        val corrupt = bytes().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { PackedSystemNgramDictionary.readStandalone(corrupt) }
    }

    private fun node(word: String) = Node(l = 1, r = 1, score = 0, f = 0,
        tango = word, len = word.length.toShort(), yomiUsed = word, sPos = 0)
}
