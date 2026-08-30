package com.kazumaproject.markdownhelperkeyboard.converter.utility

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class ParsedUnitExpression(
    val category: UnitCategory,
    val baseValue: BigDecimal,
    val terms: List<ParsedUnitTerm>,
)

data class ParsedUnitTerm(
    val value: BigDecimal,
    val unit: UnitDefinition,
)

class UnitExpressionParser(
    private val registry: UnitRegistry = UnitRegistry.Default,
    private val maxTerms: Int = 128,
) {
    private val context = MathContext(50, RoundingMode.HALF_EVEN)

    fun parse(
        expression: String,
        profile: RegionalUnitProfile = RegionalUnitProfile.JAPAN,
    ): ParsedUnitExpression? {
        val source = rewriteJapaneseTemperature(expression.trim())
        if (source.isEmpty()) return null
        var offset = 0
        var category: UnitCategory? = null
        var baseValue = BigDecimal.ZERO
        val terms = mutableListOf<ParsedUnitTerm>()

        while (true) {
            offset = skipSpaces(source, offset)
            if (offset >= source.length) break
            var sign = 1
            when (source[offset]) {
                '+' -> {
                    offset++
                    offset = skipSpaces(source, offset)
                }
                '-' -> {
                    sign = -1
                    offset++
                    offset = skipSpaces(source, offset)
                }
            }

            val number = readNumber(source, offset) ?: return null
            offset = number.end
            offset = skipSpaces(source, offset)
            val match = registry.matchAt(source, offset, category, profile) ?: return null
            offset += match.length

            val unit = match.definition
            if (category == null) category = unit.category
            if (unit.category != category) return null
            val signedValue = if (sign < 0) number.value.negate() else number.value
            terms += ParsedUnitTerm(signedValue, unit)
            if (terms.size > maxTerms) return null

            if (category == UnitCategory.TEMPERATURE && terms.size > 1) return null
            baseValue = if (category == UnitCategory.TEMPERATURE) {
                unit.toBase(signedValue, context)
            } else {
                baseValue.add(unit.toBase(signedValue, context), context)
            }

            offset = skipSpaces(source, offset)
            if (offset >= source.length) break
            val next = source[offset]
            if (next != '+' && next != '-' && !next.isDigit() && next != '.') return null
        }

        return ParsedUnitExpression(
            category = category ?: return null,
            baseValue = baseValue,
            terms = terms,
        )
    }

    private data class NumberRead(val value: BigDecimal, val end: Int)

    private fun readNumber(source: String, start: Int): NumberRead? {
        var offset = start
        var digits = 0
        var integerDigits = 0
        while (source.getOrNull(offset)?.isDigit() == true) {
            offset++
            digits++
            integerDigits++
        }
        if (integerDigits > 0) {
            var consumedGrouping = false
            while (source.getOrNull(offset) == ',') {
                val groupStart = offset + 1
                var groupLength = 0
                while (source.getOrNull(groupStart + groupLength)?.isDigit() == true) groupLength++
                if (groupLength != 3 || (!consumedGrouping && integerDigits !in 1..3)) break
                consumedGrouping = true
                offset += 4
                digits += 3
            }
        }
        if (source.getOrNull(offset) == '.') {
            offset++
            while (source.getOrNull(offset)?.isDigit() == true) {
                offset++
                digits++
            }
        }
        if (digits == 0 || digits > MAX_LITERAL_DIGITS) return null
        val exponentStart = offset
        if (source.getOrNull(offset) == 'e' || source.getOrNull(offset) == 'E') {
            var probe = offset + 1
            if (source.getOrNull(probe) == '+' || source.getOrNull(probe) == '-') probe++
            val exponentDigits = probe
            while (source.getOrNull(probe)?.isDigit() == true) probe++
            if (probe > exponentDigits) {
                if (probe - exponentDigits > 6) return null
                offset = probe
            } else {
                offset = exponentStart
            }
        }
        val value = source.substring(start, offset).replace(",", "").toBigDecimalOrNull()
            ?: return null
        return NumberRead(value, offset)
    }

    private fun rewriteJapaneseTemperature(source: String): String {
        val match = JAPANESE_TEMPERATURE.matchEntire(source) ?: return source
        val unit = if (match.groupValues[1] == "華氏") "°F" else "°C"
        return match.groupValues[2] + unit
    }

    private fun skipSpaces(source: String, start: Int): Int {
        var offset = start
        while (source.getOrNull(offset)?.isWhitespace() == true) offset++
        return offset
    }

    private companion object {
        const val MAX_LITERAL_DIGITS = 64
        val JAPANESE_TEMPERATURE = Regex(
            "^(華氏|摂氏)\\s*([+-]?(?:\\d+(?:,\\d{3})*|\\d*\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*度?$"
        )
    }
}
