package com.kazumaproject.quantity

/** A finite cardinal grammar. Offsets refer to the accepted reading, not the output spelling. */
object CardinalGrammar {
    enum class Role { DIGIT, SMALL_MAGNITUDE, LARGE_MAGNITUDE }
    data class Atom(val start: Int, val end: Int, val value: Long, val role: Role, val lexicalReading: String)
    data class Features(val smallProducts: Int, val largeProducts: Int, val additions: Int) {
        operator fun minus(other: Features) = Features(smallProducts - other.smallProducts,
            largeProducts - other.largeProducts, additions - other.additions)
        companion object { val ZERO = Features(0, 0, 0) }
    }
    data class Expression(val value: Long, val atoms: List<Atom>, val features: Features)
    private data class Term(val reading: String, val value: Int, val magnitude: Int, val coefficientEnd: Int,
                            val coefficientReading: String, val magnitudeReading: String)
    private val digitReadings = listOf("いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう")
    private val places = listOf(
        listOf("せん", "いっせん", "にせん", "さんぜん", "よんせん", "ごせん", "ろくせん", "ななせん", "はっせん", "きゅうせん")
            .mapIndexed { i, reading -> Term(reading, (if (i == 0) 1 else i) * 1000, 1000, reading.length - 2,
                digitReadings[(if (i == 0) 1 else i) - 1], "せん") },
        listOf("ひゃく", "にひゃく", "さんびゃく", "よんひゃく", "ごひゃく", "ろっぴゃく", "ななひゃく", "はっぴゃく", "きゅうひゃく")
            .mapIndexed { i, reading -> Term(reading, (i + 1) * 100, 100, reading.length - 3, digitReadings[i], "ひゃく") },
        listOf("じゅう", "にじゅう", "さんじゅう", "よんじゅう", "ごじゅう", "ろくじゅう", "ななじゅう", "はちじゅう", "きゅうじゅう", "しじゅう", "しちじゅう")
            .mapIndexed { i, reading -> val n = when (i) { 9 -> 4; 10 -> 7; else -> i + 1 }
                Term(reading, n * 10, 10, reading.length - 3, digitReadings[n - 1], "じゅう") },
        (digitReadings.mapIndexed { i, reading -> Term(reading, i + 1, 1, 0, reading, reading) } +
            Term("しち", 7, 1, 0, "しち", "しち")))

    /** Strict numeric lexemes only; POS alone also admits 二重 and 五獣. */
    fun surfaceValue(text: String): Long? {
        if (text.isEmpty()) return null
        val digits = text.map { when (it) {
            in '０'..'９' -> (it.code - 0xfee0).toChar()
            '零' -> '0'
            in "〇一二三四五六七八九" -> ('0'.code + "〇一二三四五六七八九".indexOf(it)).toChar()
            else -> it
        } }.joinToString("")
        if (digits.all { it in '0'..'9' }) return digits.toLongOrNull()
        when (text) { "万" -> return 10000; "億" -> return 100000000; "兆" -> return 1000000000000; "京" -> return 10000000000000000 }
        var total = 0L; var section = 0L; var digit = 0L
        var previousSmall = 10000L; var previousLarge = Long.MAX_VALUE
        for (char in text) {
            val n = "一二三四五六七八九".indexOf(char) + 1
            if (n > 0) {
                if (digit != 0L) return null
                digit = n.toLong(); continue
            }
            val unit = when (char) {
                '十' -> 10L; '百' -> 100L; '千' -> 1000L
                '万' -> 10000L; '億' -> 100000000L; '兆' -> 1000000000000L; '京' -> 10000000000000000L
                else -> return null
            }
            if (unit < 10000) {
                if (unit >= previousSmall) return null
                section += (if (digit == 0L) 1 else digit) * unit
                previousSmall = unit
            } else {
                if (unit >= previousLarge || section + digit == 0L) return null
                if (section + digit > (Long.MAX_VALUE - total) / unit) return null
                total += (section + digit) * unit
                previousLarge = unit; previousSmall = 10000; section = 0
            }
            digit = 0
        }
        if (section + digit > Long.MAX_VALUE - total) return null
        return total + section + digit
    }

