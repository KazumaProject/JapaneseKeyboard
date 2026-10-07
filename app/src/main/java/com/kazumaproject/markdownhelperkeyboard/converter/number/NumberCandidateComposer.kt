package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.*

/** Generates before filtering, and reorders without generation after sources are merged. */
object NumberCandidateComposer {
    fun prepare(
        input: String,
        candidates: List<Candidate>,
        config: NumberCandidateConfig = NumberCandidateConfig(),
        segmentsByString: MutableMap<String, List<CandidateConversionSegment>>? = null,
        splitPatternsByString: MutableMap<String, List<Int>>? = null,
    ): List<Candidate> {
        val numbers = NumberCandidateProvider.parse(input)
        if (numbers.isNotEmpty()) {
            val tagged = tagStandalone(input, candidates, numbers)
            val expanded = if (config.enhanceCounterCandidates && numbers.any { it.counter.isNotEmpty() }) {
                val existing = tagged.filterNot(::isSpecial).mapTo(hashSetOf()) { it.string }
                tagged + numbers.flatMap { number ->
                    number.renderings().mapNotNull { (text, style) ->
                        if (!existing.add(text)) null else standalone(input, text, number, style).also {
                            segmentsByString?.set(text, it.conversionSegments)
                            splitPatternsByString?.set(text, emptyList())
                        }
                    }
                }
            } else tagged
            return reorderTagged(expanded, config)
        }
        if (!config.enhanceCounterCandidates || segmentsByString.isNullOrEmpty()) return reorderTagged(candidates, config)

        val parsedSpans = HashMap<Long, List<ParsedNumber>>()
        val expanded = ArrayList<Candidate>(candidates.size)
        val existing = candidates.filterNot(::isSpecial).mapTo(hashSetOf()) { it.string }
        for (candidate in candidates) {
            val segments = segmentsByString[candidate.string]
            if (isSpecial(candidate) || segments == null || !isExactPath(input, candidate, segments)) {
                expanded.add(candidate)
                continue
            }
            val splits = splitPatternsByString?.get(candidate.string).orEmpty()
            val spans = findSpans(input, segments, parsedSpans)
            if (spans.isEmpty()) {
                expanded.add(candidate)
                continue
            }
            val group = renderSegments(segments, spans, NumberStyle.HALF).joinToString("") { it.output }
            val originalStyle = NumberStyle.entries.firstOrNull { style ->
                renderSegments(segments, spans, style).joinToString("") { it.output } == candidate.string
            }
            val firstSpan = spans.first()
            val firstOutput = segments.subList(firstSpan.first, firstSpan.last + 1).joinToString("") { it.output }
            val firstStyle = firstSpan.number.renderings().getValue(firstOutput)
            val coherentSplits = splits.filterNot { position -> spans.any { span ->
                position > segments[span.first].inputStart && position < segments[span.last].inputEnd
            } }
            val originalSegments = renderSegments(segments, spans, null)
            segmentsByString[candidate.string] = originalSegments
            splitPatternsByString?.set(candidate.string, coherentSplits)
            expanded.add(candidate.copy(
                conversionSegments = originalSegments,
                numberVariant = NumberCandidateVariant(group, originalStyle?.format ?: firstStyle.format,
                    originalStyle?.priority ?: 3),
            ))
            for (style in NumberStyle.entries) {
                val transformed = renderSegments(segments, spans, style)
                val text = transformed.joinToString("") { it.output }
                if (!existing.add(text)) continue
                expanded.add(candidate.copy(
                    string = text,
                    commitText = text,
                    conversionSegments = transformed,
                    numberVariant = NumberCandidateVariant(group, style.format, style.priority),
                ))
                segmentsByString[text] = transformed
                splitPatternsByString?.set(text, coherentSplits)
            }
        }
        return reorderTagged(expanded, config)
    }

    /** Idempotent; this operation never reintroduces a filtered candidate. */
    fun reorder(input: String, candidates: List<Candidate>, config: NumberCandidateConfig): List<Candidate> {
        val numbers = NumberCandidateProvider.parse(input)
        return reorderTagged(if (numbers.isEmpty()) candidates else tagStandalone(input, candidates, numbers), config)
    }

    /** A learned or template row can win de-duplication over an identical engine row. */
    fun inheritVariantIdentity(candidates: List<Candidate>): List<Candidate> {
        if (candidates.none { it.numberVariant != null }) return candidates
        val identities = candidates.mapNotNull { candidate ->
            candidate.numberVariant?.let { candidate.string to it }
        }.toMap()
        return candidates.map { candidate ->
            val identity = identities[candidate.string]
            if (candidate.numberVariant != null || identity == null || isSpecial(candidate)) candidate
            else candidate.copy(numberVariant = identity)
        }
    }

    private fun standalone(input: String, text: String, number: ParsedNumber, style: NumberStyle): Candidate {
        val type: Byte = when (style) {
            NumberStyle.HALF -> if (number.counter == "時" || number.counter == "分") CANDIDATE_TYPE_TIME else 18
            NumberStyle.COMMA -> 19
            NumberStyle.MIXED -> 23
            NumberStyle.FULL -> 22
            NumberStyle.KANJI -> 32
        }
        val leftId: Short = if (style == NumberStyle.KANJI || style == NumberStyle.MIXED) 2046 else 2044
        val segment = CandidateConversionSegment(0, input.length, text, leftId, number.rightId, CandidateSource.SYSTEM)
        return Candidate(
            string = text, type = type, length = input.length.toUByte(), score = 8000,
            yomi = input, leftId = leftId, rightId = number.rightId,
            conversionSegments = listOf(segment),
            numberVariant = NumberCandidateVariant(standaloneGroup(number), style.format, style.priority),
        )
    }

