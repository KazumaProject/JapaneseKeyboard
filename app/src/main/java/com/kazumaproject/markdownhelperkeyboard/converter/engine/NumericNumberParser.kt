package com.kazumaproject.markdownhelperkeyboard.converter.engine

import java.math.BigInteger

internal data class ParsedNumericNumber(
    val value: BigInteger,
    val sourceReading: String? = null,
    val sourceDigits: String? = null,
    val phonologicalFeatures: Set<NumericPhonologicalFeature> = emptySet(),
) {
    fun toValue(inputStart: Int, inputEnd: Int): NumericValue = NumericValue(
        value = value,
        sourceReading = sourceReading,
        sourceDigits = sourceDigits,
        phonologicalFeatures = phonologicalFeatures,
        inputStart = inputStart,
        inputEnd = inputEnd,
    )
}

internal data class NumericNumberPrefix(
    val end: Int,
    val number: ParsedNumericNumber,
)

/**
 * The one grammar for numeric values used by both the public number extensions and the numeric
 * expression parser.  It intentionally has no knowledge of counters or units.
 */
internal object NumericNumberParser {

    private enum class ReadingType {
        UNIT,
        SMALL_PLACE,
        BIG_PLACE,
    }

    private data class ReadingToken(
        val reading: String,
        val type: ReadingType,
        val value: BigInteger,
        val placeOrder: Int = 1,
    )

    private val zero = BigInteger.ZERO

    private val readings = listOf(
        ReadingToken("ぜろ", ReadingType.UNIT, BigInteger.ZERO),
        ReadingToken("れい", ReadingType.UNIT, BigInteger.ZERO),
        ReadingToken("いち", ReadingType.UNIT, BigInteger.ONE),
        ReadingToken("いっ", ReadingType.UNIT, BigInteger.ONE),
        ReadingToken("に", ReadingType.UNIT, BigInteger.valueOf(2)),
        ReadingToken("さん", ReadingType.UNIT, BigInteger.valueOf(3)),
        ReadingToken("し", ReadingType.UNIT, BigInteger.valueOf(4)),
        ReadingToken("よん", ReadingType.UNIT, BigInteger.valueOf(4)),
        ReadingToken("よ", ReadingType.UNIT, BigInteger.valueOf(4)),
        ReadingToken("ご", ReadingType.UNIT, BigInteger.valueOf(5)),
        ReadingToken("ろく", ReadingType.UNIT, BigInteger.valueOf(6)),
        ReadingToken("ろっ", ReadingType.UNIT, BigInteger.valueOf(6)),
        ReadingToken("なな", ReadingType.UNIT, BigInteger.valueOf(7)),
        ReadingToken("しち", ReadingType.UNIT, BigInteger.valueOf(7)),
        ReadingToken("はち", ReadingType.UNIT, BigInteger.valueOf(8)),
        ReadingToken("はっ", ReadingType.UNIT, BigInteger.valueOf(8)),
        ReadingToken("きゅう", ReadingType.UNIT, BigInteger.valueOf(9)),
        ReadingToken("きゅー", ReadingType.UNIT, BigInteger.valueOf(9)),
        ReadingToken("く", ReadingType.UNIT, BigInteger.valueOf(9)),
        ReadingToken("じゅう", ReadingType.SMALL_PLACE, BigInteger.TEN, 2),
        ReadingToken("じゅー", ReadingType.SMALL_PLACE, BigInteger.TEN, 2),
        ReadingToken("じゅっ", ReadingType.SMALL_PLACE, BigInteger.TEN, 2),
        ReadingToken("じっ", ReadingType.SMALL_PLACE, BigInteger.TEN, 2),
        ReadingToken("ひゃく", ReadingType.SMALL_PLACE, BigInteger.valueOf(100), 3),
        ReadingToken("ひゃっ", ReadingType.SMALL_PLACE, BigInteger.valueOf(100), 3),
        ReadingToken("びゃく", ReadingType.SMALL_PLACE, BigInteger.valueOf(100), 3),
        ReadingToken("びゃっ", ReadingType.SMALL_PLACE, BigInteger.valueOf(100), 3),
        ReadingToken("ぴゃく", ReadingType.SMALL_PLACE, BigInteger.valueOf(100), 3),
        ReadingToken("ぴゃっ", ReadingType.SMALL_PLACE, BigInteger.valueOf(100), 3),
        ReadingToken("せん", ReadingType.SMALL_PLACE, BigInteger.valueOf(1_000), 4),
        ReadingToken("ぜん", ReadingType.SMALL_PLACE, BigInteger.valueOf(1_000), 4),
        ReadingToken("まん", ReadingType.BIG_PLACE, BigInteger.valueOf(10_000), 5),
        ReadingToken("おく", ReadingType.BIG_PLACE, BigInteger.valueOf(100_000_000), 9),
        ReadingToken("おっ", ReadingType.BIG_PLACE, BigInteger.valueOf(100_000_000), 9),
        ReadingToken("ちょう", ReadingType.BIG_PLACE, BigInteger.valueOf(1_000_000_000_000L), 13),
    ).sortedByDescending { it.reading.length }

