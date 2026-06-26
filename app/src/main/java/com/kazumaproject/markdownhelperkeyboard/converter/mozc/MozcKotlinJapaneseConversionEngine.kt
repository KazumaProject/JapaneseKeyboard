package com.kazumaproject.markdownhelperkeyboard.converter.mozc

import com.kazumaproject.markdownhelperkeyboard.converter.api.JapaneseConversionEngine
import com.kazumaproject.markdownhelperkeyboard.converter.api.JapaneseConversionEngineId
import com.kazumaproject.markdownhelperkeyboard.converter.api.JapaneseConversionRequest
import com.kazumaproject.markdownhelperkeyboard.converter.api.JapaneseConversionRequestType
import com.kazumaproject.markdownhelperkeyboard.converter.api.JapaneseConversionResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter.MozcKotlinConversionOptions
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter.MozcKotlinConversionRequest
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.converter.MozcKotlinImmutableConverter
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.MozcDictionaryLookupOptions
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.RuntimeMozcLearnDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary.RuntimeMozcUserDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcCandidate
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.model.MozcSegments
import javax.inject.Inject

class MozcKotlinJapaneseConversionEngine @Inject constructor(
    private val immutableConverter: MozcKotlinImmutableConverter,
) : JapaneseConversionEngine {
    override suspend fun convert(
        request: JapaneseConversionRequest,
    ): JapaneseConversionResult {
        val result = immutableConverter.convert(request.toMozcKotlinRequest())
        val candidates = result.candidates.map { it.toLegacyCandidate() }
        val bunsetsuResult =
            if (request.requestType.hasBunsetsuResult()) {
                result.segments.toBunsetsuCandidateResult(candidates)
            } else {
                null
            }
        return JapaneseConversionResult(
            engineId = JapaneseConversionEngineId.MOZC_KOTLIN,
            candidates = candidates,
            bunsetsuResult = bunsetsuResult,
        )
    }
}

private fun JapaneseConversionRequest.toMozcKotlinRequest(): MozcKotlinConversionRequest =
    MozcKotlinConversionRequest(
        input = input,
        nBest = nBest,
        dictionaryOptions = MozcDictionaryLookupOptions(
            enablePersonName = mozcUtPersonName == true,
            enablePlaces = mozcUTPlaces == true,
            enableWiki = mozcUTWiki == true,
            enableNeologd = mozcUTNeologd == true,
            enableWeb = mozcUTWeb == true,
        ),
        runtimeUserDictionary = RuntimeMozcUserDictionary(userDictionaryRepository),
        runtimeLearnDictionary = RuntimeMozcLearnDictionary(learnRepository),
        options = MozcKotlinConversionOptions(
            isSingleSegment = requestType == JapaneseConversionRequestType.SUGGESTION ||
                    requestType == JapaneseConversionRequestType.WITHOUT_PREDICTION,
        ),
    )

private fun JapaneseConversionRequestType.hasBunsetsuResult(): Boolean =
    this == JapaneseConversionRequestType.CONVERSION_WITH_BUNSETSU ||
            this == JapaneseConversionRequestType.SUGGESTION_WITH_BUNSETSU ||
            this == JapaneseConversionRequestType.WITHOUT_PREDICTION_WITH_BUNSETSU

private fun MozcCandidate.toLegacyCandidate(): Candidate =
    Candidate(
        string = value,
        type = 1,
        length = key.length.toUByte(),
        score = cost,
        yomi = key,
        leftId = lid,
        rightId = rid,
    )

private fun MozcSegments.toBunsetsuCandidateResult(
    candidates: List<Candidate>,
): BunsetsuCandidateResult {
    val splitPositions = mutableListOf<Int>()
    var offset = 0
    conversionSegments().dropLast(1).forEach { segment ->
        offset += segment.key.length
        if (offset > 0) splitPositions += offset
    }
    val splitPatternByCandidateString = candidates.associate { it.string to splitPositions }
    return BunsetsuCandidateResult(
        candidates = candidates,
        splitPatterns = if (splitPositions.isEmpty()) emptyList() else listOf(splitPositions),
        splitPatternByCandidateString = splitPatternByCandidateString,
    )
}
