package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.SumireSpecialKeyDirection
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyOverrideType
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SumireSpecialKeyActionDraft(
    val overrideType: SumireSpecialKeyOverrideType = SumireSpecialKeyOverrideType.DEFAULT,
    val actionString: String? = null,
    val inputText: String? = null
)

data class SumireSpecialKeyActionEditorUiState(
    val layoutType: String = "",
    val inputMode: String = "",
    val keyId: String = "",
    val drafts: Map<SumireSpecialKeyDirection, SumireSpecialKeyActionDraft> =
        SumireSpecialKeyDirection.entries.associateWith { SumireSpecialKeyActionDraft() },
    val navigateBack: Boolean = false
)

@HiltViewModel
class SumireSpecialKeyActionEditorViewModel @Inject constructor(
    private val repository: SumireSpecialKeyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val layoutType: String = savedStateHandle["layoutType"] ?: ""
    private val inputMode: String = savedStateHandle["inputMode"] ?: ""
    private val keyId: String = savedStateHandle["keyId"] ?: ""

    private val _uiState = MutableStateFlow(
        SumireSpecialKeyActionEditorUiState(
            layoutType = layoutType,
            inputMode = inputMode,
            keyId = keyId
        )
    )
    val uiState: StateFlow<SumireSpecialKeyActionEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeActionOverridesForKey(layoutType, inputMode, keyId)
                .collect { overrides ->
                    val drafts = SumireSpecialKeyDirection.entries.associateWith { direction ->
                        overrides.firstOrNull { it.direction == direction.name }?.toDraft()
                            ?: SumireSpecialKeyActionDraft()
                    }
                    _uiState.update { it.copy(drafts = drafts) }
                }
        }
    }

    fun setDefault(direction: SumireSpecialKeyDirection) {
        updateDraft(direction, SumireSpecialKeyActionDraft())
    }

    fun setNone(direction: SumireSpecialKeyDirection) {
        updateDraft(
            direction,
            SumireSpecialKeyActionDraft(SumireSpecialKeyOverrideType.NONE)
        )
    }

    fun setInputText(direction: SumireSpecialKeyDirection, text: String) {
        updateDraft(
            direction,
            SumireSpecialKeyActionDraft(
                overrideType = SumireSpecialKeyOverrideType.INPUT_TEXT,
                inputText = text
            )
        )
    }

    fun setKeyAction(direction: SumireSpecialKeyDirection, action: KeyAction) {
        val actionString = KeyActionMapper.fromKeyAction(action) ?: return
        updateDraft(
            direction,
            SumireSpecialKeyActionDraft(
                overrideType = SumireSpecialKeyOverrideType.KEY_ACTION,
                actionString = actionString
            )
        )
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            SumireSpecialKeyDirection.entries.forEach { direction ->
                val draft = state.drafts[direction] ?: SumireSpecialKeyActionDraft()
                if (draft.overrideType == SumireSpecialKeyOverrideType.DEFAULT) {
                    repository.deleteActionDirection(
                        state.layoutType,
                        state.inputMode,
                        state.keyId,
                        direction
                    )
                } else {
                    repository.upsertActionOverride(
                        SumireSpecialKeyActionOverrideEntity(
                            layoutType = state.layoutType,
                            inputMode = state.inputMode,
                            keyId = state.keyId,
                            direction = direction.name,
                            overrideType = draft.overrideType.name,
                            actionString = draft.actionString,
                            inputText = draft.inputText,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            _uiState.update { it.copy(navigateBack = true) }
        }
    }

    fun resetThisKey() {
        viewModelScope.launch {
            repository.deleteActionKey(layoutType, inputMode, keyId)
        }
    }

    fun onDoneNavigating() {
        _uiState.update { it.copy(navigateBack = false) }
    }

    private fun updateDraft(
        direction: SumireSpecialKeyDirection,
        draft: SumireSpecialKeyActionDraft
    ) {
        _uiState.update { state ->
            state.copy(drafts = state.drafts + (direction to draft))
        }
    }

    private fun SumireSpecialKeyActionOverrideEntity.toDraft(): SumireSpecialKeyActionDraft {
        return SumireSpecialKeyActionDraft(
            overrideType = runCatching {
                SumireSpecialKeyOverrideType.valueOf(overrideType)
            }.getOrDefault(SumireSpecialKeyOverrideType.DEFAULT),
            actionString = actionString,
            inputText = inputText
        )
    }
}

