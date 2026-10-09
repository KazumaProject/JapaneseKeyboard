package com.kazumaproject.counter

data class QuantityAnalysis(val counterId: String, val number: Long, val category: String, val source: String)
data class TimeAnalysis(val hour: Int, val minute: Int, val second: Int?, val period: String, val originalHour: Int, val half: Boolean, val hasMinute: Boolean)
data class CounterCandidate(val value: String, val notation: String, val counterId: String)
/** A meaning survives independently of ASCII/kanji/fullwidth presentation. */
data class CounterInterpretation(
    val counterId: String,
    val number: Long,
    val category: String,
    val suffix: String,
    val source: String,
    val forms: List<CounterCandidate>,
    val time: TimeAnalysis? = null,
    val inRange: Boolean = true,
    val reading: String = "",
    val numberReading: String = "",
    val unitReading: String = "",
)

data class CounterConversion(val input: String, val quantities: List<QuantityAnalysis>, val time: TimeAnalysis?, val candidates: List<CounterCandidate>)

/** Thread-safe quantity/time reader. Upstream exact parsing is shared by sentence prefix lookup. */
class CounterConverter internal constructor(private val dictionary: CounterDictionary) {
    private data class Numeric(val value: Long, val terminal: Int, val literal: Boolean = false)
    private data class Match(val unit: Int, val number: Long, val suffix: String, val source: String, val numberReading: String = "")
    private val aliases = dictionary.units.indices.map { unit -> dictionary.surfaces.filter { it.unit == unit } }
    private val replaced = dictionary.units.indices.map { unit -> dictionary.exceptions.filter { it.unit == unit && it.replace }.map { it.number }.distinct().toLongArray() }
    // A restored numeric tail can begin with a character absent from the number trie (くじ/よじ).
    private val endingStartLetters = dictionary.endingTrie.let { trie ->
        trie.labels.filterIndexed { edge, _ ->
            val target = trie.targets[edge]
            trie.postings[target] < trie.postings[target + 1]
        }.toSet()
    }
    private val hourId = dictionary.units.indexOfFirst { it.id == "clock_hour" }
    private val minuteId = dictionary.units.indexOfFirst { it.id == "minute" }
    private val secondId = dictionary.units.indexOfFirst { it.id == "second" }

    private val regularUnitReadings: Array<String> by lazy {
        val readings = Array(dictionary.units.size) { "" }
        fun visit(state: Int, reversed: String) {
            val trie = dictionary.endingTrie
            for (posting in trie.postings[state] until trie.postings[state + 1]) {
                val ending = dictionary.endings[trie.outputs[posting]]
                if (ending.restored.isEmpty() && ending.terminal < 0) readings[ending.unit] = reversed.reversed()
            }
            for (edge in trie.edges[state] until trie.edges[state + 1]) visit(trie.targets[edge], reversed + trie.labels[edge])
        }
        visit(0, "")
        readings
    }

    fun convert(input: String, includeAliases: Boolean = true, limit: Int = Int.MAX_VALUE): CounterConversion {
        require(limit >= 0) { "Candidate limit must be nonnegative" }
        if (input.isEmpty() || input.length > 128) return CounterConversion(input, emptyList(), null, emptyList())
        val reading = normalize(input)
        val matches = matchQuantity(reading, 0, reading.length).sortedWith(compareBy({ dictionary.units[it.unit].priority }, { it.unit }, { it.number }))
        val time = parseTime(reading)
        val candidates = mutableListOf<CounterCandidate>()
        val seen = HashSet<String>()
        fun add(value: String, notation: String, id: String) {
            if (candidates.size < limit && seen.add(value)) candidates += CounterCandidate(value, notation, id)
        }
        for (style in 0..2) {
            if (candidates.size >= limit) break
            val notation = NOTATIONS[style]
            if (time != null) add(timeText(time, style), notation, "time")
            for (match in matches) {
                if (candidates.size >= limit) break
                val unit = dictionary.units[match.unit]
                val number = numberText(match.number, style)
                add(number + match.suffix, notation, unit.id)
                if (includeAliases && match.suffix == unit.surface) {
                    for (alias in aliases[match.unit]) {
                        if (candidates.size >= limit) break
                        add(number + alias.surface, notation, unit.id)
                    }
                }
            }
        }
        if (time != null) {
            val clock = pad(time.hour) + ":" + pad(time.minute) + (time.second?.let { ":" + pad(it) } ?: "")
            add(clock, "clock", "time")
        }
        return CounterConversion(input, matches.map { QuantityAnalysis(dictionary.units[it.unit].id, it.number, dictionary.units[it.unit].category, it.source) }.distinct(), time, candidates)
    }

