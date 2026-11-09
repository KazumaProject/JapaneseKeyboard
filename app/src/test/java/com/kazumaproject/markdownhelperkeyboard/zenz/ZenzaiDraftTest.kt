package com.kazumaproject.markdownhelperkeyboard.zenz

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.ConstrainedDraftSearch
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.DraftConstraint
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ZenzaiDraftTest {
    @Test
    fun prefixCanCrossNodesAndRecoverAPrunedPath() {
        val graph = linkedMapOf(
            1 to mutableListOf(word("西", 0), word("東", 0, cost = 100)),
            2 to mutableListOf(word("京駅", 1)),
            3 to mutableListOf(eos(3)),
        )
        val costs = ConnectionMatrix.fromShortArray(shortArrayOf(0))
        val search = ConstrainedDraftSearch(graph, 2, costs)
        val original = FindPath().backwardAStar(
            graph = graph.mapValuesTo(LinkedHashMap()) { (_, nodes) -> nodes.toMutableList() },
            length = 2,
            connectionMatrix = costs,
            n = 1,
            beamWidth = 1,
        )

        assertEquals(listOf("西京駅"), original.map { it.string })
        assertEquals("東京駅", search.find(DraftConstraint("東京", exact = false)) {})
        assertNull(search.find(DraftConstraint("東京", exact = true)) {})
    }

    @Test
    fun searchKeepsDistinctPrefixPositionsAtTheSameNode() {
        val graph = mapOf(
            1 to listOf(word("あ", 0), word("ああ", 0, cost = 100)),
            2 to listOf(word("あ", 1)),
            3 to listOf(word("い", 2)),
            4 to listOf(eos(4)),
        )
        val search = ConstrainedDraftSearch(graph, 3, ConnectionMatrix.fromShortArray(shortArrayOf(0)))

        assertEquals("あああい", search.find(DraftConstraint("あああ", exact = false)) {})
        assertNull(search.find(DraftConstraint("あああ", exact = true)) {})
    }

    @Test
    fun exactSearchUsesConnectionCostsAndKeepsTheDictionarySurface() {
        val graph = mapOf(
            1 to listOf(word("A", 0, right = 1), word("A ", 0, cost = 2)),
            2 to listOf(word(" B", 1, cost = 1, left = 1), word("C", 1)),
            3 to listOf(eos(3)),
        )
        val costs = ConnectionMatrix.fromShortArray(shortArrayOf(0, 0, 0, 100))
        val search = ConstrainedDraftSearch(graph, 2, costs)

        assertEquals("A C", search.find(DraftConstraint("A\u3000", exact = false)) {})
        assertEquals("A B", search.find(DraftConstraint("A\u3000B", exact = true)) {})
        assertNull(search.find(DraftConstraint("A", exact = true)) {})
    }

    @Test
    fun usesConstrainedDraftEvenWhenEvaluationBudgetIsSpent() = runBlocking {
        val result = reviewZenzaiDraft(
            initialDraft = "西京駅",
            evaluationLimit = 1,
            evaluate = { "FIX:東京" },
            search = { constraint, _ ->
                assertEquals(DraftConstraint("東京", exact = false), constraint)
                "東京駅"
            },
        )

        assertEquals(DraftReviewResult("東京駅", 1, DraftReviewStop.EVALUATION_LIMIT), result)
    }

    @Test
    fun acceptsTheRevisedDraftOnTheNextReview() = runBlocking {
        val reviewed = mutableListOf<String>()
        val result = reviewZenzaiDraft("西京駅", 3, { candidate ->
            reviewed.add(candidate)
            if (candidate == "西京駅") "FIX:東京" else "PASS:-0.5"
        }) { _, _ -> "東京駅" }

        assertEquals(listOf("西京駅", "東京駅"), reviewed)
        assertEquals(DraftReviewResult("東京駅", 2, DraftReviewStop.PASS), result)
    }

    @Test
    fun wholeResultNeedsAnExactDictionaryPath() = runBlocking {
        val result = reviewZenzaiDraft(
            initialDraft = "東京駅",
            evaluationLimit = 3,
            evaluate = { "WHOLE:東京" },
            search = { constraint, _ ->
                assertEquals(DraftConstraint("東京", exact = true), constraint)
                "東京"
            },
        )
        assertEquals(DraftReviewResult("東京", 1, DraftReviewStop.WHOLE), result)

        val fallback = reviewZenzaiDraft("東京駅", 3, { "WHOLE:東京" }) { _, _ -> null }
        assertEquals(DraftReviewResult("東京駅", 1, DraftReviewStop.NO_PATH), fallback)
    }

    @Test
    fun repeatedConstraintAndErrorKeepTheLatestDraft() = runBlocking {
        val repeated = reviewZenzaiDraft("西京駅", 3, { "FIX:東京" }) { _, _ -> "東京駅" }
        assertEquals(DraftReviewResult("東京駅", 2, DraftReviewStop.REPEATED_CONSTRAINT), repeated)

        val error = reviewZenzaiDraft("西京駅", 3, {
            if (it == "西京駅") "FIX:東京" else "ERROR"
        }) { _, _ -> "東京駅" }
        assertEquals(DraftReviewResult("東京駅", 2, DraftReviewStop.EVALUATION_ERROR), error)
    }

    @Test
    fun cancellationEscapesTheReviewAndTheLatticeSearch() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                reviewZenzaiDraft("東京", 1, { throw CancellationException() }) { _, _ ->
                    error("A cancelled request cannot search again")
                }
            }
        }

        val search = ConstrainedDraftSearch(emptyMap(), 1, ConnectionMatrix.fromShortArray(shortArrayOf(0)))
        assertThrows(CancellationException::class.java) {
            search.find(DraftConstraint("東", exact = false)) { throw CancellationException() }
        }
    }

    private fun word(surface: String, start: Int, cost: Int = 0, left: Short = 0, right: Short = 0) =
        Node(l = left, r = right, score = cost, f = cost, tango = surface, len = 1, yomiUsed = "あ", sPos = start)

    private fun eos(position: Int) = Node(
        l = 0, r = 0, score = 0, f = 0, tango = "EOS", len = 0,
        yomiUsed = "EOS", sPos = position, mozcNodeType = MozcNodeType.EOS,
    )
}
