package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber

/** Creates only readings backed by the explicit counter lexicon or a listed counter suffix. */
object NumberFallbackCandidateFactory {
    private const val NUMBER_ARABIC_POS_ID: Short = 2044
    private const val COUNTER_GENERIC_POS_ID: Short = 2011
    private const val COUNTER_TIME_POS_ID: Short = 2015

    private data class CounterPart(
        val inputStart: Int,
        val inputEnd: Int,
        val value: Long,
        val counter: String,
        val interpretation: String,
    )

    private data class ComposedTimeMatch(
        val inputStart: Int,
        val inputEnd: Int,
        val candidates: List<Candidate>,
    )

    private val supportedTimeSequences = setOf(
        listOf("時", "分"),
        listOf("時", "分", "秒"),
        listOf("時間", "分"),
        listOf("時間", "分", "秒"),
        listOf("分", "秒"),
    )

    fun candidatesForReading(input: String): List<Candidate> {
        if (input.isBlank()) return emptyList()
        val matches = LinkedHashMap<String, Candidate>()

        CounterReadingLexicon.matchAll(input).forEach { reading ->
            addCandidate(
                matches = matches,
                input = input,
                numericReadingEnd = input.length,
                value = reading.value.toString(),
                counter = reading.counter,
                interpretation = reading.interpretation,
            )
        }

        CounterReadingLexicon.suffixMatches(input).forEach { (numberReading, suffix) ->
            val value = numberReading.toNumber()?.second ?: return@forEach
            addCandidate(
                matches = matches,
                input = input,
                numericReadingEnd = numberReading.length,
                value = value,
                counter = suffix.counter,
                interpretation = suffix.interpretation,
            )
        }

        composedTimeCandidatesWithin(input).forEach { candidate ->
            matches.putIfAbsent(candidate.numberMetadata?.familyKey.orEmpty(), candidate)
        }

        return matches.values.toList()
    }

    /** Finds a complete, contiguous, reviewed time-unit sequence at a plausible reading boundary. */
    fun hasComposedTimeReading(input: String): Boolean {
        return findComposedTimeMatches(input).isNotEmpty()
    }

    /**
     * Builds a whole-reading fallback while re-rendering every reviewed time sequence found in
     * the reading. Non-numeric text around those sequences is preserved as entered.
     */
    private fun composedTimeCandidatesWithin(input: String): List<Candidate> {
        val matches = findComposedTimeMatches(input)
        if (matches.isEmpty()) return emptyList()

        val combinations = mutableListOf<List<Candidate>>(emptyList())
        for (match in matches) {
            val next = buildList {
                combinations.forEach { combination ->
                    match.candidates.forEach { candidate ->
                        if (size < MAX_TIME_INTERPRETATIONS) add(combination + candidate)
                    }
                }
            }
            combinations.clear()
            combinations.addAll(next)
        }

        return combinations.map { candidates ->
            val output = StringBuilder()
            val spans = mutableListOf<NumberSpan>()
            var inputOffset = 0
            candidates.forEachIndexed { index, candidate ->
                val match = matches[index]
                output.append(input.substring(inputOffset, match.inputStart))
                val outputOffset = output.length
                output.append(candidate.string)
                candidate.numberMetadata?.numericSpans.orEmpty().forEach { span ->
                    spans += span.copy(
                        inputStart = span.inputStart + match.inputStart,
                        inputEnd = span.inputEnd + match.inputStart,
                        outputStart = span.outputStart + outputOffset,
                        outputEnd = span.outputEnd + outputOffset,
                    )
                }
                inputOffset = match.inputEnd
            }
            output.append(input.substring(inputOffset))
            val familyKey = "time-sentence:" + candidates.mapIndexed { index, candidate ->
                "${matches[index].inputStart}-${matches[index].inputEnd}:" +
                    candidate.numberMetadata?.familyKey.orEmpty()
            }.joinToString("|")
            candidates.first().copy(
                string = output.toString(),
                commitText = output.toString(),
                length = input.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
                yomi = input,
                numberMetadata = candidates.first().numberMetadata!!.copy(
                    familyKey = familyKey,
                    numericSpans = spans,
                ),
            )
        }
    }

