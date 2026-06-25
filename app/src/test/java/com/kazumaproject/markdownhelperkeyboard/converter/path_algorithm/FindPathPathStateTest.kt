package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.JapaneseCandidateDedupeAction
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.JapaneseCandidateDedupeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindPathPathStateTest {

    @Test
    fun backwardAStarWithBunsetsu_doesNotMutateNodePathState() {
        val graph = duplicateValueGraph()
        val trackedNodes = graph.values.flatten() + BOS
        val initialG = trackedNodes.map { it to it.g }

        FindPath().backwardAStarWithBunsetsu(
            graph = graph,
            length = 2,
            connectionIds = zeroConnectionMatrix(),
            connectionMatrixSize = MATRIX_SIZE,
            n = 4,
            input = "ab",
            options = JapaneseBunsetsuOptions(
                candidateDedupeMode = JapaneseCandidateDedupeMode.IDENTITY,
            ),
        )

        trackedNodes.forEach { node ->
            val expectedG = initialG.first { it.first === node }.second
            assertEquals("Node.g changed for ${node.tango}", expectedG, node.g)
            assertNull("Node.next changed for ${node.tango}", node.next)
        }
    }

    @Test
    fun backwardAStarWithBunsetsu_identityModeRetainsSameValueDifferentSplitPattern() {
        val result = FindPath().backwardAStarWithBunsetsu(
            graph = duplicateValueGraph(),
            length = 2,
            connectionIds = zeroConnectionMatrix(),
            connectionMatrixSize = MATRIX_SIZE,
            n = 4,
            input = "ab",
            options = JapaneseBunsetsuOptions(
                candidateDedupeMode = JapaneseCandidateDedupeMode.IDENTITY,
            ),
        )

        val sameValueCandidates = result.candidates.filter { it.string == "AB" }
        assertEquals(2, sameValueCandidates.size)
        assertEquals(listOf(emptyList<Int>(), listOf(1)), sameValueCandidates.map {
            it.japaneseCandidateIdentity?.splitPattern.orEmpty()
        })
        assertTrue(result.dedupeTraces.any {
            it.action == JapaneseCandidateDedupeAction.WOULD_DROP_BY_VALUE_ONLY &&
                it.identity.value == "AB"
        })
    }

    @Test
    fun backwardAStarWithBunsetsu_legacyValueModeDropsSameValueDifferentSplitPattern() {
        val result = FindPath().backwardAStarWithBunsetsu(
            graph = duplicateValueGraph(),
            length = 2,
            connectionIds = zeroConnectionMatrix(),
            connectionMatrixSize = MATRIX_SIZE,
            n = 4,
            input = "ab",
            options = JapaneseBunsetsuOptions(
                candidateDedupeMode = JapaneseCandidateDedupeMode.LEGACY_VALUE,
            ),
        )

        assertEquals(1, result.candidates.count { it.string == "AB" })
        assertTrue(result.dedupeTraces.any {
            it.action == JapaneseCandidateDedupeAction.DROPPED_VALUE_DUPLICATE &&
                it.identity.value == "AB"
        })
    }

    @Test
    fun backwardAStarWithBunsetsu_finalUiCanStillDeduplicateByDisplayedValue() {
        val result = FindPath().backwardAStarWithBunsetsu(
            graph = duplicateValueGraph(),
            length = 2,
            connectionIds = zeroConnectionMatrix(),
            connectionMatrixSize = MATRIX_SIZE,
            n = 4,
            input = "ab",
            options = JapaneseBunsetsuOptions(
                candidateDedupeMode = JapaneseCandidateDedupeMode.IDENTITY,
            ),
        )

        assertEquals(1, result.candidates.distinctBy { it.string }.count { it.string == "AB" })
    }

    @Test
    fun backwardAStarWithBunsetsu_isDeterministicAcrossRepeatedRuns() {
        val expected = pathSignature()

        repeat(10) {
            assertEquals(expected, pathSignature())
        }
    }

    private fun pathSignature(): List<Pair<String, List<Int>>> {
        val result = FindPath().backwardAStarWithBunsetsu(
            graph = duplicateValueGraph(),
            length = 2,
            connectionIds = zeroConnectionMatrix(),
            connectionMatrixSize = MATRIX_SIZE,
            n = 4,
            input = "ab",
            options = JapaneseBunsetsuOptions(
                candidateDedupeMode = JapaneseCandidateDedupeMode.IDENTITY,
            ),
        )
        return result.candidates.map {
            it.string to it.japaneseCandidateIdentity?.splitPattern.orEmpty()
        }
    }

    private fun duplicateValueGraph(): MutableMap<Int, MutableList<Node>> {
        val wordA = createNode(tango = "A", yomi = "a", l = 1, r = 1, len = 1, sPos = 0)
        val wordB = createNode(tango = "B", yomi = "b", l = 12, r = 12, len = 1, sPos = 1)
        val wordAB = createNode(tango = "AB", yomi = "ab", l = 1, r = 1, len = 2, sPos = 0)
        val eos = createNode(tango = "EOS", yomi = "EOS", l = 0, r = 0, len = 0, sPos = 3)

        return linkedMapOf(
            0 to mutableListOf(BOS),
            1 to mutableListOf(wordA),
            2 to mutableListOf(wordAB, wordB),
            3 to mutableListOf(eos),
        )
    }

    private fun createNode(
        tango: String,
        yomi: String,
        l: Short,
        r: Short,
        len: Short,
        sPos: Int,
    ): Node = Node(
        l = l,
        r = r,
        score = 0,
        f = 0,
        g = 7,
        tango = tango,
        len = len,
        yomiUsed = yomi,
        sPos = sPos,
        prev = null,
        next = null,
    )

    private fun zeroConnectionMatrix(): ShortArray = ShortArray(MATRIX_SIZE * MATRIX_SIZE)

    private companion object {
        const val MATRIX_SIZE = 32
    }
}
