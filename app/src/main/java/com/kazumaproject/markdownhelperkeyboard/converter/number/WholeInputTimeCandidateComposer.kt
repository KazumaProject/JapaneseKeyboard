package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toFullWidthChar
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/** A complete hour + minute reading is strong enough to prefer time over a lexical N-best path. */
internal object WholeInputTimeCandidateComposer {
    fun promote(
        input: String,
        candidates: List<Candidate>,
        config: NumberCandidateConfig,
        numericSpans: List<NumericSpan>? = null,
        segmentsByString: MutableMap<String, List<CandidateConversionSegment>>? = null,
        splitPatternsByString: MutableMap<String, List<Int>>? = null,
    ): List<Candidate> {
        // In particular, English-kana does not need an additional number scan for ordinary input.
        if (!config.enhanceCounterCandidates || !(input.endsWith("ふん") || input.endsWith("ぷん"))) return candidates
        val spans = numericSpans ?: NumberCandidateProvider.analyze(input).spans
        if (spans.size != 2) return candidates
        val hour = spans[0]
        val minute = spans[1]
        if (hour.start != 0 || hour.end != minute.start || minute.end != input.length ||
            !hour.canSupplement || !minute.canSupplement ||
            hour.identity.counter != "時" || minute.identity.counter != "分" ||
            // Keep extended hours (24-29) used by timetables, but not arbitrary durations.
            hour.identity.value !in 0L..29L || minute.identity.value !in 0L..59L) return candidates

        val promoted = config.normalizedOrder.map { format ->
            // These bounded values need only one spelling per format, not comma/mixed variants.
            val outputs = spans.map { span ->
                val number = span.identity
                val digits = when (format) {
                    NumberCandidateFormat.HALF_WIDTH -> number.digits
                    NumberCandidateFormat.FULL_WIDTH -> buildString(number.digits.length) {
                        number.digits.forEach { append(it.toFullWidthChar()) }
                    }
                    NumberCandidateFormat.KANJI -> number.value.toKanji()
                }
                digits + number.counter
            }
            val text = outputs.joinToString("")
            // Reuse a real, exact candidate rather than replacing its path, score or source.
            candidates.firstOrNull { it.string == text && it.commitText == text &&
                it.length.toInt() == input.length && (it.yomi == null || it.yomi == input) &&
                it.sourceId == null && it.presentation == null && it.dateFormat == null }
                ?: run {
                    val leftId: Short = if (format == NumberCandidateFormat.KANJI) 2046 else 2044
                    val segments = spans.mapIndexed { index, span ->
                        CandidateConversionSegment(span.start, span.end, outputs[index], leftId,
                            span.rightId, CandidateSource.SYSTEM, numericIdentity = span.identity)
                    }
                    Candidate(text, CANDIDATE_TYPE_TIME, input.length.toUByte(), 8000,
                        yomi = input, leftId = leftId, rightId = minute.rightId, conversionSegments = segments)
                        .also {
                            segmentsByString?.set(text, segments)
                            splitPatternsByString?.set(text, emptyList())
                        }
                }
        }
        // No numberVariant: merged-source inheritance must not retag or move a learned time row.
        // Format ordering is local to this engine result, after the existing score/number sorts.
        return buildList(candidates.size + promoted.size) {
            addAll(promoted)
            candidates.forEach { candidate ->
                if (promoted.none { it === candidate }) add(candidate)
            }
        }
    }
}
