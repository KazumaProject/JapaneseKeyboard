package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_TEMPLATE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TEXT_MACRO
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.addCommasToNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.createValueBasedSymbolCandidates

/** Generates numeric additions only. Dictionary candidates are never validated or filtered here. */
object NumberCandidateGenerator {
    fun generate(input: String, config: PredictionConfig): List<Candidate> {
        val proofs = ValidatedNumber.parseAll(input, config.numberCandidateConfig)
            .filter { it.origin == NumberInputOrigin.DIGITS || config.japaneseNumberCandidatesEnabled }
        if (proofs.isEmpty() && input.isNotEmpty() && input.all { it in '0'..'9' || it in '０'..'９' }) {
            // Width conversion is still useful for digit strings larger than Long.MAX_VALUE.
            val half = input.map { if (it in '０'..'９') it - 0xFEE0 else it }.joinToString("")
            val forms = listOf(half, half.map { it + 0xFEE0 }.joinToString(""))
            return config.numberCandidateOrder.indices.filter { it < forms.size }.mapIndexed { rank, index ->
                Candidate(forms[index], if (index == 0) 18 else 22, input.length.toUByte(), 8000 + rank, yomi = input)
            }
        }
        return proofs.flatMap { generate(it, config) }.distinctBy { it.string }
    }

    fun generate(proof: ValidatedNumber, config: PredictionConfig): List<Candidate> {
        fun candidate(text: String, type: Byte, score: Int) = Candidate(
            string = text, type = type, length = proof.reading.length.toUByte(), score = score,
            yomi = proof.reading,
            leftId = if (type.toInt() == 32) 2046 else 2044,
            rightId = if (proof.counter == "時") 2015 else if (proof.counter.isNotEmpty()) 2011
                else if (type.toInt() == 32) 2046 else 2044,
        )
        val time = proof.clock != null || proof.counter in listOf("時", "分")
        val result = config.numberCandidateOrder.indices.mapIndexed { rank, index ->
            val type: Byte = when {
                time && index == 0 -> CANDIDATE_TYPE_TIME
                time && index == 1 -> 30
                else -> listOf<Byte>(18, 22, 32)[index]
            }
            candidate(proof.basicForms[index], type, 8000 + rank)
        }.toMutableList()
        proof.clockText?.let { result += candidate(it, CANDIDATE_TYPE_TIME, 8003) }
        if (proof.counter.isEmpty()) {
            proof.digits.addCommasToNumber().takeIf { it.contains(',') }?.let { result += candidate(it, 19, 8003) }
            if (proof.value >= 10000) result += candidate(proof.value.convertToKanjiNotation(), 23, 8004)
            proof.exponent()?.let { result += candidate(it, 20, 8005) }
            result += candidate(proof.digits.map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString(""), 23, 8006)
            result += candidate(proof.digits.map { "₀₁₂₃₄₅₆₇₈₉"[it - '0'] }.joinToString(""), 24, 8007)
            if (proof.digits.length == 4) {
                val hour = proof.digits.take(2)
                val minute = proof.digits.takeLast(2)
                if (hour.toInt() in 0..29 && minute.toInt() in 0..59) {
                    result += candidate("$hour:$minute", CANDIDATE_TYPE_TIME, 8008)
                    result += candidate("${hour}時${minute}分", CANDIDATE_TYPE_TIME, 8008)
                }
            }
            if (proof.digits.length in 3..4) {
                val month = proof.digits.dropLast(2).toInt()
                val day = proof.digits.takeLast(2).toInt()
                val days = when (month) {
                    2 -> 29
                    4, 6, 9, 11 -> 30
                    1, 3, 5, 7, 8, 10, 12 -> 31
                    else -> 0
                }
                if (day in 1..days) result += candidate("${month}月${day}日", 40, 8009)
            }
            if (config.showSymbolCandidates) result += createValueBasedSymbolCandidates(proof.value, proof.reading.length.toUByte())
        }
        return result.filter { config.numberCandidateConfig.permits(proof, it.string) }.distinctBy { it.string }
    }

    /** Orders only slots for this input's numeric forms; explicit user entries keep their slots. */
    fun order(input: String, candidates: List<Candidate>, config: PredictionConfig): List<Candidate> {
        val groups = ValidatedNumber.parseAll(input, config.numberCandidateConfig)
            .filter { it.origin == NumberInputOrigin.DIGITS || config.japaneseNumberCandidatesEnabled }
            .map { proof ->
                (config.numberCandidateOrder.indices.map { proof.basicForms[it] } + listOfNotNull(proof.clockText))
                    .filter { config.numberCandidateConfig.permits(proof, it) }
            }.ifEmpty {
                if (input.isEmpty() || input.any { it !in '0'..'9' && it !in '０'..'９' }) return candidates
                listOf(generate(input, config).map { it.string })
            }
        val result = candidates.toMutableList()
        for (forms in groups) {
            val slots = result.indices.filter { index ->
                val candidate = result[index]
                candidate.string in forms && candidate.commitText == candidate.string &&
                    candidate.length.toInt() == input.length &&
                    candidate.type !in listOf(CANDIDATE_TYPE_USER_DICTIONARY, CANDIDATE_TYPE_USER_TEMPLATE, CANDIDATE_TYPE_TEXT_MACRO)
            }
            val ordered = slots.map(result::get).sortedBy { forms.indexOf(it.string) }
            slots.forEachIndexed { index, slot -> result[slot] = ordered[index] }
        }
        return result
    }
}
