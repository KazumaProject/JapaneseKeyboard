package com.kazumaproject.markdownhelperkeyboard.delete_key_flick.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database.DeleteKeyFlickDeleteTarget
import com.kazumaproject.markdownhelperkeyboard.repository.DeleteKeyFlickDeleteTargetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeleteKeyFlickTargetsViewModel @Inject constructor(
    private val repository: DeleteKeyFlickDeleteTargetRepository
) : ViewModel() {

    val targets = repository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultTargets()
        }
    }

    fun addSymbol(symbol: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repository.addSymbol(symbol))
    }

    fun updateSymbol(
        target: DeleteKeyFlickDeleteTarget,
        symbol: String,
        onResult: (Boolean) -> Unit
    ) = viewModelScope.launch {
        onResult(repository.updateSymbol(target, symbol))
    }

    fun delete(target: DeleteKeyFlickDeleteTarget, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repository.delete(target))
    }

    fun resetToDefault() = viewModelScope.launch {
        repository.resetToDefault()
    }
}
