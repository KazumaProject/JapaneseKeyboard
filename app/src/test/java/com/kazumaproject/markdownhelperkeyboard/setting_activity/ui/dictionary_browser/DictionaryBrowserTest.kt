package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_browser

import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId as EnglishTrie

class DictionaryBrowserTest {
    private val version = DictionaryBrowserVersion(0, true, false)

    @Test fun catalogContainsAll39WordDictionariesWithoutRetiredCategoriesOrCommonTables() {
        val categories = DictionaryBrowserCatalog.all.map { it.category }
        assertEquals(39, categories.size)
        assertEquals(DictionaryFileSpecs.tripleCategories().toSet(), categories.toSet())
        assertEquals(categories.size, categories.distinct().size)
        assertEquals(DictionaryCategory.SYSTEM, categories.first())
    }

    @Test fun searchNormalizesWidthCaseAndKanaWithoutChangingWhitespaceOrSymbols() {
        assertEquals(normalizeDictionarySearch("ｶﾞｯﾂＡＰＩ"), normalizeDictionarySearch("がっつapi"))
        assertEquals(" api%_\\ ", normalizeDictionarySearch(" ＡＰＩ%_\\ "))
        assertEquals("😀", normalizeDictionarySearch("😀"))
    }

    @Test fun pagesHaveNoMissingOrRepeatedRowsIncludingFinalPartialPage() = runBlocking {
        val expected = (0 until 251).map { DictionaryBrowserRow("よみ$it", "単語$it") }
        val cursor = DictionaryBrowserCursor(version, expected.iterator())
        val first = cursor.nextPage()
        val second = cursor.nextPage()
        val last = cursor.nextPage()
        assertEquals(listOf(100, 100, 51), listOf(first, second, last).map { it.rows.size })
        assertTrue(first.hasMore)
        assertTrue(second.hasMore)
        assertFalse(last.hasMore)
        assertEquals(expected, first.rows + second.rows + last.rows)
        assertTrue(cursor.nextPage().rows.isEmpty())
    }

    @Test fun aFullPageDoesNotScanAllRemainingNonMatchesBeforeDisplayingResults() = runBlocking {
        var visited = 0
        val rows = sequence {
            repeat(100) {
                visited++
                yield(DictionaryBrowserRow("$it", "match$it"))
            }
            repeat(100000) {
                visited++
                yield(DictionaryBrowserRow("other$it", "other$it"))
            }
        }
        val page = DictionaryBrowserCursor(version, rows.iterator(), "match").nextPage()
        assertEquals(100, page.rows.size)
        assertTrue(page.hasMore)
        assertTrue("Only one unfiltered row may be prefetched", visited <= 101)
    }

    @Test fun partialMatchesApplyToReadingAndWordAndTreatWildcardsLiterally() = runBlocking {
        val rows = listOf(
            DictionaryBrowserRow("えーぴーあい", "OpenAPI"),
            DictionaryBrowserRow("api_gateway", "ゲートウェイ"),
            DictionaryBrowserRow("きごう", "a%_\\b"),
        )
        assertEquals(rows.take(2), DictionaryBrowserCursor(version, rows.iterator(), "ＡＰＩ").nextPage().rows)
        assertEquals(rows.take(1), DictionaryBrowserCursor(version, rows.iterator(), "ピｰ").nextPage().rows)
        assertEquals(rows.takeLast(1), DictionaryBrowserCursor(version, rows.iterator(), "%_\\").nextPage().rows)
        assertFalse(DictionaryBrowserCursor(version, rows.iterator(), "absent").nextPage().hasMore)
    }

    @Test fun cancelledScansStopBeforeReturningAStalePage() = runBlocking {
        val job = Job()
        val rows = sequence {
            repeat(1000) {
                if (it == 10) job.cancel()
                yield(DictionaryBrowserRow("$it", "$it"))
            }
        }
        try {
            withContext(job) { DictionaryBrowserCursor(version, rows.iterator(), "absent").nextPage() }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }

    @Test fun lazyCursorsMatchExistingTraversalAndTermIdsForBothDictionaryFormats() {
        val tree = PrefixTreeWithTermId().apply {
            listOf("a", "ab", "abc", "a😀", "か", "かき", "が", "字").forEach(::insert)
            repeat(1000) { insert("word${it.toString().padStart(4, '0')}") }
        }
        val converted = ConverterWithTermId().convert(tree.root).apply { convertListToBitSet() }
        val common = LOUDSWithTermId(converted.LBS, converted.getAllLabels(), converted.isLeaf, converted.termIds.toIntArray())
        val lbs = SuccinctBitVector(common.LBS)
        val leaves = SuccinctBitVector(common.isLeaf)
        val expected = common.predictiveSearch("", lbs).map { it to common.getTermId(common.getNodeIndex(it, lbs), leaves) }
        val cursor = common.termIterator(lbs, leaves)
        assertTrue(cursor.hasNext())
        assertTrue(cursor.hasNext())
        assertEquals(expected, cursor.asSequence().toList())
        assertFalse(cursor.hasNext())
        val english = EnglishTrie(common.LBS, common.getAllLabels(), common.isLeaf, common.getAllTermIds())
        assertEquals(expected, english.termIterator(lbs, leaves).asSequence().toList())
    }
}
