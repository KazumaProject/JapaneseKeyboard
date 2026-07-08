package com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryKeyNormalizer
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQuerySaveResult
import com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.CustomZeroQueryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomZeroQueryGroupUiState(
    val lookupKey: String = "",
    val displayKey: String = "",
    val keyDraft: String = "",
    val entries: List<CustomZeroQueryEntry> = emptyList(),
)

@HiltViewModel
class CustomZeroQueryGroupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: CustomZeroQueryRepository,
) : ViewModel() {

    private val lookupKey = MutableStateFlow(savedStateHandle["lookupKey"] ?: "")
    private val _uiState = MutableStateFlow(
        CustomZeroQueryGroupUiState(lookupKey = lookupKey.value)
    )
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            lookupKey
                .flatMapLatest { repository.observeGroup(it) }
                .collectLatest { entries ->
                    val displayKey = entries.firstOrNull()?.displayKey ?: lookupKey.value
                    _uiState.update {
                        it.copy(
                            lookupKey = lookupKey.value,
                            displayKey = displayKey,
                            keyDraft = if (it.keyDraft.isBlank()) displayKey else it.keyDraft,
                            entries = entries,
                        )
                    }
                }
        }
    }

    fun updateKeyDraft(value: String) {
        _uiState.update { it.copy(keyDraft = value) }
    }

    fun saveKey() {
        val currentLookupKey = uiState.value.lookupKey
        val draft = uiState.value.keyDraft
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.renameGroup(currentLookupKey, draft)
            if (result == CustomZeroQuerySaveResult.Saved) {
                lookupKey.value = CustomZeroQueryKeyNormalizer.normalizeKey(draft)
            }
            _messages.emit(result.toMessage())
        }
    }

    fun addCandidate(candidate: String, enabled: Boolean) {
        val displayKey = uiState.value.displayKey
        saveEntry(
            id = 0,
            displayKey = displayKey,
            candidate = candidate,
            enabled = enabled,
        )
    }

    fun updateCandidate(
        entry: CustomZeroQueryEntry,
        candidate: String,
        enabled: Boolean,
    ) {
        saveEntry(
            id = entry.id,
            displayKey = uiState.value.displayKey,
            candidate = candidate,
            enabled = enabled,
        )
    }

    fun updateEnabled(entry: CustomZeroQueryEntry, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateEnabled(entry, enabled)
        }
    }

    fun moveCandidate(from: Int, to: Int) {
        val current = uiState.value.entries
        if (from !in current.indices || to !in current.indices) return

        val reordered = current.toMutableList()
        val item = reordered.removeAt(from)
        reordered.add(to, item)
        _uiState.update { it.copy(entries = reordered) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.reorder(
                lookupKey = uiState.value.lookupKey,
                orderedIds = reordered.map { it.id },
            )
        }
    }

    fun deleteEntry(entry: CustomZeroQueryEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEntry(entry.id)
            _messages.emit(context.getString(R.string.custom_zero_query_candidate_deleted))
        }
    }

    fun deleteGroup() {
        val currentLookupKey = uiState.value.lookupKey
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteGroup(currentLookupKey)
            _messages.emit(context.getString(R.string.custom_zero_query_group_deleted))
        }
    }

    private fun saveEntry(
        id: Long,
        displayKey: String,
        candidate: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.saveEntry(
                id = id,
                rawDisplayKey = displayKey,
                rawCandidate = candidate,
                enabled = enabled,
            )
            _messages.emit(result.toMessage())
        }
    }

    private fun CustomZeroQuerySaveResult.toMessage(): String =
        when (this) {
            CustomZeroQuerySaveResult.Saved ->
                context.getString(R.string.custom_zero_query_saved)
            CustomZeroQuerySaveResult.EmptyKey ->
                context.getString(R.string.custom_zero_query_error_empty_key)
            CustomZeroQuerySaveResult.EmptyCandidate ->
                context.getString(R.string.custom_zero_query_error_empty_candidate)
            CustomZeroQuerySaveResult.Duplicate ->
                context.getString(R.string.custom_zero_query_error_duplicate)
        }
}
