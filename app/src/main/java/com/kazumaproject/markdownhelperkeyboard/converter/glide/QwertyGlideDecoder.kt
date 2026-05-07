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
    private val reranker: QwertyGlideCandidateReranker = QwertyGlideCandidateReranker(),
    private val prefilter: QwertyGlideCandidatePrefilter = QwertyGlideCandidatePrefilter(options),
    private val cache: QwertyGlideDecodeCache = QwertyGlideDecodeCache(),
    private val dictionaryReady: Boolean = true,
    private val metricsListener: ((QwertyGlideDecodeMetrics) -> Unit)? = null
) {
    fun decode(
        inputPointers: QwertyInputPointers,
        proximityInfo: QwertyKeyboardProximityInfo,
        previousText: String,
        limit: Int = options.maxResults
    ): List<Candidate> {
        val startedAt = System.nanoTime()
        if (inputPointers.points.size < 2 || proximityInfo.keys.isEmpty()) return emptyList()
        val stroke = strokeNormalizer.normalize(inputPointers, proximityInfo)
        if (stroke.points.size < 2) return emptyList()
        val cacheKey = QwertyGlideDecodeCache.keyOf(stroke, proximityInfo, previousText)
        cache.get(cacheKey)?.let { cached ->
            emitMetrics(
                startedAt = startedAt,
                rawBucketCandidateCount = cached.size,
                prefilterCandidateCount = cached.size,
                fullScoreCandidateCount = 0,
                rerankCandidateCount = cached.size,
                prefilterMs = 0L,
                fullScoreMs = 0L,
                rerankMs = 0L,
                cacheHit = true
            )
            return cached.take(limit)
        }

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

        val prefilterStartedAt = System.nanoTime()
        val indexedProvider = dictionaryProvider as? QwertyGlideIndexedDictionaryProvider
        val rawBucketCandidateCount: Int
        val fullScoreInputs: List<QwertyGlideDictionaryEntry>
        if (indexedProvider != null) {
            val rawEntries = indexedProvider.indexedEntriesFor(startChars, endChars, minLength, maxLength)
            rawBucketCandidateCount = rawEntries.size
            fullScoreInputs = prefilter
                .prefilter(
                    entries = rawEntries,
                    stroke = stroke,
                    pointProbabilities = pointProbabilities,
                    proximityInfo = proximityInfo,
                    targetCount = options.fullScoreCandidateLimit
                )
                .map { it.entry.asDictionaryEntry() }
        } else {
            val rawEntries = ArrayList<QwertyGlideDictionaryEntry>()
            val seen = HashSet<String>()
            for (first in startChars) {
                for (last in endChars) {
                    dictionaryProvider
                        .entriesFor(first, last, minLength, maxLength)
                        .forEach { entry ->
                            if (seen.add(entry.word)) rawEntries.add(entry)
                        }
                }
            }
            rawBucketCandidateCount = rawEntries.size
            fullScoreInputs = rawEntries
        }
        val prefilterMs = elapsedMs(prefilterStartedAt)

        val fullScoreStartedAt = System.nanoTime()
        val scored = ArrayList<QwertyGlideScoredWord>()
        val keyByChar = proximityInfo.keys.associateBy { it.char }
        val keyScale = proximityInfo.normalizedKeyScale()
        for (entry in fullScoreInputs) {
            val score = wordPathScorer.score(
                entry = entry,
                stroke = stroke,
                pointProbabilities = pointProbabilities,
                proximityInfo = proximityInfo,
                keyByChar = keyByChar,
                keyScale = keyScale
            )
            if (score != null) {
                scored.add(score)
            }
        }
        val fullScoreMs = elapsedMs(fullScoreStartedAt)

        val rerankStartedAt = System.nanoTime()
        val candidates = reranker
            .rerank(scored, previousText, options.maxResults)
            .map { scoredWord ->
                Candidate(
                    string = scoredWord.entry.word,
                    type = 36.toByte(),
                    length = scoredWord.entry.word.length.toUByte(),
                    score = (scoredWord.totalCost * 1000f).toInt().coerceAtLeast(1)
                )
            }
        val rerankMs = elapsedMs(rerankStartedAt)
        cache.put(cacheKey, candidates)
        emitMetrics(
            startedAt = startedAt,
            rawBucketCandidateCount = rawBucketCandidateCount,
            prefilterCandidateCount = fullScoreInputs.size,
            fullScoreCandidateCount = fullScoreInputs.size,
            rerankCandidateCount = scored.size,
            prefilterMs = prefilterMs,
            fullScoreMs = fullScoreMs,
            rerankMs = rerankMs,
            cacheHit = false
        )
        return candidates.take(limit.coerceAtMost(options.maxResults))
    }

    fun clearCache() {
        cache.clear()
    }

    private fun emitMetrics(
        startedAt: Long,
        rawBucketCandidateCount: Int,
        prefilterCandidateCount: Int,
        fullScoreCandidateCount: Int,
        rerankCandidateCount: Int,
        prefilterMs: Long,
        fullScoreMs: Long,
        rerankMs: Long,
        cacheHit: Boolean
    ) {
        metricsListener?.invoke(
            QwertyGlideDecodeMetrics(
                dictionaryReady = dictionaryReady,
                rawBucketCandidateCount = rawBucketCandidateCount,
                prefilterCandidateCount = prefilterCandidateCount,
                fullScoreCandidateCount = fullScoreCandidateCount,
                rerankCandidateCount = rerankCandidateCount,
                decodeTotalMs = elapsedMs(startedAt),
                prefilterMs = prefilterMs,
                fullScoreMs = fullScoreMs,
                rerankMs = rerankMs,
                cacheHit = cacheHit
            )
        )
    }

    private fun elapsedMs(startedAt: Long): Long {
        return (System.nanoTime() - startedAt) / 1_000_000L
    }
}
