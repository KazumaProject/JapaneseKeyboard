package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.counter.CounterConverter
import com.kazumaproject.counter.CounterInterpretation
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult

/** Quantity presentation never participates in ordinary lexical search or changes its candidates. */
internal object CounterCandidateComposer {
    private const val PROTECTED_COUNT = 3
    private const val MAX_ADDITIONS = 64
    private val temporalCategories = setOf("calendar", "clock", "duration")
    private const val NUMBER_CHARACTERS = "0123456789０１２３４５６７８９〇零一二三四五六七八九十百千万億兆京廿卅卌"
    internal data class Span(val start: Int, val end: Int, val meanings: List<CounterInterpretation>)
    private data class Addition(val candidate: Candidate, val splits: List<Int>, val promote: Boolean)

    internal class Prepared internal constructor(
        private val input: String,
        private val converter: CounterConverter,
        private val spans: List<Span>,
    ) {
        fun compose(ordinary: KanaKanjiQueryResult, lexicalEntries: (String) -> List<CounterLexicalAlternative>) =
            composePrepared(input, ordinary, converter, spans, lexicalEntries)
    }

    fun prepare(input: String, converter: CounterConverter): Prepared? {
        if (input.isEmpty() || input.length > UByte.MAX_VALUE.toInt()) return null
        val normalized = converter.normalizedReading(input)
        val allSpans = buildList {
            for (start in input.indices) converter.forEachAnalysis(normalized, start) { end, meanings ->
                add(Span(start, end, meanings))
            }
        }
        // An inner reading must not truncate a larger number, even when a lexical path splits it.
        val spans = allSpans.filter { span -> allSpans.none { outer ->
            outer.start <= span.start && outer.end >= span.end &&
                (outer.start < span.start || outer.end > span.end)
        } }
        if (spans.isEmpty()) return null
        return Prepared(input, converter, spans)
    }

