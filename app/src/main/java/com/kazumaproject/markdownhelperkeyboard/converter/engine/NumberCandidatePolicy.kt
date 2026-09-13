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
    // These ranges are the numeral/suffix/prefix classes in the shipped id.def.
    // A system lexical entry is evidence for its own reading/output, never a generated copy.
    private fun independentTextSegment(segment: CandidateConversionSegment): Boolean =
        segment.nonNumericSource?.let { it.first == segment.reading && it.second == segment.output } == true

    private fun lexicalEntry(segment: CandidateConversionSegment): Boolean =
        independentTextSegment(segment) || (segment.source == CandidateSource.SYSTEM && segment.leftId != null &&
            segment.leftId.toInt() !in 2043..2055)

    private fun directWidth(input: String, output: String): Boolean {
        if (input.isEmpty() || input.any { it !in '0'..'9' && it !in '０'..'９' }) return false
        fun half(s: String) = s.map { if (it in '０'..'９') it - 0xFEE0 else it }.joinToString("")
        return half(input) == half(output)
    }

    private fun explicit(candidate: Candidate): Boolean = candidate.type in setOf(
        CANDIDATE_TYPE_USER_DICTIONARY, CANDIDATE_TYPE_USER_TEMPLATE, CANDIDATE_TYPE_TEXT_MACRO)

    /** Called only at dictionary lookup sites; copies cannot change the recorded output. */
    fun dictionaryCandidate(candidate: Candidate): Candidate {
        val reading = candidate.yomi ?: return candidate
        return candidate.copy(conversionSegments = listOf(CandidateConversionSegment(
            0, reading.length, candidate.string, reading,
            leftId = candidate.leftId, rightId = candidate.rightId,
        )))
    }

    fun eligible(input: String, candidate: Candidate, config: PredictionConfig = PredictionConfig()): Boolean {
        if (explicit(candidate) && candidate.conversionSegments.isEmpty()) return true
        // These have independent expression/unit/TeX grammars, not spoken-number generation.
        if (candidate.type in setOf(CANDIDATE_TYPE_CALCULATION, CANDIDATE_TYPE_UNIT_CONVERSION,
                CANDIDATE_TYPE_UTILITY_LITERAL, CANDIDATE_TYPE_FORMULA_UNICODE, CANDIDATE_TYPE_FORMULA_TEX)) return true
        candidate.temporalSource?.let { (reading, output) ->
            return reading == input && candidate.string == output && candidate.commitText == output
        }
        candidate.nonNumericSource?.let { (reading, output) ->
            return candidate.number == null && !candidate.generatedNumber && reading == input &&
                candidate.string == output && candidate.commitText == output
        }
        if (candidate.number == null && candidate.string == input && candidate.commitText == input) return true
        if (directWidth(input, candidate.string) && candidate.commitText == candidate.string) return true
        // A prefix numeral must not make an invalid whole reading look numeric (ごぜん → 5).
        // Complete sentence paths can still prove their individual numeric segments below.
        if (candidate.length.toInt() < input.length && (numeric.matches(candidate.string) ||
                derived.matches(candidate.string) || numberSymbol(candidate.string)) &&
            candidate.conversionSegments.none { lexicalEntry(it) && it.output == candidate.string &&
                (it.output.length > 1 || !ValidatedNumber.isNumericFragment(it.reading.orEmpty())) }) return false
        candidate.number?.let { proof ->
            if (candidate.generatedNumber && proof.origin == NumberInputOrigin.READING && !config.japaneseNumberCandidatesEnabled) return false
            if (candidate.generatedNumber && proof.reading != input) return false
            if (proof.reading != input) return false
            return candidate.commitText == candidate.string && validSurface(proof, candidate.string)
        }
        val reading = candidate.yomi ?: input.take(candidate.length.toInt())
        val segments = candidate.conversionSegments
        val hasSegments = segments.isNotEmpty()
        val lexicalCandidate = segments.size == 1 && lexicalEntry(segments.single()) &&
            segments.single().reading == reading && segments.single().output == candidate.string &&
            (reading == input || candidate.string.length > 1 || !ValidatedNumber.isNumericFragment(reading))
        val numericCandidate = !lexicalCandidate && (containsNumericText(reading, candidate.string) || containsNumericText(reading, candidate.commitText) ||
            segments.any { !lexicalEntry(it) && containsNumericText(it.reading ?: reading, it.output) })
        if (candidate.commitText != candidate.string &&
            (numericCandidate || containsNumericText(reading, candidate.commitText))) return false
        if (!numericCandidate && !lexicalCandidate &&
            segments.none { it.source == CandidateSource.USER_DICTIONARY }) return true
        if (numericCandidate && reading != input) return false
        if (hasSegments) {
            var end = 0
            for (segment in segments) {
                if (segment.inputStart != end || segment.inputEnd !in segment.inputStart..reading.length) return false
                if (numericCandidate && segment.reading != null &&
                    segment.reading != reading.substring(segment.inputStart, segment.inputEnd)) return false
                end = segment.inputEnd
            }
            if (end != reading.length) return false
            // A path proves only its own output. Copies must not borrow its proof for a
            // different display/commit string, including paths containing user entries.
            if (segments.joinToString("") { it.output } != candidate.string) return false
            if (segments.any { it.source == CandidateSource.USER_DICTIONARY } &&
                candidate.commitText != candidate.string) return false
            if (lexicalCandidate) return true
            if (!validNumericRuns(reading, segments)) return false
            return true
        } else if (unmappedDigits(reading, candidate.string) || unmappedDigits(reading, candidate.commitText)) {
            return false
        }
        return eligibleSurface(reading, candidate.string) && eligibleSurface(reading, candidate.commitText)
    }

    private fun validNumericRuns(input: String, segments: List<CandidateConversionSegment>): Boolean {
        var reading = ""
        var output = ""
        var hasNumber = false
        var leftWord = false
        var permitsBareNumber = true
        fun complete(withDictionaryCounter: Boolean = false): Boolean {
            if (!hasNumber) return true
            if (directWidth(reading, output)) return true
            val proof = ValidatedNumber.parse(reading) ?: if (withDictionaryCounter) {
                // Dictionary counter inflections (一 + 本, 六 + 個, 八 + 回) do not
                // extend the grammar used to generate standalone numerical candidates.
                val ending = listOf("じゅっ" to "じゅう", "じっ" to "じゅう", "ひゃっ" to "ひゃく",
                    "びゃっ" to "びゃく", "ぴゃっ" to "ぴゃく", "いっ" to "いち", "ろっ" to "ろく", "はっ" to "はち")
                    .firstOrNull { reading.endsWith(it.first) } ?: return false
                ValidatedNumber.parseReading(reading.dropLast(ending.first.length) + ending.second) ?: return false
            } else return false
            return validSurface(proof, output) &&
                (!leftWord || permitsBareNumber || withDictionaryCounter || proof.counter.isNotEmpty())
        }
        fun clear() { reading = ""; output = ""; hasNumber = false }
        for (segment in segments) {
            val yomi = segment.reading ?: input.substring(segment.inputStart, segment.inputEnd)
            val id = segment.leftId?.toInt()
            val trusted = lexicalEntry(segment)
            val counter = trusted && id in 2011..2018
            val supportedCounter = trusted && (counter || hasNumber) && segment.output in setOf("時", "人", "分", "円")
            val fragment = ValidatedNumber.isNumericFragment(yomi)
            // Numeric fragments stay together even if the dictionary calls 全 a prefix.
            // A complete lexical numeral spelling (一時/万人/七五三) is an ordinary word.
            val numeralAtom = fragment && segment.output.length == 1 && numeric.matches(segment.output)
            val lexicalNumeral = trusted && numeric.matches(segment.output) && !numeralAtom
            val boundary = independentTextSegment(segment) || segment.source == CandidateSource.USER_DICTIONARY ||
                (trusted && !supportedCounter && !numeralAtom && (!fragment || lexicalNumeral ||
                    (segment.output != yomi && id !in 2596..2642))) ||
                (segment.startsWithParticle && (ValidatedNumber.parse(reading) != null ||
                    (!hasNumber && !ValidatedNumber.isNumericFragment(reading)))) ||
                (segment.output == yomi && yomi in setOf("です", "でした", "だ", "だった") &&
                    ValidatedNumber.parse(reading) != null)
            if (boundary) {
                if (!complete(withDictionaryCounter = counter && !supportedCounter)) return false
                clear()
                leftWord = !segment.startsWithParticle
                permitsBareNumber = id in 2639..2642 || id == 2628 || id == 2630 || id in 2643..2656 ||
                    segment.startsWithParticle
                continue
            }
            reading += yomi
            output += segment.output
            hasNumber = hasNumber || containsNumericText(yomi, segment.output)
        }
        return complete()
    }

    private fun eligibleSurface(reading: String, surface: String): Boolean {
        // Ungenerated kanji words with a non-numeral reading are lexical, including history.
        // Suspect numeric fragments still require an exact dictionary segment or numeric proof.
        if (!surface.any { it in '0'..'9' || it in '０'..'９' } &&
            numeric.matches(surface) && !ValidatedNumber.isNumericFragment(reading)) return true
        if (!numeric.matches(surface) && !derived.matches(surface) && !numberSymbol(surface)) return true
        val proof = ValidatedNumber.parse(reading) ?: return false
        return validSurface(proof, surface)
    }

    internal fun needsDictionaryEvidence(reading: String, output: String): Boolean =
        containsNumericText(reading, output)

    private fun containsNumericText(reading: String, surface: String): Boolean =
        containsNumericText(surface) || (surface.any { it in "〇零一二三四五六七八九十百千万億兆京" } &&
            ValidatedNumber.isNumericFragment(reading))

    private fun containsNumericText(surface: String): Boolean =
        numeric.matches(surface) || derived.matches(surface) || numberSymbol(surface) ||
            surface.any { it in '0'..'9' || it in '０'..'９' || it in "⁰¹²³⁴⁵⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉" || numberSymbol(it.toString()) }

    private fun unmappedDigits(reading: String, surface: String): Boolean =
        surface != reading && containsNumericText(reading, surface) &&
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
            if (candidate.number != null || candidate.temporalSource != null || candidate.nonNumericSource != null || explicit(candidate) ||
                (candidate.conversionSegments.size == 1 && lexicalEntry(candidate.conversionSegments.single()))) candidate else {
                val reading = candidate.yomi ?: input.take(candidate.length.toInt())
                val proof = if (numeric.matches(candidate.string) || derived.matches(candidate.string) || numberSymbol(candidate.string))
                    ValidatedNumber.parse(reading) else null
                if (proof != null && validSurface(proof, candidate.string)) candidate.copy(number = proof, generatedNumber = false, yomi = reading) else candidate
            }
        }

    /** Replaces only slots already occupied by the three basic representations. */
    fun order(input: String, candidates: List<Candidate>, order: NumberCandidateOrder): List<Candidate> {
        if (input.isNotEmpty() && input.all { it in '0'..'9' || it in '０'..'９' } &&
            ValidatedNumber.parseDigits(input) == null) {
            val half = input.map { if (it in '０'..'９') it - 0xFEE0 else it }.joinToString("")
            val forms = listOf(half, half.map { it + 0xFEE0 }.joinToString(""))
            val slots = candidates.indices.filter { candidates[it].string in forms &&
                candidates[it].commitText == candidates[it].string && !explicit(candidates[it]) }
            val sorted = slots.map(candidates::get).sortedBy { order.indices.indexOf(forms.indexOf(it.string)) }
            return candidates.toMutableList().apply { slots.forEachIndexed { index, slot -> this[slot] = sorted[index] } }
        }
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
