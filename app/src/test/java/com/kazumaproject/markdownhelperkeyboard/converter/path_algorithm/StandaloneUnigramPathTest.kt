package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.PackedSystemNgramDictionary
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class StandaloneUnigramPathTest {
    private val dictionary = PackedSystemNgramDictionary.readStandalone(
        File("src/main/assets/ngram/system_ngram_standalone_unigram.dat").readBytes(),
    )
    private fun node(word: String, start: Int, len: Int, cost: Int, reading: String = word) =
        Node(l = 1, r = 1, score = cost, f = 0, tango = word, len = len.toShort(), yomiUsed = reading, sPos = start)
    private fun graph(single: Boolean = true, suffix: Boolean = false): MutableMap<Int, MutableList<Node>> {
        val length = if (suffix) 3 else 2
        return mutableMapOf(
            0 to mutableListOf(node("BOS", 0, 0, 0)),
            1 to mutableListOf(node("し", 0, 1, 0)),
            2 to mutableListOf(node("ろ", 1, 1, 0), node("城", 0, 2, -10, "しろ")).also {
                if (single) it.add(node("しろ", 0, 2, 100))
            },
            (length + 1) to mutableListOf(node("EOS", length + 1, 0, 0)),
        ).also { if (suffix) it[3] = mutableListOf(node("医", 2, 1, 0)) }
    }

    @Test fun laterSingleNodeDuplicatePromotesExistingCandidateWithoutChangingScore() {
        for (bunsetsu in listOf(false, true)) for (n in listOf(1, 4, 10)) {
            val finder = FindPath(systemNgramDictionaryProvider = { dictionary })
            val candidates = if (bunsetsu) finder.backwardAStarWithBunsetsu(graph(), 2, ShortArray(4), 2, n, originalInput = "しろ").candidates
                else finder.backwardAStar(graph(), 2, ShortArray(4), 2, n, originalInput = "しろ")
            assertEquals("bunsetsu=$bunsetsu n=$n", "しろ", candidates.first().string)
            assertEquals(0, candidates.first().score)
            assertEquals(1, candidates.count { it.string == "しろ" })
        }
    }

    @Test fun neitherMultiNodeLiteralNorStandaloneWordInsideCompoundIsPromoted() {
        for (suffix in listOf(false, true)) for (bunsetsu in listOf(false, true)) {
            val finder = FindPath(systemNgramDictionaryProvider = { dictionary })
            val length = if (suffix) 3 else 2
            val candidates = if (bunsetsu) finder.backwardAStarWithBunsetsu(graph(single = suffix, suffix = suffix), length, ShortArray(4), 2, 4, originalInput = if (suffix) "しろい" else "しろ").candidates
                else finder.backwardAStar(graph(single = suffix, suffix = suffix), length, ShortArray(4), 2, 4, originalInput = if (suffix) "しろい" else "しろ")
            assertEquals(if (suffix) "城医" else "城", candidates.first().string)
        }
    }
}
