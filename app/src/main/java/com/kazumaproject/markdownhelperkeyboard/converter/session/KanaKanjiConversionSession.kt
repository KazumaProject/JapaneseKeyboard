package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ConversionBackend {
    LEGACY,
    INCREMENTAL_SESSION,
}

enum class CandidateQueryMode {
    NO_TAB_DEFAULT,
    PREDICTION,
    CONVERSION,
    EISUKANA,
}

data class KanaKanjiQueryRequest(
    val input: String,
    val mode: CandidateQueryMode,
    val bunsetsuSeparation: Boolean,
    val n: Int,
    val mozcUtPersonName: Boolean?,
    val mozcUtPlaces: Boolean?,
    val mozcUtWiki: Boolean?,
    val mozcUtNeologd: Boolean?,
    val mozcUtWeb: Boolean?,
    val userDictionaryRepository: UserDictionaryRepository,
    val learnRepository: LearnRepository?,
    val omissionSearchEnabled: Boolean,
    val typoCorrectionJapaneseFlickEnabled: Boolean,
    val typoCorrectionQwertyEnglishEnabled: Boolean,
    val typoCorrectionOffsetScore: Int,
    val omissionSearchOffsetScore: Int,
    val beamWidth: Int,
)

data class KanaKanjiQueryResult(
    val candidates: List<Candidate>,
    val bunsetsuResult: BunsetsuCandidateResult? = null,
)

/**
 * Owns mutable lattice/forward-DP state for one InputConnection session.
 *
 * Requests are serialized because GraphBuilder and FindPath mutate the graph nodes while searching.
 * The legacy backend deliberately supplies no session state and therefore keeps the previous engine
 * path.  The incremental backend supplies isolated state that survives candidate-mode changes.
 */
