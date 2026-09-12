package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.createValueBasedSymbolCandidates
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.addCommasToNumber

/** Shared eligibility for engine, history merge, display and automatic application. */
object NumberCandidatePolicy {
    private val numeric = Regex("[0-9０-９〇零一二三四五六七八九十百千万億兆京,，]+(?:時|人|分|円)?")
    private val derived = Regex("(?:[0-9０-９]+[⁰¹²³⁴⁵⁶⁷⁸⁹]+|[⁰¹²³⁴⁵⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉]+|[0-9０-９]+[:/][0-9０-９]+|[0-9０-９]+月[0-9０-９]+日|[0-9０-９]+時[0-9０-９]+分)")
    // Lexical words, not numeral readings. Only automatic dictionary words may use this list;
    // generated variants always require a proof. Keep paired readings, never blanket exceptions.
    private val lexical = setOf("じゅうぶん" to "十分", "いちぶん" to "一分", "まんいち" to "万一")
    private fun explicit(candidate: Candidate): Boolean = candidate.type in setOf(
        CANDIDATE_TYPE_USER_DICTIONARY, CANDIDATE_TYPE_USER_TEMPLATE, CANDIDATE_TYPE_TEXT_MACRO)

    fun eligible(input: String, candidate: Candidate, config: PredictionConfig = PredictionConfig()): Boolean {
        if (explicit(candidate) && candidate.conversionSegments.isEmpty()) return true
        // These have independent expression/unit/TeX grammars, not spoken-number generation.
        if (candidate.type in setOf(CANDIDATE_TYPE_CALCULATION, CANDIDATE_TYPE_UNIT_CONVERSION,
                CANDIDATE_TYPE_UTILITY_LITERAL, CANDIDATE_TYPE_FORMULA_UNICODE, CANDIDATE_TYPE_FORMULA_TEX)) return true
        candidate.temporalSource?.let { (reading, output) ->
            return reading == input && candidate.string == output && candidate.commitText == output
        }
        // A prefix numeral must not make an invalid whole reading look numeric (ごぜん → 5).
        // Complete sentence paths can still prove their individual numeric segments below.
        if (candidate.length.toInt() < input.length && (numeric.matches(candidate.string) ||
                derived.matches(candidate.string) || numberSymbol(candidate.string))) return false
        candidate.number?.let { proof ->
            if (candidate.generatedNumber && proof.origin == NumberInputOrigin.READING && !config.japaneseNumberCandidatesEnabled) return false
            if (candidate.generatedNumber && proof.reading != input) return false
            if (!candidate.generatedNumber && !(proof.reading.startsWith(input) || input.startsWith(proof.reading))) return false
            return validSurface(proof, candidate.string) && validSurface(proof, candidate.commitText)
        }
        val reading = candidate.yomi ?: input.take(candidate.length.toInt())
        if (candidate.conversionSegments.any { it.source == CandidateSource.USER_DICTIONARY }) {
            return candidate.conversionSegments.all { it.source == CandidateSource.USER_DICTIONARY ||
                eligibleSurface(it.reading ?: input.substring(it.inputStart, it.inputEnd), it.output) }
        }
        if (!validNumericRuns(input, candidate.conversionSegments)) return false
        val hasSegments = candidate.conversionSegments.isNotEmpty()
        if (!hasSegments && (unmappedDigits(reading, candidate.string) || unmappedDigits(reading, candidate.commitText))) return false
        if (!eligibleSurface(reading, candidate.string) || !eligibleSurface(reading, candidate.commitText)) return false
        // A complete counter may cross lattice nodes (e.g. よ + 時). Validate its complete
        // reading above; a standalone-node rule must not erase legitimate counter allomorphs.
        if (numeric.matches(candidate.string) || derived.matches(candidate.string)) return true
        // The complete interval was checked above, including its exact numeric value.
        // Rechecking よ + 時 as isolated nodes would erase a valid 4時 inside a sentence.
        return true
    }

    private fun validNumericRuns(input: String, segments: List<CandidateConversionSegment>): Boolean {
        var reading = ""
        var output = ""
        var hasNumber = false
        var hasLiteral = false
        var hasLexicalWord = false
        fun complete(): Boolean = !hasNumber ||
            ValidatedNumber.parse(reading)?.let { validSurface(it, output) } == true
        for (segment in segments) {
            val yomi = segment.reading ?: input.substring(segment.inputStart, segment.inputEnd)
            val numericOutput = (numeric.matches(segment.output) || derived.matches(segment.output) ||
                numberSymbol(segment.output)) && yomi to segment.output !in lexical
            val clockPrefix = segment.inputStart == 0 && segment.source == CandidateSource.SYSTEM &&
                (yomi to segment.output in setOf("ごぜん" to "午前", "ごご" to "午後"))
            // A guessed particle cannot rescue a malformed number. A real boundary follows
            // a complete number or an independent lexical word, never unknown kana fragments.
            val completeNumber = ValidatedNumber.parse(reading) != null
            val particleBoundary = segment.startsWithParticle &&
                (completeNumber || (!hasNumber && !hasLiteral && hasLexicalWord))
            val copulaBoundary = completeNumber && segment.source == CandidateSource.SYSTEM &&
                segment.output == yomi && yomi in setOf("です", "でした", "だ", "だった")
            if (particleBoundary || copulaBoundary || clockPrefix || segment.source == CandidateSource.USER_DICTIONARY) {
                if (!complete()) return false
                reading = ""
                output = ""
                hasNumber = false
                hasLiteral = false
                hasLexicalWord = false
            } else {
                // Audit the entire corresponding interval, including ordinary-word nodes:
                // 日本 + 5 must not turn the tail of にほんご into an isolated valid ご.
                reading += yomi
                output += segment.output
                hasNumber = hasNumber || numericOutput
                val numericFragment = ValidatedNumber.isNumericFragment(yomi)
                hasLiteral = hasLiteral || (segment.output == yomi && numericFragment) || segment.source == CandidateSource.UNKNOWN
                hasLexicalWord = hasLexicalWord || (!numericOutput &&
                    segment.source == CandidateSource.SYSTEM && !numericFragment)
            }
        }
        return complete()
    }

