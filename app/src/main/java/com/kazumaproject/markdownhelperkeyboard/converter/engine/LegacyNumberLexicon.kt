package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.engine.NumberLexicon.Entry
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji

/** Compatibility scoring for installations without the optional typed model asset. */
internal class LegacyNumberLexicon(
    private val lookup: (String) -> List<Entry>,
    private val matrix: ConnectionMatrix.CostTable,
    private val cancellationCheck: () -> Unit = {},
) {
    private val numberCache = HashMap<Long, List<Entry>>()
    private val numericIds = QuantityRuntime.dictionary.numericContextIds
    private fun counterEntries(reading: String, output: String): List<Entry> {
        val entries = lookup(reading).filter { it.text == output }
        val counters = entries.filter { it.left.toInt() in QuantityRuntime.dictionary.counterContextIds }
        return counters.ifEmpty { entries }
    }
    private fun customEntries(reading: String, output: String): List<Entry> =
        lookup(reading).filter { it.left.toInt() in QuantityRuntime.dictionary.counterContextIds }
            .let { counters -> counters.filter { it.text == output }.ifEmpty { counters } }
            .ifEmpty { counterEntries("こ", "個") }.map { Entry(output, it.left, it.right, it.cost) }

    fun forms(proof: ValidatedNumber): List<NumberGraphMatcher.Form> {
        if (proof.clock != null || numericIds.isEmpty()) return emptyList()
        // A whole lexical entry already has calibrated scoring. Do not create a competing cheap alias.
        if (lookup(proof.reading).any { it.text in proof.basicForms && it.left.toInt() in numericIds }) return emptyList()
        val baseReading = proof.builtInCounter?.reading ?: proof.customUnit?.reading ?: when (proof.counter) {
            "円" -> "えん"; "人" -> "にん"; "分" -> "ふん"; "時" -> "じ"; else -> null
        }
        val suffix = QuantityRuntime.dictionary.suffixes.firstOrNull { it.base + it.output == proof.counter ||
            it.base == "@registered" && proof.customUnit != null && proof.customUnit.output + it.output == proof.counter }
        val base = baseReading ?: suffix?.let { when(it.base) { "分" -> "ふん"; else -> null } } ?: return emptyList()
        val reading = base + (suffix?.reading ?: "")
        var counters = counterEntries(reading, proof.counter)
        if (counters.isEmpty() && suffix != null) {
            val bases = if (proof.customUnit != null) customEntries(base, proof.customUnit.output) else counterEntries(base, suffix.base)
            counters = bases.flatMap { before ->
                lookup(suffix.reading).filter { it.text == suffix.output }.map { after ->
                    Entry(proof.counter, before.left, after.right, before.cost + matrix.cost(before.right.toInt(), after.left.toInt()) + after.cost, before.parts + after.parts)
                }
            }
        }
        if (counters.isEmpty() && proof.customUnit != null) {
            // Preserve the reading's lexical metadata where available; otherwise use an actual
            // counter entry as a conservative fallback, without a global numeric discount.
            counters = customEntries(base, proof.customUnit.output)
        }
        return numbers(proof.value).flatMap { number -> counters.map { counter ->
            NumberGraphMatcher.Form(proof.basicForms[0], number.left, counter.right,
                number.cost + matrix.cost(number.right.toInt(), counter.left.toInt()) + counter.cost, number.parts + counter.parts)
        } }.groupBy { it.leftId to it.rightId }.values.map { entries -> entries.minBy { it.cost } }
    }
    private fun numbers(value: Long): List<Entry> = numberCache.getOrPut(value) {
        val reading = value.toKanji().map { when(it) {
            '零','〇' -> "ぜろ"; '一' -> "いち"; '二' -> "に"; '三' -> "さん"; '四' -> "よん"; '五' -> "ご"
            '六' -> "ろく"; '七' -> "なな"; '八' -> "はち"; '九' -> "きゅう"; '十' -> "じゅう"
            '百' -> "ひゃく"; '千' -> "せん"; '万' -> "まん"; '億' -> "おく"; '兆' -> "ちょう"; else -> ""
        } }.joinToString("")
        if (reading.isEmpty()) return@getOrPut emptyList()
        val paths = Array(reading.length + 1) { mutableListOf<Entry>() }
        for (start in reading.indices) {
            cancellationCheck()
            if (start != 0 && paths[start].isEmpty()) continue
            for (end in start + 1..reading.length) for (entry in lookup(reading.substring(start, end)).filter { it.left.toInt() in numericIds }) {
                if (start == 0) paths[end].add(entry)
                else paths[start].forEach { prev -> paths[end].add(Entry("", prev.left, entry.right,
                    prev.cost + matrix.cost(prev.right.toInt(), entry.left.toInt()) + entry.cost, prev.parts + entry.parts)) }
            }
            for (end in start + 1..reading.length) {
                val reduced = paths[end].groupBy { it.left to it.right }.values.map { it.minBy { e -> e.cost } }
                paths[end].clear(); paths[end].addAll(reduced)
            }
        }
        paths.last()
    }
}
