package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class EditorUiState(
    val layoutId: Long? = null,
    val name: String = "新しいキーボード",
    val layout: KeyboardLayout = KeyboardLayout(emptyList(), emptyMap(), 5, 4),
    val isEditMode: Boolean = true,
    val isLoading: Boolean = true,
    val selectedKeyIdentifier: String? = null,
    val navigateBack: Boolean = false
)

@HiltViewModel
class KeyboardEditorViewModel @Inject constructor(
    private val repository: KeyboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState = _uiState.asStateFlow()

    private var isInitialized = false

    fun start(layoutId: Long) {
        if (isInitialized) {
            return
        }
        if (layoutId != -1L) {
            loadLayout(layoutId)
        } else {
            createNewLayout()
        }
        isInitialized = true
    }

    private fun loadLayout(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val layoutName = repository.getLayoutName(id) ?: "名称未設定"
            val loadedLayout = repository.getFullLayout(id).first()
            _uiState.update {
                it.copy(
                    layoutId = id,
                    name = layoutName,
                    layout = loadedLayout,
                    isLoading = false
                )
            }
        }
    }

    private fun createNewLayout() {
        _uiState.update {
            it.copy(
                layoutId = null,
                name = "新しいキーボード",
                layout = KeyboardLayout(
                    keys = (0 until 4).flatMap { row ->
                        (0 until 5).map { col -> createEmptyKey(row, col) }
                    },
                    flickKeyMaps = emptyMap(),
                    columnCount = 5,
                    rowCount = 4
                ),
                isLoading = false
            )
        }
    }

    // ▼▼▼ この関数を修正し、FragmentからIDを直接受け取るように変更 ▼▼▼
    fun saveLayout(id: Long?) {
        viewModelScope.launch {
            val currentState = _uiState.value
            Timber.d("saveLayout: ID passed directly from Fragment = $id")
            repository.saveLayout(
                layout = currentState.layout,
                name = currentState.name,
                id = id
            )
            _uiState.update { it.copy(navigateBack = true) }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun addRow() {
        _uiState.update { currentState ->
            val layout = currentState.layout
            val newRowCount = layout.rowCount + 1
            val newKeys = layout.keys.toMutableList()
            for (col in 0 until layout.columnCount) {
                newKeys.add(createEmptyKey(newRowCount - 1, col))
            }
            val newLayout = layout.copy(keys = newKeys, rowCount = newRowCount)
            currentState.copy(layout = newLayout)
        }
    }

    fun removeRow() {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.rowCount <= 1) return@update currentState
            val newRowCount = layout.rowCount - 1
            val newKeys = layout.keys.filter { it.row < newRowCount }
            val newLayout = layout.copy(keys = newKeys, rowCount = newRowCount)
            currentState.copy(layout = newLayout)
        }
    }

    fun addColumn() {
        _uiState.update { currentState ->
            val layout = currentState.layout
            val newColumnCount = layout.columnCount + 1
            val newKeys = layout.keys.toMutableList()
            for (row in 0 until layout.rowCount) {
                newKeys.add(createEmptyKey(row, newColumnCount - 1))
            }
            val newLayout = layout.copy(
                keys = newKeys.sortedWith(compareBy({ it.row }, { it.column })),
                columnCount = newColumnCount
            )
            currentState.copy(layout = newLayout)
        }
    }

    fun removeColumn() {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.columnCount <= 1) return@update currentState
            val newColumnCount = layout.columnCount - 1
            val newKeys = layout.keys.filter { it.column < newColumnCount }
            val newLayout = layout.copy(keys = newKeys, columnCount = newColumnCount)
            currentState.copy(layout = newLayout)
        }
    }

    private fun createEmptyKey(row: Int, column: Int): KeyData {
        return KeyData(
            label = " ", row = row, column = column, isFlickable = false,
            keyId = UUID.randomUUID().toString(), keyType = KeyType.NORMAL
        )
    }

    fun selectKeyForEditing(keyId: String?) {
        _uiState.update { it.copy(selectedKeyIdentifier = keyId) }
    }

    fun doneNavigatingToKeyEditor() {
        _uiState.update { it.copy(selectedKeyIdentifier = null) }
    }

    fun updateKeyAndFlicks(keyData: KeyData, flickMap: Map<FlickDirection, FlickAction>) {
        _uiState.update { currentState ->
            val newKeys =
                currentState.layout.keys.map { if (it.keyId == keyData.keyId) keyData else it }
            val newFlickMaps = currentState.layout.flickKeyMaps.toMutableMap()
            newFlickMaps[keyData.keyId!!] = listOf(flickMap)
            val newLayout = currentState.layout.copy(keys = newKeys, flickKeyMaps = newFlickMaps)
            currentState.copy(layout = newLayout)
        }
    }

    fun onDoneNavigating() {
        _uiState.update { it.copy(navigateBack = false) }
    }
}