    /**
     * Find complete rule readings beginning at [startIndex], retaining original UTF-16 offsets.
     * The caller supplies a normalized (length-preserving) reading once per graph build.
     * Start/ending tries reject impossible spans before allocating a substring or parsing.
     * On append, only spans ending after [minimumEndExclusive] need to be emitted.
     */
    fun forEachPrefix(
        input: String,
        startIndex: Int,
        minimumEndExclusive: Int = startIndex,
        action: (endIndex: Int, conversion: CounterConversion) -> Unit,
    ) {
        forEachPossibleEnd(input, startIndex, minimumEndExclusive) { end ->
            val result = convert(input.substring(startIndex, end))
            if (result.candidates.isNotEmpty()) action(end, result)
        }
    }

    private inline fun forEachPossibleEnd(
        input: String, startIndex: Int, minimumEndExclusive: Int,
        action: (Int) -> Unit,
    ) {
        if (startIndex !in input.indices) return
        val first = input[startIndex]
        if (first !in '0'..'9' && dictionary.numberTrie.next(0, first) < 0 &&
            dictionary.exceptionTrie.next(0, first) < 0 && first !in endingStartLetters) return
        var exceptionState = 0
        val maximumEnd = minOf(input.length, startIndex + 128)
        for (end in startIndex + 1..maximumEnd) {
            if (exceptionState >= 0) exceptionState = dictionary.exceptionTrie.next(exceptionState, input[end - 1])
            if (end <= minimumEndExclusive) continue
            val exceptionEnd = exceptionState >= 0 &&
                dictionary.exceptionTrie.postings[exceptionState] < dictionary.exceptionTrie.postings[exceptionState + 1]
            var ending = false
            var state = 0
            for (position in end - 1 downTo startIndex) {
                state = dictionary.endingTrie.next(state, input[position])
                if (state < 0) break
                if (dictionary.endingTrie.postings[state] < dictionary.endingTrie.postings[state + 1]) {
                    ending = true
                    break
                }
            }
            // Half hours terminate in はん, which is a grammar token, not a counter ending.
            val halfEnd = end - startIndex >= 2 && input[end - 2] == 'は' && input[end - 1] == 'ん'
            if (!exceptionEnd && !ending && !halfEnd) continue
            action(end)
        }
    }

