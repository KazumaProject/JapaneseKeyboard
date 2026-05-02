package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
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
        // QWERTY 風テンプレートは「英語入力」の隣に置くのが自然なのでここに追加。
        // 既存の表示順は維持する。
        LayoutTemplate(
            "英語 QWERTY",
            KeyboardDefaultLayouts.createQwertyTemplateLayout()
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

        if (newId != null) {
            loadLayout(newId)
        } else {
            createNewLayout()
        }
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
                _uiState.update { it.copy(duplicateNameError = true) }
            } else {
                if (idToSave != null) {
                    repository.deleteLayout(idToSave)
                }

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

            val updatedKeys = layout.keys.mapNotNull { key ->
                if (key.row >= newRowCount) {
                    null
                } else if (key.row + key.rowSpan > newRowCount) {
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

    fun deleteRowAt(rowIndex: Int) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.rowCount <= 1) return@update currentState

            val updatedKeys = layout.keys.mapNotNull { key ->
                val keyRowStart = key.row
                val keyRowEnd = key.row + key.rowSpan

                when {
                    keyRowStart > rowIndex -> key.copy(row = key.row - 1)
                    keyRowStart <= rowIndex && keyRowEnd > rowIndex -> {
                        val newSpan = key.rowSpan - 1
                        if (newSpan > 0) key.copy(rowSpan = newSpan) else null
                    }

                    else -> key
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

            val updatedKeys = layout.keys.mapNotNull { key ->
                val keyColStart = key.column
                val keyColEnd = key.column + key.colSpan

                when {
                    keyColStart > columnIndex -> key.copy(column = key.column - 1)
                    keyColStart <= columnIndex && keyColEnd > columnIndex -> {
                        val newSpan = key.colSpan - 1
                        if (newSpan > 0) key.copy(colSpan = newSpan) else null
                    }

                    else -> key
                }
            }

            val newLayout = layout.copy(keys = updatedKeys, columnCount = layout.columnCount - 1)
            currentState.copy(layout = newLayout)
        }
    }

    private fun createEmptyKey(row: Int, column: Int): KeyData {
        return KeyData(
            label = " ",
            row = row,
            column = column,
            isFlickable = true,
            keyId = UUID.randomUUID().toString(),
            keyType = KeyType.PETAL_FLICK
        )
    }

    fun selectKeyForEditing(keyId: String?) {
        _uiState.update { it.copy(selectedKeyIdentifier = keyId) }
    }

    fun doneNavigatingToKeyEditor() {
        _uiState.update { it.copy(selectedKeyIdentifier = null) }
    }

    /**
     * Petal Flick / TwoStep Flick 両対応
     */
    fun updateKeyAndMappings(
        newKeyData: KeyData,
        flickMap: Map<FlickDirection, FlickAction>,
        twoStepMap: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>,
        longPressFlickMap: Map<FlickDirection, String>,
        twoStepLongPressMap: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>,
        circularFlickMaps: List<Map<CircularFlickDirection, FlickAction>> = emptyList()
    ) {
        val keyId = newKeyData.keyId ?: run {
            Timber.e("FATAL: updateKeyAndMappings received a KeyData with a null keyId!")
            return
        }

        _uiState.update { currentState ->
            val layout = currentState.layout

            val oldKeyData = layout.keys.find { it.keyId == keyId } ?: return@update currentState
            val otherKeys = layout.keys.filter { it.keyId != keyId }

            val crushedKeys = otherKeys.filter { isRectOverlapping(newKeyData, it) }
            val crushedKeyIds = crushedKeys.map { it.keyId }.toSet()

            val oldCells = getOccupiedCells(oldKeyData)
            val newCells = getOccupiedCells(newKeyData)
            val abandonedCells = oldCells.filter { !newCells.contains(it) }

            val newEmptyKeys = abandonedCells.map { (row, col) -> createEmptyKey(row, col) }

            val finalKeys = otherKeys
                .filter { !crushedKeyIds.contains(it.keyId) }
                .plus(newKeyData)
                .plus(newEmptyKeys)

            val finalFlickMaps = layout.flickKeyMaps.toMutableMap()
            val finalCircularFlickMaps = layout.circularFlickKeyMaps.toMutableMap()
            val finalTwoStepMaps = layout.twoStepFlickKeyMaps.toMutableMap()
            val finalLongPressFlickMaps = layout.longPressFlickKeyMaps.toMutableMap()
            val finalTwoStepLongPressMaps = layout.twoStepLongPressKeyMaps.toMutableMap()

            crushedKeyIds.forEach {
                finalFlickMaps.remove(it)
                finalCircularFlickMaps.remove(it)
                finalTwoStepMaps.remove(it)
                finalLongPressFlickMaps.remove(it)
                finalTwoStepLongPressMaps.remove(it)
            }

            when (newKeyData.keyType) {
                KeyType.TWO_STEP_FLICK -> {
                    // 2段フリックに切り替えた場合、1段フリック設定を消して2段を保存
                    finalFlickMaps.remove(keyId)
                    finalCircularFlickMaps.remove(keyId)
                    finalLongPressFlickMaps.remove(keyId)
                    finalTwoStepMaps[keyId] = twoStepMap
                    if (twoStepLongPressMap.isNotEmpty()) {
                        finalTwoStepLongPressMaps[keyId] = twoStepLongPressMap
                    } else {
                        finalTwoStepLongPressMaps.remove(keyId)
                    }
                }

                KeyType.CIRCULAR_FLICK -> {
                    finalFlickMaps.remove(keyId)
                    finalTwoStepMaps.remove(keyId)
                    finalTwoStepLongPressMaps.remove(keyId)
                    finalLongPressFlickMaps.remove(keyId)
                    finalCircularFlickMaps[keyId] =
                        circularFlickMaps.ifEmpty { listOf(emptyMap()) }
                }

                else -> {
                    // 1段フリック系の場合、2段フリック設定を消して1段を保存
                    finalTwoStepMaps.remove(keyId)
                    finalTwoStepLongPressMaps.remove(keyId)
                    finalCircularFlickMaps.remove(keyId)
                    finalFlickMaps[keyId] = listOf(flickMap)
                    if (longPressFlickMap.isNotEmpty()) {
                        finalLongPressFlickMaps[keyId] = longPressFlickMap
                    } else {
                        finalLongPressFlickMaps.remove(keyId)
                    }
                }
            }

            val newLayout = layout.copy(
                keys = finalKeys,
                flickKeyMaps = finalFlickMaps,
                circularFlickKeyMaps = finalCircularFlickMaps,
                twoStepFlickKeyMaps = finalTwoStepMaps,
                longPressFlickKeyMaps = finalLongPressFlickMaps,
                twoStepLongPressKeyMaps = finalTwoStepLongPressMaps
            )
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

    /**
     * テンプレートを現在の編集対象レイアウトに適用する。
     *
     * このメソッドの責務はキー単位の `flickKeyMaps` 等のキーを、
     * テンプレート上の識別子（label / templateKeyId）から、
     * 実行時に保存される keyId（runtimeKeyId）へ変換すること。
     *
     * ## 設計
     *
     * 1. **keyId ベース変換を主経路にする。**
     *    QWERTY のように同じ label のキー、空 label のアイコンキー、
     *    複数の特殊キーが並ぶテンプレートでは、label をキーとする変換は破綻しやすい。
     *    そのためテンプレートに `KeyData.keyId` がある場合は、
     *    その keyId を「テンプレートID」として扱い、
     *    `templateKeyId -> runtimeKeyId` のマップを優先的に引く。
     *
     * 2. **runtime keyId は既存方式を踏襲する。**
     *    既存実装では `keyId == null` のキーに対してのみ UUID を生成し、
     *    既に keyId が設定されているキーはその keyId をそのまま runtime keyId として使う。
     *    `"delete_key"` / `"switch_next_ime"` / `"dakuten_toggle_key"` のように
     *    IMEService 側が文字列で参照している keyId があるため、ここで安易に再生成しない。
     *
     * 3. **既存テンプレート互換のため label ベース fallback を残す。**
     *    既存「かな入力」「英語入力」「数字入力」テンプレートは
     *    `flickKeyMaps[label]` 形式で書かれているため、
     *    keyId ベースで解決できない場合のみ label でフォールバック検索する。
     *    ただし label 重複時は識別子として安全でないので、
     *    重複している label は label ベース fallback の対象から外す。
     *    label はあくまで表示用、というスタンス。
     */
    fun applyTemplate(templateLayout: KeyboardLayout) {
        // 1) 各キーの runtime keyId を確定する。
        //    keyId 未設定のキーだけ UUID を割り当て、既存 keyId はそのまま保持する。
        val keysWithEnsuredIds = templateLayout.keys.map { key ->
            if (key.keyId == null) key.copy(keyId = UUID.randomUUID().toString()) else key
        }

        // 2) templateKeyId -> runtimeKeyId （主経路）。
        //    テンプレートで keyId が明示されているキーだけマッピングする。
        //    現在の方式では runtimeKeyId == templateKeyId だが、
        //    将来的に runtime 側で別 ID を使うことになっても、
        //    flickKeyMaps の変換側はこのマップを引くだけで済むようにしている。
        val templateKeyIdToRuntimeId: Map<String, String> = templateLayout.keys
            .zip(keysWithEnsuredIds)
            .mapNotNull { (templateKey, runtimeKey) ->
                val templateKeyId = templateKey.keyId
                val runtimeKeyId = runtimeKey.keyId
                if (templateKeyId != null && runtimeKeyId != null) {
                    templateKeyId to runtimeKeyId
                } else {
                    null
                }
            }
            .toMap()

        // 3) label -> runtimeKeyId （fallback、既存テンプレート互換用）。
        //    重複 label は識別子として使えないので label ベース fallback から除外する。
        val labelToRuntimeId: Map<String, String> = keysWithEnsuredIds
            .filter { it.label.isNotEmpty() && it.keyId != null }
            .groupBy { it.label }
            .filterValues { it.size == 1 }
            .mapValues { (_, group) -> group.single().keyId!! }

        // 4) flickKeyMaps 系のキー変換。
        //    templateKeyId 経路を最優先で引き、ヒットしない場合のみ label fallback を引く。
        //    どちらにもヒットしない entry は黙ってドロップする（既存実装と同じ挙動）。
        fun <V> rekey(source: Map<String, V>): Map<String, V> {
            return source.mapNotNull { (entryKey, value) ->
                val runtimeId = templateKeyIdToRuntimeId[entryKey]
                    ?: labelToRuntimeId[entryKey]
                if (runtimeId != null) runtimeId to value else null
            }.toMap()
        }

        val finalLayout = templateLayout.copy(
            keys = keysWithEnsuredIds,
            flickKeyMaps = rekey(templateLayout.flickKeyMaps),
            circularFlickKeyMaps = rekey(templateLayout.circularFlickKeyMaps),
            twoStepFlickKeyMaps = rekey(templateLayout.twoStepFlickKeyMaps),
            longPressFlickKeyMaps = rekey(templateLayout.longPressFlickKeyMaps),
            twoStepLongPressKeyMaps = rekey(templateLayout.twoStepLongPressKeyMaps)
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
