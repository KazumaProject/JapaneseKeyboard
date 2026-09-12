package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.displayExponent
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji

enum class NumberInputOrigin { READING, DIGITS }

/** Only the complete grammar below can construct this proof; never re-decode its reading. */
class ValidatedNumber private constructor(
    val value: Long,
    val reading: String,
    val origin: NumberInputOrigin,
    val counter: String,
    val digits: String,
) {
    override fun equals(other: Any?): Boolean = other is ValidatedNumber &&
        value == other.value && reading == other.reading && origin == other.origin && counter == other.counter && digits == other.digits
    override fun hashCode(): Int = listOf(value, reading, origin, counter, digits).hashCode()

    val fullWidth: String get() = digits.map { it + 0xFEE0 }.joinToString("")
    val basicForms: List<String> get() = listOf(digits + counter, fullWidth + counter, value.toKanji() + counter)

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
            "よんじゅう" to 40, "ごじゅう" to 50, "ろくじゅう" to 60,
            "ななじゅう" to 70, "しちじゅう" to 70, "はちじゅう" to 80, "きゅうじゅう" to 90)
        private val hundreds = listOf("ひゃく" to 100, "にひゃく" to 200, "さんびゃく" to 300,
            "よんひゃく" to 400, "ごひゃく" to 500, "ろっぴゃく" to 600,
            "ななひゃく" to 700, "はっぴゃく" to 800, "きゅうひゃく" to 900)
        private val thousands = listOf("せん" to 1000, "いっせん" to 1000, "にせん" to 2000,
            "さんぜん" to 3000, "よんせん" to 4000, "ごせん" to 5000, "ろくせん" to 6000,
            "ななせん" to 7000, "はっせん" to 8000, "きゅうせん" to 9000)

        // Recognition for auditing split paths only; this NEVER supplies a numeric value.
        // Include incomplete/sound-changed forms so labelling a broken unit lexical cannot
        // bypass the complete grammar. Particle nodes delimit a sentence's numeric span.
        private val fragments by lazy {
            (ones + tens + hundreds + thousands).map { it.first } + listOf(
                "ぜろ", "れい", "じゅう", "ひゃく", "せん", "まん", "おく", "ちょう",
                "ぜん", "びゃく", "ぴゃく", "いっ", "ろっ", "はっ", "じゅっ", "じっ",
                "ひゃっ", "びゃっ", "ぴゃっ", "おっ", "じゅー", "きゅー", "し", "よ", "く",
                "にん", "えん", "ふん", "ぷん", "じ",
            )
        }
        internal fun isNumericFragment(reading: String): Boolean {
            if (reading.isEmpty()) return false
            if (fragments.any { it.startsWith(reading) }) return true
            // Mark even composite malformed spans (e.g. ぜんご) as one suspect run.
            // This lexer has no values and can ONLY cause rejection by the strict parser.
            val reachable = BooleanArray(reading.length + 1)
            reachable[0] = true
            for (offset in reading.indices) if (reachable[offset]) {
                if (fragments.any { it.startsWith(reading.substring(offset)) }) return true
                for (fragment in fragments) if (reading.startsWith(fragment, offset)) {
                    reachable[offset + fragment.length] = true
                }
            }
            return reachable[reading.length]
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
            if (terminal && hasTens && !hasOnes && text.substring(offset) in listOf("し", "く")) {
                value += if (text.substring(offset) == "し") 4 else 9
                offset = text.length
            }
            return value.takeIf { offset == text.length && it in 1L..9999L }
        }

        private fun cardinal(text: String, terminal: Boolean): Long? {
            if (text == "ぜろ" || text == "れい") return 0
            if (text.isEmpty() || text.any { it !in 'ぁ'..'ゖ' }) return null
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

        fun parseReading(input: String): ValidatedNumber? = make(input, "", cardinal(input, true))

        fun parseDigits(input: String): ValidatedNumber? {
            if (input.isEmpty() || input.any { it !in '0'..'9' && it !in '０'..'９' }) return null
            val digits = input.map { if (it in '０'..'９') it - 0xFEE0 else it }.joinToString("")
            val value = digits.toLongOrNull() ?: return null
            return ValidatedNumber(value, input, NumberInputOrigin.DIGITS, "", digits)
        }

        fun parse(input: String): ValidatedNumber? {
            parseDigits(input)?.let { return it }
            parseReading(input)?.let { return it }
            if (input == "ひとり") return make(input, "人", 1)
            if (input == "ふたり") return make(input, "人", 2)
            for ((suffix, counter) in listOf("えん" to "円", "にん" to "人", "ふん" to "分", "ぷん" to "分", "じ" to "時")) {
                if (!input.endsWith(suffix)) continue
                val stem = input.dropLast(suffix.length)
                val value = when (suffix) {
                    "えん" -> cardinal(stem, false)
                    "じ" -> {
                        val special = listOf("よ" to "よん", "く" to "きゅう")
                            .firstOrNull { stem in listOf(it.first, "じゅう" + it.first, "にじゅう" + it.first) }
                        val normalized = if (special == null) stem else stem.dropLast(special.first.length) + special.second
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
                        cardinal(normalized, false)?.takeIf { it != 1L && it != 2L &&
                            (it % 10 != 4L || stem.endsWith("よ")) }
                    }
                    else -> minute(stem, suffix)
                }
                return make(input, counter, value)
            }
            return null
        }

        private fun minute(stem: String, suffix: String): Long? {
            if (suffix == "ふん") {
                val value = cardinal(stem, false) ?: return null
                return value.takeIf { it == 0L || it % 10 in listOf(2L, 4L, 5L, 7L, 8L, 9L) }
            }
            val contraction = listOf("じゅっ" to "じゅう", "じっ" to "じゅう",
                "ひゃっ" to "ひゃく", "びゃっ" to "びゃく", "ぴゃっ" to "ぴゃく",
                "いっ" to "いち", "ろっ" to "ろく", "はっ" to "はち").firstOrNull { stem.endsWith(it.first) }
            if (contraction != null) return cardinal(stem.dropLast(contraction.first.length) + contraction.second, false)
            val value = cardinal(stem, false) ?: return null
            return value.takeIf { it % 10 in listOf(3L, 4L) || (it > 0 && it % 1000 == 0L &&
                listOf("せん", "ぜん", "まん").any(stem::endsWith)) }
        }

        private fun make(input: String, counter: String, value: Long?): ValidatedNumber? =
            value?.let { ValidatedNumber(it, input, NumberInputOrigin.READING, counter, it.toString()) }
    }
}