    /** Range failures remain available for boundary validation, but never become candidates. */
    fun analyze(input: String, includeOutOfRange: Boolean = false): List<CounterInterpretation> {
        if (input.isEmpty() || input.length > 128) return emptyList()
        val reading = normalize(input)
        val matches = matchQuantity(reading, 0, reading.length, validateRange = !includeOutOfRange)
            .sortedWith(compareBy({ dictionary.units[it.unit].priority }, { it.unit }, { it.number }))
        val time = parseTime(reading)
        return buildList {
            if (time != null) {
                val forms = (0..2).map { CounterCandidate(timeText(time, it), NOTATIONS[it], "time") } +
                    CounterCandidate(pad(time.hour) + ":" + pad(time.minute) +
                        (time.second?.let { ":" + pad(it) } ?: ""), "clock", "time")
                add(CounterInterpretation("time", time.originalHour.toLong(), "clock", "時", "time", forms, time, reading = reading))
            }
            for (match in matches) {
                if (time != null && match.unit == hourId) continue
                val unit = dictionary.units[match.unit]
                val valid = match.number in unit.min..unit.max
                val forms = if (!valid) emptyList() else buildList {
                    for (style in 0..2) {
                        val number = numberText(match.number, style)
                        add(CounterCandidate(number + match.suffix, NOTATIONS[style], unit.id))
                        if (match.suffix == unit.surface) aliases[match.unit].forEach {
                            add(CounterCandidate(number + it.surface, NOTATIONS[style], unit.id))
                        }
                    }
                }.distinctBy { it.value }
                add(CounterInterpretation(unit.id, match.number, unit.category, match.suffix, match.source, forms, inRange = valid, reading = reading,
                    numberReading = match.numberReading.ifEmpty { dictionary.numbers.firstOrNull { it.value == match.number && it.kind in 0..2 }?.reading.orEmpty() },
                    unitReading = regularUnitReadings[match.unit]))
            }
        }
    }

    fun numberValue(input: String): Long? {
        val reading = normalize(input)
        return parseNumber(reading, 0, reading.length, "")?.value
    }

    /** Includes syntactically complete quantities outside their permitted range. */
    fun hasQuantitySyntax(input: String): Boolean = analyze(input, includeOutOfRange = true).isNotEmpty()

    fun forEachAnalysis(
        input: String, startIndex: Int, minimumEndExclusive: Int = startIndex,
        action: (Int, List<CounterInterpretation>) -> Unit,
    ) = forEachPossibleEnd(input, startIndex, minimumEndExclusive) { end ->
        val meanings = analyze(input.substring(startIndex, end))
        if (meanings.isNotEmpty()) action(end, meanings)
    }

    fun normalizedReading(input: String): String = normalize(input)

    private fun normalize(input: String): String {
        if (input.none { it in 'ァ'..'ヶ' || it in '０'..'９' }) return input
        return buildString(input.length) { input.forEach { append(when (it) { in 'ァ'..'ヶ' -> (it.code - 0x60).toChar(); in '０'..'９' -> (it.code - '０'.code + '0'.code).toChar(); else -> it }) } }
    }

    private fun matchQuantity(input: String, start: Int, end: Int, onlyUnit: Int = -1, validateRange: Boolean = true): List<Match> {
        if (start >= end) return emptyList()
        val result = mutableListOf<Match>()
        fun add(match: Match) { if (result.none { it.unit == match.unit && it.number == match.number && it.suffix == match.suffix }) result += match }
        val exceptionTrie = dictionary.exceptionTrie
        var node = 0
        for (position in start until end) { node = exceptionTrie.next(node, input[position]); if (node < 0) break }
        if (node >= 0) for (posting in exceptionTrie.postings[node] until exceptionTrie.postings[node + 1]) {
            val exception = dictionary.exceptions[exceptionTrie.outputs[posting]]
            if (onlyUnit < 0 || exception.unit == onlyUnit) add(Match(exception.unit, exception.number, exception.suffix, "exception"))
        }
        // Candidate endings sharing the same numeric span/restoration reuse one parse.
        val cacheEnds = mutableListOf<Int>(); val cacheTails = mutableListOf<String>(); val cacheValues = mutableListOf<Numeric?>()
        val trie = dictionary.endingTrie
        node = 0
        for (position in end - 1 downTo start) {
            node = trie.next(node, input[position]); if (node < 0) break
            for (posting in trie.postings[node] until trie.postings[node + 1]) {
                val ending = dictionary.endings[trie.outputs[posting]]
                if (onlyUnit >= 0 && ending.unit != onlyUnit) continue
                var cached = -1
                for (i in cacheEnds.indices) if (cacheEnds[i] == position && cacheTails[i] == ending.restored) { cached = i; break }
                val numeric = if (cached >= 0) cacheValues[cached] else parseNumber(input, start, position, ending.restored).also {
                    cacheEnds += position; cacheTails += ending.restored; cacheValues += it
                }
                if (numeric == null) continue
                val unit = dictionary.units[ending.unit]
                if (validateRange && numeric.value !in unit.min..unit.max) continue
                if (ending.terminal >= 0 && numeric.terminal != ending.terminal) continue
                if (!numeric.literal) {
                    if (ending.terminal < 0 && unit.blocked and terminalBit(numeric.terminal) != 0) continue
                    if (replaced[ending.unit].binarySearch(numeric.value) >= 0) continue
                }
                add(Match(ending.unit, numeric.value, unit.surface, if (ending.terminal < 0) "regular" else "tail-rule", input.substring(start,position) + ending.restored))
            }
        }
        return result
    }

