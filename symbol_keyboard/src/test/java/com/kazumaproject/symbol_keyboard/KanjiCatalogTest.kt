package com.kazumaproject.symbol_keyboard

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanjiCatalogTest {
    private val catalog = asset(KanjiCatalog.CATALOG_ASSET).bufferedReader().use { catalog ->
        asset(KanjiCatalog.EXAMPLES_ASSET).bufferedReader().use { examples ->
            KanjiCatalog.parse(catalog, examples)
        }
    }

    @Test
    fun bundledCatalogCoversThePinnedEditionAndUsesRadicalStrokeOrder() {
        assertEquals(13108, catalog.entries.size)
        assertEquals((1..214).toList(), catalog.radicals.map { it.number })
        assertEquals(12356, catalog.entries.values.count {
            it.onReadings.isNotEmpty() || it.kunReadings.isNotEmpty()
        })
        assertEquals(303, catalog.entries.keys.count { it.codePointAt(0) > 0xFFFF })
        assertEquals(39, catalog.entries.getValue("学").radical)
        assertEquals("子", catalog.radicals[38].label)
        assertTrue("𠮟" in catalog.symbols(30))
        assertEquals("U+20B9F", kanjiCodePoint("𠮟"))
        val ordering = compareBy<KanjiEntry>({ !it.isJoyo }, { it.strokes }, { it.symbol.codePointAt(0) })
        for (radical in catalog.radicals) {
            val entries = catalog.symbols(radical.number).map(catalog.entries::getValue)
            assertTrue(entries.isNotEmpty())
            assertEquals(entries.sortedWith(ordering), entries)
        }
    }

    @Test
    fun everyJoyoKanjiHasAnOriginalExampleAndOtherEntriesCanShowReadingsAlone() {
        val examples = catalog.entries.values.filter { it.isJoyo }
        assertEquals(2136, examples.size)
        assertTrue(examples.all { entry ->
            entry.example?.let { it.endsWith("。") && entry.symbol in it } == true
        })
        assertEquals(examples.size, examples.map { it.example }.toSet().size)
        assertNotNull(catalog.entries.getValue("𠮟").example)
        val uncommon = catalog.entries.getValue("龍")
        assertNull(uncommon.example)
        assertTrue(uncommon.onReadings.isNotEmpty())
    }

    private fun asset(path: String) = File("src/main/assets", path)
}
