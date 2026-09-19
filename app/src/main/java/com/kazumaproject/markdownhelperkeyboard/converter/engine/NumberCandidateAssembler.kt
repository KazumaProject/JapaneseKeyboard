package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TEXT_MACRO
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_TEMPLATE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.NumberCandidateSpan
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.NumericCandidateRole

/**
 * Adds notation variants after linguistic ranking has completed.
 *
 * This class deliberately has no score comparator. Existing candidates retain their list position;
 * display-only variants and parser-only supplements are appended in separate phases.
 */
object NumberCandidateAssembler {
    private val protectedTypes = setOf(
        CANDIDATE_TYPE_USER_DICTIONARY,
        CANDIDATE_TYPE_LEARNED_DICTIONARY,
        CANDIDATE_TYPE_USER_TEMPLATE,
        CANDIDATE_TYPE_TEXT_MACRO,
    )

    fun assemble(
        input: String,
        candidates: List<Candidate>,
        config: PredictionConfig,
    ): List<Candidate> {
        if (input.isEmpty() || candidates.isEmpty()) return candidates
        val wholeProofs = ValidatedNumber.parseAll(input, config.numberCandidateConfig)
            .filter { it.origin == NumberInputOrigin.DIGITS || config.japaneseNumberCandidatesEnabled }
        val generated = NumberCandidateGenerator.generate(input, config)
        val generatedTexts = generated.mapTo(HashSet()) { it.string }
        val generatedFormsByProof = wholeProofs.associateWith { generatedForms(it, config) }
        val literalDigitInput = input.all { it in '0'..'9' || it in '０'..'９' }

        // Generated candidates used to participate in the score sort. Remove those unproven copies
        // and re-add them only after every ranked interpretation has retained one representative.
        var keptLiteral = false
        val ranked = candidates.filter { candidate ->
            val generatedCopy = candidate.conversionSegments.isEmpty() &&
                candidate.type !in protectedTypes && candidate.string in generatedTexts
            if (!generatedCopy) return@filter true
            if (literalDigitInput && !keptLiteral && candidate.string == input) {
                keptLiteral = true
                true
            } else {
                false
            }
        }

        val annotated = ranked.mapIndexed { index, candidate ->
            val id = index.toLong() + 1L
            val spans = when {
                candidate.type in protectedTypes || candidate.commitText != candidate.string -> emptyList()
                candidate.conversionSegments.isNotEmpty() -> spansFromPath(input, candidate, config)
                literalDigitInput && candidate.string == input -> spansForWholeProof(
                    candidate,
                    wholeProofs.firstOrNull(),
                    config,
                )
                else -> emptyList()
            }
            candidate.copy(
                interpretationId = id,
                numericRole = if (spans.isEmpty()) NumericCandidateRole.NONE else NumericCandidateRole.REPRESENTATIVE,
                numberSpans = spans,
            )
        }

        val representatives = ArrayList<Candidate>(annotated.size)
        val variants = ArrayList<Candidate>()
        val representedWholeProofs = HashSet<String>()
        for (candidate in annotated) {
            if (candidate.numberSpans.isEmpty()) {
                representatives += candidate
                continue
            }
            val preferred = preferredForm(candidate.numberSpans, config)
            if (preferred == null) {
                representatives += candidate.copy(numberSpans = emptyList(), numericRole = NumericCandidateRole.NONE)
                continue
            }
            val representative = project(candidate, preferred, NumericCandidateRole.REPRESENTATIVE)
            representatives += representative
            if (candidate.numberSpans.size == 1 && candidate.numberSpans[0].inputStart == 0 &&
                candidate.numberSpans[0].inputEnd == input.length
            ) {
                representedWholeProofs += proofKey(candidate.numberSpans[0])
            }
            for (form in config.numberCandidateOrder.indices) {
                if (form == preferred || candidate.numberSpans.any { form !in it.allowedForms }) continue
                variants += project(candidate, form, NumericCandidateRole.VARIANT)
            }
        }

        // Rich forms (comma, exponent, symbols) inherit an existing whole-input interpretation.
        // If no path proved that interpretation they remain parser-only supplements.
        val tail = generated.mapIndexedNotNull { index, candidate ->
            val proof = wholeProofs.firstOrNull { candidate.string in generatedFormsByProof.getValue(it) }
            val inherited = proof?.let { proofKey(it) in representedWholeProofs } == true
            if (inherited && proof != null && candidate.string in proof.basicForms) return@mapIndexedNotNull null
            val origin = if (inherited) annotated.firstOrNull { base ->
                base.numberSpans.any { it.inputStart == 0 && it.inputEnd == input.length && proofKey(it) == proofKey(proof!!) }
            } else null
            candidate.copy(
                interpretationId = if (inherited) origin?.interpretationId else annotated.size.toLong() + index + 1L,
                derivedFromInterpretationId = origin?.interpretationId,
                numericRole = if (inherited) NumericCandidateRole.VARIANT else NumericCandidateRole.SUPPLEMENT,
                rankingEligible = false,
            )
        } + parserOnlyEmbeddedSupplements(input, annotated, config)

        val seen = HashSet<IdentityKey>()
        return buildList {
            fun append(candidate: Candidate) {
                val key = IdentityKey(
                    candidate.string,
                    candidate.commitText,
                    candidate.length,
                    candidate.derivedFromInterpretationId ?: candidate.interpretationId,
                    candidate.numberSpans.map { Triple(it.inputStart, it.inputEnd, it.unitId) },
                )
                if (seen.add(key)) add(candidate)
            }
            representatives.forEach(::append)
            variants.forEach(::append)
            tail.forEach(::append)
        }
    }

