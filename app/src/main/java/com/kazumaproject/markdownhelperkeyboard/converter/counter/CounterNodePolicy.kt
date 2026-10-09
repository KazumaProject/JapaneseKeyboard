package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.counter.CounterConverter
import com.kazumaproject.counter.CounterInterpretation
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix

/** Frozen lexical data, not mutable graph nodes or output-string priority. */
data class CounterCompetitor(val meaning: CounterInterpretation, val wordCost: Int)

data class CounterLexicalAlternative(
    val surface: String,
    val leftId: Short,
    val rightId: Short,
    val cost: Int,
)

internal object CounterNodePolicy {
    // Used only when no existing quantity entry supplies a lexical cost. No per-format discount.
    const val WORD_COST = 2000
    private val temporalCategories = setOf("calendar", "duration")
    private val calendarCategories = setOf("calendar", "clock")
    private val nonCountCategories = setOf("clock", "calendar", "duration", "text")

    fun leftId(meaning: CounterInterpretation): Short = if (meaning.time != null) 1909 else 2043
    fun rightId(meaning: CounterInterpretation): Short = when (meaning.counterId) {
        "time" -> 1909
        "tsu" -> 2012
        "kai" -> 2014
        "clock_hour" -> 2015
        "month" -> 2016
        "kai_floor" -> 2018
        else -> 2011
    }

    /** Recognize numeric spelling variants without treating 珊瑚/日本/なのか as quantities. */
    fun represents(meaning: CounterInterpretation, surface: String): Boolean {
        if (meaning.forms.any { it.value == surface }) return true
        if (surface.isEmpty() || (surface.first() !in "0123456789０１２３４５６７８９〇零一二三四五六七八九十百千万億兆京廿卅卌" &&
            !surface.startsWith("ひと") && !surface.startsWith("ふた"))) return false
        val kanjiNumber = CounterConverter.numberText(meaning.number, 1)
        if (surface.startsWith(kanjiNumber)) {
            val tail = surface.removePrefix(kanjiNumber)
            if (tail.isNotEmpty() && tail.all { it in 'ぁ'..'ん' } && meaning.reading.endsWith(tail)) return true
        }
        fun normalized(value: String): String = buildString {
            value.replace("廿", "二十").replace("卅", "三十").replace("卌", "四十")
                .replace("零", "〇").forEach { c ->
                    append(when(c) {
                        in '0'..'9' -> "〇一二三四五六七八九"[c-'0']
                        in '０'..'９' -> "〇一二三四五六七八九"[c-'０']
                        else -> c
                    })
                }
        }
        val native = when (meaning.number) {
            1L -> if (surface.startsWith("ひと")) "一" + surface.drop(2) else surface
            2L -> if (surface.startsWith("ふた")) "二" + surface.drop(2) else surface
            else -> surface
        }
        val canonical = normalized(native)
        return meaning.forms.any { normalized(it.value) == canonical }
    }

    fun dictionaryEntries(
        reading: String, yomi: LOUDSWithTermId, tango: LOUDS, tokens: TokenArray,
        yomiBits: SuccinctBitVector, leaves: SuccinctBitVector,
        tokenBits: SuccinctBitVector, tangoBits: SuccinctBitVector,
    ): List<CounterLexicalAlternative> {
        val node = yomi.getNodeIndex(reading, yomiBits)
        if (node <= 0) return emptyList()
        val term = yomi.getTermId(node, leaves)
        if (term < 0) return emptyList()
        return buildList {
            tokens.forEachDictionaryByYomiTermId(term, tokenBits) { pos, cost, word ->
                val surface = when (word) { -2 -> reading; -1 -> reading.hiraToKata(); else -> tango.getLetter(word, tangoBits) }
                add(CounterLexicalAlternative(surface, tokens.leftIds[pos.toInt()], tokens.rightIds[pos.toInt()], cost.toInt()))
            }
        }
    }

    /** Existing numeral + suffix paths are evidence even when no whole-word token exists. */
    fun composedNumericEntries(
        meaning: CounterInterpretation,
        matrix: ConnectionMatrix.CostTable? = null,
        entries: (String) -> List<CounterLexicalAlternative>,
    ): List<CounterLexicalAlternative> {
        val reading = meaning.numberReading
        if (reading.isEmpty() || meaning.unitReading.isEmpty() ||
            reading + meaning.unitReading != meaning.reading) return emptyList()
        data class NumericPath(val left: Short, val right: Short, val cost: Int, val tokens: Int = 0)
        val states = Array(reading.length + 1) { LinkedHashMap<Pair<Short,Short>,NumericPath>() }
        for (start in reading.indices) {
            if (start > 0 && states[start].isEmpty()) continue
            for (end in start + 1..reading.length) {
                val tokens = entries(reading.substring(start,end)).filter {
                    it.leftId.toInt() in 2043..2053 && it.rightId.toInt() in 2043..2053
                }
                for (token in tokens) {
                    val predecessors = if (start == 0) listOf(NumericPath(token.leftId,token.leftId,0)) else states[start].values.toList()
                    for (previous in predecessors) {
                        val cost = previous.cost + token.cost +
                            (if (start == 0) 0 else matrix?.cost(previous.right.toInt(),token.leftId.toInt()) ?: 0)
                        val key = previous.left to token.rightId
                        if (states[end][key]?.cost?.let { cost < it } != false)
                            states[end][key] = NumericPath(previous.left,token.rightId,cost, previous.tokens + 1)
                    }
                }
            }
        }
        val units = entries(meaning.unitReading).filter { it.surface == meaning.suffix }
        return states.last().values.filter { it.tokens > 1 }.flatMap { number -> units.map { unit ->
            CounterLexicalAlternative(meaning.forms.first { it.notation == "kanji" }.value,number.left,unit.rightId,
                number.cost + unit.cost + (matrix?.cost(number.right.toInt(),unit.leftId.toInt()) ?: 0))
        } }
    }

