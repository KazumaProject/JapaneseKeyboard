package com.kazumaproject.markdownhelperkeyboard.candidate_order.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.candidate_order.model.CandidateOrderItem
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.CandidateOrderOverrideRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class CandidateOrderOverrideViewModel @Inject constructor(
    private val kanaKanjiEngine: KanaKanjiEngine,
    private val appPreference: AppPreference,
    private val userDictionaryRepository: UserDictionaryRepository,
    private val learnRepository: LearnRepository,
    private val candidateOrderOverrideRepository: CandidateOrderOverrideRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CandidateOrderOverrideUiState())
    val uiState: StateFlow<CandidateOrderOverrideUiState> = _uiState.asStateFlow()

    fun updateReading(reading: String) {
        _uiState.update { it.copy(reading = reading) }
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
                    n = 50,
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
                .filter { it.string.isNotBlank() }
                .distinctBy { it.string }

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
            _uiState.update { it.copy(message = "保存しました") }
        }
    }

    fun deleteCurrentReadingOrder() {
        val reading = uiState.value.reading.trim()
        if (reading.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            candidateOrderOverrideRepository.deleteByInput(reading)
            _uiState.update { it.copy(message = "削除しました") }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
