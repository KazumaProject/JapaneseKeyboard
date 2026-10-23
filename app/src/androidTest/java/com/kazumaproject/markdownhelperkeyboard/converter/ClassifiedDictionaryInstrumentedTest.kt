package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.converter.engine.TripleDictionaryData
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ClassifiedDictionaryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val entryPoint get() = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
    private fun expected() = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
        .open("classified-expected-records.json").bufferedReader().use { it.readText() })

    @Test fun allRecordsMatchFrozenReadingSurfacePosAndCost() {
        val reader = entryPoint.dictionaryBinaryReader()
        val expected = expected()
        val digest = MessageDigest.getInstance("SHA-256")
        val modulus = BigInteger.ONE.shiftLeft(256)
        var total = 0
        val mismatches = mutableListOf<String>()
        ClassifiedDictionaries.all.forEach { spec ->
            val files = DictionaryFileSpecs.forCategory(spec.category)
            val yomi = reader.loadLoudsWithTermId(files.single { it.role == DictionaryFileRole.YOMI }.key)
            val tango = reader.loadLouds(files.single { it.role == DictionaryFileRole.TANGO }.key)
            val token = reader.loadTokenArray(files.single { it.role == DictionaryFileRole.TOKEN }.key)
            val lbs = SuccinctBitVector(yomi.LBS)
            val leaf = SuccinctBitVector(yomi.isLeaf)
            val words = SuccinctBitVector(tango.LBS)
            val tokens = SuccinctBitVector(token.bitvector)
            val dictionary = TripleDictionaryData(tango, yomi, token, lbs, leaf, tokens, words, reader.loadLiteralSurfaces(spec.category))
            var sum = BigInteger.ZERO
            var count = 0
            val decoded = mutableListOf<String>()
            yomi.predictiveSearch("", lbs).forEach { reading ->
                val term = yomi.getTermId(yomi.getNodeIndex(reading, lbs), leaf)
                assertTrue("${spec.id}: $reading", term >= 0)
                token.forEachDictionaryByYomiTermId(term, tokens) { pos, cost, node ->
                    val surface = dictionary.surfaceFor(node, reading)
                    val left = token.leftIds[pos.toInt()]
                    val right = token.rightIds[pos.toInt()]
                    assertTrue(left >= 0 && right >= 0)
                    val row = "$reading\t$left\t$right\t$cost\t$surface"
                    decoded.add(row)
                    sum = sum.add(BigInteger(1, digest.digest(row.toByteArray(Charsets.UTF_8))))
                    count++
                }
            }
            assertEquals(spec.id, expected.getJSONObject(spec.id).getInt("count"), count)
            val actualDigest = sum.mod(modulus).toString(16).padStart(64, '0')
            if (actualDigest != expected.getJSONObject(spec.id).getString("digestSum")) {
                File(context.cacheDir, "classified-decoded-${spec.id.replace('/', '_')}.tsv").writeText(decoded.joinToString("\n", postfix = "\n"))
            }
            if (actualDigest != expected.getJSONObject(spec.id).getString("digestSum")) mismatches.add(spec.id)
            total += count
            println("CLASSIFIED_RECORDS ${spec.id}=$count")
        }
        assertEquals(ClassifiedDictionaries.all.sumOf { expected.getJSONObject(it.id).getInt("count") }, total)
        assertEquals("Categories with differing decoded records", emptyList<String>(), mismatches)
    }

    @Test fun all5138EnglishReadingPairsMatchPinnedCatalog() {
        val reader = entryPoint.dictionaryBinaryReader()
        val files = DictionaryFileSpecs.forCategory(DictionaryCategory.ENGLISH_READING)
        val yomi = reader.loadLoudsWithTermId(files.single { it.role == DictionaryFileRole.YOMI }.key)
        val tango = reader.loadLouds(files.single { it.role == DictionaryFileRole.TANGO }.key)
        val token = reader.loadTokenArray(files.single { it.role == DictionaryFileRole.TOKEN }.key)
        val lbs = SuccinctBitVector(yomi.LBS)
        val leaf = SuccinctBitVector(yomi.isLeaf)
        val words = SuccinctBitVector(tango.LBS)
        val tokens = SuccinctBitVector(token.bitvector)
        val expected = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("english-reading-expected.json").bufferedReader().use { it.readText() })
        val digest = MessageDigest.getInstance("SHA-256")
        var sum = BigInteger.ZERO
        var count = 0
        val pairs = mutableSetOf<Pair<String, String>>()
        yomi.predictiveSearch("", lbs).forEach { reading ->
            val term = yomi.getTermIdShortArray(yomi.getNodeIndex(reading, lbs), leaf)
            assertTrue(term >= 0)
            token.forEachDictionaryByYomiTermIdShortArray(term, tokens) { pos, cost, node ->
                val surface = tango.getLetterShortArray(node, words)
                assertTrue(token.leftIds[pos.toInt()] >= 0 && token.rightIds[pos.toInt()] >= 0)
                val row = "$reading\t$cost\t$surface"
                sum = sum.add(BigInteger(1, digest.digest(row.toByteArray(Charsets.UTF_8))))
                pairs.add(reading to surface)
                count++
            }
        }
        assertEquals(expected.getInt("count"), count)
        assertEquals(expected.getString("digestSum"), sum.mod(BigInteger.ONE.shiftLeft(256)).toString(16).padStart(64, '0'))
        assertTrue(pairs.containsAll(listOf("しんく" to "sync", "ふぇっち" to "fetch", "えいしんく" to "async", "うぇぶふっく" to "webhook")))
        assertTrue("かー" to "car" in pairs)
        assertFalse("かー" to "Car" in pairs)
        println("CLASSIFIED_ENGLISH_RECORDS $count")
    }

    @Test fun allCategoriesToggleAndKeepLabelsAcrossBackendsAndModes() = runBlocking {
        val engine = entryPoint.kanaKanjiEngine()
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val previous = ClassifiedDictionaries.all.associate { it.category to store.isOptionalBundledEnabled(it.category) }
        val expected = expected()
        try {
            ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, false) }
            engine.applyDictionaryOverrideState(context)
            System.gc()
            val beforeHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val started = android.os.SystemClock.elapsedRealtime()
            ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, true) }
            engine.applyDictionaryOverrideState(context)
            val loadMs = android.os.SystemClock.elapsedRealtime() - started
            System.gc()
            val afterHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            println("CLASSIFIED_LOAD all77ReloadMs=$loadMs javaHeapDeltaBytes=${afterHeap - beforeHeap}")
            assertEquals(ClassifiedDictionaries.categories, engine.loadedClassifiedCategories())
            ConversionBackend.entries.forEach { backend ->
                val session = KanaKanjiConversionSession(engine, backend)
                for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.CONVERSION, CandidateQueryMode.PREDICTION)) {
                    for (bunsetsu in listOf(false, true)) for (spec in ClassifiedDictionaries.all) {
                        val sample = expected.getJSONObject(spec.id).getJSONObject("sample")
                        val fullReading = sample.getString("reading")
                        val reading = if (mode == CandidateQueryMode.PREDICTION) fullReading.dropLast(1) else fullReading
                        val request = KanaKanjiQueryRequest(
                            input = reading, mode = mode, bunsetsuSeparation = bunsetsu, n = 120,
                            enabledDictionaryCategories = setOf(spec.category),
                            userDictionaryRepository = entryPoint.userDictionaryRepository(), learnRepository = null,
                            omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
                            typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 0,
                            omissionSearchOffsetScore = 0, beamWidth = 100,
                        )
                        val enabled = session.query(request).candidates
                        assertTrue("$backend $mode ${spec.id} $reading: $enabled", enabled.any { spec.category in it.dictionaryCategories })
                        val disabled = session.query(request.copy(enabledDictionaryCategories = emptySet())).candidates
                        assertTrue(disabled.none { it.dictionaryCategories.isNotEmpty() })
                    }
                }
            }
            ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, false) }
            engine.applyDictionaryOverrideState(context)
            assertTrue(engine.loadedClassifiedCategories().isEmpty())
        } finally {
            previous.forEach { (category, enabled) -> store.setOptionalBundledEnabled(category, enabled) }
            engine.applyDictionaryOverrideState(context)
        }
    }
}
