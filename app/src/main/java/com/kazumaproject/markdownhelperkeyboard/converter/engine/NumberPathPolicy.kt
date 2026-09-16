package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.graph.Node
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.quantity.QuantityDictionary

/** Per-input memoized reading proofs. Never infer a number by replacing arbitrary kanji in output. */
class NumberPathPolicy(val input: String, val config: PredictionConfig, previous: NumberPathPolicy? = null) {
    private val dictionary = QuantityRuntime.dictionary
    internal val usesWeightedScoring = QuantityRuntime.scoringModel != null
    private var reusable = previous?.takeIf {
        it.dictionary === dictionary && it.config == config && input.startsWith(it.input) &&
            input.length > it.input.length && input.length <= 255
    }
    private val previousLength = reusable?.input?.length ?: -1
    private var primaryRecorded = false
    private var ordinaryReadings: Set<Pair<Int, Int>> = emptySet()
    fun recordPrimaryPath(nodes: List<Node>) {
        if (usesWeightedScoring || primaryRecorded) return
        primaryRecorded = true
        ordinaryReadings = nodes.filter { node ->
            node.candidateSource == CandidateSource.SYSTEM && !node.isGeneratedNumber &&
                !dictionary.isNumericContext(node.l.toInt()) &&
                (node.sPos to node.sPos + node.len) in recognized &&
                parse(node.yomiUsed).none { node.tango in it.basicForms }
        }.mapTo(HashSet()) { it.sPos to it.sPos + it.len }
    }
    private val proofs: HashMap<String, List<ValidatedNumber>> = HashMap<String, List<ValidatedNumber>>().apply {
        reusable?.proofs?.forEach { (reading, values) -> if (values.isNotEmpty()) put(reading, values) }
    }
    fun parse(reading: String): List<ValidatedNumber> = proofs.getOrPut(reading) {
        if (!config.japaneseNumberCandidatesEnabled || input.length > 255) emptyList()
        else ValidatedNumber.parseUncached(reading, config.numberCandidateConfig)
    }
    private val recognitionMatcher by lazy { NumberGraphMatcher(input, config, parseReading = ::parse) }
    private val previousMatches = reusable?.recognizedAt?.copyOf()
    private val recognizedAt = arrayOfNulls<List<NumberGraphMatcher.Match>>(input.length)
    init { reusable = null }
    internal fun matchesAt(start: Int): List<NumberGraphMatcher.Match> = recognizedAt[start] ?: run {
        val prefix = previousMatches?.getOrNull(start)
        val result = if (prefix == null) recognitionMatcher.matches(start)
            else prefix + recognitionMatcher.matches(start, previousLength + 1)
        result.also { recognizedAt[start] = it }
    }
    private val recognized by lazy {
        input.indices.flatMap { start -> matchesAt(start).map { start to it.end } }
    }
    private val recognizedEnds by lazy { recognized.groupBy({ it.first }, { it.second }) }
    fun recognizedSpans(): List<Pair<Int, Int>> = recognized
    val hasQuantities: Boolean get() = recognized.isNotEmpty()
    internal fun permitsPreference(start: Int, end: Int, contextual: Boolean): Boolean = contextual ||
        ((start to end) !in ordinaryReadings && recognized.none { it.first < start && it.second >= end })
    fun startsQuantity(node: Node): Boolean = node.isGeneratedNumber || dictionary.isNumericContext(node.l.toInt())
    fun spans(nodes: List<Node>): List<NumberCandidateSpan> {
        if (!config.japaneseNumberCandidatesEnabled || !hasQuantities) return emptyList()
        val result = mutableListOf<NumberCandidateSpan>()
        var at = 0; var offset = 0
        while (at < nodes.size) {
            val text = StringBuilder(); val reading = StringBuilder()
            var best: NumberCandidateSpan? = null; var bestEnd = at
            val ends = if (startsQuantity(nodes[at])) recognizedEnds[nodes[at].sPos].orEmpty() else emptyList()
            val maximumEnd = ends.maxOrNull() ?: -1
            for (end in at until nodes.size) {
                if (nodes[end].sPos + nodes[end].len > maximumEnd) break
                val node = nodes[end]
                if (node.candidateSource == CandidateSource.USER_DICTIONARY || node.candidateSource == CandidateSource.LEARNED_DICTIONARY) break
                if (end > at && nodes[end - 1].sPos + nodes[end - 1].len != node.sPos) break
                text.append(node.tango); reading.append(node.yomiUsed)
                if (node.sPos + node.len !in ends) continue
                val surface = text.toString()
                val proof = parse(reading.toString()).firstOrNull { candidate ->
                    candidate.counter.isNotEmpty() && surface in candidate.basicForms &&
                        candidate.basicForms.any { config.numberCandidateConfig.permits(candidate, it) }
                }
                if (proof != null) {
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
    private val surfaceUnits = HashMap<String, Map<String, Set<String>>>()
    private fun units(reading: String, surface: String): Set<String> = surfaceUnits.getOrPut(reading) {
        val result = HashMap<String, MutableSet<String>>()
        for (proof in parse(reading)) for (form in proof.basicForms) {
            val units = result.getOrPut(form) { HashSet() }
            units.add(proof.counter)
            if (proof.customUnit != null) units.add("@registered")
        }
        result
    }[surface].orEmpty()

    fun matchStrength(nodes: List<Node>): Int {
        if (QuantityRuntime.rules.isEmpty()) return 0
        class Verified(val generic: Map<String, IntArray>, val contextual: Map<String, IntArray>)
        val verified = arrayOfNulls<Verified>(nodes.size)
        val empty = IntArray(0)
        fun spansAt(at: Int): Verified {
            verified[at]?.let { return it }
            val generic = HashMap<String, MutableList<Int>>()
            val contextual = HashMap<String, MutableList<Int>>()
            val ends = if (startsQuantity(nodes[at])) recognizedEnds[nodes[at].sPos].orEmpty() else emptyList()
            val maximum = ends.maxOrNull() ?: -1
            val reading = StringBuilder()
            val text = StringBuilder()
            for (end in at until nodes.size) {
                val node = nodes[end]
                val finish = node.sPos + node.len
                if (finish > maximum || node.candidateSource != CandidateSource.SYSTEM && node.candidateSource != CandidateSource.UNKNOWN) break
                if (end > at && nodes[end - 1].sPos + nodes[end - 1].len != node.sPos) break
                reading.append(node.yomiUsed)
                if (reading.length > 255) break
                text.append(node.tango)
                if (finish !in ends) continue
                val allowed = permitsPreference(nodes[at].sPos, finish, false)
                for (unit in units(reading.toString(), text.toString())) {
                    contextual.getOrPut(unit) { ArrayList() }.add(end)
                    if (allowed) generic.getOrPut(unit) { ArrayList() }.add(end)
                }
            }
            return Verified(generic.mapValues { it.value.toIntArray() }, contextual.mapValues { it.value.toIntArray() })
                .also { verified[at] = it }
        }
        // Literal-first rules can disambiguate homophones; quantity-first rules
        // retain ordinary-word protection. Both consume the same path proofs.
        return QuantityRuntime.dictionary.matchVerified(nodes.map { it.tango }) { at, unit, contextual ->
            if (!startsQuantity(nodes[at])) empty else {
                val spans = spansAt(at)
                (if (contextual) spans.contextual else spans.generic)[unit] ?: empty
            }
        }
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
                        var shift = 0
                        val projectedSpans = candidate.numberSpans.map { span ->
                            val start = span.outputStart + shift
                            shift += span.forms[form].length - (span.outputEnd - span.outputStart)
                            span.copy(outputStart = start, outputEnd = start + span.forms[form].length)
                        }
                        add(candidate.copy(string = text, commitText = text, numberSpans = projectedSpans))
                    }
                }
            }
        }
    }
}
