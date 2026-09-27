package com.kazumaproject.markdownhelperkeyboard.converter.number

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber

/** Creates only readings backed by the explicit counter lexicon or a listed counter suffix. */
object NumberFallbackCandidateFactory {
    private const val NUMBER_ARABIC_POS_ID: Short = 2044
    private const val COUNTER_GENERIC_POS_ID: Short = 2011
    private const val COUNTER_TIME_POS_ID: Short = 2015

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

        return matches.values.toList()
    }

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
}
