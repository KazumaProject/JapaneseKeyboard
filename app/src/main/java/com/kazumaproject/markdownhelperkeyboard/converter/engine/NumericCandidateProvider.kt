package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.addCommasToNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertFullWidthNumbersToHalfWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.createValueBasedSymbolCandidates
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toSubscriptDigits
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toSuperscriptDigits

private const val NUMERIC_POS_ID_COUNTER_GENERIC: Short = 2011
private const val NUMERIC_POS_ID_COUNTER_TIME: Short = 2015
private const val NUMERIC_POS_ID_NUMBER_ARABIC: Short = 2044
private const val NUMERIC_POS_ID_NUMBER_SEPARATED: Short = 2045
private const val NUMERIC_POS_ID_NUMBER_KANJI: Short = 2046

private const val NUMERIC_TYPE_KANJI_MIXED: Byte = 17
private const val NUMERIC_TYPE_EXPONENT_OR_SUBSCRIPT: Byte = 20
private const val NUMERIC_TYPE_SUPERSCRIPT: Byte = 21
private const val NUMERIC_TYPE_FULL_WIDTH: Byte = 30
private const val NUMERIC_TYPE_HALF_WIDTH: Byte = 31
private const val NUMERIC_TYPE_KANJI: Byte = 32
private const val NUMERIC_TYPE_SEPARATED: Byte = 19

private const val NUMERIC_BASE_SCORE = 700

/**
 * A lexical definition for a Japanese counter.
 *
 * Counter readings belong here instead of in the conversion engine so the parser can apply the
 * same number + counter grammar to every conversion path.  The list is deliberately explicit:
 * accepting every arbitrary dictionary suffix would turn ordinary words into numbers.
 */
private data class CounterDefinition(
    val reading: String,
    val output: String,
    val timeLike: Boolean = false,
    val supportsHalf: Boolean = false,
    val rightId: Short = NUMERIC_POS_ID_COUNTER_GENERIC,
    val lexicalPriorityExceptions: Set<String> = emptySet(),
)

private data class ParsedNumber(
    val halfWidthDigits: String,
    val value: Long?,
)

private data class ParsedNumericExpression(
    val number: ParsedNumber,
    val counter: CounterDefinition? = null,
    val hasHalfModifier: Boolean = false,
)

private enum class NumericForm {
    HALF_WIDTH,
    FULL_WIDTH,
    KANJI,
    MIXED_KANJI,
    COMMA,
    EXPONENT,
    SUPERSCRIPT,
    SUBSCRIPT,
}

/**
 * Shared parser and formatter for numeric readings.
 *
 * The parser recognizes a complete numeric expression, not just a whole-string Japanese number.
 * This makes inputs such as "50えん" and "いちじかんはん" instances of the same grammar as
 * "さんにん".  Formatting is performed only after the semantic expression is parsed.
 */
internal object NumericCandidateProvider {

    private val counters = listOf(
        CounterDefinition(
            reading = "じかん",
            output = "時間",
            timeLike = true,
            supportsHalf = true,
            rightId = NUMERIC_POS_ID_COUNTER_TIME,
        ),
        CounterDefinition("しゅうかん", "週間", supportsHalf = true),
        CounterDefinition("かげつ", "か月", supportsHalf = true),
        CounterDefinition("びょう", "秒", timeLike = true, supportsHalf = true),
        CounterDefinition("ぷん", "分", timeLike = true, supportsHalf = true),
        CounterDefinition("ふん", "分", timeLike = true, supportsHalf = true),
        CounterDefinition("にち", "日", supportsHalf = true),
        CounterDefinition(
            reading = "ねん",
            output = "年",
            supportsHalf = true,
        ),
        CounterDefinition("えん", "円"),
        CounterDefinition("にん", "人"),
        CounterDefinition(
            reading = "じ",
            output = "時",
            timeLike = true,
            supportsHalf = true,
            rightId = NUMERIC_POS_ID_COUNTER_TIME,
        ),
        CounterDefinition(
            reading = "しゅう",
            output = "週",
            supportsHalf = true,
        ),
        CounterDefinition("がつ", "月"),
        CounterDefinition("こ", "個"),
        CounterDefinition("まい", "枚"),
        CounterDefinition("ぽん", "本"),
        CounterDefinition("ぼん", "本"),
        CounterDefinition(
            reading = "ほん",
            output = "本",
            lexicalPriorityExceptions = setOf("にほん"),
        ),
        CounterDefinition("さつ", "冊"),
        CounterDefinition("だい", "台"),
        CounterDefinition(
            reading = "かい",
            output = "回",
        ),
        CounterDefinition("さい", "歳"),
        CounterDefinition("けん", "件"),
    ).sortedByDescending { it.reading.length }

    fun isDigitSequence(input: String): Boolean =
        input.isNotEmpty() && input.all { it in '0'..'9' || it in '０'..'９' }