    private fun findComposedTimeMatches(input: String): List<ComposedTimeMatch> {
        if (input.length !in 4..64 || input.none { it in "ふぷんびょう" }) return emptyList()
        val allMatches = mutableListOf<ComposedTimeMatch>()
        for (start in input.indices) {
            if (!NumberReadingBoundary.isStartBoundary(input, start)) continue
            val lastEnd = minOf(input.length, start + 32)
            for (end in start + 4..lastEnd) {
                if (!hasTimeCounterReadingEndingAt(input, end)) continue
                if (!NumberReadingBoundary.isEndBoundary(input, end)) continue
                val candidates = composedTimeCandidates(input.substring(start, end))
                if (candidates.isNotEmpty()) allMatches += ComposedTimeMatch(start, end, candidates)
            }
        }

        // Prefer the longest reviewed sequence at each start, then keep only disjoint matches.
        // Ambiguous meanings within one exact span remain separate candidate families.
        val selected = mutableListOf<ComposedTimeMatch>()
        allMatches.groupBy(ComposedTimeMatch::inputStart).toSortedMap().forEach { (_, atStart) ->
            val longestEnd = atStart.maxOf(ComposedTimeMatch::inputEnd)
            val longest = atStart.filter { it.inputEnd == longestEnd }
            val mergedCandidates = longest.flatMap(ComposedTimeMatch::candidates)
                .distinctBy { it.numberMetadata?.familyKey }
            val match = longest.first().copy(candidates = mergedCandidates)
            if (selected.lastOrNull()?.inputEnd?.let { match.inputStart >= it } != false) {
                selected += match
            }
        }
        return selected
    }

    private fun hasTimeCounterReadingEndingAt(input: String, end: Int): Boolean =
        (end >= 2 && (input.regionMatches(end - 2, "ふん", 0, 2) ||
            input.regionMatches(end - 2, "ぷん", 0, 2))) ||
            (end >= 3 && input.regionMatches(end - 3, "びょう", 0, 3))

    private fun composedTimeCandidates(input: String): List<Candidate> {
        if (input.length !in 4..64 ||
            input.none { it in "ふぷんびょう" } ||
            !(input.endsWith("ふん") || input.endsWith("ぷん") || input.endsWith("びょう"))
        ) {
            return emptyList()
        }

        val sequences = mutableListOf<List<CounterPart>>()
        fun visit(offset: Int, parts: List<CounterPart>) {
            if (offset == input.length) {
                if (parts.size >= 2 && parts.map(CounterPart::counter) in supportedTimeSequences &&
                    isValidTimeValues(parts)
                ) {
                    sequences += parts
                }
                return
            }
            if (parts.size >= 3) return

            for (end in offset + 1..input.length) {
                val reading = input.substring(offset, end)
                val matches = buildList {
                    CounterReadingLexicon.matchAll(reading).forEach { item ->
                        if (item.counter in TIME_COUNTERS) {
                            add(
                                CounterPart(
                                    inputStart = offset,
                                    inputEnd = end,
                                    value = item.value.toLong(),
                                    counter = item.counter,
                                    interpretation = timeInterpretation(item.counter, item.interpretation),
                                ),
                            )
                        }
                    }
                    CounterReadingLexicon.suffixMatches(reading).forEach { (numberReading, suffix) ->
                        if (suffix.counter in TIME_COUNTERS) {
                            val value = numberReading.toNumber()?.second?.toLongOrNull() ?: return@forEach
                            add(
                                CounterPart(
                                    inputStart = offset,
                                    inputEnd = end,
                                    value = value,
                                    counter = suffix.counter,
                                    interpretation = timeInterpretation(suffix.counter, suffix.interpretation),
                                ),
                            )
                        }
                    }
                }.distinctBy { Triple(it.value, it.counter, it.interpretation) }

                for (match in matches) {
                    val nextParts = parts + match
                    val nextCounters = nextParts.map(CounterPart::counter)
                    if (isSupportedPrefix(nextCounters)) visit(end, nextParts)
                }
            }
        }
        visit(0, emptyList())

        return sequences.distinctBy { parts ->
            parts.joinToString("|") { "${it.interpretation}:${it.counter}:${it.value}" }
        }.map { parts -> createTimeCandidate(input, parts) }
    }