    fun lexicalAlternatives(meaning: CounterInterpretation, nodes: List<Node>): List<CounterLexicalAlternative> =
        nodes.filter { !represents(meaning, it.tango) }.map {
            CounterLexicalAlternative(it.tango, it.l, it.r, it.score)
        }

    private fun supportsQuantity(previous: Node, node: Node, next: Node?): Boolean {
        val q = node.counter ?: return false
        if (node.yomiUsed.any { it in '0'..'9' || it in '０'..'９' }) return true
        if (q.category == "duration" && q.suffix.endsWith("間") && node.counterAlternatives.none {
                it.leftId.toInt() in 1841..1949 ||
                    (it.leftId.toInt() in 0..169 && it.rightId.toInt() in 170..340)
            }) return true
        val following = next ?: return false
        // Explicit temporal case/suffix, rather than a month prefix alone (今月なのかもしれない).
        val temporalSuffix = following.r.toInt() in 2005..2008 || following.l.toInt() == 1914
        val temporalCase = following.l.toInt() in 372..373
        val contextualCase = following.l.toInt() == 370 || following.l.toInt() == 375
        if ((temporalCase || temporalSuffix || contextualCase) && (q.time != null || q.category in temporalCategories)) {
            val priorCalendar = previous.counter?.category in calendarCategories ||
                (previous.l.toInt() == 1913 || previous.l.toInt() == 1916) ||
                (previous.l.toInt() == 1909 && (previous.tango.endsWith("月") || previous.tango.endsWith("年")))
            if (priorCalendar || temporalCase || temporalSuffix) return true
        }
        // Amount between an object-case marker and a verb; the quantity must be a countable unit.
        return q.category !in nonCountCategories &&
            previous.l.toInt() == 379 &&
            following.l.toInt() in 577..1840
    }

    /** Nonnegative local correction preserves the lexical alternative under the same neighbours. */
    fun contextualPenalty(previous: Node, current: Node, next: Node?, matrix: ConnectionMatrix.CostTable, segmenter: MozcSegmenter? = null): Int {
        if (current.counter == null) {
            val peer = current.counterCompetitor ?: return 0
            val quantityNode = current.copy(counter = peer.meaning, l = leftId(peer.meaning), r = rightId(peer.meaning),
                score = peer.wordCost, adjustedScore = peer.wordCost)
            if (!supportsQuantity(previous, quantityNode, next)) return 0
            val qCost = peer.wordCost.toLong() + matrix.cost(previous.r.toInt(), quantityNode.l.toInt()) +
                (next?.let { matrix.cost(quantityNode.r.toInt(), it.l.toInt()) } ?: 0)
            val ownCost = current.adjustedScore.toLong() + matrix.cost(previous.r.toInt(), current.l.toInt()) +
                (next?.let { matrix.cost(current.r.toInt(), it.l.toInt()) } ?: 0)
            return (qCost - ownCost + 1).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        }
        if (current.counterAlternatives.isEmpty() || supportsQuantity(previous, current, next)) return 0
        val quantityCost = current.adjustedScore.toLong() + matrix.cost(previous.r.toInt(), current.l.toInt()) +
            (next?.let { matrix.cost(current.r.toInt(), it.l.toInt()) } ?: 0)
        fun localCost(entry: CounterLexicalAlternative): Long = entry.cost.toLong() +
            (if (previous.tango == "BOS") segmenter?.getPrefixPenalty(entry.leftId.toInt()) ?: 0 else 0) +
            (if (next?.tango == "EOS") segmenter?.getSuffixPenalty(entry.rightId.toInt()) ?: 0 else 0) +
            matrix.cost(previous.r.toInt(), entry.leftId.toInt()) +
            (next?.let { matrix.cost(entry.rightId.toInt(), it.l.toInt()) } ?: 0)
        val ordinaryCost = current.counterAlternatives.minOf(::localCost)
        val grammatical = current.counterAlternatives.any { it.leftId.toInt() in 0..169 && it.rightId.toInt() in 170..340 }
        // Existing digit spellings had a 2000 candidate surcharge before semantic nodes.
        // Include that cost as lexical evidence; removing it must not invert ordinary words.
        val numericCost = current.counterNumericSupports.minOfOrNull { entry ->
            localCost(entry) + if (entry.surface.any { it.isDigit() }) 2000 else 0
        }
        if (!grammatical && numericCost != null && numericCost <= ordinaryCost) return 0
        return (ordinaryCost - quantityCost + 1).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }
}
