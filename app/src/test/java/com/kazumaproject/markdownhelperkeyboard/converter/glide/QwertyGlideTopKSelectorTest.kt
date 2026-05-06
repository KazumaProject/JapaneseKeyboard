package com.kazumaproject.markdownhelperkeyboard.converter.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGlideTopKSelectorTest {
    private val selector = QwertyGlideTopKSelector()

    @Test
    fun partialTopKMatchesSortedTakeWithStableTies() {
        val candidates = listOf(
            scored("delta", 4f),
            scored("alpha", 2f),
            scored("bravo", 2f),
            scored("charlie", 3f)
        )

        val expected = candidates
            .sortedWith(QwertyGlideTopKSelector.finalComparator)
            .take(3)
            .map { it.entry.word }

        assertEquals(expected, selector.selectScored(candidates, 3).map { it.entry.word })
    }

    @Test
    fun duplicateWordsKeepBestCost() {
        val result = selector.selectScored(
            listOf(
                scored("same", 9f),
                scored("same", 1f),
                scored("other", 2f)
            ),
            limit = 6
        )

        assertEquals(listOf("same", "other"), result.map { it.entry.word })
        assertEquals(1f, result.first().totalCost, 0.0001f)
    }

    @Test
    fun emptyAndSmallInputsAreSafe() {
        assertTrue(selector.selectScored(emptyList(), 10).isEmpty())
        assertTrue(selector.selectScored(listOf(scored("one", 1f)), 0).isEmpty())
        assertEquals(listOf("one"), selector.selectScored(listOf(scored("one", 1f)), 10).map { it.entry.word })
    }

    private fun scored(word: String, cost: Float): QwertyGlideScoredWord {
        return QwertyGlideScoredWord(
            entry = QwertyGlideDictionaryEntry(word, 100),
            totalCost = cost,
            spatialCost = cost,
            dictionaryCost = 0f
        )
    }
}
