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
        numericSpans: List<NumericSpan>? = null,
    ): List<Candidate> {
        val numbers = if (numericSpans == null) NumberCandidateProvider.parse(input) else numericSpans
            .filter { it.start == 0 && it.end == input.length }.map {
                ParsedNumber(it.identity.value, it.identity.counter, it.rightId, it.identity.digits)
            }
        if (numbers.isNotEmpty()) {
            val tagged = tagStandalone(input, candidates, numbers, segmentsByString)
            tagged.filter { it.numberVariant != null }.forEach {
                segmentsByString?.set(it.string, it.conversionSegments)
                splitPatternsByString?.set(it.string, emptyList())
            }
            val expanded = if (config.enhanceCounterCandidates && numbers.any { it.counter.isNotEmpty() }) {
                val existing = tagged.filterNot(::isSpecial).mapTo(hashSetOf()) { it.string }
                val spellings = (numbers + tagged.mapNotNull { candidate -> numbers.firstNotNullOfOrNull {
                    NumberCandidateProvider.matchSurface(candidate.string, it)
                } }).distinctBy { it.counter }
                tagged + spellings.flatMap { number ->
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
        if (segmentsByString.isNullOrEmpty()) return reorderTagged(candidates, config)

        val readingSpans = numericSpans?.groupBy { it.start to it.end }?.map { (range, spans) ->
            NumberCandidateProvider.ReadingSpan(range.first, range.second, spans.map {
                ParsedNumber(it.identity.value, it.identity.counter, it.rightId, it.identity.digits)
            })
        } ?: NumberCandidateProvider.spans(input)
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
            val spans = findSpans(segments, parsedSpans, readingSpans)
            if (spans.isEmpty()) {
                expanded.add(candidate)
                continue
            }
            val group = renderSegments(segments, spans.map { it.copy(number = it.number.copy(counter = NumberCandidateProvider.counterIdentity(it.number.counter))) }, NumberStyle.HALF).joinToString("") { it.output }
            val originalStyle = NumberStyle.entries.firstOrNull { style ->
                renderSegments(segments, spans, style).joinToString("") { it.output } == candidate.string
            }
            val styles = spans.map { span -> NumberCandidateProvider.styleOf(
                segments.subList(span.first, span.last + 1).joinToString("") { it.output }, span.number)
            }
            val uniformStyle = originalStyle ?: styles.firstOrNull()?.takeIf { style -> styles.all { it?.format == style.format } }
            val coherentSplits = splits.filterNot { position -> spans.any { span ->
                position > segments[span.first].inputStart && position < segments[span.last].inputEnd
            } }
            val originalSegments = renderSegments(segments, spans, null)
            segmentsByString[candidate.string] = originalSegments
            splitPatternsByString?.set(candidate.string, coherentSplits)
            expanded.add(candidate.copy(
                conversionSegments = originalSegments,
                numberVariant = uniformStyle?.let { NumberCandidateVariant(group, it.format, originalStyle?.priority ?: 3) },
            ))
            for (style in if (config.enhanceCounterCandidates) NumberStyle.entries else emptyList()) {
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
            candidate.numberVariant?.let { Triple(candidate.string, candidate.yomi, candidate.length) to it }
        }.toMap()
        return candidates.map { candidate ->
            val identity = identities[Triple(candidate.string, candidate.yomi, candidate.length)]
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
        val segment = CandidateConversionSegment(0, input.length, text, leftId, number.rightId, CandidateSource.SYSTEM,
            numericIdentity = NumericIdentity(number.value, NumberCandidateProvider.counterIdentity(number.counter), number.digits))
        return Candidate(
            string = text, type = type, length = input.length.toUByte(), score = 8000,
            yomi = input, leftId = leftId, rightId = number.rightId,
            conversionSegments = listOf(segment),
            numberVariant = NumberCandidateVariant(standaloneGroup(number), style.format, style.priority),
        )
    }

    private fun standaloneGroup(number: ParsedNumber) = "number:${number.value}:${NumberCandidateProvider.counterIdentity(number.counter)}"

    private fun tagStandalone(input: String, candidates: List<Candidate>, numbers: List<ParsedNumber>, paths: Map<String, List<CandidateConversionSegment>>? = null): List<Candidate> {
        return candidates.map { candidate ->
            if (isSpecial(candidate) || candidate.length.toInt() != input.length ||
                candidate.yomi != null && candidate.yomi != input) return@map candidate
            val number = numbers.firstNotNullOfOrNull { NumberCandidateProvider.matchSurface(candidate.string, it) }
                ?: return@map candidate
            val style = NumberCandidateProvider.styleOf(candidate.string, number)
            val oldSegments = paths?.get(candidate.string) ?: candidate.conversionSegments
            val identity = NumericIdentity(number.value, NumberCandidateProvider.counterIdentity(number.counter), number.digits)
            val segment = oldSegments.firstOrNull()?.copy(inputStart = 0, inputEnd = input.length,
                output = candidate.string, rightId = oldSegments.last().rightId, numericIdentity = identity)
                ?: CandidateConversionSegment(0, input.length, candidate.string,
                    candidate.leftId ?: 2044, candidate.rightId ?: number.rightId, CandidateSource.SYSTEM,
                    numericIdentity = identity)
            candidate.copy(conversionSegments = listOf(segment),
                numberVariant = NumberCandidateVariant(standaloneGroup(number), style.format, style.priority))
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

    internal fun coversNumericSpans(segments: List<CandidateConversionSegment>, numericSpans: List<NumericSpan>): Boolean =
        numericSpans.groupBy { it.start to it.end }.all { (range, alternatives) ->
            val parts = segments.filter { it.inputStart >= range.first && it.inputEnd <= range.second }
            if (parts.isEmpty() || parts.first().inputStart != range.first || parts.last().inputEnd != range.second ||
                parts.any { !eligible(it) }) false else {
                val text = parts.joinToString("") { it.output }
                alternatives.any { NumberCandidateProvider.matchSurface(text,
                    ParsedNumber(it.identity.value, it.identity.counter, it.rightId, it.identity.digits)) != null }
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

    private val numericCharacters = "0123456789０１２３４５６７８９〇零一二三四五六七八九十百千万億兆京壱弐参拾萬"
    private fun beginsWithNumber(output: String): Boolean = output.isNotEmpty() && output.first() in numericCharacters

    private fun eligible(segment: CandidateConversionSegment): Boolean = segment.leftId != null && segment.rightId != null &&
        segment.leftId.toInt() !in 1920..1929 && segment.rightId.toInt() !in 1920..1929 &&
        (segment.source == CandidateSource.SYSTEM || segment.source == CandidateSource.UNKNOWN) && !segment.isSystemUserDictionary

    private fun findSpans(segments: List<CandidateConversionSegment>, parsed: MutableMap<Long, List<ParsedNumber>>, readingSpans: List<NumberCandidateProvider.ReadingSpan>): List<Span> {
        val spans = mutableListOf<Span>()
        var first = 0
        while (first < segments.size) {
            if (!eligible(segments[first]) || !beginsWithNumber(segments[first].output)) { first++; continue }
            val readingSpan = readingSpans.firstOrNull { it.start == segments[first].inputStart }
            if (readingSpan == null) { first++; continue }
            var best: Span? = null
            val output = StringBuilder()
            for (last in first until segments.size) {
                val segment = segments[last]
                if (!eligible(segment) || (last > first && !beginsWithNumber(segment.output) &&
                    segment.output !in NumberCandidateProvider.counterSurfaces)) break
                if (segment.inputEnd > readingSpan.end) break
                output.append(segment.output)
                val start = segments[first].inputStart
                val key = (start.toLong() shl 32) or segment.inputEnd.toLong()
                val numbers = parsed.getOrPut(key) {
                    if (segment.inputEnd == readingSpan.end) readingSpan.numbers else emptyList()
                }
                val text = output.toString()
                val number = numbers.firstNotNullOfOrNull { NumberCandidateProvider.matchSurface(text, it) } ?: continue
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
            add(first.copy(inputEnd = last.inputEnd, output = if (style == null) segments.subList(span.first, span.last + 1).joinToString("") { it.output } else span.number.render(style), rightId = last.rightId,
                numericIdentity = NumericIdentity(span.number.value, NumberCandidateProvider.counterIdentity(span.number.counter), span.number.digits)))
            next = span.last + 1
        }
        while (next < segments.size) add(segments[next++])
    }
}
