package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TEXT_MACRO
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_TEMPLATE
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation

data class NumberPresentationResult(
    val candidates: List<Candidate>,
    val segmentsByCandidateString: Map<String, List<CandidateConversionSegment>>,
    val hasNumericFamilies: Boolean = false,
)

/**
 * Keeps conversion meaning order intact while putting one preferred surface per meaning first,
 * then the remaining surface styles, followed by explicitly generated numeric fallbacks.
 */
object NumberCandidatePresenter {
    private const val NUMBER_ARABIC_POS_ID = 2044
    private const val NUMBER_SEPARATED_POS_ID = 2045
    private const val NUMBER_KANJI_POS_ID = 2046
    private const val COUNTER_GENERIC_POS_ID = 2011
    private const val COUNTER_TIME_POS_ID = 2015
    private const val MAX_COMPLETED_PATHS = 96

    private data class NumericRun(
        val firstSegment: Int,
        val lastSegmentExclusive: Int,
        val inputStart: Int,
        val inputEnd: Int,
        val valueDigits: String,
        val digitSequence: Boolean,
        val commaSeparated: Boolean,
        val outputStart: Int,
        val outputEnd: Int,
        val counterSurface: String? = null,
        val counterIdentity: String? = null,
    )

    private data class CounterInterpretation(
        val value: Long,
        val counterSurface: String,
        val meaning: String,
    )

    private data class ParsedSurface(
        val digits: String,
        val digitSequence: Boolean,
        val commaSeparated: Boolean,
    )

    private data class Family(
        val key: String,
        val firstCandidate: Candidate,
        val runs: List<NumericRun>,
        val numericSpans: List<NumberSpan>,
        val segments: List<CandidateConversionSegment>?,
        val originalIndex: Int,
        val isFallback: Boolean,
        val candidatesByStyle: Map<NumberStyle, Candidate>,
    )

    /** Search more completed paths only for input that can contain a number or listed counter. */
    fun expandedSearchCount(input: String, requested: Int, additionsEnabled: Boolean = true): Int {
        if (requested <= 0) return 0
        if (!additionsEnabled) return requested
        val mayUseNumericAlternatives = input.any { it in '0'..'9' || it in '０'..'９' } ||
            containsNumericReading(input) ||
            CounterReadingLexicon.hasCounterReadingWithin(input)
        if (!mayUseNumericAlternatives) return requested
        return (requested.toLong() * 6L).coerceAtLeast(32L).coerceAtMost(MAX_COMPLETED_PATHS.toLong()).toInt()
    }

    fun shouldCollectSegments(input: String): Boolean =
        // Numeric input spans are also needed to keep bunsetsu boundaries out of the middle of a
        // number when generated presentation candidates are disabled.
        isPotentialNumericInput(input)

    fun isPotentialNumericInput(input: String): Boolean {
        if (input.isEmpty()) return false
        if (input.any { it in '0'..'9' || it in '０'..'９' }) return true
        if (containsNumericReading(input)) return true
        return CounterReadingLexicon.hasCounterReadingWithin(input)
    }

    private fun containsNumericReading(input: String): Boolean {
        if (input.toNumber() != null) return true
        val starts = input.indices.filter { NumberReadingBoundary.isStartBoundary(input, it) }
        for (start in starts) {
            for (end in start + 1..input.length) {
                val isReadingBoundary = NumberReadingBoundary.isEndBoundary(input, end)
                if (isReadingBoundary && input.substring(start, end).toNumber() != null) return true
            }
        }
        return false
    }

    fun semanticFamilyKey(segments: List<CandidateConversionSegment>): String? {
        val runs = numericRuns(segments)
        if (runs.isEmpty()) return null
        runs.singleOrNull()?.takeIf { run ->
            run.firstSegment == 0 &&
                run.lastSegmentExclusive == segments.size &&
                run.counterSurface == null
        }?.let { run ->
            return NumberCandidateFamilyKey.directDigits(run.valueDigits, run.inputStart)
        }
        val normalized = buildString {
            var segmentIndex = 0
            runs.forEachIndexed { runIndex, run ->
                while (segmentIndex < run.firstSegment) {
                    appendSegmentIdentity(segments[segmentIndex++])
                }
                append("<N:").append(run.valueDigits)
                    .append(if (run.digitSequence) ":digits>" else ":value>")
                run.counterIdentity?.let { append("<counter:").append(it).append('>') }
                append("<span:").append(run.inputStart).append('-').append(run.inputEnd).append('>')
                segmentIndex = run.lastSegmentExclusive
                if (runIndex == runs.lastIndex) {
                    while (segmentIndex < segments.size) {
                        appendSegmentIdentity(segments[segmentIndex++])
                    }
                }
            }
        }
        return "numeric:$normalized"
    }

