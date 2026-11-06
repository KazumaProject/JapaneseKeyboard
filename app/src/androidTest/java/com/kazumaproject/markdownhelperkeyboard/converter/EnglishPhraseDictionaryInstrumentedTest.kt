package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.EnglishPhraseDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.PhraseConverter
import com.kazumaproject.markdownhelperkeyboard.converter.english.phrase.RuntimeGrammarRoles
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltDictionaryLoader
import com.kazumaproject.markdownhelperkeyboard.converter.glide.QwertyGlidePrebuiltLoadResult
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnglishPhraseDictionaryInstrumentedTest {
    @Test
    fun packagedDictionaryMatchesReferenceExamples() = withDictionary { _, resolver, reader ->
        val dictionary = checkNotNull(reader.loadBundledEnglishForEngine())
        val grammar = checkNotNull(reader.loadBundledEnglishGrammar())
        val converter = PhraseConverter(EnglishPhraseDictionary(dictionary, grammar))
        val joined = converter.convert("hecantake", prefixMode = false, limit = 12).first()
        assertEquals("he can take", joined.text)
        assertEquals(listOf(0 to 2, 2 to 5, 5 to 9), joined.segments.map { it.start to it.end })
        assertEquals(4669, joined.dictionaryCost)
        assertEquals("he takes", converter.convert("he tak", prefixMode = true, limit = 12).first().text)
        assertEquals("don't", converter.convert("don’", prefixMode = true, limit = 12).first().text)
        assertTrue(converter.convert("google maps", prefixMode = false, limit = 12).any { it.text == "Google Maps" })
        assertTrue(QwertyGlidePrebuiltDictionaryLoader(resolver).load() is QwertyGlidePrebuiltLoadResult.Loaded)
    }

    @Test
    fun predictionAndDictionarySwitchesControlPhraseCandidates() = withDictionary { store, resolver, reader ->
        val engine = EnglishEngine().apply { configureLazyDictionaryLoading(reader) }
        fun query(enabled: Boolean) =
            engine.getConversionCandidates("hecantake", "hecantake", false, enabled, true, emptyList())

        assertFalse(query(false).any { it.string == "he can take" })
        val enabled = query(true)
        assertEquals("hecantake", enabled.first().string)
        assertEquals(9, enabled.first { it.string == "he can take" }.length.toInt())
        for ((input, expected) in listOf("he tak" to "he takes", "don’" to "don't", "they tak" to "they take")) {
            val suggestions = engine.getConversionCandidates(input, null, false, true, true, emptyList())
            assertEquals(input, expected, suggestions.getOrNull(1)?.string)
        }

        val specs = DictionaryFileSpecs.forCategory(DictionaryCategory.ENGLISH)
        for (spec in specs) {
            resolver.openBundledForKey(spec.key).use {
                assertTrue(store.saveOverrideFromInputStream(spec.key, it).isValid)
            }
        }
        engine.reloadDictionariesFromCurrentSources(reader)
        assertFalse(query(true).any { it.string == "he can take" })
        assertTrue(QwertyGlidePrebuiltDictionaryLoader(resolver).load() is QwertyGlidePrebuiltLoadResult.NotAvailable)

        store.setExternalEnabledForCategory(DictionaryCategory.ENGLISH, false)
        engine.reloadDictionariesFromCurrentSources(reader)
        assertTrue(query(true).any { it.string == "he can take" })
    }

    @Test
    fun corruptGrammarIndexIsRejected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = context.assets.open("english/grammar/categories.erol").use { it.readBytes() }
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { RuntimeGrammarRoles.read(ByteBuffer.wrap(bytes)) }
    }

    private fun withDictionary(
        test: (DictionaryOverrideStore, DictionarySourceResolver, DictionaryBinaryReader) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "english-phrase-test-${UUID.randomUUID()}"
        val directory = File(context.cacheDir, name)
        val overridePrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val defaultPrefs = context.getSharedPreferences("$name-default", Context.MODE_PRIVATE)
        val store = DictionaryOverrideStore(directory, overridePrefs, defaultPrefs)
        val resolver = DictionarySourceResolver(context, store)
        try {
            test(store, resolver, DictionaryBinaryReader(resolver, store))
        } finally {
            directory.deleteRecursively()
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("$name-default")
        }
    }
}