    private fun parseNumber(input: String, start: Int, end: Int, restored: String): Numeric? {
        val rawSize = end - start; val size = rawSize + restored.length
        if (size == 0) return null
        fun letter(position: Int): Char = if (position < rawSize) input[start + position] else restored[position - rawSize]
        if (restored.isEmpty() && rawSize > 0 && input[start] in '0'..'9') {
            var value = 0L
            for (position in start until end) {
                val digit = input[position] - '0'
                if (digit !in 0..9 || value > (Long.MAX_VALUE - digit) / 10) return null
                value = value * 10 + digit
            }
            return Numeric(value, decimalTerminal(value), true)
        }
        var position = 0; var group = 0L; var total = 0L
        var previousPlace = 10000L; var previousScale = Long.MAX_VALUE; var terminal = 0; var pendingScale = 0
        val trie = dictionary.numberTrie
        while (position < size) {
            var node = 0; var scan = position; var acceptedEnd = -1; var partIndex = -1
            while (scan < size) {
                node = trie.next(node, letter(scan)); if (node < 0) break
                scan++
                if (trie.postings[node] < trie.postings[node + 1]) {
                    acceptedEnd = scan; partIndex = trie.outputs[trie.postings[node]]
                }
            }
            if (partIndex < 0) return null
            val part = dictionary.numbers[partIndex]
            if (pendingScale != 0 && part.kind != 2) return null
            when (part.kind) {
                0 -> return if (position == 0 && acceptedEnd == size) Numeric(0, 0) else null
                1, 3, 4 -> {
                    if (part.place >= previousPlace) return null
                    group += part.value; previousPlace = part.place
                    terminal = if (part.place == 1L) part.value.toInt() else part.place.toInt()
                    pendingScale = if (part.kind == 1) 0 else part.kind
                }
                2 -> {
                    val cho = part.value == 1000000000000L
                    val kei = part.value == 10000000000000000L
                    if (pendingScale == 3 && !cho && !kei) return null
                    if (pendingScale == 4 && !kei) return null
                    if (pendingScale == 0 && ((cho && terminal in listOf(1, 10)) || (kei && terminal in listOf(1, 6, 10, 100)))) return null
                    if (part.value >= previousScale || (group == 0L && position != 0)) return null
                    val multiplier = if (group == 0L) 1L else group
                    if (multiplier > (Long.MAX_VALUE - total) / part.value) return null
                    total += multiplier * part.value; group = 0; previousPlace = 10000; previousScale = part.value
                    terminal = if (part.value == 10000L) 10000 else -2
                    pendingScale = 0
                }
            }
            position = acceptedEnd
        }
        if (pendingScale != 0 || group > Long.MAX_VALUE - total) return null
        return Numeric(total + group, terminal)
    }

