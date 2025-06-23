package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyboardListViewModel @Inject constructor(
    private val repository: KeyboardRepository
) : ViewModel() {

    val layouts: StateFlow<List<CustomKeyboardLayout>> = repository.getLayouts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteLayout(id: Long) {
        viewModelScope.launch {
            repository.deleteLayout(id)
        }
    }

    fun duplicateLayout(id: Long) {
        viewModelScope.launch {
            repository.duplicateLayout(id) // Repositoryにこのメソッドを追加する必要があります
        }
    }
}
