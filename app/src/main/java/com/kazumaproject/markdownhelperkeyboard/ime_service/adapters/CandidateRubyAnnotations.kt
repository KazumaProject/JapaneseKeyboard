package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/** UTF-16 ranges in the unpadded candidate text; a compound kanji word is one annotation. */
internal data class CandidateRubyAnnotation(val start: Int, val end: Int, val reading: String)

/** Null means correspondence is unavailable and the caller should use the original whole reading. */
internal fun resolveCandidateRubyAnnotations(
    output: String,
    reading: String,
    segments: List<CandidateConversionSegment>,
): List<CandidateRubyAnnotation>? {
    if (segments.isEmpty() || reading.isEmpty()) return null
    var inputEnd = 0
    var outputEnd = 0
    for (segment in segments) {
        if (segment.inputStart != inputEnd || segment.inputEnd <= inputEnd ||
            segment.inputEnd > reading.length || segment.output.isEmpty() ||
            !reading.isCodePointBoundary(segment.inputStart) ||
            !reading.isCodePointBoundary(segment.inputEnd) ||
            !output.startsWith(segment.output, outputEnd) ||
            !output.isCodePointBoundary(outputEnd + segment.output.length)
        ) return null
        inputEnd = segment.inputEnd
        outputEnd += segment.output.length
    }
    if (inputEnd != reading.length || outputEnd != output.length) return null

    val annotations = mutableListOf<CandidateRubyAnnotation>()
    var offset = 0
    for (segment in segments) {
        val yomi = reading.substring(segment.inputStart, segment.inputEnd)
        annotations += alignSegment(segment.output, yomi).map {
            it.copy(start = it.start + offset, end = it.end + offset)
        }
        offset += segment.output.length
    }
    return annotations
}

private data class RubyToken(val start: Int, val end: Int, val kanji: Boolean)

private fun alignSegment(output: String, reading: String): List<CandidateRubyAnnotation> {
    val tokens = mutableListOf<RubyToken>()
    var index = 0
    while (index < output.length) {
        val start = index
        val kanji = isRubyKanji(output.codePointAt(index))
        do {
            index += Character.charCount(output.codePointAt(index))
        } while (index < output.length && isRubyKanji(output.codePointAt(index)) == kanji)
        tokens += RubyToken(start, index, kanji)
    }
    if (tokens.none { it.kanji }) return emptyList()
    val fallback = listOf(CandidateRubyAnnotation(0, output.length, reading))
    // Bound work for unusual dictionary entries, without ever guessing a correspondence.
    if (output.length > 512 || reading.length > 512) return fallback
    val normalizedReading = reading.toRubyKana()
    val literalTokens = tokens.map { output.substring(it.start, it.end).toRubyKana() }
    val counts = Array(tokens.size + 1) { IntArray(reading.length + 1) }
    counts[tokens.size][reading.length] = 1
    for (tokenIndex in tokens.indices.reversed()) {
        val token = tokens[tokenIndex]
        val literal = literalTokens[tokenIndex]
        var suffixCount = 0
        for (position in reading.length downTo 0) {
            if (position < reading.length && reading.isCodePointBoundary(position + 1)) {
                suffixCount = (suffixCount + counts[tokenIndex + 1][position + 1]).coerceAtMost(2)
            }
            if (!reading.isCodePointBoundary(position)) continue
            if (!token.kanji) {
                val end = position + literal.length
                if (end <= reading.length && normalizedReading.startsWith(literal, position)) {
                    counts[tokenIndex][position] = counts[tokenIndex + 1][end]
                }
            } else {
                counts[tokenIndex][position] = suffixCount
            }
        }
    }
    if (counts[0][0] != 1) return fallback
    var position = 0
    val result = mutableListOf<CandidateRubyAnnotation>()
    tokens.forEachIndexed { tokenIndex, token ->
        if (token.kanji) {
            val end = (position + 1..reading.length).first {
                reading.isCodePointBoundary(it) && counts[tokenIndex + 1][it] == 1
            }
            result += CandidateRubyAnnotation(token.start, token.end, reading.substring(position, end))
            position = end
        } else {
            position += literalTokens[tokenIndex].length
        }
    }
    return result
}

private fun isRubyKanji(codePoint: Int): Boolean =
    Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN || codePoint == 0x3005

private fun String.isCodePointBoundary(index: Int): Boolean =
    index == 0 || index == length || !(this[index].isLowSurrogate() && this[index - 1].isHighSurrogate())

/** Katakana dictionary literals can correspond to hiragana input, with the same UTF-16 offsets. */
private fun String.toRubyKana(): String = buildString(length) {
    for (char in this@toRubyKana) append(if (char in '\u30a1'..'\u30f6') char - 0x60 else char)
}