    private fun spansFromPath(
        input: String,
        candidate: Candidate,
        config: PredictionConfig,
    ): List<NumberCandidateSpan> {
        val segments = candidate.conversionSegments
        val outputOffsets = IntArray(segments.size + 1)
        for (i in segments.indices) outputOffsets[i + 1] = outputOffsets[i] + segments[i].output.length
        val found = ArrayList<NumberCandidateSpan>()
        var start = 0
        while (start < segments.size) {
            var best: NumberCandidateSpan? = null
            var end = start
            while (end < segments.size) {
                if (end > start && segments[end - 1].inputEnd != segments[end].inputStart) break
                val inputStart = segments[start].inputStart
                val inputEnd = segments[end].inputEnd
                if (inputStart !in 0..input.length || inputEnd !in inputStart..input.length) break
                val reading = input.substring(inputStart, inputEnd)
                val surface = buildString { for (i in start..end) append(segments[i].output) }
                val proof = ValidatedNumber.parseAll(reading, config.numberCandidateConfig)
                    .asSequence()
                    .filter { it.origin == NumberInputOrigin.DIGITS || config.japaneseNumberCandidatesEnabled }
                    .firstOrNull { surface in it.basicForms }
                if (proof != null) {
                    val allowed = allowedForms(proof, config)
                    if (allowed.isNotEmpty()) {
                        best = NumberCandidateSpan(
                            inputStart = inputStart,
                            inputEnd = inputEnd,
                            outputStart = outputOffsets[start],
                            outputEnd = outputOffsets[end + 1],
                            forms = proof.basicForms,
                            allowedForms = allowed,
                            normalizedDigits = proof.digits,
                            unitId = unitId(proof),
                        )
                    }
                }
                end++
            }
            if (best == null) {
                start++
            } else {
                found += best
                start = segments.indexOfFirst { it.inputStart >= best.inputEnd }.let { if (it < 0) segments.size else it }
            }
        }
        return found
    }

    private fun spansForWholeProof(
        candidate: Candidate,
        proof: ValidatedNumber?,
        config: PredictionConfig,
    ): List<NumberCandidateSpan> {
        proof ?: return emptyList()
        val allowed = allowedForms(proof, config)
        if (allowed.isEmpty()) return emptyList()
        return listOf(NumberCandidateSpan(
            inputStart = 0,
            inputEnd = candidate.length,
            outputStart = 0,
            outputEnd = candidate.string.length,
            forms = proof.basicForms,
            allowedForms = allowed,
            normalizedDigits = proof.digits,
            unitId = unitId(proof),
        ))
    }

    private fun preferredForm(spans: List<NumberCandidateSpan>, config: PredictionConfig): Int? =
        config.numberCandidateOrder.indices.firstOrNull { form -> spans.all { form in it.allowedForms } }

    private fun project(candidate: Candidate, form: Int, role: NumericCandidateRole): Candidate {
        var text = candidate.string
        for (span in candidate.numberSpans.asReversed()) {
            text = text.replaceRange(span.outputStart, span.outputEnd, span.forms[form])
        }
        val projectedSegments = projectSegments(candidate.conversionSegments, candidate.numberSpans, form)
        return candidate.copy(
            string = text,
            commitText = text,
            conversionSegments = projectedSegments,
            derivedFromInterpretationId = candidate.interpretationId,
            numericRole = role,
            rankingEligible = role == NumericCandidateRole.REPRESENTATIVE,
        )
    }

