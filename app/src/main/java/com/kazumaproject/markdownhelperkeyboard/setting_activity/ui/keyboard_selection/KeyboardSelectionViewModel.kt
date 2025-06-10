package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection

import androidx.lifecycle.ViewModel
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class KeyboardSelectionUiState(
    val keyboards: List<KeyboardType> = emptyList(),
    val isEditing: Boolean = false
)

@HiltViewModel
class KeyboardSelectionViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(KeyboardSelectionUiState())
    val uiState: StateFlow<KeyboardSelectionUiState> = _uiState.asStateFlow()

    fun toggleEditMode() {
        _uiState.update { currentState ->
            currentState.copy(isEditing = !currentState.isEditing)
        }
    }

    fun setInitialKeyboards(keyboards: List<KeyboardType>) {
        _uiState.update { it.copy(keyboards = keyboards) }
    }

    fun updateKeyboardOrder(newKeyboards: List<KeyboardType>) {
        _uiState.update { it.copy(keyboards = newKeyboards) }
    }
}
