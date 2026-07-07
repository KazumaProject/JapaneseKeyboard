package com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.physical_keyboard.shortcut.database.PhysicalKeyboardShortcutItem
import com.kazumaproject.markdownhelperkeyboard.repository.PhysicalKeyboardShortcutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhysicalKeyboardShortcutViewModel @Inject constructor(
    private val repository: PhysicalKeyboardShortcutRepository
) : ViewModel() {
    val shortcuts = repository.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultShortcuts()
        }
    }

    fun shortcut(id: Long) = repository.getById(id)

    fun save(item: PhysicalKeyboardShortcutItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = if (item.id == 0L) repository.insert(item) else repository.update(item)
            onResult(result)
        }
    }

    fun delete(item: PhysicalKeyboardShortcutItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun toggle(item: PhysicalKeyboardShortcutItem, enabled: Boolean) {
        viewModelScope.launch {
            repository.update(item.copy(enabled = enabled))
        }
    }
}
