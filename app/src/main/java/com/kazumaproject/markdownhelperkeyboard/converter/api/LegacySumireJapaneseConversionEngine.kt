package com.kazumaproject.markdownhelperkeyboard.converter.api

import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import javax.inject.Inject

class LegacySumireJapaneseConversionEngine @Inject constructor(
    private val kanaKanjiEngine: KanaKanjiEngine,
) : JapaneseConversionEngine {
    override suspend fun convert(request: JapaneseConversionRequest): JapaneseConversionResult =
        when (request.requestType) {
            JapaneseConversionRequestType.CONVERSION -> JapaneseConversionResult(
                engineId = JapaneseConversionEngineId.LEGACY_SUMIRE,
                candidates = kanaKanjiEngine.getCandidatesOriginal(
                    input = request.input,
                    n = request.nBest,
                    mozcUtPersonName = request.mozcUtPersonName,
                    mozcUTPlaces = request.mozcUTPlaces,
                    mozcUTWiki = request.mozcUTWiki,
                    mozcUTNeologd = request.mozcUTNeologd,
                    mozcUTWeb = request.mozcUTWeb,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    isOmissionSearchEnable = request.isOmissionSearchEnable,
                    enableTypoCorrectionJapaneseFlick = request.enableTypoCorrectionJapaneseFlick,
                    enableTypoCorrectionQwertyEnglish = request.enableTypoCorrectionQwertyEnglish,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                ),
            )

            JapaneseConversionRequestType.CONVERSION_WITH_BUNSETSU -> {
                val result = kanaKanjiEngine.getCandidatesOriginalWithBunsetsu(
                    input = request.input,
                    n = request.nBest,
                    mozcUtPersonName = request.mozcUtPersonName,
                    mozcUTPlaces = request.mozcUTPlaces,
                    mozcUTWiki = request.mozcUTWiki,
                    mozcUTNeologd = request.mozcUTNeologd,
                    mozcUTWeb = request.mozcUTWeb,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    isOmissionSearchEnable = request.isOmissionSearchEnable,
                    enableTypoCorrectionJapaneseFlick = request.enableTypoCorrectionJapaneseFlick,
                    enableTypoCorrectionQwertyEnglish = request.enableTypoCorrectionQwertyEnglish,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                )
                JapaneseConversionResult(
                    engineId = JapaneseConversionEngineId.LEGACY_SUMIRE,
                    candidates = result.candidates,
                    bunsetsuResult = result,
                )
            }

            JapaneseConversionRequestType.SUGGESTION -> JapaneseConversionResult(
                engineId = JapaneseConversionEngineId.LEGACY_SUMIRE,
                candidates = kanaKanjiEngine.getCandidates(
                    input = request.input,
                    n = request.nBest,
                    mozcUtPersonName = request.mozcUtPersonName,
                    mozcUTPlaces = request.mozcUTPlaces,
                    mozcUTWiki = request.mozcUTWiki,
                    mozcUTNeologd = request.mozcUTNeologd,
                    mozcUTWeb = request.mozcUTWeb,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    isOmissionSearchEnable = request.isOmissionSearchEnable,
                    enableTypoCorrectionJapaneseFlick = request.enableTypoCorrectionJapaneseFlick,
                    enableTypoCorrectionQwertyEnglish = request.enableTypoCorrectionQwertyEnglish,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                ),
            )

            JapaneseConversionRequestType.SUGGESTION_WITH_BUNSETSU -> {
                val result = kanaKanjiEngine.getCandidatesWithBunsetsuSeparation(
                    input = request.input,
                    n = request.nBest,
                    mozcUtPersonName = request.mozcUtPersonName,
                    mozcUTPlaces = request.mozcUTPlaces,
                    mozcUTWiki = request.mozcUTWiki,
                    mozcUTNeologd = request.mozcUTNeologd,
                    mozcUTWeb = request.mozcUTWeb,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    isOmissionSearchEnable = request.isOmissionSearchEnable,
                    enableTypoCorrectionJapaneseFlick = request.enableTypoCorrectionJapaneseFlick,
                    enableTypoCorrectionQwertyEnglish = request.enableTypoCorrectionQwertyEnglish,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                )
                JapaneseConversionResult(
                    engineId = JapaneseConversionEngineId.LEGACY_SUMIRE,
                    candidates = result.candidates,
                    bunsetsuResult = result,
                )
            }

            JapaneseConversionRequestType.WITHOUT_PREDICTION -> JapaneseConversionResult(
                engineId = JapaneseConversionEngineId.LEGACY_SUMIRE,
                candidates = kanaKanjiEngine.getCandidatesWithoutPrediction(
                    input = request.input,
                    n = request.nBest,
                    mozcUtPersonName = request.mozcUtPersonName,
                    mozcUTPlaces = request.mozcUTPlaces,
                    mozcUTWiki = request.mozcUTWiki,
                    mozcUTNeologd = request.mozcUTNeologd,
                    mozcUTWeb = request.mozcUTWeb,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                ),
            )

            JapaneseConversionRequestType.WITHOUT_PREDICTION_WITH_BUNSETSU -> {
                val result = kanaKanjiEngine.getCandidatesWithoutPredictionWithBunsetsu(
                    input = request.input,
                    n = request.nBest,
                    mozcUtPersonName = request.mozcUtPersonName,
                    mozcUTPlaces = request.mozcUTPlaces,
                    mozcUTWiki = request.mozcUTWiki,
                    mozcUTNeologd = request.mozcUTNeologd,
                    mozcUTWeb = request.mozcUTWeb,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                )
                JapaneseConversionResult(
                    engineId = JapaneseConversionEngineId.LEGACY_SUMIRE,
                    candidates = result.candidates,
                    bunsetsuResult = result,
                )
            }
        }
}