    /**
     * Re-renders without adding candidates when disabled. This also makes a second application
     * safe: generated variants carry their family key and are removed or regrouped deterministically.
     */
    fun present(
        candidates: List<Candidate>,
        segmentsByCandidateString: Map<String, List<CandidateConversionSegment>>,
        config: NumberPresentationConfig,
        isNgWord: (Candidate) -> Boolean = { false },
        maxRepresentedMeanings: Int = Int.MAX_VALUE,
        maxDisplayedCandidates: Int = Int.MAX_VALUE,
    ): NumberPresentationResult {
        val normalizedConfig = config.normalized()
        val rawCandidates = candidates.filterNot { candidate ->
            !normalizedConfig.additionsEnabled &&
                candidate.numberMetadata?.origin in GENERATED_ORIGINS
        }

        if (!normalizedConfig.additionsEnabled) {
            val retained = rawCandidates.filterNot(isNgWord).distinctBy(::candidateIdentity)
            return NumberPresentationResult(
                candidates = retained,
                segmentsByCandidateString = segmentsByCandidateString.filterKeys { key ->
                    retained.any { it.string == key }
                },
                hasNumericFamilies = false,
            )
        }

        val familiesByKey = LinkedHashMap<String, Family>()
        val ordinary = mutableListOf<Pair<Int, Candidate>>()
        val simpleFallbacks = mutableListOf<Pair<Int, Candidate>>()

        rawCandidates.forEachIndexed { index, candidate ->
            if (!isTextConversionCandidate(candidate)) {
                if (!isNgWord(candidate)) ordinary += index to candidate
                return@forEachIndexed
            }

            val segments = segmentsByCandidateString[candidate.string]
                ?: candidate.yomi?.takeIf(String::isNotEmpty)?.let { syntheticSegments(candidate) }
            val runs = segments?.let(::numericRuns).orEmpty()
            val metadata = candidate.numberMetadata
            val familyKey = metadata?.familyKey ?: segments?.let(::semanticFamilyKey)
            val spans = metadata?.numericSpans?.takeIf { it.isNotEmpty() }
                ?: runs.map(::toNumberSpan)

            if (familyKey == null || spans.isEmpty()) {
                if (metadata?.origin == NumberCandidateOrigin.ENGINE_SUPPLEMENT) {
                    if (!isNgWord(candidate)) simpleFallbacks += index to candidate
                } else if (!isNgWord(candidate)) {
                    ordinary += index to candidate
                }
                return@forEachIndexed
            }

            val fallback = metadata?.isFallback == true ||
                metadata?.origin == NumberCandidateOrigin.ENGINE_SUPPLEMENT
            val style = metadata?.style ?: styleOf(runs, segments)
            val previous = familiesByKey[familyKey]
            if (previous == null) {
                familiesByKey[familyKey] = Family(
                    key = familyKey,
                    firstCandidate = candidate,
                    runs = runs,
                    numericSpans = spans,
                    segments = segments,
                    originalIndex = index,
                    isFallback = fallback,
                    candidatesByStyle = style?.let { mapOf(it to candidate) }.orEmpty(),
                )
            } else {
                val updated = previous.copy(
                    candidatesByStyle = if (style != null && style !in previous.candidatesByStyle) {
                        previous.candidatesByStyle + (style to candidate)
                    } else {
                        previous.candidatesByStyle
                    },
                )
                familiesByKey[familyKey] = updated
            }
        }

        val generatedSegments = LinkedHashMap(segmentsByCandidateString)
        fun styleCandidate(family: Family, style: NumberStyle): Candidate? {
            val existing = family.candidatesByStyle[style]
            if (existing != null) {
                val identified = when {
                    existing.numberMetadata == null -> existing.copy(
                        numberMetadata = NumberCandidateMetadata(
                            familyKey = family.key,
                            origin = if (family.isFallback) {
                                NumberCandidateOrigin.ENGINE_SUPPLEMENT
                            } else {
                                NumberCandidateOrigin.SYSTEM_PATH
                            },
                            style = style,
                            numericSpans = family.numericSpans,
                            isFallback = family.isFallback,
                        ),
                    )

                    !family.isFallback &&
                        existing.numberMetadata.origin == NumberCandidateOrigin.ENGINE_SUPPLEMENT ->
                        existing.copy(
                            numberMetadata = existing.numberMetadata.copy(
                                origin = NumberCandidateOrigin.PRESENTATION_VARIANT,
                                isFallback = false,
                            ),
                        )

                    else -> existing
                }
                if (identified.string !in generatedSegments) {
                    val candidateSegments = family.segments?.let { sourceSegments ->
                        if (identified.string == family.firstCandidate.string) {
                            sourceSegments
                        } else {
                            renderedSegments(sourceSegments, family.runs, style)
                        }
                    }
                    generatedSegments[identified.string] = candidateSegments ?: syntheticSegments(identified)
                }
                return identified
            }
            val rendered = renderFamily(family, style) ?: return null
            val renderedSegments = family.segments?.let { renderedSegments(it, family.runs, style) }
                ?: syntheticSegments(rendered)
            generatedSegments[rendered.string] = renderedSegments
            return rendered
        }

        fun allowedStyles(family: Family): List<Pair<NumberStyle, Candidate>> =
            normalizedConfig.styleOrder.mapNotNull { style ->
                val candidate = styleCandidate(family, style) ?: return@mapNotNull null
                if (isNgWord(candidate)) null else style to candidate
            }.distinctBy { it.second.string }

        val regularFamilies = familiesByKey.values.filterNot { it.isFallback }
        val fallbackFamilies = familiesByKey.values.filter { it.isFallback }

        data class RankedRepresentative(
            val originalIndex: Int,
            val candidate: Candidate,
            val family: Family?,
        )
        val rankedRepresentatives = buildList {
            ordinary.forEach { (index, candidate) ->
                add(RankedRepresentative(index, candidate, null))
            }
            regularFamilies.forEach { family ->
                val allowed = allowedStyles(family)
                allowed.firstOrNull()?.let { (_, candidate) ->
                    add(RankedRepresentative(family.originalIndex, candidate, family))
                }
            }
        }.sortedBy { it.originalIndex }
            .take(maxRepresentedMeanings.coerceAtLeast(1))
        val representatives = rankedRepresentatives.map { it.candidate }
        val visibleRegularFamilies = rankedRepresentatives.mapNotNull { it.family }

        val regularRepresentatives = visibleRegularFamilies.associateWith { family ->
            allowedStyles(family).firstOrNull()?.second
        }
        // Keep the alternative-list order independent from each family's source style:
        // style rank comes before semantic rank after the representatives.
        val regularAlternatives = buildList {
            normalizedConfig.styleOrder.forEach { style ->
                visibleRegularFamilies.forEach { family ->
                    val candidate = styleCandidate(family, style)
                    if (
                        candidate != null &&
                        candidate.string != regularRepresentatives[family]?.string &&
                        !isNgWord(candidate)
                    ) {
                        add(candidate)
                    }
                }
            }
        }

        val fallbackCandidates = buildList {
            normalizedConfig.styleOrder.forEach { style ->
                fallbackFamilies.forEach { family ->
                    val candidate = styleCandidate(family, style)
                    if (candidate != null && !isNgWord(candidate)) add(candidate)
                }
            }
            addAll(simpleFallbacks.sortedBy { it.first }.map { it.second })
        }

        val uncappedCandidates = (representatives + regularAlternatives + fallbackCandidates)
            .distinctBy(::candidateIdentity)
        // A number may expand into three surfaces per meaning, but its path search is deliberately
        // larger than the user's N-best setting. Keep the final UI list bounded after putting all
        // meaning representatives first. Ordinary conversion results are untouched when no
        // numeric family was found.
        val finalCandidates = if (familiesByKey.isEmpty()) {
            uncappedCandidates
        } else {
            uncappedCandidates.take(maxDisplayedCandidates.coerceAtLeast(1))
        }
        val finalSegments = finalCandidates.mapNotNull { candidate ->
            generatedSegments[candidate.string]?.let { candidate.string to it }
        }.toMap()
        return NumberPresentationResult(
            candidates = finalCandidates,
            segmentsByCandidateString = finalSegments,
            hasNumericFamilies = familiesByKey.isNotEmpty(),
        )
    }