class KanaKanjiConversionSession(
    private val engine: KanaKanjiEngine,
    val backend: ConversionBackend,
) {
    private val mutex = Mutex()
    private val incrementalState = if (backend == ConversionBackend.INCREMENTAL_SESSION) {
        engine.createIncrementalSessionState()
    } else {
        null
    }

    internal fun enablePerformanceProbe() {
        incrementalState?.enablePerformanceProbe()
    }

    internal fun performanceSnapshot(): KanaKanjiEngine.IncrementalPerformanceSnapshot? =
        incrementalState?.performanceSnapshot()

    suspend fun query(request: KanaKanjiQueryRequest): KanaKanjiQueryResult = mutex.withLock {
        try {
            when (request.mode) {
                CandidateQueryMode.EISUKANA -> KanaKanjiQueryResult(
                    candidates = engine.getCandidatesEnglishKana(request.input),
                )

                CandidateQueryMode.NO_TAB_DEFAULT -> queryOriginal(request)
                CandidateQueryMode.PREDICTION -> queryPrediction(request)
                CandidateQueryMode.CONVERSION -> queryConversion(request)
            }
        } catch (throwable: Throwable) {
            // Graph construction and forward DP update state in place.  A collectLatest
            // cancellation (or any failure) must make the next request rebuild atomically.
            incrementalState?.reset()
            throw throwable
        }
    }

    private suspend fun queryOriginal(request: KanaKanjiQueryRequest): KanaKanjiQueryResult =
        if (request.bunsetsuSeparation) {
            engine.getCandidatesOriginalWithBunsetsu(
                input = request.input,
                n = request.n,
                mozcUtPersonName = request.mozcUtPersonName,
                mozcUTPlaces = request.mozcUtPlaces,
                mozcUTWiki = request.mozcUtWiki,
                mozcUTNeologd = request.mozcUtNeologd,
                mozcUTWeb = request.mozcUtWeb,
                userDictionaryRepository = request.userDictionaryRepository,
                learnRepository = request.learnRepository,
                isOmissionSearchEnable = request.omissionSearchEnabled,
                enableTypoCorrectionJapaneseFlick = request.typoCorrectionJapaneseFlickEnabled,
                enableTypoCorrectionQwertyEnglish = request.typoCorrectionQwertyEnglishEnabled,
                typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                beamWidth = request.beamWidth,
                incrementalSessionState = incrementalState,
            ).asQueryResult()
        } else {
            KanaKanjiQueryResult(
                candidates = engine.getCandidatesOriginal(
                    input = request.input,
                    n = request.n,
                    mozcUtPersonName = request.mozcUtPersonName,
                    mozcUTPlaces = request.mozcUtPlaces,
                    mozcUTWiki = request.mozcUtWiki,
                    mozcUTNeologd = request.mozcUtNeologd,
                    mozcUTWeb = request.mozcUtWeb,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    isOmissionSearchEnable = request.omissionSearchEnabled,
                    enableTypoCorrectionJapaneseFlick = request.typoCorrectionJapaneseFlickEnabled,
                    enableTypoCorrectionQwertyEnglish = request.typoCorrectionQwertyEnglishEnabled,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                    beamWidth = request.beamWidth,
                    incrementalSessionState = incrementalState,
                ),
            )
        }

    private suspend fun queryPrediction(request: KanaKanjiQueryRequest): KanaKanjiQueryResult =
        if (request.bunsetsuSeparation) {
            engine.getCandidatesWithBunsetsuSeparation(
                input = request.input,
                n = request.n,
                mozcUtPersonName = request.mozcUtPersonName,
                mozcUTPlaces = request.mozcUtPlaces,
                mozcUTWiki = request.mozcUtWiki,
                mozcUTNeologd = request.mozcUtNeologd,
                mozcUTWeb = request.mozcUtWeb,
                userDictionaryRepository = request.userDictionaryRepository,
                learnRepository = request.learnRepository,
                isOmissionSearchEnable = request.omissionSearchEnabled,
                enableTypoCorrectionJapaneseFlick = request.typoCorrectionJapaneseFlickEnabled,
                enableTypoCorrectionQwertyEnglish = request.typoCorrectionQwertyEnglishEnabled,
                typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                beamWidth = request.beamWidth,
                incrementalSessionState = incrementalState,
            ).asQueryResult()
        } else {
            KanaKanjiQueryResult(
                candidates = engine.getCandidates(
                    input = request.input,
                    n = request.n,
                    mozcUtPersonName = request.mozcUtPersonName,
                    mozcUTPlaces = request.mozcUtPlaces,
                    mozcUTWiki = request.mozcUtWiki,
                    mozcUTNeologd = request.mozcUtNeologd,
                    mozcUTWeb = request.mozcUtWeb,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    isOmissionSearchEnable = request.omissionSearchEnabled,
                    enableTypoCorrectionJapaneseFlick = request.typoCorrectionJapaneseFlickEnabled,
                    enableTypoCorrectionQwertyEnglish = request.typoCorrectionQwertyEnglishEnabled,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                    beamWidth = request.beamWidth,
                    incrementalSessionState = incrementalState,
                ),
            )
        }

    private suspend fun queryConversion(request: KanaKanjiQueryRequest): KanaKanjiQueryResult =
        if (request.bunsetsuSeparation) {
            engine.getCandidatesWithoutPredictionWithBunsetsu(
                input = request.input,
                n = request.n,
                mozcUtPersonName = request.mozcUtPersonName,
                mozcUTPlaces = request.mozcUtPlaces,
                mozcUTWiki = request.mozcUtWiki,
                mozcUTNeologd = request.mozcUtNeologd,
                mozcUTWeb = request.mozcUtWeb,
                userDictionaryRepository = request.userDictionaryRepository,
                learnRepository = request.learnRepository,
                typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                beamWidth = request.beamWidth,
                incrementalSessionState = incrementalState,
            ).asQueryResult()
        } else {
            KanaKanjiQueryResult(
                candidates = engine.getCandidatesWithoutPrediction(
                    input = request.input,
                    n = request.n,
                    mozcUtPersonName = request.mozcUtPersonName,
                    mozcUTPlaces = request.mozcUtPlaces,
                    mozcUTWiki = request.mozcUtWiki,
                    mozcUTNeologd = request.mozcUtNeologd,
                    mozcUTWeb = request.mozcUtWeb,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                    beamWidth = request.beamWidth,
                    incrementalSessionState = incrementalState,
                ),
            )
        }

    private fun BunsetsuCandidateResult.asQueryResult(): KanaKanjiQueryResult =
        KanaKanjiQueryResult(candidates = candidates, bunsetsuResult = this)
}
