package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.NBestCandidateCollector
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.date.DateCandidateComposer
import com.kazumaproject.markdownhelperkeyboard.converter.date.DateCandidateConfig
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
    val enabledDictionaryCategories: Set<DictionaryCategory> = emptySet(),
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
    val dateCandidateConfig: DateCandidateConfig = DateCandidateConfig(),
    /** Null uses automatic segmentation; an empty list requests one whole segment. */
    val forcedSplitPositions: List<Int>? = null,
)

data class KanaKanjiQueryResult(
    val candidates: List<Candidate>,
    val bunsetsuResult: BunsetsuCandidateResult? = null,
    val candidateSegmentsByString: Map<String, List<CandidateConversionSegment>> = emptyMap(),
    val bestNBestCandidate: Candidate? = null,
    val nBestSelectionNs: Long? = null,
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

    private var measureNBestSelection = false

    internal fun enablePerformanceProbe() {
        measureNBestSelection = true
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
            val nBestCollector = NBestCandidateCollector(measureNBestSelection)
            val result = if (request.forcedSplitPositions != null && request.mode != CandidateQueryMode.EISUKANA) {
                val collector = request.newCandidateSegmentCollector()
                engine.getCandidatesWithForcedBoundaries(
                    input = request.input,
                    splitPositions = request.forcedSplitPositions,
                    n = request.n,
                    enabledDictionaryCategories = request.enabledDictionaryCategories,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    isOmissionSearchEnable = request.omissionSearchEnabled,
                    enableTypoCorrectionJapaneseFlick = request.typoCorrectionJapaneseFlickEnabled,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                    beamWidth = request.beamWidth,
                    candidateSegmentCollector = collector,
                ).asQueryResult(collector)
            } else when (request.mode) {
                CandidateQueryMode.EISUKANA -> KanaKanjiQueryResult(
                    candidates = engine.getCandidatesEnglishKana(
                        input = request.input,
                        predictionConfig = request.predictionConfig,
                    ),
                )

                CandidateQueryMode.NO_TAB_DEFAULT -> queryOriginal(request, nBestCollector)
                CandidateQueryMode.PREDICTION -> queryPrediction(request, nBestCollector)
                CandidateQueryMode.CONVERSION -> queryConversion(request, nBestCollector)
            }
            val composedResult = result.copy(
                bestNBestCandidate = nBestCollector.best,
                nBestSelectionNs = nBestCollector.selectionNs,
                candidates = if (request.forcedSplitPositions != null) result.candidates else
                    DateCandidateComposer.compose(request.input, result.candidates, request.dateCandidateConfig),
                bunsetsuResult = result.bunsetsuResult?.let { bunsetsuResult ->
                    if (request.forcedSplitPositions != null) bunsetsuResult else bunsetsuResult.copy(
                        candidates = DateCandidateComposer.compose(
                            request.input, bunsetsuResult.candidates, request.dateCandidateConfig,
                        ),
                    )
                },
            )
            incrementalState?.commitQueryTransaction()
            composedResult
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

    private suspend fun queryOriginal(request: KanaKanjiQueryRequest, nBestCollector: NBestCandidateCollector): KanaKanjiQueryResult {
        val segmentCollector = request.newCandidateSegmentCollector()
        return if (request.bunsetsuSeparation) {
            engine.getCandidatesOriginalWithBunsetsu(
                input = request.input,
                n = request.n,
                enabledDictionaryCategories = request.enabledDictionaryCategories,
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
                nBestCandidateCollector = nBestCollector,
            ).asQueryResult(segmentCollector)
        } else {
            KanaKanjiQueryResult(
                candidates = engine.getCandidatesOriginal(
                    input = request.input,
                    n = request.n,
                    enabledDictionaryCategories = request.enabledDictionaryCategories,
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
                    nBestCandidateCollector = nBestCollector,
                ),
                candidateSegmentsByString = segmentCollector.orEmpty(),
            )
        }
    }

    private suspend fun queryPrediction(request: KanaKanjiQueryRequest, nBestCollector: NBestCandidateCollector): KanaKanjiQueryResult {
        val segmentCollector = request.newCandidateSegmentCollector()
        return if (request.bunsetsuSeparation) {
            engine.getCandidatesWithBunsetsuSeparation(
                input = request.input,
                n = request.n,
                enabledDictionaryCategories = request.enabledDictionaryCategories,
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
                nBestCandidateCollector = nBestCollector,
            ).asQueryResult(segmentCollector)
        } else {
            KanaKanjiQueryResult(
                candidates = engine.getCandidates(
                    input = request.input,
                    n = request.n,
                    enabledDictionaryCategories = request.enabledDictionaryCategories,
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
                    nBestCandidateCollector = nBestCollector,
                ),
                candidateSegmentsByString = segmentCollector.orEmpty(),
            )
        }
    }

    private suspend fun queryConversion(request: KanaKanjiQueryRequest, nBestCollector: NBestCandidateCollector): KanaKanjiQueryResult {
        val segmentCollector = request.newCandidateSegmentCollector()
        return if (request.bunsetsuSeparation) {
            engine.getCandidatesWithoutPredictionWithBunsetsu(
                input = request.input,
                n = request.n,
                enabledDictionaryCategories = request.enabledDictionaryCategories,
                userDictionaryRepository = request.userDictionaryRepository,
                learnRepository = request.learnRepository,
                isOmissionSearchEnable = request.omissionSearchEnabled,
                enableTypoCorrectionJapaneseFlick = request.typoCorrectionJapaneseFlickEnabled,
                typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                beamWidth = request.beamWidth,
                incrementalSessionState = incrementalState,
                predictionConfig = request.predictionConfig.copy(
                    japanesePredictionEnabled = false,
                    englishPredictionEnabled = false,
                ),
                candidateSegmentCollector = segmentCollector,
                nBestCandidateCollector = nBestCollector,
            ).asQueryResult(segmentCollector)
        } else {
            KanaKanjiQueryResult(
                candidates = engine.getCandidatesWithoutPrediction(
                    input = request.input,
                    n = request.n,
                    enabledDictionaryCategories = request.enabledDictionaryCategories,
                    userDictionaryRepository = request.userDictionaryRepository,
                    learnRepository = request.learnRepository,
                    isOmissionSearchEnable = request.omissionSearchEnabled,
                    enableTypoCorrectionJapaneseFlick = request.typoCorrectionJapaneseFlickEnabled,
                    typoCorrectionOffsetScore = request.typoCorrectionOffsetScore,
                    omissionSearchOffsetScore = request.omissionSearchOffsetScore,
                    beamWidth = request.beamWidth,
                    incrementalSessionState = incrementalState,
                    predictionConfig = request.predictionConfig.copy(
                        japanesePredictionEnabled = false,
                        englishPredictionEnabled = false,
                    ),
                    candidateSegmentCollector = segmentCollector,
                    nBestCandidateCollector = nBestCollector,
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
