package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_browser

import androidx.annotation.StringRes
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.TripleDictionaryData
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileRole
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.isDisableableBundledDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

data class BrowsableDictionary(val category: DictionaryCategory, @StringRes val nameRes: Int)
data class DictionaryBrowserRow(val reading: String, val word: String)
data class DictionaryBrowserVersion(val revision: Long, val enabled: Boolean, val external: Boolean)
data class DictionaryBrowserPage(val rows: List<DictionaryBrowserRow>, val hasMore: Boolean)

object DictionaryBrowserCatalog {
    val all = listOf(
        BrowsableDictionary(DictionaryCategory.SYSTEM, R.string.external_dictionary_category_system),
        BrowsableDictionary(DictionaryCategory.SINGLE_KANJI, R.string.external_dictionary_category_single_kanji),
        BrowsableDictionary(DictionaryCategory.EMOJI, R.string.external_dictionary_category_emoji),
        BrowsableDictionary(DictionaryCategory.EMOTICON, R.string.external_dictionary_category_emoticon),
        BrowsableDictionary(DictionaryCategory.SYMBOL, R.string.external_dictionary_category_symbol),
        BrowsableDictionary(DictionaryCategory.READING_CORRECTION, R.string.reading_correction_prediction_enable_title),
        BrowsableDictionary(DictionaryCategory.KOTOWAZA, R.string.dictionary_browser_kotowaza),
        BrowsableDictionary(DictionaryCategory.ENGLISH, R.string.external_dictionary_category_english),
        BrowsableDictionary(DictionaryCategory.ENGLISH_READING, R.string.external_dictionary_category_english_reading),
    ) + ClassifiedDictionaries.all.map { BrowsableDictionary(it.category, it.nameRes) }
}

internal fun normalizeDictionarySearch(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    if (normalized.none { it in '\u30a1'..'\u30f6' }) return normalized
    return buildString(normalized.length) {
        for (char in normalized) append(if (char in '\u30a1'..'\u30f6') (char.code - 0x60).toChar() else char)
    }
}

/** A cursor belongs to one query and is only advanced by one background job at a time. */
class DictionaryBrowserCursor internal constructor(
    val version: DictionaryBrowserVersion,
    private val rows: Iterator<DictionaryBrowserRow>,
    query: String = "",
) {
    private val query = normalizeDictionarySearch(query)

    private fun nextMatch(context: kotlin.coroutines.CoroutineContext): DictionaryBrowserRow? {
        while (true) {
            context.ensureActive()
            if (!rows.hasNext()) return null
            val row = rows.next()
            if (query.isEmpty() || normalizeDictionarySearch(row.reading).contains(query) ||
                normalizeDictionarySearch(row.word).contains(query)) return row
        }
    }

    suspend fun nextPage(): DictionaryBrowserPage = withContext(Dispatchers.Default) {
        val page = ArrayList<DictionaryBrowserRow>(PAGE_SIZE)
        while (page.size < PAGE_SIZE) {
            currentCoroutineContext().ensureActive()
            val row = nextMatch(currentCoroutineContext()) ?: break
            page.add(row)
        }
        currentCoroutineContext().ensureActive()
        // Do not scan the rest of a large dictionary just to look ahead for another match.
        DictionaryBrowserPage(page, page.size == PAGE_SIZE && rows.hasNext())
    }

    companion object { const val PAGE_SIZE = 100 }
}

/** Reuses the runtime decoders, but never changes override validity when a file cannot be read. */
class DictionaryBrowserRepository @Inject constructor(
    reader: DictionaryBinaryReader,
    private val resolver: DictionarySourceResolver,
) {
    private val reader = reader.forBrowsing()
    private var cachedCategory: DictionaryCategory? = null
    private var cachedVersion: DictionaryBrowserVersion? = null
    private var cachedRecords: Sequence<DictionaryBrowserRow>? = null

    fun version(category: DictionaryCategory) = DictionaryBrowserVersion(
        reader.currentRevision,
        !category.isDisableableBundledDictionary() || resolver.isOptionalBundledEnabled(category),
        resolver.shouldUseOverrideCategory(category),
    )

    suspend fun open(category: DictionaryCategory, query: String): DictionaryBrowserCursor =
        withContext(Dispatchers.IO) {
            require(DictionaryBrowserCatalog.all.any { it.category == category })
            val version = version(category)
            val records = if (cachedCategory == category && cachedVersion == version) {
                checkNotNull(cachedRecords)
            } else {
                // Release the previous dictionary before opening another category.
                cachedRecords = null
                cachedCategory = null
                cachedVersion = null
                val decoded = if (category == DictionaryCategory.ENGLISH) englishRows() else japaneseRows(category)
                currentCoroutineContext().ensureActive()
                check(version == version(category)) { "Dictionary changed while loading" }
                cachedCategory = category
                cachedVersion = version
                decoded.also { cachedRecords = it }
            }
            DictionaryBrowserCursor(version, records.iterator(), query)
        }

    private fun japaneseRows(category: DictionaryCategory): Sequence<DictionaryBrowserRow> {
        val specs = DictionaryFileSpecs.forCategory(category)
        val yomiKey = specs.single { it.role == DictionaryFileRole.YOMI }.key
        val tangoKey = specs.single { it.role == DictionaryFileRole.TANGO }.key
        val tokenKey = specs.single { it.role == DictionaryFileRole.TOKEN }.key
        val yomi = reader.loadLoudsWithTermId(yomiKey)
        val tango = reader.loadLouds(tangoKey)
        val token = reader.loadTokenArray(tokenKey)
        val lbs = reader.loadYomiLbsIndex(yomiKey, yomi)
        val leaves = reader.loadYomiLeafIndex(yomiKey, yomi)
        val tokens = reader.loadTokenIndex(tokenKey, token)
        val data = TripleDictionaryData(
            tango, yomi, token, lbs, leaves, tokens, reader.loadTangoLbsIndex(tangoKey, tango),
            reader.loadClassifiedSurfaceOverrides(category),
        )
        return sequence {
            val readings = yomi.termIterator(lbs, leaves)
            while (readings.hasNext()) {
                val (reading, term) = readings.next()
                val words = linkedSetOf<String>()
                token.forEachDictionaryByYomiTermId(term, tokens) { pos, cost, node ->
                    words.add(data.surfaceFor(node, reading, pos.toInt(), cost))
                }
                for (word in words) yield(DictionaryBrowserRow(reading, word))
            }
        }
    }

    private fun englishRows(): Sequence<DictionaryBrowserRow> {
        val readingTrie = reader.loadEnglishReading(DictionaryFileKey.ENGLISH_READING)
        val words = reader.loadEnglishWord(DictionaryFileKey.ENGLISH_WORD)
        val token = reader.loadEnglishToken(DictionaryFileKey.ENGLISH_TOKEN)
        val lbs = reader.loadEnglishReadingLbsIndex(readingTrie)
        val leaves = reader.loadEnglishReadingLeafIndex(readingTrie)
        val wordIndex = reader.loadEnglishWordLbsIndex(words)
        val tokens = reader.loadEnglishTokenIndex(token)
        return sequence {
            val readings = readingTrie.termIterator(lbs, leaves)
            while (readings.hasNext()) {
                val (reading, term) = readings.next()
                val variants = linkedSetOf<String>()
                token.forEachDictionaryByYomiTermId(term, tokens) { _, node ->
                    variants.add(if (node == -1) reading else words.getLetter(node, wordIndex))
                }
                for (word in variants) yield(DictionaryBrowserRow(reading, word))
            }
        }
    }
}
