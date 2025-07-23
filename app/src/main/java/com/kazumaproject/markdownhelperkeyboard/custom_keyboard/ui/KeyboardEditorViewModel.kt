package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
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
    val navigateBack: Boolean = false,
    val duplicateNameError: Boolean = false,
    val isRomaji: Boolean = false
)

@HiltViewModel
class KeyboardEditorViewModel @Inject constructor(
    private val repository: KeyboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState = _uiState.asStateFlow()

    private var currentEditingId: Long? = null

    fun start(layoutId: Long) {
        val newId = if (layoutId == -1L) null else layoutId
        if (currentEditingId == newId && !_uiState.value.isLoading) {
            Timber.d("Request to load same layout ($newId). Skipping.")
            return
        }
        currentEditingId = newId
        Timber.d("Starting new editing session for layoutId: $newId")
        if (newId != null) {
            loadLayout(newId)
        } else {
            createNewLayout()
        }
    }

    private fun loadLayout(id: Long) {
        Timber.d("loadLayout called with id = $id")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val layoutName = repository.getLayoutName(id) ?: "名称未設定"
            val loadedLayout = repository.getFullLayout(id).first()
            _uiState.update {
                it.copy(
                    layoutId = id,
                    name = layoutName,
                    layout = loadedLayout,
                    isLoading = false,
                    isRomaji = loadedLayout.isRomaji
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
                    rowCount = 4,
                ),
                isLoading = false,
                isRomaji = false
            )
        }
    }

    fun saveLayout() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val idToSave = currentEditingId
            val nameExists = repository.doesNameExist(currentState.name, idToSave)
            if (nameExists) {
                Timber.d("Save failed: Duplicate name found.")
                _uiState.update { it.copy(duplicateNameError = true) }
            } else {
                if (idToSave != null) {
                    repository.deleteLayout(idToSave)
                }
                Timber.d("save layout: ${currentState.layout}")
                val layoutToSave = currentState.layout.copy(isRomaji = currentState.isRomaji)
                repository.saveLayout(
                    layout = layoutToSave,
                    name = currentState.name,
                    id = idToSave
                )
                _uiState.update { it.copy(navigateBack = true) }
            }
        }
    }

    fun onCancelEditing() {
        Timber.d("Editing cancelled. Resetting ViewModel state.")
        currentEditingId = null
        _uiState.value = EditorUiState()
    }

    fun clearDuplicateNameError() {
        _uiState.update { it.copy(duplicateNameError = false) }
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

    // ▼▼▼ ここから追加 ▼▼▼
    /**
     * 指定されたインデックスの行を削除する
     */
    fun deleteRowAt(rowIndex: Int) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.rowCount <= 1) return@update currentState // 最後の1行は消せない

            // 1. 指定された行以外のキーを保持
            val remainingKeys = layout.keys.filter { it.row != rowIndex }

            // 2. 削除された行より下の行のインデックスを1つずつ上に詰める
            val updatedKeys = remainingKeys.map { key ->
                if (key.row > rowIndex) {
                    key.copy(row = key.row - 1)
                } else {
                    key
                }
            }

            // 3. 行数を1つ減らしてレイアウトを更新
            val newLayout = layout.copy(keys = updatedKeys, rowCount = layout.rowCount - 1)
            currentState.copy(layout = newLayout)
        }
    }

    /**
     * 指定されたインデックスの列を削除する
     */
    fun deleteColumnAt(columnIndex: Int) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.columnCount <= 1) return@update currentState // 最後の1列は消せない

            // 1. 指定された列以外のキーを保持
            val remainingKeys = layout.keys.filter { it.column != columnIndex }

            // 2. 削除された列より右の列のインデックスを1つずつ左に詰める
            val updatedKeys = remainingKeys.map { key ->
                if (key.column > columnIndex) {
                    key.copy(column = key.column - 1)
                } else {
                    key
                }
            }

            // 3. 列数を1つ減らしてレイアウトを更新
            val newLayout = layout.copy(keys = updatedKeys, columnCount = layout.columnCount - 1)
            currentState.copy(layout = newLayout)
        }
    }
    // ▲▲▲ ここまで追加 ▲▲▲

    private fun createEmptyKey(row: Int, column: Int): KeyData {
        return KeyData(
            label = " ", row = row, column = column, isFlickable = true,
            keyId = UUID.randomUUID().toString(), keyType = KeyType.PETAL_FLICK
        )
    }

    fun selectKeyForEditing(keyId: String?) {
        _uiState.update { it.copy(selectedKeyIdentifier = keyId) }
    }

    fun doneNavigatingToKeyEditor() {
        _uiState.update { it.copy(selectedKeyIdentifier = null) }
    }

    fun updateKeyAndFlicks(keyData: KeyData, flickMap: Map<FlickDirection, FlickAction>) {
        val keyId = keyData.keyId
        if (keyId == null) {
            Timber.e("FATAL: updateKeyAndFlicks received a KeyData with a null keyId! Aborting update.")
            return
        }
        _uiState.update { currentState ->
            Timber.d("Executing _uiState.update block for keyId: $keyId")
            val newKeys =
                currentState.layout.keys.map { if (it.keyId == keyId) keyData else it }
            val newFlickMaps = currentState.layout.flickKeyMaps.toMutableMap()
            newFlickMaps[keyId] = listOf(flickMap)
            val newLayout = currentState.layout.copy(keys = newKeys, flickKeyMaps = newFlickMaps)
            Timber.d("SUCCESS: State update finished. The new KeyData for this key is: $keyData")
            currentState.copy(layout = newLayout)
        }
    }

    fun swapKeys(draggedKeyId: String, targetKeyId: String) {
        _uiState.update { currentState ->
            val keys = currentState.layout.keys
            val draggedKeyIndex = keys.indexOfFirst { it.keyId == draggedKeyId }
            val targetKeyIndex = keys.indexOfFirst { it.keyId == targetKeyId }

            if (draggedKeyIndex == -1 || targetKeyIndex == -1) {
                Timber.w("Could not find one or both keys to swap. Dragged: $draggedKeyId, Target: $targetKeyId")
                return@update currentState
            }

            val draggedKey = keys[draggedKeyIndex]
            val targetKey = keys[targetKeyIndex]

            val newKeys = keys.toMutableList()
            newKeys[draggedKeyIndex] =
                draggedKey.copy(row = targetKey.row, column = targetKey.column)
            newKeys[targetKeyIndex] =
                targetKey.copy(row = draggedKey.row, column = draggedKey.column)

            Timber.d("Swapped keys: ${draggedKey.label} and ${targetKey.label}")

            val newLayout = currentState.layout.copy(keys = newKeys)
            currentState.copy(layout = newLayout)
        }
    }

    fun updateIsRomaji(isRomaji: Boolean) {
        _uiState.update { it.copy(isRomaji = isRomaji) }
    }

    fun onDoneNavigating() {
        _uiState.update { it.copy(navigateBack = false) }
    }

    suspend fun getLayoutsForExport(): List<FullKeyboardLayout> {
        return repository.getAllFullLayoutsForExport()
    }

    fun importLayouts(layouts: List<FullKeyboardLayout>) {
        viewModelScope.launch {
            repository.importLayouts(layouts)
        }
    }
}