    /** Canonical lexical structure for an authoritative atomic value, including custom Long values. */
        fun reading(value: Long): String {
            if (value < 0) return ""
            if (value == 0L) return "れい"
            val ones = listOf("", "いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう")
            fun section(n: Int): String = buildString {
                val thousands = n / 1000
                append(when (thousands) { 0 -> ""; 1 -> "せん"; 3 -> "さんぜん"; 8 -> "はっせん"; else -> ones[thousands] + "せん" })
                val hundreds = n / 100 % 10
                append(when (hundreds) { 0 -> ""; 1 -> "ひゃく"; 3 -> "さんびゃく"; 6 -> "ろっぴゃく"; 8 -> "はっぴゃく"; else -> ones[hundreds] + "ひゃく" })
                val tens = n / 10 % 10
                if (tens > 0) append((if (tens == 1) "" else ones[tens]) + "じゅう")
                append(ones[n % 10])
            }
            var remaining = value
            return buildString {
                for ((magnitude, suffix) in listOf(10_000_000_000_000_000L to "けい", 1_000_000_000_000L to "ちょう", 100_000_000L to "おく", 10_000L to "まん", 1L to "")) {
                    val coefficient = (remaining / magnitude).toInt()
                    remaining %= magnitude
                    if (coefficient == 0) continue
                    var part = section(coefficient)
                    if (suffix == "ちょう") {
                        listOf("いち" to "いっ", "はち" to "はっ", "じゅう" to "じゅっ")
                            .firstOrNull { part.endsWith(it.first) }?.let { part = part.dropLast(it.first.length) + it.second }
                    }
                    append(part); append(suffix)
                }
            }
        }

    fun parse(reading: String): Expression? {
        if (reading == "ぜろ" || reading == "れい") return Expression(0,
            listOf(Atom(0, reading.length, 0, Role.DIGIT, reading)), Features.ZERO)
        if (reading.isEmpty()) return null
        val atoms = ArrayList<Atom>()
        var smallProducts = 0
        var largeProducts = 0
        var additions = 0
        fun section(text: String, start: Int): Long? {
            var offset = 0
            var value = 0L
            var terms = 0
            for (place in places) {
                val term = place.firstOrNull { text.startsWith(it.reading, offset) } ?: continue
                val end = offset + term.reading.length
                if (term.magnitude == 1) atoms.add(Atom(start + offset, start + end, term.value.toLong(), Role.DIGIT, term.coefficientReading))
                else {
                    if (term.coefficientEnd > 0) {
                        atoms.add(Atom(start + offset, start + offset + term.coefficientEnd,
                            (term.value / term.magnitude).toLong(), Role.DIGIT, term.coefficientReading))
                        smallProducts++
                    }
                    atoms.add(Atom(start + offset + term.coefficientEnd, start + end, term.magnitude.toLong(),
                        Role.SMALL_MAGNITUDE, term.magnitudeReading))
                }
                offset = end; value += term.value; terms++
            }
            if (offset != text.length || terms == 0) return null
            additions += terms - 1
            return value
        }
        var offset = 0
        var value = 0L
        var groups = 0
        for ((word, magnitude) in listOf("けい" to 10_000_000_000_000_000L, "ちょう" to 1_000_000_000_000L, "おく" to 100_000_000L, "まん" to 10_000L)) {
            val at = reading.indexOf(word, offset)
            if (at < 0) continue
            val original = reading.substring(offset, at)
            val normalized = if (word == "ちょう") {
                if (listOf("いち", "はち", "じゅう").any(original::endsWith)) return null
                val contraction = listOf("じゅっ" to "じゅう", "じっ" to "じゅう", "いっ" to "いち", "はっ" to "はち")
                    .firstOrNull { original.endsWith(it.first) }
                if (contraction == null) original else original.dropLast(contraction.first.length) + contraction.second
            } else original
            val firstAtom = atoms.size
            val coefficient = section(normalized, offset) ?: return null
            if (normalized.length != original.length && atoms.size > firstAtom) {
                atoms[atoms.lastIndex] = atoms.last().copy(end = at)
            }
            atoms.add(Atom(at, at + word.length, magnitude, Role.LARGE_MAGNITUDE, word))
            if (coefficient > (Long.MAX_VALUE - value) / magnitude) return null
            value += coefficient * magnitude; largeProducts++; groups++
            offset = at + word.length
        }
        if (offset < reading.length) {
            val tail = section(reading.substring(offset), offset) ?: return null
            if (tail > Long.MAX_VALUE - value) return null
            value += tail; groups++
        }
        if (groups == 0) return null
        additions += groups - 1
        return Expression(value, atoms, Features(smallProducts, largeProducts, additions))
    }
}
