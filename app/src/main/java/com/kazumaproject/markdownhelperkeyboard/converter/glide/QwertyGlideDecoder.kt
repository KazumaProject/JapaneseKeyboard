package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.qwerty_keyboard.glide.QwertyInputPointers
import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlin.math.ceil

class QwertyGlideDecoder(
    private val dictionaryProvider: QwertyGlideDictionaryProvider,
    private val options: QwertyGlideDecodeOptions = QwertyGlideDecodeOptions(),
    private val strokeNormalizer: QwertyGlideStrokeNormalizer = QwertyGlideStrokeNormalizer(options),
    private val probabilityBuilder: QwertyGlideKeyProbabilityBuilder = QwertyGlideKeyProbabilityBuilder(options),
    private val wordPathScorer: QwertyGlideWordPathScorer = QwertyGlideWordPathScorer(options),
    private val reranker: QwertyGlideCandidateReranker = QwertyGlideCandidateReranker()
) {
    fun decode(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo,
        previousText: String,
        limit: Int = options.maxResults
    ): List<Candidate> {
        if (inputPointers.points.size < 2 || proximityInfo.keys.isEmpty()) return emptyList()
        val stroke = strokeNormalizer.normalize(inputPointers, proximityInfo)
        if (stroke.points.size < 2) return emptyList()
        val pointProbabilities = probabilityBuilder.build(stroke, proximityInfo)
        if (pointProbabilities.isEmpty()) return emptyList()

        val startChars = pointProbabilities.first().take(3).map { it.char }
        val endChars = pointProbabilities.last().take(3).map { it.char }
        val rawUnits = stroke.rawLength / proximityInfo.averageKeyWidth.coerceAtLeast(1f)
        val minLength = options.minWordLength
        val maxLength = ceil(rawUnits * 1.8f + 5f).toInt().coerceIn(
            options.minWordLength,
            options.maxWordLength
        )

        val scored = ArrayList<QwertyGlideScoredWord>()
        for (first in startChars) {
            for (last in endChars) {
                dictionaryProvider
                    .entriesFor(first, last, minLength, maxLength)
                    .forEach { entry ->
                        val score = wordPathScorer.score(
                            entry = entry,
                            stroke = stroke,
                            pointProbabilities = pointProbabilities,
                            proximityInfo = proximityInfo
                        )
                        if (score != null) scored.add(score)
                    }
            }
        }

        return reranker
            .rerank(scored, previousText, limit.coerceAtMost(options.maxResults))
            .map { scoredWord ->
                Candidate(
                    string = scoredWord.entry.word,
                    type = 36.toByte(),
                    length = scoredWord.entry.word.length.toUByte(),
                    score = (scoredWord.totalCost * 1000f).toInt().coerceAtLeast(1)
                )
            }
    }
}