    /** Input spans occupied by parsed numeric runs; used to avoid inserting bunsetsu cuts inside them. */
    fun numericInputSpans(segments: List<CandidateConversionSegment>): List<Pair<Int, Int>> =
        numericRuns(segments).map { it.inputStart to it.inputEnd }

    /** Keeps split positions in input-reading offsets and prevents cuts inside parsed numbers. */
    fun splitPositionsFromSegments(
        segments: List<CandidateConversionSegment>,
        isIndependentWordPos: (Short) -> Boolean,
    ): List<Int> {
        val numericSpans = numericInputSpans(segments)
        return segments.mapNotNull { segment ->
            val position = segment.inputStart
            if (
                position > 0 &&
                numericSpans.none { (start, end) -> position > start && position < end } &&
                segment.leftId?.let(isIndependentWordPos) == true
            ) {
                position
            } else {
                null
            }
        }.distinct().sorted()
    }

    fun filterSplitPositionsInsideNumericSpans(
        splitPositions: List<Int>,
        numericSpans: List<Pair<Int, Int>>,
    ): List<Int> = splitPositions.filterNot { position ->
        numericSpans.any { (start, end) -> position > start && position < end }
    }

    /** Applies the UI cap only after explicit candidate-order overrides have been applied. */
    fun limitForDisplay(
        candidates: List<Candidate>,
        config: NumberPresentationConfig,
        requestedMeanings: Int,
        hasNumericFamilies: Boolean = false,
    ): List<Candidate> {
        if (!config.additionsEnabled || !hasNumericFamilies) return candidates
        val maximum = requestedMeanings.coerceAtLeast(1) * NumberPresentationConfig.DEFAULT_STYLE_ORDER.size
        return candidates.take(maximum)
    }

