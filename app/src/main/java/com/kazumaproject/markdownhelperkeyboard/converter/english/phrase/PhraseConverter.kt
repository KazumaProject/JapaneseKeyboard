// Adapted from kotlin-kana-kanji-converter at dbbb97f (MIT). See the bundled English dictionary notices.
package com.kazumaproject.markdownhelperkeyboard.converter.english.phrase

internal enum class SegmentKind { EXACT, COMPLETION, LITERAL }

internal data class Segment(
    val start: Int,
    val end: Int,
    val surface: String,
    val kind: SegmentKind,
    val reading: String? = null,
    val cost: Int = 0,
    val inferredSpace: Boolean = false,
    val splitDirectPrefix: Boolean = false,
    val unknown: Boolean = false,
)

internal data class PhraseCandidate(
    val text: String,
    val segments: List<Segment>,
    val dictionaryCost: Int,
    val grammar: PhraseEvidence,
)

internal class PhraseConverter(
    private val dictionary: EnglishPhraseDictionary,
    private val beamWidth: Int = 96,
    private val completionLimit: Int = 128,
) {
    private data class Lattice(
        val edges: Array<MutableList<Segment>>,
        val protectSingleWord: Boolean,
    )

    private class Path(
        val previous: Path? = null,
        val segment: Segment? = null,
        val cost: Int = 0,
        val evidence: PhraseEvidence = PhraseEvidence(),
        val splits: Int = 0,
        val splitDirectPrefix: Boolean = false,
        val unknownChars: Int = 0,
        val addedChars: Int = 0,
    ) {
        val segmentCount: Int = (previous?.segmentCount ?: -1) + 1
        private val textLength: Int = (previous?.textLength ?: 0) + (segment?.surface?.length ?: 0) +
            if (segment?.inferredSpace == true) 1 else 0
        private var rendered: String? = null

        fun copySurfacesInto(destination: Array<String?>) {
            var path = this
            for (index in segmentCount - 1 downTo 0) {
                destination[index] = checkNotNull(path.segment).surface
                path = checkNotNull(path.previous)
            }
        }

        fun render(): String {
            rendered?.let { return it }
            val characters = CharArray(textLength)
            var end = characters.size
            var path: Path? = this
            while (path != null) {
                val part = path.segment ?: break
                val surface = part.surface
                end -= surface.length
                surface.toCharArray(characters, end)
                if (part.inferredSpace) characters[--end] = ' '
                path = path.previous
            }
            return characters.concatToString().also { rendered = it }
        }

        fun collectSegments(): List<Segment> {
            val result = ArrayList<Segment>(segmentCount)
            var path: Path? = this
            while (path != null) {
                result.add(path.segment ?: break)
                path = path.previous
            }
            result.reverse()
            return result
        }
    }

    fun convert(text: String, prefixMode: Boolean, limit: Int): List<PhraseCandidate> {
        require(text.isNotBlank() && text.length <= MAX_INPUT) {
            "Text must contain between 1 and $MAX_INPUT UTF-16 code units and cannot be blank"
        }
        require(limit in 1..MAX_RESULTS) { "Limit must be in 1..$MAX_RESULTS" }

        val grammar = PhraseGrammar(dictionary.grammarRoles)
        val lattice = lattice(text, prefixMode)
        val edges = lattice.edges
        val lexicalSpacing = if (lattice.protectSingleWord) {
            dictionary.exact(lookupKey(text)).map { lookupKey(it.surface.replace('-', ' ')) }.toSet()
        } else emptySet()
        val order = if (prefixMode) prefixOrder else exactOrder
        val paths = Array(text.length + 1) { mutableListOf<Path>() }
        val surfaces = arrayOfNulls<String>(text.length)
        paths[0].add(Path())
        for (offset in text.indices) {
            val current = paths[offset]
            if (current.isEmpty()) continue
            for (segment in edges[offset]) {
                val next = paths[segment.end]
                val addedChars = if (segment.kind == SegmentKind.COMPLETION) {
                    var typed = 0
                    for (index in segment.start until segment.end) {
                        val character = text[index]
                        if (character !in keyboardPunctuation && character != '’') typed++
                    }
                    checkNotNull(segment.reading).length - typed
                } else {
                    0
                }
                for (path in current) {
                    path.copySurfacesInto(surfaces)
                    surfaces[path.segmentCount] = segment.surface
                    val nextPath = Path(
                        previous = path,
                        segment = segment,
                        cost = path.cost + segment.cost,
                        evidence = grammar.score(surfaces, path.segmentCount + 1),
                        splits = path.splits + if (segment.inferredSpace) 1 else 0,
                        splitDirectPrefix = path.splitDirectPrefix || segment.splitDirectPrefix,
                        unknownChars = path.unknownChars + if (segment.unknown) segment.end - segment.start else 0,
                        addedChars = path.addedChars + addedChars,
                    )
                    if (segment.end == text.length && lattice.protectSingleWord && nextPath.splits > 0) {
                        // Keep lexicalized phrases such as "good morning", including
                        // phrases present only in the bundled grammar lexicon. Arbitrary
                        // segmentation and suffix expansion are not evidence of a phrase.
                        if (nextPath.addedChars > 0) continue
                        val formatted = nextPath.render()
                        val key = lookupKey(formatted)
                        if (key !in lexicalSpacing && dictionary.exact(key).isEmpty() &&
                            dictionary.grammarRoles.lookup(formatted, key).isEmpty()) continue
                    }
                    next += nextPath
                }
                if (next.size > beamWidth) {
                    next.sortWith(order)
                    next.subList(beamWidth, next.size).clear()
                }
            }
            current.clear()
        }
        val finalPaths = paths.last()
        finalPaths.sortWith(order)
        return finalPaths.asSequence()
            .distinctBy(Path::render)
            .take(limit)
            .map { PhraseCandidate(it.render(), it.collectSegments(), it.cost, it.evidence) }
            .toList()
    }

    private fun lattice(text: String, prefixMode: Boolean): Lattice {
        val edges = Array(text.length) { mutableListOf<Segment>() }
        var protectSingleWord = false
        val spans = inputWords.findAll(text).map { it.range }.toMutableList()
        val last = spans.lastOrNull()
        if (prefixMode && last != null && last.last == text.lastIndex - 1 && text.last() in prefixPunctuation) {
            spans[spans.lastIndex] = last.first..text.lastIndex
        }
        val wordEnds = spans.map { it.last + 1 }.toSet()
        val exact = mutableMapOf<String, List<DictionaryCandidate>>()
        fun lookup(reading: String) = exact.getOrPut(reading) { dictionary.exact(reading) }
        var previousEnd = 0
        for (span in spans) {
            val start = span.first
            val end = span.last + 1
            if (previousEnd < start) edges[previousEnd] += literal(text, previousEnd, start)
            previousEnd = end

            val reading = lookupKey(text.substring(start, end))
            val plain = latinWord.matches(reading)
            val complete = prefixMode && end == text.length
            val directCompletions = if (complete) completions(reading, start, end, start) else emptyList()
            edges[start] += literal(text, start, end, unknown = true)

            edges[start] += directCompletions
            if (!plain) {
                val candidates = lookup(reading)
                val normalized = reading.filterNot { it in keyboardPunctuation }
                val restored = if (normalized == reading || normalized.isEmpty()) emptyList() else
                    lookup(normalized).filter { lookupKey(it.surface) == reading }
                edges[start] += (candidates + restored).distinct().map { it.segment(start, end, start) }
            } else {
                val whole = lookup(reading)
                if (spans.size == 1 && (whole.isNotEmpty() || directCompletions.isNotEmpty())) {
                    protectSingleWord = true
                }
                // Explicit word boundaries take precedence when the word is registered.
                if (spans.size > 1 && whole.isNotEmpty()) {
                    edges[start] += whole.map { it.segment(start, end, start) }
                } else {
                    val reachable = BooleanArray(end - start).apply { this[0] = true }
                    for (offset in start until end) {
                        if (!reachable[offset - start]) continue
                        val remaining = reading.substring(offset - start)
                        for (prefix in dictionary.prefixes(remaining)) {
                            val stop = offset + prefix.length
                            if ((offset != start || stop != end) && !canSplit(prefix)) continue
                            val candidates = lookup(prefix)
                            if (stop < end && candidates.isNotEmpty()) reachable[stop - start] = true
                            edges[offset] += candidates.map {
                                it.segment(offset, stop, start, splitDirectPrefix = directCompletions.isNotEmpty())
                            }
                        }
                        if (complete && offset > start) edges[offset] += completions(
                            remaining, offset, end, start, splitDirectPrefix = directCompletions.isNotEmpty(),
                        )
                    }
                }
            }

            val suffix = text.substring(start)
            for (phrase in dictionary.prefixes(suffix.lowercase())) {
                val stop = start + phrase.length
                if (' ' in phrase && stop in wordEnds && text.substring(start, stop).lowercase() == phrase) {
                    edges[start] += lookup(phrase).map { it.segment(start, stop, start) }
                }
            }
        }
        if (previousEnd < text.length) edges[previousEnd] += literal(text, previousEnd, text.length)
        return Lattice(edges, protectSingleWord)
    }

    private fun completions(
        reading: String, start: Int, end: Int, wordStart: Int, splitDirectPrefix: Boolean = false,
    ): List<Segment> {
        val key = reading.filterNot { it in prefixPunctuation }
        if (key.length < MIN_COMPLETION_PREFIX || !completionKey.matches(key)) return emptyList()
        val inferredBoundary = start != wordStart
        if (inferredBoundary && key.length < MIN_INFERRED_COMPLETION_PREFIX) return emptyList()
        val surfacePrefix = reading.takeIf { it != key }
        return dictionary.complete(key, surfacePrefix, completionLimit).filter {
            // Completing an explicitly typed word is different from guessing both a
            // boundary and a long replacement for a short suffix of a joined input.
            !inferredBoundary || it.reading.length - key.length <= MAX_INFERRED_COMPLETION_ADDITION
        }.map {
            it.segment(start, end, wordStart, SegmentKind.COMPLETION, splitDirectPrefix)
        }
    }

    private fun canSplit(reading: String) = reading.length >= 2 || reading == "a" || reading == "i"

    private fun literal(text: String, start: Int, end: Int, unknown: Boolean = false) =
        Segment(start, end, text.substring(start, end), SegmentKind.LITERAL, unknown = unknown)

    private fun DictionaryCandidate.segment(
        start: Int, end: Int, wordStart: Int, kind: SegmentKind = SegmentKind.EXACT, splitDirectPrefix: Boolean = false,
    ) = Segment(
        start, end, surface, kind, reading, cost, inferredSpace = start != wordStart,
        splitDirectPrefix = splitDirectPrefix && start != wordStart,
    )

    companion object {
        private const val MAX_INPUT = 256
        private const val MAX_RESULTS = 50
        private const val MIN_COMPLETION_PREFIX = 2
        private const val MIN_INFERRED_COMPLETION_PREFIX = 3
        private const val MAX_INFERRED_COMPLETION_ADDITION = 2
        private const val keyboardPunctuation = "'-./"
        private const val prefixPunctuation = "'’-"
        private val completionKey = Regex("[a-z0-9]+")
        private val latinWord = Regex("[a-z]+")
        private val inputWords = Regex("""[\p{L}\p{N}][\p{L}\p{N}\p{M}]*(?:['’./_-][\p{L}\p{N}][\p{L}\p{N}\p{M}]*)*""")
        private val coverageOrder = compareBy<Path> { it.unknownChars }
        private val grammarOrder = compareByDescending<Path> { it.evidence.clauses }
            .thenBy { if (it.evidence.clauses == 0) it.splits else 0 }
            .thenByDescending { it.evidence.score }
            .thenBy { it.splits }
        private val exactOrder = coverageOrder.then(grammarOrder)
            .thenBy { it.addedChars }.thenBy { it.cost }.thenBy(Path::render)
        private val prefixOrder = coverageOrder.thenBy { it.splitDirectPrefix }.then(grammarOrder)
            .thenBy { it.cost }.thenBy { it.addedChars }.thenBy(Path::render)
    }
}