    /**
     * Returns whether a parsed numeric expression is safe to move ahead of ordinary lexical
     * candidates.  Some counter readings are unavoidable homophones (for example 「にほん」 can
     * mean 日本 or 二本); the numeric candidates remain available, but the lexical candidate keeps
     * the first position when the input is one of those readings.
     */
    fun shouldPrioritize(input: String): Boolean {
        if (isDigitSequence(input)) return true
        val expression = parse(input) ?: return false
        return expression.counter?.lexicalPriorityExceptions?.contains(input) != true
    }

    fun generate(
        input: String,
        notationPreference: NumericNotationPreference = NumericNotationPreference.HALF_WIDTH_FIRST,
        showSymbolCandidates: Boolean = true,
    ): List<Candidate> {
        val expression = parse(input) ?: return emptyList()
        return render(
            input = input,
            expression = expression,
            notationPreference = notationPreference,
            showSymbolCandidates = showSymbolCandidates,
        )
    }

    private fun parse(input: String): ParsedNumericExpression? {
        if (input.isEmpty()) return null

        if (isDigitSequence(input)) {
            return ParsedNumericExpression(parseNumber(input))
        }

        input.toNumber()?.let { return ParsedNumericExpression(parseNumber(it.second)) }

        val (body, hasHalfModifier) = when {
            input.endsWith("はん") -> input.dropLast(2) to true
            input.endsWith("半") -> input.dropLast(1) to true
            else -> input to false
        }

        for (counter in counters) {
            if (!body.endsWith(counter.reading)) continue
            if (hasHalfModifier && !counter.supportsHalf) continue

            val numberReading = body.dropLast(counter.reading.length)
            val number = parseNumberReadingForCounter(numberReading) ?: continue
            return ParsedNumericExpression(
                number = number,
                counter = counter,
                hasHalfModifier = hasHalfModifier,
            )
        }

        return null
    }

    private fun parseNumber(raw: String): ParsedNumber {
        val halfWidthDigits = raw.convertFullWidthNumbersToHalfWidth()
        return ParsedNumber(
            halfWidthDigits = halfWidthDigits,
            value = halfWidthDigits.toLongOrNull(),
        )
    }

    private fun parseNumberReadingForCounter(reading: String): ParsedNumber? {
        if (reading.isEmpty()) return null
        if (isDigitSequence(reading)) return parseNumber(reading)

        // Bare 「し」 and a final 「し」 after a place value are highly ambiguous before a
        // counter (しえん, しじ, じゅうしにん).  The canonical alternatives remain usable:
        // よんえん, よにん, しじゅうにん, etc.
        if (reading.endsWith("し")) return null

        val number = reading.toNumber() ?: return null
        return parseNumber(number.second)
    }

    private fun render(
        input: String,
        expression: ParsedNumericExpression,
        notationPreference: NumericNotationPreference,
        showSymbolCandidates: Boolean,
    ): List<Candidate> {
        val forms = if (expression.counter == null) {
            renderNumberOnlyForms(expression.number, notationPreference)
        } else {
            renderQuantityForms(expression, notationPreference)
        }

        val candidates = ArrayList<Candidate>(forms.size + 8)
        val seen = LinkedHashSet<String>()
        forms.forEachIndexed { index, (form, string) ->
            if (!seen.add(string)) return@forEachIndexed
            candidates += createCandidate(
                input = input,
                expression = expression,
                form = form,
                string = string,
                rank = index,
            )
        }

        if (expression.counter == null && expression.number.value != null) {
            val value = expression.number.value
            val halfWidthDigits = expression.number.halfWidthDigits
            val inputLength = input.length.toUByte()
            if (halfWidthDigits.isNotEmpty()) {
                val exponent = value.toNumberExponentOrNull()
                if (exponent != null && seen.add(exponent)) {
                    candidates += Candidate(
                        string = exponent,
                        type = NUMERIC_TYPE_EXPONENT_OR_SUBSCRIPT,
                        length = inputLength,
                        score = NUMERIC_BASE_SCORE + candidates.size,
                        leftId = NUMERIC_POS_ID_NUMBER_ARABIC,
                        rightId = NUMERIC_POS_ID_NUMBER_ARABIC,
                    )
                }
            }

            listOf(
                halfWidthDigits.toSuperscriptDigits() to NUMERIC_TYPE_SUPERSCRIPT,
                halfWidthDigits.toSubscriptDigits() to NUMERIC_TYPE_EXPONENT_OR_SUBSCRIPT,
            ).forEach { (string, type) ->
                if (seen.add(string)) {
                    candidates += Candidate(
                        string = string,
                        type = type,
                        length = inputLength,
                        score = NUMERIC_BASE_SCORE + candidates.size,
                        leftId = NUMERIC_POS_ID_NUMBER_ARABIC,
                        rightId = NUMERIC_POS_ID_NUMBER_ARABIC,
                    )
                }
            }

            if (showSymbolCandidates) {
                createValueBasedSymbolCandidates(value, inputLength).forEach { candidate ->
                    if (seen.add(candidate.string)) candidates += candidate
                }
            }
        }

        return candidates
    }

