package com.kazumaproject.markdownhelperkeyboard.candidate_order.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.candidate_order.database.CandidateOrderOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderItem
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.SavedCandidateOrderGroup
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
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
    val candidates: List<CandidateOrderItem> = emptyList(),
    val savedOrders: List<SavedCandidateOrderGroup> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

internal data class CandidateOrderEditingState(
    val reading: String,
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
    return groupBy { it.input }
        .map { (input, rows) ->
            val sortedRows = rows.sortedBy { it.rank }
            SavedCandidateOrderGroup(
                input = input,
                candidates = sortedRows.map { it.candidate },
                updatedAt = sortedRows.maxOfOrNull { it.updatedAt } ?: 0L
            )
        }
        .sortedWith(
            compareByDescending<SavedCandidateOrderGroup> { it.updatedAt }
                .thenBy { it.input }
        )
}

internal fun SavedCandidateOrderGroup.toCandidateOrderEditingState(): CandidateOrderEditingState? {
    val normalizedInput = input.trim()
    if (normalizedInput.isEmpty() || candidates.isEmpty()) return null

    return CandidateOrderEditingState(
        reading = normalizedInput,
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
        _uiState.update { it.copy(reading = reading) }
    }

    fun editSavedOrder(savedOrder: SavedCandidateOrderGroup) {
        val editingState = savedOrder.toCandidateOrderEditingState() ?: return

        _uiState.update {
            it.copy(
                reading = editingState.reading,
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
                    omissionSearchOffsetScore = appPreference.omission_search_offset_score_preference
                )
            }
                .let { filterCandidateOrderEditableCandidates(reading, it) }

            val orderedCandidates = withContext(Dispatchers.IO) {
                candidateOrderOverrideRepository.applyOrder(reading, candidates)
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
                    message = if (orderedCandidates.isEmpty()) "候補が見つかりません" else null
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
        val candidates = uiState.value.candidates
        if (reading.isEmpty() || candidates.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            candidateOrderOverrideRepository.saveOrder(
                input = reading,
                candidates = candidates.map { it.candidate }
            )
            _uiState.update { it.copy(candidates = emptyList(), message = context.getString(R.string.candidate_order_override_saved)) }
        }
    }

    fun deleteSavedOrder(input: String) {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            candidateOrderOverrideRepository.deleteByInput(normalizedInput)
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
}
