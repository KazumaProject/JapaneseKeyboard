package com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.FullKeyboardLayout
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
    val duplicateNameError: Boolean = false
)

@HiltViewModel
class KeyboardEditorViewModel @Inject constructor(
    private val repository: KeyboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState = _uiState.asStateFlow()

    private var currentEditingId: Long? = null

    /**
     * Fragmentから呼び出される初期化メソッド。
     * 新しいIDで呼び出された場合のみ、データの読み込み/新規作成を実行する。
     */
    fun start(layoutId: Long) {
        val newId = if (layoutId == -1L) null else layoutId

        // 前回と同じIDを編集しようとしている場合は、処理をスキップ（画面回転時の再読み込みを防ぐ）
        if (currentEditingId == newId && !_uiState.value.isLoading) {
            Timber.d("Request to load same layout ($newId). Skipping.")
            return
        }

        // 新しい編集セッションが始まったと判断し、IDを更新
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
                    rowCount = 4,
                ),
                isLoading = false,
            )
        }
    }

    fun saveLayout() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val idToSave = currentEditingId

            // 1. 保存前に名前の重複をチェック
            val nameExists = repository.doesNameExist(currentState.name, idToSave)

            if (nameExists) {
                // 2. もし名前が重複していたら、エラー状態をtrueにして処理を中断
                Timber.d("Save failed: Duplicate name found.")
                _uiState.update { it.copy(duplicateNameError = true) }
            } else {
                // 3. 重複がなければ、保存処理を実行
                if (idToSave != null) {
                    repository.deleteLayout(idToSave)
                }
                Timber.d("save layout: ${currentState.layout}")
                repository.saveLayout(
                    layout = currentState.layout,
                    name = currentState.name,
                    id = null
                )
                _uiState.update { it.copy(navigateBack = true) }
            }
        }
    }

    // ▼▼▼ ADDED: This function resets the ViewModel's state. ▼▼▼
    /**
     * Called when the user cancels the editing process (e.g., by pressing the back button).
     * This resets the state to ensure a fresh start next time.
     */
    fun onCancelEditing() {
        Timber.d("Editing cancelled. Resetting ViewModel state.")
        currentEditingId = null // Reset the tracking ID
        _uiState.value = EditorUiState() // Reset UI state to its initial default
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

    private fun createEmptyKey(row: Int, column: Int): KeyData {
        return KeyData(
            label = " ", row = row, column = column, isFlickable = true,
            keyId = UUID.randomUUID().toString(), keyType = KeyType.PETAL_FLICK
        )
    }

    private fun createDefaultKey(row: Int, column: Int): KeyData {
        return KeyData(
            label = " ",
            row = row,
            column = column,
            isFlickable = true,
            isSpecialKey = true,
            action = KeyAction.SwitchToNextIme,
            keyId = UUID.randomUUID().toString(),
            keyType = KeyType.PETAL_FLICK,
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

    fun onDoneNavigating() {
        _uiState.update { it.copy(navigateBack = false) }
    }

    // Function for the Fragment to call to get data for export
    suspend fun getLayoutsForExport(): List<FullKeyboardLayout> {
        return repository.getAllFullLayoutsForExport()
    }

    // Function for the Fragment to call to import data
    fun importLayouts(layouts: List<FullKeyboardLayout>) {
        viewModelScope.launch {
            repository.importLayouts(layouts)
        }
    }
}
