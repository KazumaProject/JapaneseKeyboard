package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_browser

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DictionaryBrowserRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private class Fixture(val context: Context) : AutoCloseable {
        private val id = "browser-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(id, Context.MODE_PRIVATE)
        val defaults = context.getSharedPreferences("$id-defaults", Context.MODE_PRIVATE)
        private val directory = File(context.cacheDir, id)
        val store = DictionaryOverrideStore(directory, prefs, defaults)
        private val resolver = DictionarySourceResolver(context, store)
        val reader = DictionaryBinaryReader(resolver, store)
        fun repository() = DictionaryBrowserRepository(reader, resolver)
        override fun close() {
            prefs.edit().clear().commit()
            defaults.edit().clear().commit()
            directory.deleteRecursively()
        }
    }

    @Test fun all39DictionariesOpenAndFrozenCategorySamplesAreSearchableWhileDisabled() = runBlocking {
        Fixture(context).use { fixture ->
            val repo = fixture.repository()
            val expected = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
                .open("classified-expected-records.json").bufferedReader().use { it.readText() })
            ClassifiedDictionaries.all.forEach { fixture.store.setOptionalBundledEnabled(it.category, false) }
            val before = fixture.prefs.all.toMap()
            for (spec in DictionaryBrowserCatalog.all) {
                val start = SystemClock.elapsedRealtime()
                val page = repo.open(spec.category, "").nextPage()
                assertFalse(spec.category.name, page.rows.isEmpty())
                assertTrue(page.rows.size <= 100)
                assertEquals(page.rows.size, page.rows.distinct().size)
                println("BROWSER_FIRST_PAGE ${spec.category} ms=${SystemClock.elapsedRealtime() - start} rows=${page.rows.size}")
                ClassifiedDictionaries.find(spec.category)?.let { classified ->
                    val sample = expected.getJSONObject(classified.id).getJSONObject("sample")
                    val reading = sample.getString("reading")
                    val surface = sample.getString("surface")
                    assertTrue(repo.open(spec.category, reading).nextPage().rows.contains(DictionaryBrowserRow(reading, surface)))
                    assertFalse(repo.version(spec.category).enabled)
                }
            }
            val repaired = repo.open(DictionaryCategory.CLASSIFIED_PLACE, "アトコシヤラカ").nextPage().rows
            assertTrue(repaired.contains(DictionaryBrowserRow("あとこしゃらか", "アトコシヤラカ")))
            val englishReading = repo.open(DictionaryCategory.ENGLISH_READING, "ＦＥＴＣＨ").nextPage().rows
            assertTrue(englishReading.contains(DictionaryBrowserRow("ふぇっち", "fetch")))
            assertEquals(before, fixture.prefs.all)
        }
    }

    @Test fun englishCursorPreservesStoredCaseAndMatchesExistingDecoder() = runBlocking {
        Fixture(context).use { fixture ->
            val reader = fixture.reader
            val trie = reader.loadEnglishReading(DictionaryFileKey.ENGLISH_READING)
            val words = reader.loadEnglishWord(DictionaryFileKey.ENGLISH_WORD)
            val tokens = reader.loadEnglishToken(DictionaryFileKey.ENGLISH_TOKEN)
            val lbs = reader.loadEnglishReadingLbsIndex(trie)
            val leaves = reader.loadEnglishReadingLeafIndex(trie)
            val wordIndex = reader.loadEnglishWordLbsIndex(words)
            val tokenIndex = reader.loadEnglishTokenIndex(tokens)
            val expected = linkedSetOf<DictionaryBrowserRow>()
            for (reading in trie.predictiveSearch("", lbs)) {
                val term = trie.getTermId(trie.getNodeIndex(reading, lbs), leaves)
                for (token in tokens.getListDictionaryByYomiTermId(term, tokenIndex)) {
                    expected.add(DictionaryBrowserRow(reading, if (token.nodeId == -1) reading else words.getLetter(token.nodeId, wordIndex)))
                }
            }
            val cursor = fixture.repository().open(DictionaryCategory.ENGLISH, "")
            val actual = mutableListOf<DictionaryBrowserRow>()
            do {
                val page = cursor.nextPage()
                actual.addAll(page.rows)
            } while (page.hasMore)
            assertEquals(expected.toList(), actual)
            assertTrue("Stored uppercase spelling must remain intact", actual.any { it.word.any(Char::isUpperCase) })
            println("BROWSER_ENGLISH_PAIRS ${actual.size}")
        }
    }

    @Test fun everyClassifiedBrowserPairMatchesTheRuntimeDecoderWithoutMissingOrDuplicateRows() = runBlocking {
        Fixture(context).use { fixture ->
            val repo = fixture.repository()
            val reader = fixture.reader
            val frozen = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
                .open("classified-expected-records.json").bufferedReader().use { it.readText() })
            var total = 0
            for (spec in ClassifiedDictionaries.all) {
                val files = DictionaryFileSpecs.forCategory(spec.category)
                val yomiKey = files.single { it.role == DictionaryFileRole.YOMI }.key
                val tangoKey = files.single { it.role == DictionaryFileRole.TANGO }.key
                val tokenKey = files.single { it.role == DictionaryFileRole.TOKEN }.key
                val yomi = reader.loadLoudsWithTermId(yomiKey)
                val tango = reader.loadLouds(tangoKey)
                val token = reader.loadTokenArray(tokenKey)
                val lbs = reader.loadYomiLbsIndex(yomiKey, yomi)
                val leaves = reader.loadYomiLeafIndex(yomiKey, yomi)
                val tokens = reader.loadTokenIndex(tokenKey, token)
                val data = com.kazumaproject.markdownhelperkeyboard.converter.engine.TripleDictionaryData(
                    tango, yomi, token, lbs, leaves, tokens, reader.loadTangoLbsIndex(tangoKey, tango),
                    reader.loadClassifiedSurfaceOverrides(spec.category),
                )
                val expected = java.security.MessageDigest.getInstance("SHA-256")
                var expectedPairs = 0
                var rawRecords = 0
                // Existing callback traversal is independent of the new resumable cursor.
                yomi.forEachPredictiveMatch("", Int.MAX_VALUE, lbs, leaves) { reading, term ->
                    val words = linkedSetOf<String>()
                    token.forEachDictionaryByYomiTermId(term, tokens) { pos, cost, node ->
                        words.add(data.surfaceFor(node, reading, pos.toInt(), cost))
                        rawRecords++
                    }
                    for (word in words) {
                        expected.update("$reading\t$word\n".toByteArray(Charsets.UTF_8))
                        expectedPairs++
                    }
                }
                assertEquals(spec.id, frozen.getJSONObject(spec.id).getInt("count"), rawRecords)
                val actual = java.security.MessageDigest.getInstance("SHA-256")
                var actualPairs = 0
                val cursor = repo.open(spec.category, "")
                do {
                    val page = cursor.nextPage()
                    for (row in page.rows) {
                        actual.update("${row.reading}\t${row.word}\n".toByteArray(Charsets.UTF_8))
                        actualPairs++
                    }
                } while (page.hasMore)
                assertEquals(spec.id, expectedPairs, actualPairs)
                assertArrayEquals(spec.id, expected.digest(), actual.digest())
                total += rawRecords
                println("BROWSER_CLASSIFIED_FULL ${spec.id} records=$rawRecords uniquePairs=$actualPairs")
            }
            assertEquals(861053, total)
        }
    }

    @Test fun overridesAreReflectedAndBrokenOverridesDoNotChangeMetadataOrSilentlyFallback() = runBlocking {
        Fixture(context).use { fixture ->
            val category = DictionaryCategory.CLASSIFIED_KAMPO
            val specs = DictionaryFileSpecs.forCategory(category)
            for (spec in specs) {
                context.assets.open(spec.bundledAssetPath).use {
                    assertTrue(fixture.store.saveOverrideFromInputStream(spec.key, it).isValid)
                }
            }
            fixture.store.setExternalEnabledForCategory(category, true)
            val repo = fixture.repository()
            assertTrue(repo.open(category, "").version.external)
            assertFalse(repo.open(category, "").nextPage().rows.isEmpty())
            val old = repo.version(category)
            fixture.store.setExternalEnabledForCategory(category, false)
            assertNotEquals(old, repo.version(category))
            assertFalse(repo.open(category, "").version.external)
            fixture.store.setExternalEnabledForCategory(category, true)
            val token = specs.single { it.role == DictionaryFileRole.TOKEN }
            File(fixture.store.directory, DictionaryOverrideStore.savedFileNameForKey(token.key)).writeBytes(byteArrayOf(1, 2, 3))
            val before = fixture.prefs.all.toMap()
            try {
                fixture.repository().open(category, "").nextPage()
                fail("Corrupted replacement should report an error")
            } catch (_: java.io.IOException) { }
            assertEquals(before, fixture.prefs.all)
            assertTrue(fixture.store.isValidOverride(token.key))
            assertEquals(listOf<Byte>(1, 2, 3), File(fixture.store.directory, DictionaryOverrideStore.savedFileNameForKey(token.key)).readBytes().toList())
        }
    }

    @Test fun systemDictionaryPaginationAndFullScanPerformance() = runBlocking {
        Fixture(context).use { fixture ->
            val repo = fixture.repository()
            System.gc()
            val beforeHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val beforePss = Debug.getPss()
            var started = SystemClock.elapsedRealtime()
            val cursor = repo.open(DictionaryCategory.SYSTEM, "")
            val first = cursor.nextPage()
            val loadMs = SystemClock.elapsedRealtime() - started
            val second = cursor.nextPage()
            assertEquals(100, first.rows.size)
            assertEquals(100, second.rows.size)
            assertEquals(200, (first.rows + second.rows).distinct().size)
            started = SystemClock.elapsedRealtime()
            val empty = repo.open(DictionaryCategory.SYSTEM, "zz_browser_absent_643905").nextPage()
            val scanMs = SystemClock.elapsedRealtime() - started
            assertTrue(empty.rows.isEmpty())
            assertFalse(empty.hasMore)
            System.gc()
            val heapDelta = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - beforeHeap
            println("BROWSER_SYSTEM firstPageMs=$loadMs noMatchFullScanMs=$scanMs javaHeapDeltaBytes=$heapDelta pssDeltaKb=${Debug.getPss() - beforePss}")
            // Keep the selected dictionary alive across the memory measurement.
            assertEquals(cursor.version, repo.version(DictionaryCategory.SYSTEM))
            assertFalse(cursor.nextPage().rows.isEmpty())
        }
    }
}
