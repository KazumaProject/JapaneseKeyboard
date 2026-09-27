package com.kazumaproject.markdownhelperkeyboard.local_font

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class LocalFontSettingsUiState(
    val currentName: String? = null,
    val previewName: String? = null,
    val previewTypeface: android.graphics.Typeface? = null,
    val busy: Boolean = true,
    val error: LocalFontUiError? = null,
    val restoreWarning: Boolean = false,
)

enum class LocalFontUiError { UNSUPPORTED, TOO_LARGE, INVALID, STORAGE, RESTORE_FAILED }

@HiltViewModel
class LocalFontSettingsViewModel @Inject constructor(
    private val repository: LocalFontRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocalFontSettingsUiState())
    val uiState: StateFlow<LocalFontSettingsUiState> = _uiState.asStateFlow()
    private var prepared: LocalFontPreview? = null

    init {
        viewModelScope.launch {
            val active = repository.loadIfNeeded()
            _uiState.update {
                it.copy(
                    currentName = active.displayName,
                    busy = false,
                    restoreWarning = active.warning == LocalFontWarning.RESTORE_FAILED,
                )
            }
            repository.state.collect { state ->
                _uiState.update {
                    it.copy(
                        currentName = state.displayName,
                        restoreWarning = state.warning == LocalFontWarning.RESTORE_FAILED,
                    )
                }
            }
        }
    }

    fun select(uri: Uri) {
        viewModelScope.launch {
            val previous = prepared
            prepared = null
            repository.cancelPreview(previous)
            _uiState.update { it.copy(busy = true, previewName = null, previewTypeface = null, error = null) }
            try {
                val next = repository.prepare(uri)
                prepared = next
                _uiState.update {
                    it.copy(busy = false, previewName = next.displayName, previewTypeface = next.typeface, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.toUiError()) }
            }
        }
    }

    fun applyPreview() {
        val candidate = prepared ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                repository.apply(candidate)
                prepared = null
                _uiState.update { it.copy(busy = false, previewName = null, previewTypeface = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = e.toUiError()) }
            }
        }
    }

    fun cancelPreview() {
        val candidate = prepared
        prepared = null
        viewModelScope.launch {
            repository.cancelPreview(candidate)
            _uiState.update { it.copy(previewName = null, previewTypeface = null, error = null) }
        }
    }

    fun restoreStandard() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                repository.restoreStandard()
                prepared = null
                _uiState.update {
                    it.copy(busy = false, currentName = null, previewName = null, previewTypeface = null, restoreWarning = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, error = LocalFontUiError.STORAGE) }
            }
        }
    }

    fun discardPreviewOnExit() {
        val candidate = prepared ?: return
        prepared = null
        viewModelScope.launch { repository.cancelPreview(candidate) }
    }

    private fun Exception.toUiError(): LocalFontUiError = when (this) {
        is FontValidationException -> when (issue) {
            FontFormatIssue.TOO_LARGE -> LocalFontUiError.TOO_LARGE
            FontFormatIssue.COLLECTION, FontFormatIssue.WEB_FONT,
            FontFormatIssue.VARIABLE, FontFormatIssue.UNSUPPORTED_SIGNATURE -> LocalFontUiError.UNSUPPORTED
            else -> LocalFontUiError.INVALID
        }
        is IOException -> LocalFontUiError.STORAGE
        else -> LocalFontUiError.INVALID
    }
}