    private fun renderFamily(family: Family, style: NumberStyle): Candidate? {
        val source = family.firstCandidate
        val renderedSegments = family.segments?.let { renderedSegments(it, family.runs, style) }
        val newString = if (renderedSegments != null) {
            renderedSegments.joinToString("") { it.output }
        } else {
            renderTextFromSpans(source.string, family.numericSpans, style) ?: return null
        }
        val transformedSpans = if (renderedSegments != null) {
            numericRuns(renderedSegments).map(::toNumberSpan)
        } else {
            transformSpans(family.numericSpans, style)
        }
        if (newString == source.string) {
            return if (source.numberMetadata != null) {
                source
            } else {
                source.copy(
                    numberMetadata = NumberCandidateMetadata(
                        familyKey = family.key,
                        origin = NumberCandidateOrigin.SYSTEM_PATH,
                        style = style,
                        numericSpans = transformedSpans,
                        isFallback = family.isFallback,
                    ),
                )
            }
        }
        return source.copy(
            string = newString,
            commitText = newString,
            type = presentationTypeForStyle(source.type, style),
            numberMetadata = NumberCandidateMetadata(
                familyKey = family.key,
                origin = NumberCandidateOrigin.PRESENTATION_VARIANT,
                style = style,
                numericSpans = transformedSpans,
                isFallback = family.isFallback,
            ),
        )
    }

    private fun presentationTypeForStyle(sourceType: Byte, style: NumberStyle): Byte =
        if (sourceType !in PRESENTATION_STYLE_TYPES) {
            sourceType
        } else {
            when (style) {
                NumberStyle.HALF_WIDTH -> 31
                NumberStyle.FULL_WIDTH -> 22
                NumberStyle.KANJI -> 17
            }.toByte()
        }

    private fun renderedSegments(
        segments: List<CandidateConversionSegment>,
        runs: List<NumericRun>,
        style: NumberStyle,
    ): List<CandidateConversionSegment>? {
        if (runs.isEmpty()) return null
        val result = segments.toMutableList()
        for (run in runs.asReversed()) {
            val rendered = renderNumber(run.valueDigits, run.digitSequence, run.commaSeparated, style)
                ?: return null
            result[run.firstSegment] = result[run.firstSegment].copy(
                output = rendered + run.counterSurface.orEmpty(),
            )
            for (index in run.firstSegment + 1 until run.lastSegmentExclusive) {
                result[index] = result[index].copy(output = "")
            }
        }
        return result
    }

