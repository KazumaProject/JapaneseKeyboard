package com.kazumaproject.markdownhelperkeyboard.candidate_order.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderItem
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderScope
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.SavedCandidateOrderGroup
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CandidateOrderOverrideUiState(
    val reading: String = "",
    val scope: CandidateOrderScope = CandidateOrderScope.EXACT_INPUT,
    val candidates: List<CandidateOrderItem> = emptyList(),
    val savedOrders: List<SavedCandidateOrderGroup> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

internal data class CandidateOrderEditingState(
    val reading: String,
    val scope: CandidateOrderScope,
    val candidates: List<CandidateOrderItem>
)

internal fun filterCandidateOrderEditableCandidates(
    reading: String,
    candidates: List<Candidate>
): List<Candidate> {
    return candidates
        .filter { candidate ->
            candidate.string.isNotBlank() &&
                    candidate.length.toInt() == reading.length
        }
        .distinctBy { it.string }
}

internal fun List<CandidateOrderOverrideEntity>.toSavedCandidateOrderGroups(): List<SavedCandidateOrderGroup> {
    return groupBy { it.input to CandidateOrderScope.fromDatabase(it.scope) }
        .map { (rule, rows) ->
            val sortedRows = rows.sortedBy { it.rank }
            SavedCandidateOrderGroup(
                input = rule.first,
                scope = rule.second,
                candidates = sortedRows.map { it.candidate },
                updatedAt = sortedRows.maxOfOrNull { it.updatedAt } ?: 0L
            )
        }
        .sortedWith(
            compareByDescending<SavedCandidateOrderGroup> { it.updatedAt }
                .thenBy { it.input }
                .thenBy { it.scope.name }
        )
}

internal fun SavedCandidateOrderGroup.toCandidateOrderEditingState(): CandidateOrderEditingState? {
    val normalizedInput = input.trim()
    if (normalizedInput.isEmpty() || candidates.isEmpty()) return null

    return CandidateOrderEditingState(
        reading = normalizedInput,
        scope = scope,
        candidates = candidates.mapIndexed { index, candidate ->
            CandidateOrderItem(
                candidate = candidate,
                originalIndex = index
            )
        }
    )
}

@HiltViewModel
class CandidateOrderOverrideViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kanaKanjiEngine: KanaKanjiEngine,
    private val appPreference: AppPreference,
    private val userDictionaryRepository: UserDictionaryRepository,
    private val learnRepository: LearnRepository,
    private val candidateOrderOverrideRepository: CandidateOrderOverrideRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CandidateOrderOverrideUiState())
    val uiState: StateFlow<CandidateOrderOverrideUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            candidateOrderOverrideRepository.observeAll()
                .collect { entities ->
                    _uiState.update {
                        it.copy(savedOrders = entities.toSavedCandidateOrderGroups())
                    }
                }
        }
    }

    fun updateReading(reading: String) {
        if (uiState.value.reading == reading) return
        _uiState.update { it.copy(reading = reading, candidates = emptyList()) }
    }

    fun updateScope(scope: CandidateOrderScope) {
        if (uiState.value.scope == scope) return
        _uiState.update { it.copy(scope = scope, candidates = emptyList(), message = null) }
    }

    fun editSavedOrder(savedOrder: SavedCandidateOrderGroup) {
        val editingState = savedOrder.toCandidateOrderEditingState() ?: return

        _uiState.update {
            it.copy(
                reading = editingState.reading,
                scope = editingState.scope,
                candidates = editingState.candidates,
                message = null
            )
        }
    }

    fun fetchCandidates() {
        val reading = uiState.value.reading.trim()
        if (reading.isEmpty()) {
            _uiState.update { it.copy(message = "読みを入力してください") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val scope = uiState.value.scope
            val candidateSegments = LinkedHashMap<String, List<CandidateConversionSegment>>()
            val candidates = withContext(Dispatchers.Default) {
                kanaKanjiEngine.getCandidates(
                    input = reading,
                    n = appPreference.n_best_preference ?: 8,
                    mozcUtPersonName = appPreference.mozc_ut_person_names_preference,
                    mozcUTPlaces = appPreference.mozc_ut_places_preference,
                    mozcUTWiki = appPreference.mozc_ut_wiki_preference,
                    mozcUTNeologd = appPreference.mozc_ut_neologd_preference,
                    mozcUTWeb = appPreference.mozc_ut_web_preference,
                    userDictionaryRepository = userDictionaryRepository,
                    learnRepository = learnRepository,
                    isOmissionSearchEnable = false,
                    enableTypoCorrectionJapaneseFlick = false,
                    enableTypoCorrectionQwertyEnglish = false,
                    typoCorrectionOffsetScore = appPreference
                        .enable_typo_correction_japanese_flick_keyboard_offset_score_preference,
                    omissionSearchOffsetScore = appPreference.omission_search_offset_score_preference,
                    beamWidth = appPreference.conversion_beam_width_preference,
                    candidateSegmentCollector = candidateSegments,
                )
            }
                .let { filterCandidateOrderEditableCandidates(reading, it) }

            val candidatesForScope = if (scope == CandidateOrderScope.LEXICAL_UNIT) {
                candidates.filterToSameStructureAsFirst(reading, candidateSegments)
            } else {
                candidates
            }

            val orderedCandidates = withContext(Dispatchers.IO) {
                candidateOrderOverrideRepository.applyOrder(
                    input = reading,
                    candidates = candidatesForScope,
                    scope = scope,
                )
            }

            _uiState.update {
                it.copy(
                    candidates = orderedCandidates.mapIndexed { index, candidate ->
                        CandidateOrderItem(
                            candidate = candidate.string,
                            originalIndex = index
                        )
                    },
                    isLoading = false,
                    message = when {
                        candidates.isEmpty() -> "候補が見つかりません"
                        scope == CandidateOrderScope.LEXICAL_UNIT && orderedCandidates.isEmpty() ->
                            "同じ語として安全に並び替えられる候補が見つかりません"
                        else -> null
                    }
                )
            }
        }
    }

    fun moveCandidate(from: Int, to: Int) {
        val current = uiState.value.candidates
        if (from !in current.indices || to !in current.indices) return

        val reordered = current.toMutableList()
        val item = reordered.removeAt(from)
        reordered.add(to, item)
        _uiState.update { it.copy(candidates = reordered) }
    }

    fun save() {
        val reading = uiState.value.reading.trim()
        val scope = uiState.value.scope
        val candidates = uiState.value.candidates
        if (reading.isEmpty() || candidates.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            candidateOrderOverrideRepository.saveOrder(
                input = reading,
                candidates = candidates.map { it.candidate },
                scope = scope,
            )
            _uiState.update { it.copy(candidates = emptyList(), message = context.getString(R.string.candidate_order_override_saved)) }
        }
    }

    fun deleteSavedOrder(input: String, scope: CandidateOrderScope) {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            candidateOrderOverrideRepository.deleteRule(normalizedInput, scope)
            _uiState.update {
                it.copy(
                    message = context.getString(
                        R.string.candidate_order_override_saved_order_deleted
                    )
                )
            }
        }
    }

    fun deleteAllSavedOrders() {
        viewModelScope.launch(Dispatchers.IO) {
            candidateOrderOverrideRepository.deleteAll()
            _uiState.update {
                it.copy(message = context.getString(R.string.candidate_order_override_delete_all_done))
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun List<Candidate>.filterToSameStructureAsFirst(
        reading: String,
        segmentsByString: Map<String, List<CandidateConversionSegment>>,
    ): List<Candidate> {
        val reference = firstOrNull() ?: return emptyList()
        val signature = segmentsByString[reference.string]?.boundarySignature(reading.length)
            ?: return emptyList()
        return filter { candidate ->
            segmentsByString[candidate.string]?.boundarySignature(reading.length) == signature
        }
    }

    private fun List<CandidateConversionSegment>.boundarySignature(
        inputLength: Int,
    ): List<Int>? {
        if (isEmpty()) return null
        var expectedStart = 0
        val result = ArrayList<Int>(size)
        for (segment in this) {
            if (segment.inputStart != expectedStart || segment.inputEnd <= segment.inputStart) {
                return null
            }
            result += segment.inputEnd
            expectedStart = segment.inputEnd
        }
        return result.takeIf { expectedStart == inputLength }
    }
}
