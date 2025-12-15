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
            // 最後の行にある、もしくは最後の行にはみ出している部分を削除/調整する簡易実装
            // (行削除ボタンによる任意位置削除とは別ロジック)
            val updatedKeys = layout.keys.mapNotNull { key ->
                if (key.row >= newRowCount) {
                    // 完全に削除される行にあるキーは削除
                    null
                } else if (key.row + key.rowSpan > newRowCount) {
                    // はみ出しているキーは縮める
                    val newSpan = newRowCount - key.row
                    if (newSpan > 0) key.copy(rowSpan = newSpan) else null
                } else {
                    key
                }
            }
            val newLayout = layout.copy(keys = updatedKeys, rowCount = newRowCount)
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

            val updatedKeys = layout.keys.mapNotNull { key ->
                if (key.column >= newColumnCount) {
                    null
                } else if (key.column + key.colSpan > newColumnCount) {
                    val newSpan = newColumnCount - key.column
                    if (newSpan > 0) key.copy(colSpan = newSpan) else null
                } else {
                    key
                }
            }
            val newLayout = layout.copy(keys = updatedKeys, columnCount = newColumnCount)
            currentState.copy(layout = newLayout)
        }
    }

    // ▼▼▼ 修正箇所：行削除ロジックの改善 ▼▼▼
    fun deleteRowAt(rowIndex: Int) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.rowCount <= 1) return@update currentState

            // 全キーに対して、削除される行との関係に基づいて位置やサイズを調整
            val updatedKeys = layout.keys.mapNotNull { key ->
                val keyRowStart = key.row
                val keyRowEnd = key.row + key.rowSpan // 排他的 (exclusive)

                when {
                    // ケース1: キーが削除行より完全に下にある -> 1つ上にずらす
                    keyRowStart > rowIndex -> {
                        key.copy(row = key.row - 1)
                    }

                    // ケース2: キーが削除行を含んでいる (跨いでいる)
                    // (開始位置が削除行以下、かつ、終了位置が削除行より大きい)
                    keyRowStart <= rowIndex && keyRowEnd > rowIndex -> {
                        // Spanを1つ減らす
                        val newSpan = key.rowSpan - 1
                        // Spanがなくなったらキー自体を削除
                        if (newSpan > 0) {
                            key.copy(rowSpan = newSpan)
                        } else {
                            null
                        }
                    }

                    // ケース3: キーが完全に削除行より上にある -> 何もしない
                    else -> key
                }
            }

            val newLayout = layout.copy(keys = updatedKeys, rowCount = layout.rowCount - 1)
            currentState.copy(layout = newLayout)
        }
    }
    // ▲▲▲ 修正箇所ここまで ▲▲▲

    // ▼▼▼ 修正箇所：列削除ロジックの改善 ▼▼▼
    fun deleteColumnAt(columnIndex: Int) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.columnCount <= 1) return@update currentState

            val updatedKeys = layout.keys.mapNotNull { key ->
                val keyColStart = key.column
                val keyColEnd = key.column + key.colSpan

                when {
                    // ケース1: キーが削除列より右にある -> 1つ左にずらす
                    keyColStart > columnIndex -> {
                        key.copy(column = key.column - 1)
                    }

                    // ケース2: キーが削除列を跨いでいる
                    keyColStart <= columnIndex && keyColEnd > columnIndex -> {
                        val newSpan = key.colSpan - 1
                        if (newSpan > 0) {
                            key.copy(colSpan = newSpan)
                        } else {
                            null
                        }
                    }

                    // ケース3: キーが完全に削除列より左にある -> 何もしない
                    else -> key
                }
            }

            val newLayout = layout.copy(keys = updatedKeys, columnCount = layout.columnCount - 1)
            currentState.copy(layout = newLayout)
        }
    }
    // ▲▲▲ 修正箇所ここまで ▲▲▲

    private fun createEmptyKey(row: Int, column: Int): KeyData {
        return KeyData(
            label = " ", row = row, column = column, isFlickable = true,
            keyId = UUID.randomUUID().toString(), keyType = KeyType.GRID_FLICK
        )
    }

    fun selectKeyForEditing(keyId: String?) {
        _uiState.update { it.copy(selectedKeyIdentifier = keyId) }
    }

    fun doneNavigatingToKeyEditor() {
        _uiState.update { it.copy(selectedKeyIdentifier = null) }
    }

    fun updateKeyAndFlicks(newKeyData: KeyData, flickMap: Map<FlickDirection, FlickAction>) {
        val keyId = newKeyData.keyId ?: run {
            Timber.e("FATAL: updateKeyAndFlicks received a KeyData with a null keyId!")
            return
        }

        _uiState.update { currentState ->
            val layout = currentState.layout

            // 変更前の古いキー情報を取得
            val oldKeyData = layout.keys.find { it.keyId == keyId } ?: return@update currentState

            // 自分以外の全てのキー
            val otherKeys = layout.keys.filter { it.keyId != keyId }

            // 1. 【拡大対策】 新しいサイズで重なってしまう「他のキー」を特定 (ブルドーザー処理)
            val crushedKeys = otherKeys.filter { isRectOverlapping(newKeyData, it) }
            val crushedKeyIds = crushedKeys.map { it.keyId }.toSet()

            // 2. 【縮小対策】 古いキーが居たけど、新しいキーは居ない場所(放棄されたセル)を特定
            val oldCells = getOccupiedCells(oldKeyData)
            val newCells = getOccupiedCells(newKeyData)
            val abandonedCells = oldCells.filter { !newCells.contains(it) }

            // 放棄されたセルに新しい空キーを作成
            val newEmptyKeys = abandonedCells.map { (row, col) ->
                createEmptyKey(row, col)
            }

            // 3. キーリストの再構築
            val finalKeys = otherKeys.filter { !crushedKeyIds.contains(it.keyId) } // 潰されたキーを除外
                .plus(newKeyData) // 更新されたメインのキーを追加
                .plus(newEmptyKeys) // 新しく生まれた空キーを追加

            // 4. フリック設定の更新
            val finalFlickMaps = layout.flickKeyMaps.toMutableMap()
            crushedKeyIds.forEach { finalFlickMaps.remove(it) } // 潰されたキーの設定を削除
            finalFlickMaps[keyId] = listOf(flickMap) // メインキーの設定を更新

            val newLayout = layout.copy(keys = finalKeys, flickKeyMaps = finalFlickMaps)
            currentState.copy(layout = newLayout)
        }
    }

    private fun getOccupiedCells(key: KeyData): List<Pair<Int, Int>> {
        val cells = mutableListOf<Pair<Int, Int>>()
        for (r in key.row until key.row + key.rowSpan) {
            for (c in key.column until key.column + key.colSpan) {
                cells.add(r to c)
            }
        }
        return cells
    }

    fun swapKeys(draggedKeyId: String, targetKeyId: String) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            val currentKeys = layout.keys

            val draggedKey =
                currentKeys.find { it.keyId == draggedKeyId } ?: return@update currentState
            val targetKey =
                currentKeys.find { it.keyId == targetKeyId } ?: return@update currentState

            if (draggedKeyId == targetKeyId) return@update currentState

            val destRow = targetKey.row
            val destCol = targetKey.column

            val movedDraggedKey = draggedKey.copy(row = destRow, column = destCol)

            val victims = currentKeys.filter { key ->
                key.keyId != draggedKeyId && isRectOverlapping(movedDraggedKey, key)
            }

            val moveRowDelta = destRow - draggedKey.row
            val moveColDelta = destCol - draggedKey.column

            val newKeysCandidate = currentKeys.map { key ->
                when {
                    key.keyId == draggedKeyId -> movedDraggedKey
                    victims.any { it.keyId == key.keyId } -> {
                        key.copy(
                            row = key.row - moveRowDelta,
                            column = key.column - moveColDelta
                        )
                    }

                    else -> key
                }
            }

            if (hasIssues(newKeysCandidate, layout.rowCount, layout.columnCount)) {
                Timber.d("Swap rejected: Resulting layout has collisions or out-of-bounds.")
                return@update currentState
            }

            val newLayout = currentState.layout.copy(keys = newKeysCandidate)
            currentState.copy(layout = newLayout)
        }
    }

    private fun hasIssues(keys: List<KeyData>, rowCount: Int, colCount: Int): Boolean {
        keys.forEach { key ->
            if (key.row < 0 || key.column < 0 ||
                key.row + key.rowSpan > rowCount ||
                key.column + key.colSpan > colCount
            ) {
                return true
            }
        }

        for (i in keys.indices) {
            for (j in i + 1 until keys.size) {
                if (isRectOverlapping(keys[i], keys[j])) {
                    return true
                }
            }
        }
        return false
    }

    private fun isRectOverlapping(key1: KeyData, key2: KeyData): Boolean {
        val k1Left = key1.column
        val k1Right = key1.column + key1.colSpan
        val k1Top = key1.row
        val k1Bottom = key1.row + key1.rowSpan

        val k2Left = key2.column
        val k2Right = key2.column + key2.colSpan
        val k2Top = key2.row
        val k2Bottom = key2.row + key2.rowSpan

        return !(k1Right <= k2Left ||
                k1Left >= k2Right ||
                k1Bottom <= k2Top ||
                k1Top >= k2Bottom)
    }

    fun updateIsRomaji(isRomaji: Boolean) {
        _uiState.update { it.copy(isRomaji = isRomaji) }
    }

    fun onDoneNavigating() {
        _uiState.update { it.copy(navigateBack = false) }
    }

    fun applyTemplate(templateLayout: KeyboardLayout) {
        val keysWithEnsuredIds = templateLayout.keys.map { key ->
            if (key.keyId == null) {
                key.copy(keyId = UUID.randomUUID().toString())
            } else {
                key
            }
        }

        val labelToIdMap = keysWithEnsuredIds
            .filter { it.label.isNotEmpty() && it.keyId != null }
            .associate { it.label to it.keyId!! }

        val reKeyedFlickMaps = templateLayout.flickKeyMaps.mapNotNull { (labelKey, flickActions) ->
            val newKeyId = labelToIdMap[labelKey]
            if (newKeyId != null) {
                newKeyId to flickActions
            } else {
                null
            }
        }.toMap()

        val finalLayout = templateLayout.copy(
            keys = keysWithEnsuredIds,
            flickKeyMaps = reKeyedFlickMaps
        )

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