    private fun standaloneGroup(number: ParsedNumber) = "number:${number.value}:${number.counter}"

    private fun tagStandalone(input: String, candidates: List<Candidate>, numbers: List<ParsedNumber>): List<Candidate> {
        val identities = buildMap {
            numbers.forEach { number -> number.renderings().forEach { (text, style) ->
                put(text, NumberCandidateVariant(standaloneGroup(number), style.format, style.priority))
            } }
        }
        return candidates.map { candidate ->
            val identity = identities[candidate.string]
            if (identity == null || isSpecial(candidate) || candidate.length.toInt() != input.length ||
                candidate.numberVariant == identity) candidate else candidate.copy(numberVariant = identity)
        }
    }

    private val specialTypes = setOf<Byte>(
        33, 36, 37, 38, 39, 40, CANDIDATE_TYPE_ERA, CANDIDATE_TYPE_CALCULATION, CANDIDATE_TYPE_UNIT_CONVERSION,
        CANDIDATE_TYPE_UTILITY_LITERAL, CANDIDATE_TYPE_TEXT_MACRO,
    )
    private fun isSpecial(candidate: Candidate): Boolean = candidate.dateFormat != null ||
        candidate.presentation != null || candidate.sourceId != null || candidate.type in specialTypes

    private fun reorderTagged(candidates: List<Candidate>, config: NumberCandidateConfig): List<Candidate> {
        if (candidates.none { it.numberVariant != null }) return candidates
        val ranks = config.normalizedOrder.withIndex().associate { it.value to it.index }
        val groups = candidates.filter { it.numberVariant != null && !isSpecial(it) }.groupBy { it.numberVariant!!.group }
            .mapValues { (_, group) -> group.groupBy { it.string }.values.map { duplicates ->
                // English-kana used to add a display-only preferred row before the typed number row.
                // Retain the typed row's POS and time identity when those strings are de-duplicated.
                duplicates.maxBy { if (it.leftId != null && it.rightId != null) 1 else 0 }
            }.sortedWith(compareBy<Candidate> { ranks[it.numberVariant!!.format] }
                .thenBy { it.numberVariant!!.style }) }
        val emitted = hashSetOf<String>()
        return buildList(candidates.size) {
            candidates.forEach { candidate ->
                val group = candidate.numberVariant?.group
                if (group == null || isSpecial(candidate)) add(candidate)
                else if (emitted.add(group)) addAll(groups.getValue(group))
            }
        }
    }

    private data class Span(val first: Int, val last: Int, val number: ParsedNumber)

    private fun isExactPath(input: String, candidate: Candidate, segments: List<CandidateConversionSegment>): Boolean {
        if (candidate.yomi != input || segments.isEmpty() || segments.first().inputStart != 0 ||
            segments.last().inputEnd != input.length || segments.joinToString("") { it.output } != candidate.string) return false
        var end = 0
        return segments.all { segment ->
            val valid = segment.inputStart == end && segment.inputEnd > end && segment.inputEnd <= input.length
            end = segment.inputEnd
            valid
        }
    }

    private val numericCharacters = "0123456789０１２３４５６７８９〇零一二三四五六七八九十百千万億兆京"
    private fun beginsWithNumber(output: String): Boolean = output.isNotEmpty() && output.first() in numericCharacters

    private fun eligible(segment: CandidateConversionSegment): Boolean = segment.leftId != null && segment.rightId != null &&
        segment.leftId.toInt() !in 1920..1929 && segment.rightId.toInt() !in 1920..1929 &&
        (segment.source == CandidateSource.SYSTEM || segment.source == CandidateSource.UNKNOWN) && !segment.isSystemUserDictionary

    private fun findSpans(input: String, segments: List<CandidateConversionSegment>, parsed: MutableMap<Long, List<ParsedNumber>>): List<Span> {
        val spans = mutableListOf<Span>()
        var first = 0
        while (first < segments.size) {
            if (!eligible(segments[first]) || !beginsWithNumber(segments[first].output)) { first++; continue }
            var best: Span? = null
            val output = StringBuilder()
            for (last in first until segments.size) {
                val segment = segments[last]
                if (!eligible(segment) || (last > first && !beginsWithNumber(segment.output) &&
                    segment.output !in NumberCandidateProvider.counterSurfaces)) break
                output.append(segment.output)
                val start = segments[first].inputStart
                val key = (start.toLong() shl 32) or segment.inputEnd.toLong()
                val numbers = parsed.getOrPut(key) {
                    NumberCandidateProvider.parse(input.substring(start, segment.inputEnd), allowBareNumber = false)
                }
                val text = output.toString()
                val number = numbers.firstOrNull { text in it.renderings() } ?: continue
                best = Span(first, last, number)
            }
            if (best == null) first++ else { spans.add(best); first = best.last + 1 }
        }
        return spans
    }

    private fun renderSegments(segments: List<CandidateConversionSegment>, spans: List<Span>, style: NumberStyle?): List<CandidateConversionSegment> = buildList {
        var next = 0
        spans.forEach { span ->
            while (next < span.first) add(segments[next++])
            val first = segments[span.first]
            val last = segments[span.last]
            add(first.copy(inputEnd = last.inputEnd, output = if (style == null) segments.subList(span.first, span.last + 1).joinToString("") { it.output } else span.number.render(style), rightId = last.rightId))
            next = span.last + 1
        }
        while (next < segments.size) add(segments[next++])
    }
}
