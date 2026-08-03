package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.CancellationException
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
    val predictionConfig: PredictionConfig = PredictionConfig(),
    val collectCandidateSegments: Boolean = false,
)

data class KanaKanjiQueryResult(
    val candidates: List<Candidate>,
    val bunsetsuResult: BunsetsuCandidateResult? = null,
    val candidateSegmentsByString: Map<String, List<CandidateConversionSegment>> = emptyMap(),
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

    internal fun committedInput(): String? = incrementalState?.committedInput()

    internal fun setAfterForwardDpForTest(callback: (() -> Unit)?) {
        incrementalState?.pathState?.afterForwardDpForTest = callback
    }

    suspend fun query(request: KanaKanjiQueryRequest): KanaKanjiQueryResult = mutex.withLock {
        incrementalState?.beginQueryTransaction()
        try {
            val result = when (request.mode) {
                CandidateQueryMode.EISUKANA -> KanaKanjiQueryResult(
                    candidates = engine.getCandidatesEnglishKana(
                        input = request.input,
                        predictionConfig = request.predictionConfig,
                    ),
                )

                CandidateQueryMode.NO_TAB_DEFAULT -> queryOriginal(request)
                CandidateQueryMode.PREDICTION -> queryPrediction(request)
                CandidateQueryMode.CONVERSION -> queryConversion(request)
            }
            incrementalState?.commitQueryTransaction()
            result
        } catch (cancellation: CancellationException) {
            // A completed graph has its own staged commit. Keep that newest frontier when only the
            // later path search was cancelled; an incomplete append still rolls back entirely.
            incrementalState?.rollbackQueryTransaction()
            throw cancellation
        } catch (throwable: Throwable) {
            // Unexpected engine/repository failures may violate invariants outside the tracked
            // append delta. Prefer a clean rebuild for those rare failures.
            incrementalState?.reset()
            throw throwable
        }
    }

    private suspend fun queryOriginal(request: KanaKanjiQueryRequest): KanaKanjiQueryResult {
        val segmentCollector = request.newCandidateSegmentCollector()
        return if (request.bunsetsuSeparation) {
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
                predictionConfig = request.predictionConfig,
                candidateSegmentCollector = segmentCollector,
            ).asQueryResult(segmentCollector)
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
                    predictionConfig = request.predictionConfig,
                    candidateSegmentCollector = segmentCollector,
                ),
                candidateSegmentsByString = segmentCollector.orEmpty(),
            )
        }
    }

    private suspend fun queryPrediction(request: KanaKanjiQueryRequest): KanaKanjiQueryResult {
        val segmentCollector = request.newCandidateSegmentCollector()
        return if (request.bunsetsuSeparation) {
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
                predictionConfig = request.predictionConfig,
                candidateSegmentCollector = segmentCollector,
            ).asQueryResult(segmentCollector)
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
                    predictionConfig = request.predictionConfig,
                    candidateSegmentCollector = segmentCollector,
                ),
                candidateSegmentsByString = segmentCollector.orEmpty(),
            )
        }
    }

    private suspend fun queryConversion(request: KanaKanjiQueryRequest): KanaKanjiQueryResult {
        val segmentCollector = request.newCandidateSegmentCollector()
        return if (request.bunsetsuSeparation) {
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
                predictionConfig = request.predictionConfig.copy(
                    japanesePredictionEnabled = false,
                    englishPredictionEnabled = false,
                ),
                candidateSegmentCollector = segmentCollector,
            ).asQueryResult(segmentCollector)
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
                    predictionConfig = request.predictionConfig.copy(
                        japanesePredictionEnabled = false,
                        englishPredictionEnabled = false,
                    ),
                    candidateSegmentCollector = segmentCollector,
                ),
                candidateSegmentsByString = segmentCollector.orEmpty(),
            )
        }
    }

    private fun KanaKanjiQueryRequest.newCandidateSegmentCollector():
        MutableMap<String, List<CandidateConversionSegment>>? =
        if (collectCandidateSegments) LinkedHashMap() else null

    private fun BunsetsuCandidateResult.asQueryResult(
        segmentCollector: Map<String, List<CandidateConversionSegment>>?,
    ): KanaKanjiQueryResult = KanaKanjiQueryResult(
        candidates = candidates,
        bunsetsuResult = this,
        candidateSegmentsByString = segmentCollector.orEmpty(),
    )
}
