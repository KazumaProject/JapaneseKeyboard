package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.displayExponent
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji

enum class NumberInputOrigin { READING, DIGITS }

/** A fully parsed numeric reading used only to generate numeric candidates. */
class ValidatedNumber private constructor(
    val value: Long,
    val reading: String,
    val origin: NumberInputOrigin,
    val counter: String,
    val digits: String,
    val clock: Pair<Int, Int>? = null,
    val customUnit: CustomNumberUnit? = null,
    val builtInCounter: BuiltInCounter? = null,
    /** Keep category identity when a dictionary suffix extends the displayed counter. */
    val baseCounter: String = counter,
    /** Parsed suffixes, in input order. Unit labels are never reparsed as syntax. */
    val counterSuffixes: List<com.kazumaproject.quantity.QuantityDictionary.Suffix> = emptyList(),
    /** The cardinal accepted by the parser, after a counter-local sound change, if any.
     * Null denotes an atomic/special reading; it must not be inferred from the unit label. */
    val cardinalReading: String? = null,
    /** Parsed hour and minute retain their own accepted readings and unit identities. */
    val clockParts: Pair<ValidatedNumber, ValidatedNumber>? = null,
    val cardinalSource: CardinalSource? = null,
    val coreInputLength: Int = reading.length,

) {
    /** Structure comes from the accepted cardinal, never from a candidate's displayed text. */
    val cardinalExpression: com.kazumaproject.quantity.CardinalGrammar.Expression? by lazy {
        cardinalReading?.let(com.kazumaproject.quantity.CardinalGrammar::parse)
    }
    val inputStructure: NumberReadingStructure by lazy { structureAt(0) }
    private fun structureAt(offset: Int): NumberReadingStructure {
        val parts = clockParts
        val expression = cardinalExpression
        val source = cardinalSource
        val core = when {
            parts != null -> NumberReadingStructure.Clock(offset, offset + coreInputLength,
                parts.first.structureAt(offset), parts.second.structureAt(offset + parts.first.reading.length))
            origin == NumberInputOrigin.DIGITS -> NumberReadingStructure.Digits(offset, offset + coreInputLength, value)
            expression != null && source != null -> {
                val alignment = buildList {
                    if (source.unchangedPrefix > 0) add(NumberReadingStructure.Alignment(offset,
                        offset + source.unchangedPrefix, 0, source.unchangedPrefix, false))
                    if (source.unchangedPrefix < source.end) add(NumberReadingStructure.Alignment(offset + source.unchangedPrefix,
                        offset + source.end, source.unchangedPrefix, cardinalReading!!.length, source.includesUnit))
                }
                NumberReadingStructure.Cardinal(offset, offset + coreInputLength, value, expression, alignment,
                    if (source.includesUnit) null else offset + source.end)
            }
            else -> NumberReadingStructure.Atomic(offset, offset + coreInputLength, value)
        }
        if (counterSuffixes.isEmpty()) return core
        var cursor = offset + coreInputLength
        val suffixes = counterSuffixes.map { suffix ->
            NumberReadingStructure.Suffix(cursor, cursor + suffix.reading.length, suffix.output)
                .also { cursor = it.inputEnd }
        }
        return NumberReadingStructure.Compound(offset, offset + reading.length, core, suffixes)
    }
        val fullWidth: String by lazy { buildString(digits.length) { digits.forEach { append(it + 0xFEE0) } } }
    val basicForms: List<String> by lazy { clock?.let { (hour, minute) ->
        val half = "${hour}時${minute}分"
        listOf(half, half.map { if (it in '0'..'9') it + 0xFEE0 else it }.joinToString(""),
            hour.toLong().toKanji() + "時" + minute.toLong().toKanji() + "分")
    } ?: listOf(digits + counter, fullWidth + counter, value.toKanji() + counter) }

    val clockText: String? get() = clock?.let { (hour, minute) -> "$hour:${minute.toString().padStart(2, '0')}" }


    fun exponent(): String? {
        if (counter.isNotEmpty() || value < 100_000_000L) return null
        val decimal = value.toString()
        return if (decimal.first() == '1' && decimal.drop(1).all { it == '0' })
            displayExponent(10, decimal.length - 1) else null
    }

    companion object {
        // Osaka, 数字, supporter pp.17/22; Irodori 入門 word list, 時間/人数.
        // https://www.pref.osaka.lg.jp/documents/26351/2suuji.pdf
        // https://www.irodori.jpf.go.jp/assets/data/wordlist_X.pdf
        // Product policy excludes standalone し/よ/く even where a dictionary accepts them.
        private val ones = listOf("いち" to 1, "に" to 2, "さん" to 3, "よん" to 4,
            "ご" to 5, "ろく" to 6, "なな" to 7, "しち" to 7, "はち" to 8, "きゅう" to 9)
        private val tens = listOf("じゅう" to 10, "にじゅう" to 20, "さんじゅう" to 30,
            "よんじゅう" to 40, "しじゅう" to 40, "ごじゅう" to 50, "ろくじゅう" to 60,
            "ななじゅう" to 70, "しちじゅう" to 70, "はちじゅう" to 80, "きゅうじゅう" to 90)
        private val hundreds = listOf("ひゃく" to 100, "にひゃく" to 200, "さんびゃく" to 300,
            "よんひゃく" to 400, "ごひゃく" to 500, "ろっぴゃく" to 600,
            "ななひゃく" to 700, "はっぴゃく" to 800, "きゅうひゃく" to 900)
        private val thousands = listOf("せん" to 1000, "いっせん" to 1000, "にせん" to 2000,
            "さんぜん" to 3000, "よんせん" to 4000, "ごせん" to 5000, "ろくせん" to 6000,
            "ななせん" to 7000, "はっせん" to 8000, "きゅうせん" to 9000)

        internal val cardinalCharacters: Set<Char> by lazy {
            (ones + tens + hundreds + thousands).flatMap { it.first.toList() }.toSet() + "ぜろれいまんおくちょうっ".toSet()
        }
        internal fun nonCardinalTailLength(reading: String): Int {
            val at = reading.indexOfFirst { it !in cardinalCharacters }
            return if (at < 0) 0 else reading.length - at
        }

        private fun section(text: String, terminal: Boolean = false): Long? {
            if (text.isEmpty()) return null
            var offset = 0
            var value = 0L
            var hasTens = false
            var hasOnes = false
            for (place in listOf(thousands, hundreds, tens, ones)) {
                val match = place.firstOrNull { text.startsWith(it.first, offset) }
                if (match != null) {
                    offset += match.first.length
                    value += match.second
                    if (place === tens) hasTens = true
                    if (place === ones) hasOnes = true
                }
            }
            if (terminal && hasTens && !hasOnes && text.substring(offset) in listOf("し", "よ", "く")) {
                value += if (text.substring(offset) == "く") 9 else 4
                offset = text.length
            }
            return value.takeIf { offset == text.length && it in 1L..9999L }
        }

        private fun cardinal(text: String, terminal: Boolean): Long? {
            if (text == "ぜろ" || text == "れい") return 0
            // Every accepted cardinal starts/ends with one of these characters. Reject
            // ordinary words before walking the numeric grammar; counter rules stay separate.
            if (text.isEmpty() || text.first() !in "いにさしよごろなはきじひせ" ||
                text.last() !in "ちにんごくなうしよ" || text.any { it !in 'ぁ'..'ゖ' }) return null
            var remaining = text
            var total = 0L
            for ((place, magnitude) in listOf("ちょう" to 1_000_000_000_000L,
                "おく" to 100_000_000L, "まん" to 10_000L)) {
                val at = remaining.indexOf(place)
                if (at < 0) continue
                var coefficient = remaining.substring(0, at)
                if (place == "ちょう") {
                    // Only the attested contextual forms are allowed for 1/8/tens before 兆.
                    // A valid isolated coefficient does not license いちちょう, etc.
                    if (listOf("いち", "はち", "じゅう").any(coefficient::endsWith)) return null
                    val ending = listOf("じゅっ" to "じゅう", "じっ" to "じゅう",
                        "いっ" to "いち", "はっ" to "はち").firstOrNull { coefficient.endsWith(it.first) }
                    if (ending != null) coefficient = coefficient.dropLast(ending.first.length) + ending.second
                }
                val value = section(coefficient) ?: return null
                total = Math.addExact(total, Math.multiplyExact(value, magnitude))
                remaining = remaining.substring(at + place.length)
            }
            if (remaining.isNotEmpty()) total = Math.addExact(total, section(remaining, terminal) ?: return null)
            return total.takeIf { it > 0 }
        }

        internal fun parseCounterReading(input: String): Long? = cardinal(input, false)

        fun parseReading(input: String): ValidatedNumber? = make(input, "", cardinal(input, true), input)

        fun parseDigits(input: String): ValidatedNumber? {
            if (input.isEmpty() || input.any { it !in '0'..'9' && it !in '０'..'９' }) return null
            val digits = input.map { if (it in '０'..'９') it - 0xFEE0 else it }.joinToString("")
            val value = digits.toLongOrNull() ?: return null
            return ValidatedNumber(value, input, NumberInputOrigin.DIGITS, "", digits)
        }

        fun parse(input: String): ValidatedNumber? {
            parseDigits(input)?.let { return it }
            parseReading(input)?.let { return it }
            if (input.endsWith("ふん") || input.endsWith("ぷん")) {
                for (split in input.indices.filter { input[it] == 'じ' }) {
                    val hour = parse(input.substring(0, split + 1))?.takeIf { it.counter == "時" } ?: continue
                    val minute = parse(input.substring(split + 1))?.takeIf { it.counter == "分" && it.value in 0..59 } ?: continue
                    return ValidatedNumber(hour.value * 60 + minute.value, input, NumberInputOrigin.READING,
                        "時分", "", hour.value.toInt() to minute.value.toInt(), clockParts = hour to minute)
                }
            }
            if (input == "ひとり") return make(input, "人", 1)
            if (input == "ふたり") return make(input, "人", 2)
            for ((suffix, counter) in listOf("えん" to "円", "にん" to "人", "ふん" to "分", "ぷん" to "分", "じ" to "時")) {
                if (!input.endsWith(suffix)) continue
                val stem = input.dropLast(suffix.length)
                var acceptedReading = stem
                val value = when (suffix) {
                    "えん" -> {
                        acceptedReading = if (stem.endsWith("よ")) stem.dropLast(1) + "よん" else stem
                        cardinal(acceptedReading, false)
                    }
                    "じ" -> {
                        val special = listOf("よ" to "よん", "く" to "きゅう")
                            .firstOrNull { stem in listOf(it.first, "じゅう" + it.first, "にじゅう" + it.first) }
                        val normalized = if (special == null) stem else stem.dropLast(special.first.length) + special.second
                        acceptedReading = normalized
                        val n = cardinal(normalized, false)
                        n?.takeIf { it in 0..29 && when (it % 10) {
                            4L -> stem.endsWith("よ")
                            7L -> stem.endsWith("しち")
                            9L -> stem.endsWith("く")
                            else -> true
                        } }
                    }
                    "にん" -> {
                        val normalized = if (stem.endsWith("よ")) stem.dropLast(1) + "よん" else stem
                        acceptedReading = normalized
                        cardinal(normalized, false)?.takeIf { it != 1L && it != 2L &&
                            (it % 10 != 4L || stem.endsWith("よ")) }
                    }
                    else -> {
                        acceptedReading = minuteCardinal(stem, suffix)
                        minute(stem, suffix, acceptedReading)
                    }
                }
                return make(input, counter, value, acceptedReading)
            }
            return null
        }

        private fun minuteCardinal(stem: String, suffix: String): String {
            if (suffix != "ぷん") return stem
            val contraction = listOf("じゅっ" to "じゅう", "じっ" to "じゅう",
                "ひゃっ" to "ひゃく", "びゃっ" to "びゃく", "ぴゃっ" to "ぴゃく",
                "いっ" to "いち", "ろっ" to "ろく", "はっ" to "はち").firstOrNull { stem.endsWith(it.first) }
            return if (contraction == null) stem else stem.dropLast(contraction.first.length) + contraction.second
        }

        private fun minute(stem: String, suffix: String, normalized: String): Long? {
            if (suffix == "ふん") {
                val value = cardinal(stem, false) ?: return null
                return value.takeIf { it == 0L || it % 10 in listOf(2L, 4L, 5L, 7L, 8L, 9L) }
            }
            if (normalized != stem) return cardinal(normalized, false)
            val value = cardinal(stem, false) ?: return null
            return value.takeIf { it % 10 in listOf(3L, 4L) || (it > 0 && it % 1000 == 0L &&
                listOf("せん", "ぜん", "まん").any(stem::endsWith)) }
        }

        fun parseAll(input: String, config: NumberCandidateConfig): List<ValidatedNumber> =
            if (input.length > UByte.MAX_VALUE.toInt()) emptyList() else config.parse(input)

        internal fun parseUncached(input: String, config: NumberCandidateConfig): List<ValidatedNumber> = buildList {
            parse(input)?.let(::add)
            var suffixBases: MutableMap<String, List<ValidatedNumber>>? = null
            for (suffix in QuantityRuntime.dictionary.suffixes) {
                if (!input.endsWith(suffix.reading) || input.length <= suffix.reading.length) continue
                val bases = suffixBases ?: HashMap<String, List<ValidatedNumber>>().also { suffixBases = it }
                val parsed = bases.getOrPut(suffix.reading) {
                    parseUncached(input.dropLast(suffix.reading.length), config)
                }
                for (base in parsed) {
                    if ((base.counter == suffix.base || suffix.base == "@registered" && base.customUnit != null && base.counter == base.customUnit.output) && base.clock == null) add(ValidatedNumber(
                        base.value, input, base.origin, base.counter + suffix.output, base.digits,
                        customUnit = base.customUnit, builtInCounter = base.builtInCounter,
                        baseCounter = base.baseCounter,
                        counterSuffixes = base.counterSuffixes + suffix,
                        cardinalReading = base.cardinalReading,
                        cardinalSource = base.cardinalSource, coreInputLength = base.coreInputLength,
                    ))
                }
            }
            for (counter in BuiltInCounter.matching(input)) {
                for (parsed in counter.parseWithReading(input)) add(ValidatedNumber(parsed.value, input, NumberInputOrigin.READING,
                    counter.output, parsed.value.toString(), builtInCounter = counter, cardinalReading = parsed.cardinalReading, cardinalSource = parsed.source))
            }
            for ((unit, values) in config.compiledUnits.match(input)) {
                for (parsed in values) add(ValidatedNumber(parsed.value, input, NumberInputOrigin.READING,
                    unit.output, parsed.value.toString(), customUnit = unit, cardinalReading = parsed.cardinalReading, cardinalSource = parsed.source))
            }
        }

        private fun make(input: String, counter: String, value: Long?, cardinalReading: String? = null): ValidatedNumber? =
            value?.let {
                val end = input.length - when (counter) { "円", "人", "分" -> 2; "時" -> 1; else -> 0 }
                val source = cardinalReading?.let { accepted -> CardinalSource(end, input.take(end).commonPrefixWith(accepted).length) }
                ValidatedNumber(it, input, NumberInputOrigin.READING, counter, it.toString(), cardinalReading = cardinalReading,
                    cardinalSource = source)
            }
    }
}
