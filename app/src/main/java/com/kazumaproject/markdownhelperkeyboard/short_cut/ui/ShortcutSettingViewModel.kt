package com.kazumaproject.markdownhelperkeyboard.short_cut.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

data class ShortcutToolbarEditUiState(
    val selected: List<ShortcutType> = emptyList(),
    val available: List<ShortcutType> = emptyList(),
    val canRemove: Boolean = false
)

sealed interface ShortcutToolbarEditEvent {
    data object CannotRemoveLastShortcut : ShortcutToolbarEditEvent
}

@HiltViewModel
class ShortcutSettingViewModel @Inject constructor(
    private val repository: ShortcutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShortcutToolbarEditUiState())
    val uiState: StateFlow<ShortcutToolbarEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ShortcutToolbarEditEvent>()
    val events: SharedFlow<ShortcutToolbarEditEvent> = _events

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.enabledShortcutsFlow.collect { shortcuts ->
                updateSelected(shortcuts)
            }
        }
    }

    fun addShortcut(type: ShortcutType) {
        val current = _uiState.value.selected
        if (type in current) return
        updateSelected(current + type)
    }

    fun removeShortcut(type: ShortcutType) {
        val current = _uiState.value.selected
        if (current.size <= 1) {
            viewModelScope.launch {
                _events.emit(ShortcutToolbarEditEvent.CannotRemoveLastShortcut)
            }
            return
        }
        updateSelected(current.filterNot { it == type })
    }

    fun moveShortcut(fromPosition: Int, toPosition: Int) {
        val currentList = _uiState.value.selected.toMutableList()
        if (fromPosition !in currentList.indices || toPosition !in currentList.indices) return
        if (fromPosition == toPosition) return

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(currentList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(currentList, i, i - 1)
            }
        }

        updateSelected(currentList)
    }

    fun resetToDefault() {
        updateSelected(repository.defaultShortcuts)
    }

    fun save() {
        viewModelScope.launch {
            repository.updateShortcuts(_uiState.value.selected)
        }
    }

    private fun updateSelected(selected: List<ShortcutType>) {
        val normalized = selected.distinct().ifEmpty { listOf(ShortcutType.SETTINGS) }
        _uiState.update {
            ShortcutToolbarEditUiState(
                selected = normalized,
                available = ShortcutType.entries.filterNot { type -> type in normalized },
                canRemove = normalized.size > 1
            )
        }
    }
}
