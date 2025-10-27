package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_tab_order.data

import androidx.lifecycle.ViewModel
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class CandidateTabOrderUiState(
    val candidateTabs: List<CandidateTab> = emptyList(),
    val isEditing: Boolean = false
)

@HiltViewModel
class CandidateTabOrderViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CandidateTabOrderUiState())
    val uiState: StateFlow<CandidateTabOrderUiState> = _uiState.asStateFlow()

    fun toggleEditMode() {
        _uiState.update { currentState ->
            currentState.copy(isEditing = !currentState.isEditing)
        }
    }

    fun setInitialTabs(tabs: List<CandidateTab>) {
        _uiState.update { it.copy(candidateTabs = tabs) }
    }

    fun updateTabOrder(newTabs: List<CandidateTab>) {
        _uiState.update { it.copy(candidateTabs = newTabs) }
    }
}