    private fun renderTextFromSpans(
        source: String,
        spans: List<NumberSpan>,
        style: NumberStyle,
    ): String? {
        if (spans.isEmpty()) return null
        val result = StringBuilder(source)
        for (span in spans.sortedByDescending { it.outputStart }) {
            if (span.outputStart < 0 || span.outputEnd > result.length || span.outputStart > span.outputEnd) {
                return null
            }
            val rendered = renderNumber(
                span.valueDigits,
                span.digitSequence,
                span.commaSeparated,
                style,
            ) ?: return null
            result.replace(span.outputStart, span.outputEnd, rendered)
        }
        return result.toString()
    }

    private fun transformSpans(spans: List<NumberSpan>, style: NumberStyle): List<NumberSpan> {
        var precedingDelta = 0
        return spans.sortedBy { it.outputStart }.map { span ->
            val renderedLength = renderNumber(
                span.valueDigits,
                span.digitSequence,
                span.commaSeparated,
                style,
            )?.length ?: span.outputEnd - span.outputStart
            val transformed = span.copy(
                outputStart = span.outputStart + precedingDelta,
                outputEnd = span.outputStart + precedingDelta + renderedLength,
            )
            precedingDelta += renderedLength - (span.outputEnd - span.outputStart)
            transformed
        }
    }

    private fun numericRuns(segments: List<CandidateConversionSegment>): List<NumericRun> {
        val runs = mutableListOf<NumericRun>()
        var index = 0
        var outputOffset = 0
        while (index < segments.size) {
            val segment = segments[index]
            if (!isNumericSegment(segment)) {
                outputOffset += segment.output.length
                index++
                continue
            }

            val first = index
            val inputStart = segment.inputStart
            val outputStart = outputOffset
            val reading = StringBuilder()
            val surface = StringBuilder()
            var inputEnd = segment.inputEnd
            while (index < segments.size && isNumericSegment(segments[index])) {
                val current = segments[index]
                if (index > first && current.inputStart != inputEnd) break
                reading.append(current.reading)
                surface.append(current.output)
                inputEnd = current.inputEnd
                outputOffset += current.output.length
                index++
            }
            val parsed = parseDigits(surface.toString(), reading.toString())
            if (parsed != null) {
                runs += NumericRun(
                    firstSegment = first,
                    lastSegmentExclusive = index,
                    inputStart = inputStart,
                    inputEnd = inputEnd,
                    valueDigits = parsed.digits,
                    digitSequence = parsed.digitSequence,
                    commaSeparated = parsed.commaSeparated,
                    outputStart = outputStart,
                    outputEnd = outputOffset,
                )
            }
        }
        val explicitCounterRuns = explicitCounterRuns(segments)
        val numericRunsOutsideCounter = runs.filterNot { numericRun ->
            explicitCounterRuns.any { counterRun ->
                numericRun.firstSegment < counterRun.lastSegmentExclusive &&
                    counterRun.firstSegment < numericRun.lastSegmentExclusive
            }
        }
        return (numericRunsOutsideCounter + explicitCounterRuns).sortedBy { it.firstSegment }
    }

    /**
     * Dictionary paths can keep a number and counter together in one lexical node (e.g. 二枚),
     * or split them into number and counter nodes. Recognize only readings listed in the explicit
     * counter lexicon, then verify that the path output actually renders the same quantity.
     */
    private fun explicitCounterRuns(segments: List<CandidateConversionSegment>): List<NumericRun> {
        if (segments.isEmpty()) return emptyList()
        val outputOffsets = IntArray(segments.size + 1)
        segments.indices.forEach { index ->
            outputOffsets[index + 1] = outputOffsets[index] + segments[index].output.length
        }
        val matches = mutableListOf<NumericRun>()
        var start = 0
        while (start < segments.size) {
            if (segments[start].reading.isEmpty()) {
                start++
                continue
            }
            val reading = StringBuilder()
            var end = start
            var inputEnd = segments[start].inputStart
            while (end < segments.size && segments[end].reading.isNotEmpty()) {
                val segment = segments[end]
                if (end > start && segment.inputStart != inputEnd) break
                reading.append(segment.reading)
                inputEnd = segment.inputEnd
                val interpretations = counterInterpretations(reading.toString())
                if (interpretations.isNotEmpty()) {
                    val surface = segments.subList(start, end + 1).joinToString("") { it.output }
                    interpretations.forEach { interpretation ->
                        if (!surface.endsWith(interpretation.counterSurface)) return@forEach
                        val numberSurface = surface.removeSuffix(interpretation.counterSurface)
                        if (!matchesNumberSurface(numberSurface, interpretation.value)) return@forEach
                        matches += NumericRun(
                            firstSegment = start,
                            lastSegmentExclusive = end + 1,
                            inputStart = segments[start].inputStart,
                            inputEnd = segment.inputEnd,
                            valueDigits = interpretation.value.toString(),
                            digitSequence = false,
                            commaSeparated = false,
                            outputStart = outputOffsets[start],
                            outputEnd = outputOffsets[start] + numberSurface.length,
                            counterSurface = interpretation.counterSurface,
                            counterIdentity = "${interpretation.meaning}:${interpretation.counterSurface}",
                        )
                    }
                }
                end++
                if (reading.length > 48) break
            }
            start++
        }
        // Prefer the longest exact lexical match when a short reading is a prefix of another.
        return matches.sortedWith(
            compareBy<NumericRun> { it.firstSegment }
                .thenByDescending { it.lastSegmentExclusive - it.firstSegment },
        ).fold(mutableListOf()) { selected, candidate ->
            if (selected.none { existing ->
                    candidate.firstSegment < existing.lastSegmentExclusive &&
                        existing.firstSegment < candidate.lastSegmentExclusive
                }) {
                selected += candidate
            }
            selected
        }
    }

