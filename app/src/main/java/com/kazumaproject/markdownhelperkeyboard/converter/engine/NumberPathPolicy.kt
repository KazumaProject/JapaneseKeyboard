package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.Node
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.quantity.QuantityDictionary

/** Per-input memoized reading proofs. Never infer a number by replacing arbitrary kanji in output. */
class NumberPathPolicy(val input: String, val config: PredictionConfig) {
    private var primaryRecorded = false
    private var ordinaryReadings: Set<Pair<Int, Int>> = emptySet()
    fun recordPrimaryPath(nodes: List<Node>) {
        if (primaryRecorded) return
        primaryRecorded = true
        ordinaryReadings = nodes.filter { node ->
            node.candidateSource == CandidateSource.SYSTEM && !node.isGeneratedNumber &&
                node.l.toInt() !in QuantityRuntime.dictionary.numericContextIds &&
                (node.sPos to node.sPos + node.len) in recognized &&
                parse(node.yomiUsed).none { node.tango in it.basicForms }
        }.mapTo(HashSet()) { it.sPos to it.sPos + it.len }
    }
    private val proofs = HashMap<String, List<ValidatedNumber>>()
    fun parse(reading: String): List<ValidatedNumber> = proofs.getOrPut(reading) {
        if (!config.japaneseNumberCandidatesEnabled || input.length > 255) emptyList()
        else ValidatedNumber.parseUncached(reading, config.numberCandidateConfig)
    }
    private val recognized by lazy {
        val matcher = NumberGraphMatcher(input, config)
        input.indices.flatMap { start -> matcher.matches(start).map { start to it.end } }
    }
    fun recognizedSpans(): List<Pair<Int, Int>> = recognized
    val hasQuantities: Boolean get() = recognized.isNotEmpty()
    internal fun permitsPreference(start: Int, end: Int, contextual: Boolean): Boolean = contextual ||
        ((start to end) !in ordinaryReadings && recognized.none { it.first < start && it.second >= end })
    fun startsQuantity(node: Node): Boolean = node.isGeneratedNumber || node.l.toInt() in QuantityRuntime.dictionary.numericContextIds
    fun spans(nodes: List<Node>): List<NumberCandidateSpan> {
        if (!config.japaneseNumberCandidatesEnabled || !hasQuantities) return emptyList()
        val result = mutableListOf<NumberCandidateSpan>()
        var at = 0; var offset = 0
        while (at < nodes.size) {
            val text = StringBuilder(); val reading = StringBuilder()
            var best: NumberCandidateSpan? = null; var bestEnd = at
            for (end in at until nodes.size) {
                if (!startsQuantity(nodes[at])) break
                val node = nodes[end]
                if (node.candidateSource == CandidateSource.USER_DICTIONARY || node.candidateSource == CandidateSource.LEARNED_DICTIONARY) break
                if (end > at && nodes[end - 1].sPos + nodes[end - 1].len != node.sPos) break
                text.append(node.tango); reading.append(node.yomiUsed)
                val proof = parse(reading.toString()).firstOrNull { it.counter.isNotEmpty() && text.toString() in it.basicForms }
                if (proof != null && proof.basicForms.any { config.numberCandidateConfig.permits(proof, it) }) {
                    best = NumberCandidateSpan(offset, offset + text.length, nodes[at].sPos,
                        node.sPos + node.len, proof.basicForms, proof.counter,
                        proof.basicForms.indices.filter { config.numberCandidateConfig.permits(proof, proof.basicForms[it]) }.toSet())
                    bestEnd = end
                }
            }
            if (best != null) {
                result += best; offset = best.outputEnd; at = bestEnd + 1
            } else { offset += nodes[at].tango.length; at++ }
        }
        return result
    }
    fun key(text: String, spans: List<NumberCandidateSpan>): String = replace(text, spans, 0)
    fun matchStrength(nodes: List<Node>): Int {
        if (QuantityRuntime.rules.isEmpty()) return 0
        val tokens = nodes.map {
            QuantityDictionary.Token(it.tango, it.yomiUsed, it.candidateSource != CandidateSource.SYSTEM && it.candidateSource != CandidateSource.UNKNOWN)
        }
        val endIndices = nodes.indices.associateBy { nodes[it].sPos + nodes[it].len }
        val recognizedEnds = recognized.groupBy({ it.first }, { it.second })
        val quantityEnds = nodes.indices.associateWith { at ->
            if (!startsQuantity(nodes[at])) emptyList() else recognizedEnds[nodes[at].sPos].orEmpty().mapNotNull(endIndices::get)
        }
        fun verify(at: Int, end: Int, reading: String, text: String, unit: String, contextual: Boolean): Boolean {
            val start = nodes[at].sPos; val finish = nodes[end].sPos + nodes[end].len
            if (!startsQuantity(nodes[at])) return false
            return permitsPreference(start, finish, contextual) &&
                parse(reading).any { (it.counter == unit || unit == "@registered" && it.customUnit != null) && text in it.basicForms }
        }
        val generic = QuantityRuntime.dictionary.matchStrength(tokens, quantityEnds = { quantityEnds[it].orEmpty() }) { at, end, reading, text, unit ->
            verify(at, end, reading, text, unit, false)
        }
        // A literal semantic context (e.g. 家 + が + quantity(軒)) may disambiguate a homophone.
        // A generic quantity + だけ rule cannot turn 発見だけ into 8件だけ.
        val contextual = QuantityRuntime.dictionary.matchStrength(tokens, { it.first() is QuantityDictionary.Feature.Word }, { quantityEnds[it].orEmpty() }) { at, end, reading, text, unit ->
            verify(at, end, reading, text, unit, true)
        }
        return maxOf(generic, contextual)
    }

    companion object {
        fun replace(text: String, spans: List<NumberCandidateSpan>, form: Int): String {
            val out = StringBuilder(text)
            for (span in spans.asReversed()) out.replace(span.outputStart, span.outputEnd, span.forms[form])
            return out.toString()
        }
        fun expand(candidates: List<Candidate>, config: PredictionConfig,
                   segments: MutableMap<String, List<CandidateConversionSegment>>? = null): List<Candidate> {
            if (!config.japaneseNumberCandidatesEnabled) return candidates
            val seen = HashSet<String>()
            return buildList {
                for (candidate in candidates) {
                    if (candidate.numberSpans.isEmpty() || candidate.commitText != candidate.string) {
                        if (seen.add(candidate.string)) add(candidate)
                        continue
                    }
                    for (form in config.numberCandidateOrder.indices) {
                        if (candidate.numberSpans.any { form !in it.allowedForms }) continue
                        val text = replace(candidate.string, candidate.numberSpans, form)
                        if (!seen.add(text)) continue
                        // Collapse only the nodes making up a verified quantity. Exact input ranges remain intact.
                        segments?.get(candidate.string)?.let { original ->
                            val projected = mutableListOf<CandidateConversionSegment>()
                            var i = 0
                            while (i < original.size) {
                                val s = original[i]
                                val number = candidate.numberSpans.firstOrNull { it.inputStart == s.inputStart }
                                if (number == null) { projected += s; i++ }
                                else {
                                    projected += CandidateConversionSegment(number.inputStart, number.inputEnd, number.forms[form])
                                    while (i < original.size && original[i].inputEnd <= number.inputEnd) i++
                                }
                            }
                            segments[text] = projected
                        }
                        add(candidate.copy(string = text, commitText = text, numberSpans = emptyList()))
                    }
                }
            }
        }
    }
}
