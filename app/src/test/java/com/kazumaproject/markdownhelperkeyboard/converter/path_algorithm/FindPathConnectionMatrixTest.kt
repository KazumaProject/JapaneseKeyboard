package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.dictionaryOriginsForType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FindPathConnectionMatrixTest {

    @Test
    fun backwardAStar_stopsWhenCancellationIsRequested() {
        assertThrows(TestCancellation::class.java) {
            FindPath().backwardAStar(
                graph = singleWordGraph(),
                length = 1,
                connectionIds = ShortArray(4),
                connectionMatrixSize = 2,
                n = 1,
                cancellationCheck = { throw TestCancellation() },
            )
        }
    }

    @Test
    fun backwardAStarWithBunsetsu_stopsWhenCancellationIsRequested() {
        assertThrows(TestCancellation::class.java) {
            FindPath().backwardAStarWithBunsetsu(
                graph = singleWordGraph(),
                length = 1,
                connectionIds = ShortArray(4),
                connectionMatrixSize = 2,
                n = 1,
                cancellationCheck = { throw TestCancellation() },
            )
        }
    }

    @Test
    fun backwardAStar_usesPassed2672ConnectionMatrixWidth() {
        val connectionIds = ShortArray(2672 * 2672)
        connectionIds[1 * 2672 + 0] = (-123).toShort()
        connectionIds[1 * 2670 + 0] = 456.toShort()
        connectionIds[0 * 2672 + 1] = 1000.toShort()
        connectionIds[2671 * 2672 + 0] = 456.toShort()

        val candidates = FindPath().backwardAStar(
            graph = singleWordGraph(leftId = 2671.toShort(), rightId = 1.toShort()),
            length = 1,
            connectionIds = connectionIds,
            connectionMatrixSize = 2672,
            n = 1,
        )

        assertEquals(-123, candidates.single().score)
    }

    @Test
    fun backwardAStar_usesBuiltIn2670ConnectionMatrixWidth() {
        val matrixSize = 2670
        val connectionIds = ShortArray(matrixSize * matrixSize)
        connectionIds[1 * matrixSize + 0] = (-70).toShort()
        connectionIds[0 * matrixSize + 1] = 500.toShort()
        connectionIds[2668 * matrixSize + 0] = 999.toShort()

        val candidates = FindPath().backwardAStar(
            graph = singleWordGraph(leftId = 2668.toShort(), rightId = 1.toShort()),
            length = 1,
            connectionIds = connectionIds,
            connectionMatrixSize = matrixSize,
            n = 1,
        )

        assertEquals(-70, candidates.single().score)
    }

    @Test
    fun backwardAStar_preservesDictionaryCandidateSource() {
        val expectedTypes = mapOf(
            CandidateSource.SYSTEM to 1.toByte(),
            CandidateSource.USER_DICTIONARY to CANDIDATE_TYPE_USER_DICTIONARY,
            CandidateSource.LEARNED_DICTIONARY to CANDIDATE_TYPE_LEARNED_DICTIONARY,
        )

        expectedTypes.forEach { (source, expectedType) ->
            val candidate = FindPath().backwardAStar(
                graph = singleWordGraph(candidateSource = source),
                length = 1,
                connectionIds = ShortArray(4),
                connectionMatrixSize = 2,
                n = 1,
            ).single()

            assertEquals(expectedType, candidate.type)
            assertEquals(dictionaryOriginsForType(expectedType), candidate.dictionaryOrigins)
        }
    }

    @Test
    fun backwardAStarWithBunsetsu_preservesLearnedDictionarySource() {
        val result = FindPath().backwardAStarWithBunsetsu(
            graph = singleWordGraph(
                candidateSource = CandidateSource.LEARNED_DICTIONARY
            ),
            length = 1,
            connectionIds = ShortArray(4),
            connectionMatrixSize = 2,
            n = 1,
        )

        assertEquals(CANDIDATE_TYPE_LEARNED_DICTIONARY, result.candidates.single().type)
        assertEquals(dictionaryOriginsForType(CANDIDATE_TYPE_LEARNED_DICTIONARY), result.candidates.single().dictionaryOrigins)
    }

    @Test
    fun backwardSearch_breaksCostTiesConsistentlyAtCandidateLimit() {
        val words = listOf("郷", "京", "強", "教", "経")
        for (order in listOf(words, words.reversed())) {
            fun graph() = singleWordGraph().apply {
                this[1] = (order.map { word ->
                    createNode(tango = word, l = 1, r = 1, len = 1, sPos = 0)
                        .copy(yomiUsed = "きょう")
                } + createNode(tango = "今日", l = 1, r = 1, len = 1, sPos = 0)
                    .copy(yomiUsed = "きょう", score = -100, adjustedScore = -100)).toMutableList()
            }

            val findPath = FindPath()
            val costs = ShortArray(4)
            val expected = listOf("今日" to -100, "京" to 0, "強" to 0)
            val plain = findPath.backwardAStar(
                graph = graph(), length = 1, connectionIds = costs, connectionMatrixSize = 2, n = 3,
            )
            val bunsetsu = findPath.backwardAStarWithBunsetsu(
                graph = graph(), length = 1, connectionIds = costs, connectionMatrixSize = 2, n = 3,
            )
            assertEquals(expected, plain.map { it.string to it.score })
            assertEquals(expected, bunsetsu.candidates.map { it.string to it.score })
        }
    }

    private fun singleWordGraph(
        leftId: Short = 1,
        rightId: Short = 1,
        candidateSource: CandidateSource = CandidateSource.SYSTEM,
    ): MutableMap<Int, MutableList<Node>> =
        mutableMapOf(
            0 to mutableListOf(createNode(tango = "BOS", l = 0, r = 0, len = 0, sPos = 0)),
            1 to mutableListOf(
                createNode(
                    tango = "語",
                    l = leftId,
                    r = rightId,
                    len = 1,
                    sPos = 0,
                    candidateSource = candidateSource,
                )
            ),
            2 to mutableListOf(createNode(tango = "EOS", l = 0, r = 0, len = 0, sPos = 2)),
        )

    private fun createNode(
        tango: String,
        l: Short,
        r: Short,
        len: Short,
        sPos: Int,
        candidateSource: CandidateSource = CandidateSource.SYSTEM,
    ): Node =
        Node(
            l = l,
            r = r,
            score = 0,
            f = 0,
            g = 0,
            tango = tango,
            len = len,
            yomiUsed = tango,
            sPos = sPos,
            prev = null,
            next = null,
            candidateSource = candidateSource,
        )

    private class TestCancellation : RuntimeException()
}
