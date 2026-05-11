package com.kazumaproject.markdownhelperkeyboard.sumire_special_key.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
import com.kazumaproject.custom_keyboard.data.swapKeyPlacements
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyPlacementOverrideApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SumireSpecialKeyEditorUiState(
    val layoutType: String = "toggle",
    val inputMode: KeyboardInputMode = KeyboardInputMode.HIRAGANA,
    val previewLayout: KeyboardLayout? = null
)

@HiltViewModel
class SumireSpecialKeyEditorViewModel @Inject constructor(
    private val repository: SumireSpecialKeyRepository,
    private val appPreference: AppPreference
) : ViewModel() {
    private val _uiState = MutableStateFlow(SumireSpecialKeyEditorUiState())
    val uiState: StateFlow<SumireSpecialKeyEditorUiState> = _uiState.asStateFlow()

    private var placementJob: Job? = null
    private var currentPlacementOverrides: List<SumireSpecialKeyPlacementOverrideEntity> =
        emptyList()

    init {
        observePlacements()
    }

    fun updateLayoutType(layoutType: String) {
        if (layoutType == _uiState.value.layoutType) return
        _uiState.update { it.copy(layoutType = layoutType) }
        observePlacements()
    }

    fun updateInputMode(inputMode: KeyboardInputMode) {
        if (inputMode == _uiState.value.inputMode) return
        _uiState.update { it.copy(inputMode = inputMode) }
        observePlacements()
    }

    fun swapSpecialKeys(draggedKeyId: String, targetKeyId: String) {
        val state = _uiState.value
        val layout = state.previewLayout ?: return
        if (draggedKeyId == targetKeyId) return

        val dragged = layout.specialKeyItemById(draggedKeyId) ?: return
        val target = layout.specialKeyItemById(targetKeyId) ?: return
        if (dragged.keyData.keyId.isNullOrBlank() || target.keyData.keyId.isNullOrBlank()) return

        val swapped = layout.swapKeyPlacements(draggedKeyId, targetKeyId)
        if (swapped === layout) return
        if (hasPlacementIssues(swapped.items, swapped.rowUnitCount, swapped.columnUnitCount)) return

        _uiState.update { it.copy(previewLayout = swapped) }
        viewModelScope.launch {
            repository.upsertPlacementOverrides(
                swapped.items
                    .filterIsInstance<KeyItem>()
                    .filter { it.keyData.isSpecialKey && !it.keyData.keyId.isNullOrBlank() }
                    .map { item ->
                        item.toPlacementEntity(
                            layoutType = state.layoutType,
                            inputMode = state.inputMode.name
                        )
                    }
            )
        }
    }

    fun resetPlacement() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.deleteAllPlacements(state.layoutType, state.inputMode.name)
        }
    }

    fun resetAll() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.deleteAllPlacements(state.layoutType, state.inputMode.name)
            repository.deleteAllActions(state.layoutType, state.inputMode.name)
        }
    }

    private fun observePlacements() {
        placementJob?.cancel()
        val layoutType = _uiState.value.layoutType
        val inputMode = _uiState.value.inputMode
        placementJob = viewModelScope.launch {
            repository.observePlacementOverrides(layoutType, inputMode.name).collect { overrides ->
                currentPlacementOverrides = overrides
                _uiState.update {
                    it.copy(previewLayout = buildPreviewLayout(layoutType, inputMode, overrides))
                }
            }
        }
    }

    private fun buildPreviewLayout(
        layoutType: String,
        inputMode: KeyboardInputMode,
        placementOverrides: List<SumireSpecialKeyPlacementOverrideEntity>
    ): KeyboardLayout {
        val baseLayout = KeyboardDefaultLayouts.createFinalLayout(
            mode = inputMode,
            dynamicKeyStates = previewDynamicStates,
            inputLayoutType = layoutType,
            inputStyle = appPreference.sumire_keyboard_style,
            deleteKeyFlickSettings = KeyboardDefaultLayouts.DeleteKeyFlickSettings(
                left = appPreference.delete_key_left_flick_preference,
                up = appPreference.delete_key_up_flick_preference,
                down = appPreference.delete_key_down_flick_preference
            )
        )
        return SumireSpecialKeyPlacementOverrideApplier.apply(
            layout = baseLayout,
            layoutType = layoutType,
            inputMode = inputMode.name,
            overrides = placementOverrides
        )
    }

    private fun KeyboardLayout.specialKeyItemById(keyId: String): KeyItem? {
        return items.filterIsInstance<KeyItem>().firstOrNull {
            it.keyData.isSpecialKey && it.keyData.keyId?.takeIf(String::isNotBlank) == keyId
        }
    }

    private fun KeyItem.toPlacementEntity(
        layoutType: String,
        inputMode: String
    ): SumireSpecialKeyPlacementOverrideEntity {
        val placement: GridPlacement = this.placement
        return SumireSpecialKeyPlacementOverrideEntity(
            layoutType = layoutType,
            inputMode = inputMode,
            keyId = keyData.keyId.orEmpty(),
            rowUnits = placement.rowUnits,
            columnUnits = placement.columnUnits,
            rowSpanUnits = placement.rowSpanUnits,
            columnSpanUnits = placement.columnSpanUnits,
            updatedAt = System.currentTimeMillis()
        )
    }

    private companion object {
        val previewDynamicStates = mapOf(
            "enter_key" to 0,
            "dakuten_toggle_key" to 0,
            "katakana_toggle_key" to 0,
            "space_convert_key" to 0
        )
    }
}