    /**
     * Adds a missing numeric interpretation without feeding it back into the lattice.  Only ranges
     * aligned to an already selected system path are projected, and each range is expanded on its
     * own so multiple quantities never form a Cartesian product.
     */
    private fun parserOnlyEmbeddedSupplements(
        input: String,
        candidates: List<Candidate>,
        config: PredictionConfig,
    ): List<Candidate> {
        val mayContainQuantity = input.any { it in '0'..'9' || it in '０'..'９' } ||
            BuiltInCounter.entries.any { input.contains(it.reading) } ||
            config.numberCandidateConfig.units.any { it.enabled && input.contains(it.reading) } ||
            listOf("えん", "にん", "ふん", "ぷん", "じ").any(input::contains)
        if (!mayContainQuantity) return emptyList()
        val base = candidates.firstOrNull {
            it.type !in protectedTypes && it.commitText == it.string && it.conversionSegments.isNotEmpty()
        } ?: return emptyList()
        val segments = base.conversionSegments
        val offsets = IntArray(segments.size + 1)
        for (i in segments.indices) offsets[i + 1] = offsets[i] + segments[i].output.length
        val alreadyRepresented = base.numberSpans.mapTo(HashSet()) { it.inputStart to it.inputEnd }
        val result = ArrayList<Candidate>()
        var nextId = candidates.size.toLong() + 10_000L
        for (start in segments.indices) {
            var bestEnd = -1
            var bestProof: ValidatedNumber? = null
            for (end in start until segments.size) {
                if (end > start && segments[end - 1].inputEnd != segments[end].inputStart) break
                val from = segments[start].inputStart
                val to = segments[end].inputEnd
                if ((from to to) in alreadyRepresented || from !in 0..input.length || to !in from..input.length) continue
                val proof = ValidatedNumber.parseAll(input.substring(from, to), config.numberCandidateConfig)
                    .firstOrNull { it.origin == NumberInputOrigin.DIGITS || config.japaneseNumberCandidatesEnabled }
                if (proof != null) {
                    bestEnd = end
                    bestProof = proof
                }
            }
            val proof = bestProof ?: continue
            val from = segments[start].inputStart
            val to = segments[bestEnd].inputEnd
            val outputFrom = offsets[start]
            val outputTo = offsets[bestEnd + 1]
            val forms = NumberCandidateGenerator.generate(proof, config)
            for (generated in forms) {
                val text = base.string.replaceRange(outputFrom, outputTo, generated.string)
                result += base.copy(
                    string = text,
                    commitText = text,
                    interpretationId = nextId++,
                    derivedFromInterpretationId = null,
                    numericRole = NumericCandidateRole.SUPPLEMENT,
                    rankingEligible = false,
                    numberSpans = emptyList(),
                    conversionSegments = projectSegments(
                        segments,
                        listOf(NumberCandidateSpan(from, to, outputFrom, outputTo, proof.basicForms,
                            allowedForms(proof, config), proof.digits, unitId(proof))),
                        proof.basicForms.indexOf(generated.string).takeIf { it >= 0 } ?: continue,
                    ),
                )
            }
        }
        return result
    }

    private fun projectSegments(
        segments: List<CandidateConversionSegment>,
        spans: List<NumberCandidateSpan>,
        form: Int,
    ): List<CandidateConversionSegment> {
        if (segments.isEmpty() || spans.isEmpty()) return segments
        val result = ArrayList<CandidateConversionSegment>()
        var index = 0
        for (span in spans) {
            while (index < segments.size && segments[index].inputEnd <= span.inputStart) result += segments[index++]
            while (index < segments.size && segments[index].inputStart < span.inputEnd) index++
            result += CandidateConversionSegment(span.inputStart, span.inputEnd, span.forms[form])
        }
        while (index < segments.size) result += segments[index++]
        return result
    }

    private fun allowedForms(proof: ValidatedNumber, config: PredictionConfig): Set<Int> =
        proof.basicForms.indices.filterTo(LinkedHashSet()) { index ->
            config.numberCandidateConfig.permits(proof, proof.basicForms[index])
        }

    private fun generatedForms(proof: ValidatedNumber, config: PredictionConfig): Set<String> =
        NumberCandidateGenerator.generate(proof, config).mapTo(LinkedHashSet()) { it.string }

    private fun unitId(proof: ValidatedNumber): String = when {
        proof.customUnit != null -> "custom:${proof.customUnit.id}"
        proof.builtInCounter != null -> "builtin:${proof.builtInCounter.storageId}"
        proof.counter.isNotEmpty() -> "counter:${proof.counter}"
        else -> "cardinal"
    }

    private fun proofKey(proof: ValidatedNumber): String = "${proof.digits}\u0000${unitId(proof)}"
    private fun proofKey(span: NumberCandidateSpan): String = "${span.normalizedDigits}\u0000${span.unitId}"

    private data class IdentityKey(
        val string: String,
        val commitText: String,
        val length: Int,
        val interpretationId: Long?,
        val spans: List<Triple<Int, Int, String>>,
    )
}