    private fun parseTime(input: String): TimeAnalysis? {
        if (hourId < 0 || minuteId < 0 || secondId < 0) return null
        val period = when { input.startsWith("ごぜん") -> "午前"; input.startsWith("ごご") -> "午後"; else -> "" }
        val start = when (period) { "午前" -> 3; "午後" -> 2; else -> 0 }
        for (hourEnd in start + 1..input.length) {
            if (input[hourEnd - 1] != 'じ') continue
            val hour = matchQuantity(input, start, hourEnd, hourId).firstOrNull()?.number?.toInt() ?: continue
            if (hour !in 0..23 || (period.isNotEmpty() && hour !in 0..12)) continue
            val adjusted = when (period) { "午前" -> hour % 12; "午後" -> hour % 12 + 12; else -> hour }
            fun result(minute: Int, second: Int?, half: Boolean, hasMinute: Boolean) = TimeAnalysis(adjusted, minute, second, period, hour, half, hasMinute)
            if (hourEnd == input.length) return result(0, null, false, false)
            fun seconds(position: Int): Int? = matchQuantity(input, position, input.length, secondId).firstOrNull()?.number?.let { if (it in 0..59) it.toInt() else null }
            if (input.startsWith("はん", hourEnd)) {
                val afterHalf = hourEnd + 2
                if (afterHalf == input.length) return result(30, null, true, true)
                val second = seconds(afterHalf)
                if (second != null) return result(30, second, true, true)
            }
            for (minuteEnd in hourEnd + 2..input.length) {
                if (input[minuteEnd - 1] != 'ん') continue
                val minute = matchQuantity(input, hourEnd, minuteEnd, minuteId).firstOrNull()?.number ?: continue
                if (minute !in 0..59) continue
                if (minuteEnd == input.length) return result(minute.toInt(), null, false, true)
                val second = seconds(minuteEnd)
                if (second != null) return result(minute.toInt(), second, false, true)
            }
            val second = seconds(hourEnd)
            if (second != null) return result(0, second, false, false)
        }
        return null
    }

    private fun timeText(time: TimeAnalysis, style: Int): String = buildString {
        append(time.period); append(numberText(time.originalHour.toLong(), style)); append("時")
        if (time.half) append("半") else if (time.hasMinute) { append(numberText(time.minute.toLong(), style)); append("分") }
        if (time.second != null) { append(numberText(time.second.toLong(), style)); append("秒") }
    }

    private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()
    private fun decimalTerminal(value: Long): Int = when {
        value == 0L -> 0; value % 10 != 0L -> (value % 10).toInt(); value % 100 != 0L -> 10
        value % 1000 != 0L -> 100; value % 10000 != 0L -> 1000; else -> -2
    }

    companion object {
        private val NOTATIONS = arrayOf("ascii", "kanji", "fullwidth")
        private val SCALES = longArrayOf(10000000000000000L, 1000000000000L, 100000000L, 10000L, 1L)
        private val SCALE_NAMES = arrayOf("京", "兆", "億", "万", "")
        private val SMALL_PLACES = intArrayOf(1000, 100, 10)
        private val SMALL_NAMES = arrayOf("千", "百", "十")
        fun numberText(value: Long, style: Int): String {
            require(value >= 0 && style in 0..2)
            if (style == 0) return value.toString()
            if (style == 2) return buildString { value.toString().forEach { append((it.code - '0'.code + '０'.code).toChar()) } }
            if (value == 0L) return "零"
            val digits = "〇一二三四五六七八九"
            return buildString {
                var remaining = value
                for (index in SCALES.indices) {
                    var group = (remaining / SCALES[index]).toInt(); remaining %= SCALES[index]
                    if (group == 0) continue
                    for (small in SMALL_PLACES.indices) {
                        val digit = group / SMALL_PLACES[small]; group %= SMALL_PLACES[small]
                        if (digit > 0) { if (digit > 1) append(digits[digit]); append(SMALL_NAMES[small]) }
                    }
                    if (group > 0) append(digits[group])
                    append(SCALE_NAMES[index])
                }
            }
        }
    }
}
