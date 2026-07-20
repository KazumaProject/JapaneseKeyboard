package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FindPathSessionScratchTest {

    @Test
    fun characterPathsAreCanonicalAcrossTokenBoundaries() {
        val arena = FindPath.CharPathArena()
        val oneToken = arena.prepend("変換候補😀", 0)
        val suffix = arena.prepend("候補😀", 0)
        val splitTokens = arena.prepend("変換", suffix)

        assertEquals(oneToken, splitTokens)
        assertEquals("変換候補😀", arena.asString(splitTokens))

        arena.reset()
        assertEquals("再利用", arena.asString(arena.prepend("再利用", 0)))
    }

    @Test
    fun stateTableKeepsOnlyTheLowestCostForAnExactState() {
        val table = FindPath.StateCostTable(initialCapacity = 16)
        assertTrue(table.putIfLower(1, 2, 3, 4, 5, 0, 100))
        assertFalse(table.putIfLower(1, 2, 3, 4, 5, 0, 101))
        assertTrue(table.putIfLower(1, 2, 3, 4, 5, 0, 99))
        assertTrue("a different canonical output remains a candidate", table.putIfLower(1, 2, 3, 4, 6, 0, 101))

        table.clear()
        assertTrue(table.putIfLower(1, 2, 3, 4, 5, 0, 200))
    }

    @Test
    fun kBestStateTableRetainsOnlyTheExactLowestDistinctSuffixes() {
        val table = FindPath.KBestStateCostTable(k = 2, initialCapacity = 16)
        assertTrue(table.putIfAmongLowest(1, 2, -1, -1, pathId = 10, cost = 100))
        assertTrue(table.putIfAmongLowest(1, 2, -1, -1, pathId = 11, cost = 90))
        assertFalse(table.putIfAmongLowest(1, 2, -1, -1, pathId = 12, cost = 110))
        assertTrue(table.putIfAmongLowest(1, 2, -1, -1, pathId = 12, cost = 80))

        assertFalse(table.contains(1, 2, -1, -1, pathId = 10, cost = 100))
        assertTrue(table.contains(1, 2, -1, -1, pathId = 11, cost = 90))
        assertTrue(table.contains(1, 2, -1, -1, pathId = 12, cost = 80))

        repeat(100) { context ->
            assertTrue(
                table.putIfAmongLowest(
                    nodeIdentity = context + 100,
                    nextIdentity1 = context,
                    nextIdentity2 = -1,
                    nextIdentity3 = -1,
                    pathId = context,
                    cost = context,
                ),
            )
        }
        assertTrue(table.contains(199, 99, -1, -1, pathId = 99, cost = 99))
    }

    @Test
    fun characterPathTracksDigitPenaltyStateWithoutBuildingAString() {
        val arena = FindPath.CharPathArena()
        val text = arena.prepend("日本5", 0)
        val noDigit = arena.prepend("日本語", 0)

        assertTrue(arena.containsDigit(text))
        assertFalse(arena.containsDigit(noDigit))
    }

    @Test
    fun reusablePriorityQueuePreservesSearchOrderingAfterClear() {
        val queue = FindPath.PathPriorityQueue(initialCapacity = 1)
        val expensive = element("expensive", 30)
        val cheapest = element("cheapest", 10)
        val middle = element("middle", 20)
        queue.add(expensive)
        queue.add(cheapest)
        queue.add(middle)

        assertSame(cheapest, queue.poll())
        assertSame(middle, queue.poll())
        assertSame(expensive, queue.poll())
        assertNull(queue.poll())

        queue.add(expensive)
        queue.clear()
        assertFalse(queue.isNotEmpty())
        queue.add(cheapest)
        assertSame(cheapest, queue.poll())
    }

    private fun element(word: String, priority: Int): FindPath.PathQueueElement =
        FindPath.PathQueueElement().set(
            node = Node(
                l = 0,
                r = 0,
                score = 0,
                f = 0,
                tango = word,
                len = 1,
                yomiUsed = word,
                sPos = 0,
            ),
            priorityCost = priority,
            backwardCost = priority,
            next = null,
            outputPathId = 0,
            sourceMask = 0,
        )
}
