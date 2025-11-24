package com.kazumaproject.markdownhelperkeyboard.short_cut.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.short_cut.data.EditableShortcut
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@HiltViewModel
class ShortcutSettingViewModel @Inject constructor(
    private val repository: ShortcutRepository
) : ViewModel() {

    private val _uiList = MutableStateFlow<List<EditableShortcut>>(emptyList())
    val uiList: StateFlow<List<EditableShortcut>> = _uiList.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val data = repository.getAllShortcutsWithStatus()
            _uiList.value = data.map { (type, isEnabled) ->
                EditableShortcut(type, isEnabled)
            }
        }
    }

    // Handle drag and drop reordering
    fun onItemMoved(fromPosition: Int, toPosition: Int) {
        val currentList = _uiList.value.toMutableList()

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(currentList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(currentList, i, i - 1)
            }
        }

        // Update StateFlow.
        // Note: In a complex UI, you might want to delay this emission
        // or handle it differently to avoid fighting with ItemTouchHelper's animation,
        // but this is the correct logic for state consistency.
        _uiList.value = currentList
    }

    // Handle switch toggle
    fun onItemToggle(position: Int, isChecked: Boolean) {
        val currentList = _uiList.value.toMutableList()
        // Create a copy to trigger Flow emission/DiffUtil
        val item = currentList[position]
        currentList[position] = item.copy(isEnabled = isChecked)
        _uiList.value = currentList
        save()
    }

    // Save to DB
    fun save() {
        viewModelScope.launch {
            val toSave = _uiList.value
                .filter { it.isEnabled }
                .map { it.type }
            repository.updateShortcuts(toSave)
        }
    }
}