    private fun isSupportedPrefix(counters: List<String>): Boolean =
        supportedTimeSequences.any { sequence ->
            sequence.size >= counters.size && sequence.take(counters.size) == counters
        }

    private fun isValidTimeValues(parts: List<CounterPart>): Boolean {
        val counters = parts.map(CounterPart::counter)
        if ("時" in counters && parts.first().value !in 1L..29L) return false
        val minuteIndex = counters.indexOf("分")
        if (minuteIndex >= 0) {
            val isClockMinute = minuteIndex > 0 && counters[minuteIndex - 1] in setOf("時", "時間")
            val isDurationMinute = counters == listOf("分", "秒")
            if ((!isClockMinute && !isDurationMinute) || (isClockMinute && parts[minuteIndex].value !in 1L..59L)) {
                return false
            }
        }
        val secondIndex = counters.indexOf("秒")
        if (secondIndex >= 0 && parts[secondIndex].value !in 1L..59L) return false
        return true
    }

    private fun createTimeCandidate(input: String, parts: List<CounterPart>): Candidate {
        val surface = StringBuilder()
        var outputOffset = 0
        val spans = parts.map { part ->
            val digits = part.value.toString()
            surface.append(digits).append(part.counter)
            val span = NumberSpan(
                inputStart = part.inputStart,
                inputEnd = part.inputEnd,
                outputStart = outputOffset,
                outputEnd = outputOffset + digits.length,
                valueDigits = digits,
                digitSequence = false,
            )
            outputOffset += digits.length + part.counter.length
            span
        }
        val familyKey = "time:${parts.joinToString("|") { "${it.interpretation}:${it.counter}:${it.value}" }}"
        return Candidate(
            string = surface.toString(),
            type = CANDIDATE_TYPE_TIME,
            length = input.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
            score = 8_500,
            yomi = input,
            leftId = NUMBER_ARABIC_POS_ID,
            rightId = COUNTER_TIME_POS_ID,
            numberMetadata = NumberCandidateMetadata(
                familyKey = familyKey,
                origin = NumberCandidateOrigin.ENGINE_SUPPLEMENT,
                style = NumberStyle.HALF_WIDTH,
                numericSpans = spans,
                isFallback = true,
            ),
        )
    }

    private fun timeInterpretation(counter: String, interpretation: String): String =
        if (counter == "時") "時刻" else interpretation

    private fun addCandidate(
        matches: MutableMap<String, Candidate>,
        input: String,
        numericReadingEnd: Int,
        value: String,
        counter: String,
        interpretation: String,
    ) {
        val familyKey = "counter:$interpretation:$counter:$value"
        val surface = "$value$counter"
        val timeLike = counter in setOf("時", "分", "秒", "時間")
        val rightId = if (timeLike) COUNTER_TIME_POS_ID else COUNTER_GENERIC_POS_ID
        matches.putIfAbsent(
            familyKey,
            Candidate(
                string = surface,
                type = if (timeLike) CANDIDATE_TYPE_TIME else 18,
                length = input.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
                score = 8_500,
                yomi = input,
                leftId = NUMBER_ARABIC_POS_ID,
                rightId = rightId,
                numberMetadata = NumberCandidateMetadata(
                    familyKey = familyKey,
                    origin = NumberCandidateOrigin.ENGINE_SUPPLEMENT,
                    style = NumberStyle.HALF_WIDTH,
                    numericSpans = listOf(
                        NumberSpan(
                            inputStart = 0,
                            inputEnd = numericReadingEnd,
                            outputStart = 0,
                            outputEnd = value.length,
                            valueDigits = value,
                            digitSequence = value.length > 1 && value.startsWith('0'),
                        ),
                    ),
                    isFallback = true,
                ),
            ),
        )
    }

    private val TIME_COUNTERS = setOf("時", "時間", "分", "秒")
    private const val MAX_TIME_INTERPRETATIONS = 12
}