    private fun counterInterpretations(reading: String): List<CounterInterpretation> = buildList {
        CounterReadingLexicon.matchAll(reading).forEach { match ->
            add(CounterInterpretation(match.value.toLong(), match.counter, match.interpretation))
        }
        CounterReadingLexicon.suffixMatches(reading).forEach { (numberReading, suffix) ->
            val value = numberReading.toNumber()?.second?.toLongOrNull() ?: return@forEach
            add(CounterInterpretation(value, suffix.counter, suffix.interpretation))
        }
    }.distinct()

    private fun matchesNumberSurface(surface: String, value: Long): Boolean {
        if (surface.isEmpty()) return false
        val normalized = surface.map { char ->
            if (char in '０'..'９') (char.code - 0xFEE0).toChar() else char
        }.joinToString("")
        return normalized == value.toString() ||
            surface == value.toKanji() ||
            surface == value.convertToKanjiNotation()
    }

    private fun parseDigits(surface: String, reading: String): ParsedSurface? {
        val normalized = surface
            .map { char ->
                when (char) {
                    in '０'..'９' -> (char.code - 0xFEE0).toChar()
                    '＋' -> '+'
                    '－', '−' -> '-'
                    '．' -> '.'
                    '，' -> ','
                    else -> char
                }
            }.joinToString("")
        val commaSeparated = normalized.contains(',') || normalized.contains('，')
        val withoutCommas = normalized.replace(",", "")
        if (withoutCommas.matches(Regex("[+-]?\\d+(?:\\.\\d+)?"))) {
            val digitSequence = withoutCommas.startsWith('+') || withoutCommas.startsWith('-') ||
                withoutCommas.contains('.') ||
                (withoutCommas.count(Char::isDigit) > 1 && withoutCommas.trimStart('+', '-').startsWith('0'))
            return ParsedSurface(withoutCommas, digitSequence, commaSeparated)
        }
        val number = reading.toNumber()?.second ?: return null
        return ParsedSurface(number, digitSequence = false, commaSeparated = commaSeparated)
    }

    private fun renderNumber(
        digits: String,
        digitSequence: Boolean,
        commaSeparated: Boolean,
        style: NumberStyle,
    ): String? {
        val normalized = digits.map { char ->
            when (char) {
                in '０'..'９' -> (char.code - 0xFEE0).toChar()
                '－', '−' -> '-'
                '＋' -> '+'
                '．' -> '.'
                '，' -> ','
                else -> char
            }
        }.joinToString("")
        val withGrouping = if (commaSeparated && !normalized.contains('.') && !normalized.startsWith('-') && !normalized.startsWith('+')) {
            normalized.toLongOrNull()?.let(::groupThousands) ?: normalized
        } else normalized

        return when (style) {
            NumberStyle.HALF_WIDTH -> withGrouping
            NumberStyle.FULL_WIDTH -> withGrouping.map { char ->
                when (char) {
                    in '0'..'9' -> (char.code + 0xFEE0).toChar()
                    ',' -> '，'
                    '.' -> '．'
                    '-' -> '－'
                    else -> char
                }
            }.joinToString("")
            NumberStyle.KANJI -> {
                if (digitSequence || normalized.contains('.') || normalized.startsWith('-') || normalized.startsWith('+')) {
                    normalized.map { char ->
                        when (char) {
                            in '0'..'9' -> "〇一二三四五六七八九"[char - '0'].toString()
                            '.' -> "・"
                            '-' -> "−"
                            ',' -> "，"
                            else -> char.toString()
                        }
                    }.joinToString("")
                } else {
                    normalized.toLongOrNull()?.toKanji()
                }
            }
        }
    }

