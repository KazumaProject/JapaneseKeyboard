package com.kazumaproject.markdownhelperkeyboard.converter.utility

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_CALCULATION
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_FORMULA_TEX
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_FORMULA_UNICODE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_UNIT_CONVERSION
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_UTILITY_LITERAL
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

object UtilityCandidateComposer {
    fun compose(
        input: String,
        existingCandidates: List<Candidate>,
        result: UtilityCandidateResult,
    ): List<Candidate> {
        if (!result.hasCandidates) return existingCandidates
        val utilityCandidates = result.candidates.map { candidate ->
            Candidate(
                string = candidate.text,
                type = when (candidate.kind) {
                    UtilityCandidateKind.CALCULATION -> CANDIDATE_TYPE_CALCULATION
                    UtilityCandidateKind.UNIT_CONVERSION -> CANDIDATE_TYPE_UNIT_CONVERSION
                    UtilityCandidateKind.LITERAL -> CANDIDATE_TYPE_UTILITY_LITERAL
                    UtilityCandidateKind.FORMULA_UNICODE -> CANDIDATE_TYPE_FORMULA_UNICODE
                    UtilityCandidateKind.FORMULA_TEX -> CANDIDATE_TYPE_FORMULA_TEX
                },
                length = input.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
                score = 0,
                yomi = input,
                commitText = candidate.text,
                presentation = candidate.formulaPresentation,
            )
        }
        val formulaCandidates = utilityCandidates.filter { candidate ->
            candidate.isFormulaCandidate()
        }
        val nonFormulaUtilityCandidates = utilityCandidates.filterNot { candidate ->
            candidate.isFormulaCandidate()
        }
        val ordered = when (result.trigger) {
            UtilityTrigger.FORMULA -> {
                val source = input.trim()
                val sourceIsCanonicalTex = formulaCandidates.any {
                    it.presentation?.normalizedTex == source
                }
                val originalInput = if (sourceIsCanonicalTex) {
                    null
                } else {
                    existingCandidates.firstOrNull {
                        it.string == input || it.commitText == input ||
                            it.string == source || it.commitText == source
                    } ?: utilityLiteral(input, input)
                }
                val remainingCandidates = existingCandidates.filterNot { existing ->
                    existing === originalInput ||
                        (sourceIsCanonicalTex &&
                            (existing.string == input || existing.commitText == input ||
                                existing.string == source || existing.commitText == source))
                }
                val unitCandidates = nonFormulaUtilityCandidates.filter {
                    it.type == CANDIDATE_TYPE_UNIT_CONVERSION
                } + remainingCandidates.filter {
                    it.type == CANDIDATE_TYPE_UNIT_CONVERSION
                }
                val otherUtilityCandidates = nonFormulaUtilityCandidates.filterNot {
                    it.type == CANDIDATE_TYPE_UNIT_CONVERSION
                }
                val normalCandidates = remainingCandidates.filterNot {
                    it.type == CANDIDATE_TYPE_UNIT_CONVERSION
                }
                formulaCandidates + unitCandidates + otherUtilityCandidates +
                    listOfNotNull(originalInput) + normalCandidates
            }

            UtilityTrigger.EXPLICIT_CALCULATION,
            UtilityTrigger.EXPLICIT_UNIT_CONVERSION ->
                utilityCandidates + existingCandidates

            UtilityTrigger.AUTOMATIC_UNIT_CONVERSION -> {
                val inputLiteral = existingCandidates.firstOrNull { it.string == input }
                    ?: utilityLiteral(input, input)
                val preferredSource = result.preferredSourceText
                    ?.takeIf { it.isNotBlank() && it != input }
                    ?.let { text ->
                        existingCandidates.firstOrNull { it.string == text }
                            ?: utilityLiteral(text, input)
                    }
                if (preferredSource == null) {
                    listOf(inputLiteral) + nonFormulaUtilityCandidates +
                        existingCandidates.filterNot { it === inputLiteral }
                } else {
                    listOf(preferredSource) + nonFormulaUtilityCandidates + inputLiteral +
                        existingCandidates.filterNot {
                            it === preferredSource || it === inputLiteral
                        }
                }
            }

            UtilityTrigger.NONE -> existingCandidates
        }
        return if (result.trigger == UtilityTrigger.FORMULA) {
            distinctFormulaCandidates(ordered)
        } else {
            ordered.distinctBy(Candidate::string)
        }
    }

    private fun Candidate.isFormulaCandidate(): Boolean =
        type == CANDIDATE_TYPE_FORMULA_UNICODE || type == CANDIDATE_TYPE_FORMULA_TEX

    private fun distinctFormulaCandidates(candidates: List<Candidate>): List<Candidate> {
        val emittedTexts = mutableSetOf<String>()
        val emittedFormulaTypes = mutableSetOf<Pair<String, Byte>>()
        return candidates.filter { candidate ->
            if (candidate.isFormulaCandidate()) {
                if (!emittedFormulaTypes.add(candidate.string to candidate.type)) {
                    false
                } else {
                    emittedTexts.add(candidate.string)
                    true
                }
            } else {
                emittedTexts.add(candidate.string)
            }
        }
    }

    private fun utilityLiteral(text: String, input: String) = Candidate(
        string = text,
        type = CANDIDATE_TYPE_UTILITY_LITERAL,
        length = input.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
        score = 0,
        yomi = input,
    )

    fun isUtilityCandidate(candidate: Candidate?): Boolean = when (candidate?.type) {
        CANDIDATE_TYPE_CALCULATION,
        CANDIDATE_TYPE_UNIT_CONVERSION,
        CANDIDATE_TYPE_UTILITY_LITERAL -> true
        CANDIDATE_TYPE_FORMULA_UNICODE,
        CANDIDATE_TYPE_FORMULA_TEX -> true
        else -> false
    }

    fun isResultCandidate(candidate: Candidate?): Boolean = when (candidate?.type) {
        CANDIDATE_TYPE_CALCULATION,
        CANDIDATE_TYPE_UNIT_CONVERSION -> true
        else -> false
    }
}
