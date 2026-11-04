package com.kazumaproject.symbol_keyboard

import android.content.Context
import java.io.BufferedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class KanjiRadical(val number: Int, val label: String)

internal data class KanjiEntry(
    val symbol: String,
    val radical: Int,
    val strokes: Int,
    val isJoyo: Boolean,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    val example: String?,
)

internal class KanjiCatalog private constructor(
    val radicals: List<KanjiRadical>,
    val entries: Map<String, KanjiEntry>,
) {
    private val symbolsByRadical = entries.values.groupBy(
        keySelector = KanjiEntry::radical,
        valueTransform = KanjiEntry::symbol,
    )

    fun symbols(radical: Int): List<String> = symbolsByRadical[radical].orEmpty()

    companion object {
        const val CATALOG_ASSET = "kanji/catalog.tsv"
        const val EXAMPLES_ASSET = "kanji/examples.tsv"

        private val mutex = Mutex()
        private var cached: KanjiCatalog? = null

        suspend fun load(context: Context): KanjiCatalog = withContext(Dispatchers.IO) {
            mutex.withLock {
                cached ?: context.assets.open(CATALOG_ASSET).bufferedReader().use { catalog ->
                    context.assets.open(EXAMPLES_ASSET).bufferedReader().use { examples ->
                        parse(catalog, examples)
                    }
                }.also { cached = it }
            }
        }

        fun parse(catalog: BufferedReader, examples: BufferedReader): KanjiCatalog {
            val sentences = mutableMapOf<String, String>()
            examples.forEachLine { line ->
                val columns = line.split('\t', limit = 2)
                require(columns.size == 2)
                val (symbol, sentence) = columns
                require(sentence.isNotBlank() && symbol in sentence)
                require(sentences.put(symbol, sentence) == null)
            }

            val radicals = mutableListOf<KanjiRadical>()
            val entries = linkedMapOf<String, KanjiEntry>()
            catalog.forEachLine { line ->
                val columns = line.split('\t')
                if (columns.size == 2) {
                    radicals += KanjiRadical(columns[0].toInt(), columns[1])
                } else {
                    require(columns.size == 6)
                    val (symbol, radical, strokes, joyo, on) = columns
                    require(symbol.codePointCount(0, symbol.length) == 1)
                    val entry = KanjiEntry(
                        symbol = symbol,
                        radical = radical.toInt(),
                        strokes = strokes.toInt(),
                        isJoyo = joyo == "1",
                        onReadings = readings(on),
                        kunReadings = readings(columns[5]),
                        example = sentences.remove(symbol),
                    )
                    require(entries.put(symbol, entry) == null)
                }
            }
            require(radicals.map(KanjiRadical::number) == (1..214).toList())
            require(entries.values.all { it.radical in 1..214 })
            require(sentences.isEmpty())
            return KanjiCatalog(radicals, entries)
        }

        private fun readings(value: String): List<String> =
            if (value == "-") emptyList() else value.split('|')
    }
}

internal fun kanjiCodePoint(symbol: String): String =
    "U+${symbol.codePointAt(0).toString(16).uppercase().padStart(4, '0')}"