    private fun groupThousands(value: Long): String {
        val raw = value.toString()
        val sign = if (raw.startsWith('-')) "-" else ""
        val digits = raw.removePrefix("-")
        return sign + digits.reversed().chunked(3).joinToString(",").reversed()
    }

    private fun toNumberSpan(run: NumericRun): NumberSpan = NumberSpan(
        inputStart = run.inputStart,
        inputEnd = run.inputEnd,
        outputStart = run.outputStart,
        outputEnd = run.outputEnd,
        valueDigits = run.valueDigits,
        digitSequence = run.digitSequence,
        commaSeparated = run.commaSeparated,
    )

    private fun styleOf(
        runs: List<NumericRun>,
        segments: List<CandidateConversionSegment>?,
    ): NumberStyle? {
        if (runs.isEmpty() || segments == null) return null
        val styles = runs.map { run ->
            val text = segments.subList(run.firstSegment, run.lastSegmentExclusive)
                .joinToString("") { it.output }
                .removeSuffix(run.counterSurface.orEmpty())
            when {
                text.all { it in '0'..'9' || it in ",.+-−" } -> NumberStyle.HALF_WIDTH
                text.all { it in '０'..'９' || it in "，．＋－−" } -> NumberStyle.FULL_WIDTH
                text.all { it in "〇零一二三四五六七八九十百千万億兆京・−+" } -> NumberStyle.KANJI
                else -> null
            }
        }
        return styles.firstOrNull()?.takeIf { first -> styles.all { it == first } }
    }

    private fun isNumericSegment(segment: CandidateConversionSegment): Boolean =
        segment.leftId?.toInt()?.let(NUMBER_POS_IDS::contains) == true ||
            segment.rightId?.toInt()?.let(NUMBER_POS_IDS::contains) == true

    private fun StringBuilder.appendSegmentIdentity(segment: CandidateConversionSegment) {
        append(segment.output)
        if (isCounterSegment(segment)) {
            append("<counter:").append(segment.leftId).append(':').append(segment.rightId)
                .append(':').append(segment.reading).append('>')
        }
    }

    private fun isCounterSegment(segment: CandidateConversionSegment): Boolean {
        val counterPos = setOf(COUNTER_GENERIC_POS_ID, COUNTER_TIME_POS_ID)
        val counterPosMatch = segment.leftId?.toInt()?.let(counterPos::contains) == true ||
            segment.rightId?.toInt()?.let(counterPos::contains) == true
        return counterPosMatch &&
            segment.output in CounterReadingLexicon.counterSurfaces
    }

    private fun isTextConversionCandidate(candidate: Candidate): Boolean =
        candidate.presentation == null &&
            candidate.sourceId == null &&
            candidate.commitText == candidate.string &&
            candidate.type !in setOf(
                CANDIDATE_TYPE_USER_DICTIONARY,
                CANDIDATE_TYPE_USER_TEMPLATE,
                CANDIDATE_TYPE_TEXT_MACRO,
            )

    private fun candidateIdentity(candidate: Candidate): String =
        candidate.sourceId?.let { "action:$it" } ?: "text:${candidate.string}"

    private fun syntheticSegments(candidate: Candidate): List<CandidateConversionSegment> = listOf(
        CandidateConversionSegment(
            inputStart = 0,
            inputEnd = candidate.yomi?.length ?: candidate.length.toInt(),
            output = candidate.string,
            reading = candidate.yomi.orEmpty(),
            leftId = candidate.leftId,
            rightId = candidate.rightId,
        ),
    )

    private val NUMBER_POS_IDS = setOf(NUMBER_ARABIC_POS_ID, NUMBER_SEPARATED_POS_ID, NUMBER_KANJI_POS_ID)
    private val GENERATED_ORIGINS = setOf(
        NumberCandidateOrigin.ENGINE_SUPPLEMENT,
        NumberCandidateOrigin.PRESENTATION_VARIANT,
    )
    private val PRESENTATION_STYLE_TYPES = setOf(17, 22, 30, 31).map(Int::toByte).toSet()
}