    private fun eligibleSurface(reading: String, surface: String): Boolean {
        if (reading to surface in lexical) return true
        if (!numeric.matches(surface) && !derived.matches(surface) && !numberSymbol(surface)) return true
        val proof = ValidatedNumber.parse(reading) ?: return false
        return validSurface(proof, surface)
    }

    private fun unmappedDigits(reading: String, surface: String): Boolean =
        surface != reading && surface.any { it in '0'..'9' || it in '０'..'９' } &&
            !numeric.matches(surface) && !derived.matches(surface)

    private fun numberSymbol(text: String): Boolean = text.length == 1 &&
        (text[0] in '❶'..'❿' || text[0] in '⑴'..'⒛' || text[0] in '⓪'..'⓿' || text[0] in '①'..'⑳' || text[0] in '㉑'..'㉟' || text[0] in '㊱'..'㊿' || text[0] in 'Ⅰ'..'Ⅻ' || text[0] in 'ⅰ'..'ⅻ')

    private fun validSurface(proof: ValidatedNumber, text: String): Boolean {
        if (text in proof.basicForms) return true
        val written = if (proof.counter.isEmpty()) text else text.takeIf { it.endsWith(proof.counter) }?.dropLast(proof.counter.length)
        // Direct decimal input keeps its leading zeros; alternative kanji spellings may
        // express the same value. Unknown dictionary spellings must not change that value.
        if (written != null && (proof.origin == NumberInputOrigin.READING ||
                written.any { it in "〇零一二三四五六七八九十百千万億兆京," }) && WrittenNumberValue.parse(written) == proof.value) return true
        if (proof.counter.isNotEmpty()) return false
        if ((proof.value >= 10000 && text == proof.value.convertToKanjiNotation()) ||
            (text.contains(',') && text == proof.digits.addCommasToNumber()) || text == proof.exponent()) return true
        val digits = proof.digits
        if (text == digits.map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("") ||
            text == digits.map { "₀₁₂₃₄₅₆₇₈₉"[it - '0'] }.joinToString("")) return true
        if (digits.length == 4) {
            val hour = digits.take(2); val minute = digits.takeLast(2)
            if (hour.toInt() in 0..29 && minute.toInt() in 0..59 &&
                text in listOf("$hour:$minute", "${hour}時${minute}分")) return true
        }
        if (digits.length in 3..4) {
            val month = digits.dropLast(2).toInt(); val day = digits.takeLast(2).toInt()
            if (month in 1..12 && day in 1..java.time.Month.of(month).maxLength() && text == "${month}月${day}日") return true
        }
        return createValueBasedSymbolCandidates(proof.value, proof.reading.length.toUByte()).any { it.string == text }
    }

    fun filter(input: String, candidates: List<Candidate>, config: PredictionConfig): List<Candidate> =
        candidates.filter { eligible(input, it, config) }.map { candidate ->
            // Preserve a proof even when a dictionary/history duplicate wins de-duplication.
            if (candidate.number != null || candidate.temporalSource != null || explicit(candidate)) candidate else {
                val reading = candidate.yomi ?: input.take(candidate.length.toInt())
                val proof = if (numeric.matches(candidate.string) || derived.matches(candidate.string) || numberSymbol(candidate.string))
                    ValidatedNumber.parse(reading) else null
                if (proof != null) candidate.copy(number = proof, generatedNumber = false, yomi = reading) else candidate
            }
        }

    /** Replaces only slots already occupied by the three basic representations. */
    fun order(input: String, candidates: List<Candidate>, order: NumberCandidateOrder): List<Candidate> {
        val groups = linkedMapOf<Pair<Long, String>, MutableList<Int>>()
        candidates.forEachIndexed { index, candidate ->
            if (explicit(candidate)) return@forEachIndexed
            val proof = candidate.number ?: ValidatedNumber.parse(candidate.yomi ?: input) ?: return@forEachIndexed
            if (candidate.string in proof.basicForms && candidate.commitText == candidate.string) {
                groups.getOrPut(proof.value to proof.counter) { mutableListOf() }.add(index)
            }
        }
        val result = candidates.toMutableList()
        for (slots in groups.values) {
            val sorted = slots.map(candidates::get).sortedBy { candidate ->
                val proof = candidate.number ?: ValidatedNumber.parse(candidate.yomi ?: input)!!
                order.indices.indexOf(proof.basicForms.indexOf(candidate.string))
            }
            slots.forEachIndexed { index, slot -> result[slot] = sorted[index] }
        }
        return result
    }
}