    private fun renderNumberOnlyForms(
        number: ParsedNumber,
        notationPreference: NumericNotationPreference,
    ): List<Pair<NumericForm, String>> {
        val available = linkedMapOf<NumericForm, String>()
        addPrimaryForms(available, number, notationPreference)
        return available.entries.map { it.key to it.value }
    }

    private fun renderQuantityForms(
        expression: ParsedNumericExpression,
        notationPreference: NumericNotationPreference,
    ): List<Pair<NumericForm, String>> {
        val available = linkedMapOf<NumericForm, String>()
        addPrimaryForms(
            target = available,
            number = expression.number,
            notationPreference = notationPreference,
            suffix = expression.counter?.output.orEmpty() +
                if (expression.hasHalfModifier) "半" else "",
        )
        return available.entries.map { it.key to it.value }
    }

    private fun addPrimaryForms(
        target: LinkedHashMap<NumericForm, String>,
        number: ParsedNumber,
        notationPreference: NumericNotationPreference,
        suffix: String = "",
    ) {
        val orderedForms = when (notationPreference) {
            NumericNotationPreference.HALF_WIDTH_FIRST -> listOf(
                NumericForm.HALF_WIDTH,
                NumericForm.FULL_WIDTH,
                NumericForm.KANJI,
                NumericForm.MIXED_KANJI,
                NumericForm.COMMA,
            )

            NumericNotationPreference.FULL_WIDTH_FIRST -> listOf(
                NumericForm.FULL_WIDTH,
                NumericForm.HALF_WIDTH,
                NumericForm.KANJI,
                NumericForm.MIXED_KANJI,
                NumericForm.COMMA,
            )

            NumericNotationPreference.KANJI_FIRST -> listOf(
                NumericForm.KANJI,
                NumericForm.HALF_WIDTH,
                NumericForm.FULL_WIDTH,
                NumericForm.MIXED_KANJI,
                NumericForm.COMMA,
            )
        }

        orderedForms.forEach { form ->
            val rendered = when (form) {
                NumericForm.HALF_WIDTH -> number.halfWidthDigits
                NumericForm.FULL_WIDTH -> number.halfWidthDigits.toFullWidthDigits()
                NumericForm.KANJI -> number.value?.toKanji()
                NumericForm.MIXED_KANJI -> number.value?.convertToKanjiNotation()
                NumericForm.COMMA -> number.value?.let { number.halfWidthDigits.addCommasToNumber() }
                else -> null
            } ?: return@forEach

            target.putIfAbsent(form, rendered + suffix)
        }
    }

    private fun createCandidate(
        input: String,
        expression: ParsedNumericExpression,
        form: NumericForm,
        string: String,
        rank: Int,
    ): Candidate {
        val counter = expression.counter
        val timeLike = counter?.timeLike == true
        val type = when (form) {
            NumericForm.HALF_WIDTH -> if (timeLike) CANDIDATE_TYPE_TIME else NUMERIC_TYPE_HALF_WIDTH
            NumericForm.FULL_WIDTH -> NUMERIC_TYPE_FULL_WIDTH
            NumericForm.KANJI -> NUMERIC_TYPE_KANJI
            NumericForm.MIXED_KANJI -> NUMERIC_TYPE_KANJI_MIXED
            NumericForm.COMMA -> NUMERIC_TYPE_SEPARATED
            NumericForm.EXPONENT -> NUMERIC_TYPE_EXPONENT_OR_SUBSCRIPT
            NumericForm.SUPERSCRIPT -> NUMERIC_TYPE_SUPERSCRIPT
            NumericForm.SUBSCRIPT -> NUMERIC_TYPE_EXPONENT_OR_SUBSCRIPT
        }
        val rightId = when {
            counter == null -> when (form) {
                NumericForm.COMMA -> NUMERIC_POS_ID_NUMBER_SEPARATED
                NumericForm.KANJI, NumericForm.MIXED_KANJI -> NUMERIC_POS_ID_NUMBER_KANJI
                else -> NUMERIC_POS_ID_NUMBER_ARABIC
            }

            else -> counter.rightId
        }
        val leftId = when (form) {
            NumericForm.COMMA -> NUMERIC_POS_ID_NUMBER_SEPARATED
            NumericForm.KANJI, NumericForm.MIXED_KANJI -> NUMERIC_POS_ID_NUMBER_KANJI
            else -> NUMERIC_POS_ID_NUMBER_ARABIC
        }
        return Candidate(
            string = string,
            type = type,
            length = input.length.toUByte(),
            score = NUMERIC_BASE_SCORE + rank,
            leftId = leftId,
            rightId = rightId,
        )
    }

    private fun Long.toNumberExponentOrNull(): String? {
        if (this < 100_000_000L) return null
        return this.toString().length.minus(1).let { exponent ->
            val superscripts = mapOf(
                '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
                '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            )
            "10${exponent.toString().map { superscripts[it] ?: it }.joinToString("")}"
        }
    }

    private fun String.toFullWidthDigits(): String = map { character ->
        if (character in '0'..'9') (character.code + 0xFEE0).toChar() else character
    }.joinToString("")
}