    fun parse(input: String): ParsedNumericNumber? {
        if (input.isEmpty()) return null
        if (isDigitSequence(input)) return parseDigits(input)

        var index = 0
        var total = zero
        var section: BigInteger? = null
        var smallPlaceOrder = -1
        var bigPlaceOrder = Int.MAX_VALUE
        var hasNumber = false
        var restrictedUnit: String? = null

        while (index < input.length) {
            val token = readings.firstOrNull { input.startsWith(it.reading, index) }
                ?: return null

            // 「よ」「く」は final-unit readings and 「し」 may only be followed by 十.
            when (restrictedUnit) {
                "よ", "く" -> return null
                "し" -> if (token.reading != "じゅう") return null
            }
            restrictedUnit = if (
                token.type == ReadingType.UNIT &&
                token.reading in setOf("し", "よ", "く")
            ) {
                token.reading
            } else {
                null
            }

            when (token.type) {
                ReadingType.UNIT -> {
                    if (hasNumber && token.value == zero) return null
                    if (section == zero || (section != null && section.mod(BigInteger.TEN) != zero)) {
                        return null
                    }
                    section = if (section == null) token.value else section.add(token.value)
                    hasNumber = true
                }

                ReadingType.SMALL_PLACE -> {
                    if (smallPlaceOrder > 1 && token.placeOrder >= smallPlaceOrder) return null
                    if (section == zero) return null

                    section = if (section == null) {
                        token.value
                    } else {
                        val unit = section.mod(BigInteger.TEN).max(BigInteger.ONE)
                        val base = section.divide(BigInteger.TEN).multiply(BigInteger.TEN)
                        base.add(unit.multiply(token.value))
                    }
                    smallPlaceOrder = token.placeOrder
                    hasNumber = true
                }

                ReadingType.BIG_PLACE -> {
                    if (token.placeOrder >= bigPlaceOrder || section == null || section <= zero) {
                        return null
                    }
                    total = total.add(section.multiply(token.value))
                    section = null
                    smallPlaceOrder = -1
                    bigPlaceOrder = token.placeOrder
                    hasNumber = true
                }
            }
            index += token.reading.length
        }

        if (!hasNumber) return null
        return ParsedNumericNumber(
            value = if (section == null) total else total.add(section),
            sourceReading = input,
            phonologicalFeatures = if ('っ' in input) {
                setOf(NumericPhonologicalFeature.GEMINATE)
            } else {
                emptySet()
            },
        )
    }

    /** Returns the longest contiguous ASCII/full-width digit prefix. */
    fun digitPrefixLength(input: String, start: Int = 0): Int {
        var index = start
        while (index < input.length && isDigit(input[index])) index++
        return index - start
    }

    /** Enumerates every valid Japanese-number prefix so the suffix parser can keep ambiguities. */
    fun parsePrefixes(input: String): List<NumericNumberPrefix> {
        if (input.isEmpty()) return emptyList()
        val digitLength = digitPrefixLength(input)
        if (digitLength > 0) {
            val raw = input.substring(0, digitLength)
            return listOf(NumericNumberPrefix(digitLength, parseDigits(raw)))
        }

        return (1..input.length).mapNotNull { end ->
            parse(input.substring(0, end))?.let { NumericNumberPrefix(end, it) }
        }
    }

    fun normalizeDigits(input: String): String = input.map { character ->
        if (character in '０'..'９') {
            (character.code - '０'.code + '0'.code).toChar()
        } else {
            character
        }
    }.joinToString("")

    fun isDigitSequence(input: String): Boolean =
        input.isNotEmpty() && input.all(::isDigit)

    private fun parseDigits(raw: String): ParsedNumericNumber = ParsedNumericNumber(
        value = BigInteger(normalizeDigits(raw)),
        sourceDigits = normalizeDigits(raw),
    )

    private fun isDigit(character: Char): Boolean = character in '0'..'9' || character in '０'..'９'
}
