package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenterData
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.PackedSystemNgramDictionary
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class WholeInputCandidateScoreTest {
    private val costs = shortArrayOf(0, 13, 0, 0, 0, 0, 17, 0, 0)
    private val matrix = ConnectionMatrix.fromShortArray(costs, 3)
    private val segmenter = MozcSegmenter(MozcSegmenterData(
        lNumElements = 3, rNumElements = 3, lTable = intArrayOf(0, 1, 2),
        rTable = intArrayOf(0, 1, 2), bitArrayData = byteArrayOf(0),
        boundaryData = intArrayOf(0, 0, 7, 0, 0, 11),
    ))

    @Test fun directScoreEqualsActualSingleWordPathAcrossPenaltyAndBunsetsuModes() {
        for (penalties in listOf(false, true)) for (bunsetsu in listOf(false, true)) {
            for ((input, surface) in listOf("よみ" to "単語", "にて" to "にて", "にて" to "二手")) {
                val finder = FindPath(mozcSegmenterProvider = { if (penalties) segmenter else null })
                val raw = Candidate(surface, 1, input.length.toUByte(), 100, input, 1, 2)
                val node = Node(1, 2, 100, 0, tango = surface, len = input.length.toShort(), yomiUsed = input, sPos = 0)
                val graph = mutableMapOf(
                    0 to mutableListOf(Node(0, 0, 0, 0, tango = "BOS", len = 0, yomiUsed = "BOS", sPos = 0, mozcNodeType = MozcNodeType.BOS)),
                    input.length to mutableListOf(node),
                    input.length + 1 to mutableListOf(Node(0, 0, 0, 0, tango = "EOS", len = 0, yomiUsed = "EOS", sPos = input.length + 1, mozcNodeType = MozcNodeType.EOS)),
                )
                val path = if (bunsetsu) finder.backwardAStarWithBunsetsu(graph, input.length, costs, 3, 1, originalInput = input).candidates.single()
                    else finder.backwardAStar(graph, input.length, costs, 3, 1, originalInput = input).single()
                val direct = finder.scoreWholeInputCandidate(input, raw, matrix, bunsetsu).candidate
                assertEquals("penalties=$penalties bunsetsu=$bunsetsu input=$input surface=$surface", path.score, direct.score)
                assertEquals(path.leftId, direct.leftId)
                assertEquals(path.rightId, direct.rightId)
                assertTrue(direct.score > raw.score)
            }
        }
    }

    @Test fun retainsStandaloneUnigramPriorityWithoutChangingItsNumericCost() {
        val dictionary = PackedSystemNgramDictionary.readStandalone(
            File("src/main/assets/ngram/system_ngram_standalone_unigram.dat").readBytes())
        val finder = FindPath(systemNgramDictionaryProvider = { dictionary })
        for (bunsetsu in listOf(false, true)) {
            val exact = finder.scoreWholeInputCandidate("しろ", Candidate("しろ", 1, 2u, 100, "しろ", 1, 2), matrix, bunsetsu)
            val other = finder.scoreWholeInputCandidate("しろ", Candidate("城", 1, 2u, 100, "しろ", 1, 2), matrix, bunsetsu)
            assertTrue(exact.candidate.standaloneUnigramMatched)
            assertTrue(exact.systemNgramMatched)
            assertFalse(other.candidate.standaloneUnigramMatched)
            assertEquals(other.candidate.score, exact.candidate.score)
        }
    }
}