    private fun composePrepared(
        input: String, ordinary: KanaKanjiQueryResult, converter: CounterConverter,
        spans: List<Span>, lexicalEntries: (String) -> List<CounterLexicalAlternative>,
    ): KanaKanjiQueryResult {
        val ordinaryStrings = ordinary.candidates.mapTo(HashSet()) { it.string }
        val additions = LinkedHashMap<String, Addition>()
        val lexicalCache = HashMap<String, List<CounterLexicalAlternative>>()
        fun entries(reading: String) = lexicalCache.getOrPut(reading) {
            val normalized = converter.normalizedReading(reading)
            lexicalEntries(reading) + if (normalized == reading) emptyList() else lexicalEntries(normalized)
        }
        fun unambiguous(span: Span): Boolean {
            val meaning = span.meanings.first()
            val surfaces = meaning.forms.map { it.value }.toSet()
            // Calendar/duration meanings may produce the same text; this alone is not a homonym.
            if (span.meanings.any { other -> other.forms.map { it.value }.toSet() != surfaces }) return false
            val reading = input.substring(span.start, span.end)
            val alternatives = entries(reading)
            return alternatives.all { entry ->
                // Numeric spelling alone is insufficient: a noun such as 三角 is also a word.
                val numeral = entry.leftId.toInt() in 2043..2053
                val temporal = meaning.category in temporalCategories && entry.leftId.toInt() == 1909
                (numeral || temporal) && CounterNodePolicy.represents(meaning, entry.surface)
            }
        }
        fun path(candidate: Candidate): List<CandidateConversionSegment> {
            val segments = ordinary.candidateSegmentsByString[candidate.string]
                ?: candidate.conversionSegments
            return if (segments.isNotEmpty()) segments else
                listOf(CandidateConversionSegment(0, input.length, candidate.string))
        }
        fun validPath(candidate: Candidate, segments: List<CandidateConversionSegment>): Boolean =
            candidate.length.toInt() == input.length && segments.firstOrNull()?.inputStart == 0 &&
                segments.lastOrNull()?.inputEnd == input.length &&
                segments.all { it.inputStart >= 0 && it.inputStart < it.inputEnd && it.inputEnd <= input.length } &&
                segments.zipWithNext().all { (a, b) -> a.inputEnd == b.inputStart } &&
                segments.joinToString("") { it.output } == candidate.string
        val first = ordinary.candidates.firstOrNull()
        val firstPath = first?.let(::path).orEmpty()
        val fullWord = entries(input)
        val wholeSpan = spans.firstOrNull { it.start == 0 && it.end == input.length }
        val ordinaryWholeWord = fullWord.isNotEmpty() && (wholeSpan == null || !unambiguous(wholeSpan))
        val priorityAllowed = first != null && !protected(first) && validPath(first, firstPath) &&
            !ordinaryWholeWord && ordinary.candidates.none { candidate ->
                candidate.length.toInt() == input.length &&
                    (candidate.type == CANDIDATE_TYPE_USER_DICTIONARY || candidate.type == CANDIDATE_TYPE_LEARNED_DICTIONARY)
            }
        fun firstCoversSpan(span: Span): Boolean {
            val parts = firstPath.filter { it.inputStart >= span.start && it.inputEnd <= span.end }
            if (parts.firstOrNull()?.inputStart != span.start || parts.lastOrNull()?.inputEnd != span.end) return false
            return true
        }
        for (basis in ordinary.candidates) {
            if (protected(basis)) continue
            val segments = path(basis)
            if (!validPath(basis, segments)) continue
            val boundaries = segments.mapTo(HashSet()) { it.inputStart }.apply { add(input.length) }
            val selected = mutableListOf<Span>()
            var through = 0
            // Longest complete span wins; never split inside an existing ordinary dictionary word.
            for (start in segments.map { it.inputStart }) {
                if (start < through) continue
                val span = spans.asSequence().filter { it.start == start && it.end in boundaries }
                    .maxByOrNull { it.end } ?: continue
                val previous = segments.lastOrNull { it.inputEnd == span.start }
                if (previous != null && previous.output.isNotEmpty() && previous.output.all { it in NUMBER_CHARACTERS } &&
                    converter.numberValue(input.substring(previous.inputStart, previous.inputEnd)) != null &&
                    converter.hasQuantitySyntax(input.substring(previous.inputStart, span.end))) continue
                val existingSurface = segments.filter { it.inputStart >= span.start && it.inputEnd <= span.end }
                    .joinToString("") { it.output }
                selected += span.copy(meanings = span.meanings.sortedByDescending {
                    CounterNodePolicy.represents(it, existingSurface)
                })
                through = span.end
            }
            if (selected.isEmpty()) continue
            if (selected.zipWithNext().any { (a,b) -> a.end == b.start &&
                    a.meanings.any { it.category == "clock" } &&
                    converter.analyze(input.substring(a.start,b.end)).none { it.time != null } }) continue
            val promote = basis === first && priorityAllowed && selected.all { unambiguous(it) && firstCoversSpan(it) }
            val notations = selected.flatMap { it.meanings.flatMap { meaning -> meaning.forms.map { form -> form.notation } } }.distinct()
            // Uniform styles cover multiple quantities without an unbounded Cartesian product.
            val variants = notations.map { notation -> selected.map { span ->
                span.meanings.first().forms.firstOrNull { it.notation == notation }?.value
                    ?: span.meanings.first().forms.first().value
            } }.toMutableList()
            for ((index, span) in selected.withIndex()) for (meaning in span.meanings) for (form in meaning.forms) {
                variants += selected.mapIndexed { position, other ->
                    if (position == index) form.value else other.meanings.first().forms.first().value
                }
            }
            for (values in variants.distinct()) {
                val replaced = buildList {
                    var segmentIndex = 0
                    for ((index, span) in selected.withIndex()) {
                        while (segmentIndex < segments.size && segments[segmentIndex].inputEnd <= span.start)
                            add(segments[segmentIndex++])
                        add(CandidateConversionSegment(span.start, span.end, values[index]))
                        while (segmentIndex < segments.size && segments[segmentIndex].inputEnd <= span.end) segmentIndex++
                    }
                    while (segmentIndex < segments.size) add(segments[segmentIndex++])
                }
                if (values.any { !converter.mayEndQuantitySurface(it) }) continue
                val surface = replaced.joinToString("") { it.output }
                if (surface in additions || (!promote && surface in ordinaryStrings)) continue
                val splits = ordinary.bunsetsuResult?.splitPatternByCandidateString?.get(basis.string).orEmpty()
                    .filter { split -> selected.none { split > it.start && split < it.end } }
                val existing = ordinary.candidates.firstOrNull { it.string == surface }
                fun meaning(index: Int) = selected[index].meanings.first { quantity ->
                    quantity.forms.any { it.value == values[index] }
                }
                val candidate = existing ?: basis.copy(
                    string = surface, commitText = surface, yomi = input, conversionSegments = replaced,
                    leftId = if (selected.first().start == 0) CounterNodePolicy.leftId(meaning(0)) else basis.leftId,
                    rightId = if (selected.last().end == input.length) CounterNodePolicy.rightId(meaning(selected.lastIndex)) else basis.rightId,
                )
                val candidateSplits = if (existing == null) splits else
                    ordinary.bunsetsuResult?.splitPatternByCandidateString?.get(surface).orEmpty()
                additions[surface] = Addition(candidate, candidateSplits, promote)
                if (additions.size >= MAX_ADDITIONS) break
            }
            if (additions.size >= MAX_ADDITIONS ||
                (selected.size == 1 && selected.single().start == 0 && selected.single().end == input.length)) break
        }
        if (additions.isEmpty()) return ordinary
        val promoted = additions.values.filter { it.promote }.map { it.candidate }
        val supplemental = additions.values.filterNot { it.promote }.map { it.candidate }
        val moved = promoted.toMutableList()
        val remaining = ordinary.candidates.filter { candidate ->
            val index = moved.indexOfFirst { it === candidate }
            if (index < 0) true else { moved.removeAt(index); false }
        }
        val candidates = promoted + remaining.take(PROTECTED_COUNT) + supplemental + remaining.drop(PROTECTED_COUNT)
        val newAdditions = additions.filterKeys { it !in ordinaryStrings }
        val segmentMap = ordinary.candidateSegmentsByString + newAdditions.mapValues { it.value.candidate.conversionSegments }
        val bunsetsu = ordinary.bunsetsuResult?.let { original ->
            val splitMap = original.splitPatternByCandidateString + newAdditions.mapValues { it.value.splits }
            original.copy(candidates = candidates, splitPatternByCandidateString = splitMap,
                splitPatterns = (original.splitPatterns + newAdditions.values.map { it.splits }).distinct())
        }
        return ordinary.copy(candidates = candidates, bunsetsuResult = bunsetsu, candidateSegmentsByString = segmentMap)
    }

    private fun protected(candidate: Candidate): Boolean = candidate.type == CANDIDATE_TYPE_USER_DICTIONARY ||
        candidate.type == CANDIDATE_TYPE_LEARNED_DICTIONARY || candidate.commitText != candidate.string ||
        candidate.sourceId != null || candidate.presentation != null || candidate.dateFormat != null
}
