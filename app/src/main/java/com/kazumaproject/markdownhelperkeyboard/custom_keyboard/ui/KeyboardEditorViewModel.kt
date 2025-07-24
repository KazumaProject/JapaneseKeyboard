package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
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

data class LayoutTemplate(val name: String, val layout: KeyboardLayout)

@HiltViewModel
class KeyboardEditorViewModel @Inject constructor(
    private val repository: KeyboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState = _uiState.asStateFlow()

    private var currentEditingId: Long? = null

    val availableTemplates: List<LayoutTemplate> = listOf(
        LayoutTemplate(
            "かな入力 (カーソル)",
            KeyboardDefaultLayouts.createFlickKanaTemplateLayout(true)
        ),
        LayoutTemplate(
            "英語入力 (カーソル)",
            KeyboardDefaultLayouts.createFlickEnglishTemplateLayout(
                isDefaultKey = true,
                isUpperCase = false
            )
        ),
        LayoutTemplate("数字入力", KeyboardDefaultLayouts.createNumberTemplateLayout())
    )

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

    fun deleteRowAt(rowIndex: Int) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.rowCount <= 1) return@update currentState
            val remainingKeys = layout.keys.filter { it.row != rowIndex }
            val updatedKeys = remainingKeys.map { key ->
                if (key.row > rowIndex) {
                    key.copy(row = key.row - 1)
                } else {
                    key
                }
            }
            val newLayout = layout.copy(keys = updatedKeys, rowCount = layout.rowCount - 1)
            currentState.copy(layout = newLayout)
        }
    }

    fun deleteColumnAt(columnIndex: Int) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.columnCount <= 1) return@update currentState
            val remainingKeys = layout.keys.filter { it.column != columnIndex }
            val updatedKeys = remainingKeys.map { key ->
                if (key.column > columnIndex) {
                    key.copy(column = key.column - 1)
                } else {
                    key
                }
            }
            val newLayout = layout.copy(keys = updatedKeys, columnCount = layout.columnCount - 1)
            currentState.copy(layout = newLayout)
        }
    }

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
            val newKeys =
                currentState.layout.keys.map { if (it.keyId == keyId) keyData else it }
            val newFlickMaps = currentState.layout.flickKeyMaps.toMutableMap()
            newFlickMaps[keyId] = listOf(flickMap)
            val newLayout = currentState.layout.copy(keys = newKeys, flickKeyMaps = newFlickMaps)
            currentState.copy(layout = newLayout)
        }
    }

    fun swapKeys(draggedKeyId: String, targetKeyId: String) {
        _uiState.update { currentState ->
            val keys = currentState.layout.keys
            val draggedKeyIndex = keys.indexOfFirst { it.keyId == draggedKeyId }
            val targetKeyIndex = keys.indexOfFirst { it.keyId == targetKeyId }
            if (draggedKeyIndex == -1 || targetKeyIndex == -1) {
                return@update currentState
            }
            val draggedKey = keys[draggedKeyIndex]
            val targetKey = keys[targetKeyIndex]
            val newKeys = keys.toMutableList()
            newKeys[draggedKeyIndex] =
                draggedKey.copy(row = targetKey.row, column = targetKey.column)
            newKeys[targetKeyIndex] =
                targetKey.copy(row = draggedKey.row, column = draggedKey.column)
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

    /**
     * 選択されたテンプレートのレイアウトを現在のUI状態に適用する
     */
    fun applyTemplate(templateLayout: KeyboardLayout) {
        // --- STEP 1: 全てのキーにユニークなIDを割り当てる ---
        val keysWithEnsuredIds = templateLayout.keys.map { key ->
            if (key.keyId == null) {
                key.copy(keyId = UUID.randomUUID().toString())
            } else {
                key
            }
        }

        // --- STEP 2: 「ラベル」から新しい「keyId」への変換マップを作成する ---
        val labelToIdMap = keysWithEnsuredIds
            .filter { it.label.isNotEmpty() && it.keyId != null }
            .associate { it.label to it.keyId!! }

        // --- STEP 3: flickKeyMapsのキーを「ラベル」から「keyId」に変換する ---
        val reKeyedFlickMaps = templateLayout.flickKeyMaps.mapNotNull { (labelKey, flickActions) ->
            val newKeyId = labelToIdMap[labelKey] // ラベルに対応する新しいIDを検索
            if (newKeyId != null) {
                newKeyId to flickActions // 新しいIDをキーにして新しいマップエントリを作成
            } else {
                null // 対応するキーが見つからなければ、このフリック設定は無視
            }
        }.toMap()

        // --- STEP 4: IDが保証されたキーリストと、キーが変換されたフリックマップで最終的なレイアウトを作成 ---
        val finalLayout = templateLayout.copy(
            keys = keysWithEnsuredIds,
            flickKeyMaps = reKeyedFlickMaps
        )

        // --- STEP 5: 修正されたレイアウトでUI状態を更新 ---
        _uiState.update { currentState ->
            currentState.copy(layout = finalLayout)
        }
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
