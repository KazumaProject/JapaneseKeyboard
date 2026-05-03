package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.copyWithItems
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
import com.kazumaproject.custom_keyboard.data.swapKeyPlacements
import com.kazumaproject.custom_keyboard.data.usesFlexiblePlacement
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.ImportableKeyboardLayout
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
    val isRomaji: Boolean = false,
    val isDirectMode: Boolean = false
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
        LayoutTemplate(
            "QWERTY",
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
                    isRomaji = loadedLayout.isRomaji,
                    isDirectMode = loadedLayout.isDirectMode
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
                isRomaji = false,
                isDirectMode = false
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

                val layoutToSave = currentState.layout.copy(
                    isRomaji = currentState.isRomaji,
                    isDirectMode = currentState.isDirectMode
                )
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
            if (layout.usesFlexiblePlacement()) {
                return@update currentState.copy(
                    layout = layout.copy(
                        rowCount = newRowCount,
                        rowUnitCount = layout.rowUnitCount + 2
                    )
                )
            }
            val newKeys = layout.keys.toMutableList()
            for (col in 0 until layout.columnCount) {
                newKeys.add(createEmptyKey(newRowCount - 1, col))
            }
            val newLayout = layout.copyWithKeys(newKeys, rowCount = newRowCount)
            currentState.copy(layout = newLayout)
        }
    }

    fun removeRow() {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.rowCount <= 1) return@update currentState
            val newRowCount = layout.rowCount - 1
            if (layout.usesFlexiblePlacement()) {
                val newRowUnitCount = newRowCount * 2
                val newItems = trimItemsToBounds(
                    items = layout.items,
                    rowUnitCount = newRowUnitCount,
                    columnUnitCount = layout.columnUnitCount
                )
                return@update currentState.copy(
                    layout = layout.copy(
                        rowCount = newRowCount,
                        rowUnitCount = newRowUnitCount
                    ).copyWithItems(newItems)
                )
            }

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

            val newLayout = layout.copyWithKeys(updatedKeys, rowCount = newRowCount)
            currentState.copy(layout = newLayout)
        }
    }

    fun addColumn() {
        _uiState.update { currentState ->
            val layout = currentState.layout
            val newColumnCount = layout.columnCount + 1
            if (layout.usesFlexiblePlacement()) {
                return@update currentState.copy(
                    layout = layout.copy(
                        columnCount = newColumnCount,
                        columnUnitCount = layout.columnUnitCount + 2
                    )
                )
            }
            val newKeys = layout.keys.toMutableList()
            for (row in 0 until layout.rowCount) {
                newKeys.add(createEmptyKey(row, newColumnCount - 1))
            }
            val newLayout = layout.copyWithKeys(
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
            if (layout.usesFlexiblePlacement()) {
                val newColumnUnitCount = newColumnCount * 2
                val newItems = trimItemsToBounds(
                    items = layout.items,
                    rowUnitCount = layout.rowUnitCount,
                    columnUnitCount = newColumnUnitCount
                )
                return@update currentState.copy(
                    layout = layout.copy(
                        columnCount = newColumnCount,
                        columnUnitCount = newColumnUnitCount
                    ).copyWithItems(newItems)
                )
            }

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

            val newLayout = layout.copyWithKeys(updatedKeys, columnCount = newColumnCount)
            currentState.copy(layout = newLayout)
        }
    }

    fun deleteRowAt(rowIndex: Int) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.rowCount <= 1) return@update currentState
            if (layout.usesFlexiblePlacement()) {
                val deleteStart = rowIndex * 2
                val deleteEnd = deleteStart + 2
                val newItems = deleteUnitRange(
                    items = layout.items,
                    startUnits = deleteStart,
                    endUnits = deleteEnd,
                    isRow = true
                )
                val newRowCount = layout.rowCount - 1
                return@update currentState.copy(
                    layout = layout.copy(
                        rowCount = newRowCount,
                        rowUnitCount = newRowCount * 2
                    ).copyWithItems(newItems)
                )
            }

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

            val newLayout = layout.copyWithKeys(updatedKeys, rowCount = layout.rowCount - 1)
            currentState.copy(layout = newLayout)
        }
    }

    fun deleteColumnAt(columnIndex: Int) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (layout.columnCount <= 1) return@update currentState
            if (layout.usesFlexiblePlacement()) {
                val deleteStart = columnIndex * 2
                val deleteEnd = deleteStart + 2
                val newItems = deleteUnitRange(
                    items = layout.items,
                    startUnits = deleteStart,
                    endUnits = deleteEnd,
                    isRow = false
                )
                val newColumnCount = layout.columnCount - 1
                return@update currentState.copy(
                    layout = layout.copy(
                        columnCount = newColumnCount,
                        columnUnitCount = newColumnCount * 2
                    ).copyWithItems(newItems)
                )
            }

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

            val newLayout = layout.copyWithKeys(updatedKeys, columnCount = layout.columnCount - 1)
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
            val finalFlickMaps = layout.flickKeyMaps.toMutableMap()
            val finalCircularFlickMaps = layout.circularFlickKeyMaps.toMutableMap()
            val finalTwoStepMaps = layout.twoStepFlickKeyMaps.toMutableMap()
            val finalLongPressFlickMaps = layout.longPressFlickKeyMaps.toMutableMap()
            val finalTwoStepLongPressMaps = layout.twoStepLongPressKeyMaps.toMutableMap()

            applyUpdatedMappings(
                keyId = keyId,
                newKeyData = newKeyData,
                flickMap = flickMap,
                twoStepMap = twoStepMap,
                longPressFlickMap = longPressFlickMap,
                twoStepLongPressMap = twoStepLongPressMap,
                circularFlickMaps = circularFlickMaps,
                finalFlickMaps = finalFlickMaps,
                finalCircularFlickMaps = finalCircularFlickMaps,
                finalTwoStepMaps = finalTwoStepMaps,
                finalLongPressFlickMaps = finalLongPressFlickMaps,
                finalTwoStepLongPressMaps = finalTwoStepLongPressMaps
            )

            if (layout.usesFlexiblePlacement()) {
                val oldItem = layout.items.filterIsInstance<KeyItem>().firstOrNull {
                    it.id == keyId || it.keyData.keyId == keyId
                } ?: return@update currentState

                val updatedItems = layout.items.map { item ->
                    if (item is KeyItem && item.id == oldItem.id) {
                        item.copy(
                            keyData = newKeyData,
                            placement = item.placement.copy(
                                rowSpanUnits = newKeyData.rowSpan * 2,
                                columnSpanUnits = newKeyData.colSpan * 2
                            )
                        )
                    } else {
                        item
                    }
                }

                if (hasPlacementIssues(updatedItems, layout.rowUnitCount, layout.columnUnitCount)) {
                    return@update currentState
                }

                val newLayout = layout.copyWithItems(updatedItems).copy(
                    flickKeyMaps = finalFlickMaps,
                    circularFlickKeyMaps = finalCircularFlickMaps,
                    twoStepFlickKeyMaps = finalTwoStepMaps,
                    longPressFlickKeyMaps = finalLongPressFlickMaps,
                    twoStepLongPressKeyMaps = finalTwoStepLongPressMaps
                )
                return@update currentState.copy(layout = newLayout)
            }

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

            crushedKeyIds.forEach {
                finalFlickMaps.remove(it)
                finalCircularFlickMaps.remove(it)
                finalTwoStepMaps.remove(it)
                finalLongPressFlickMaps.remove(it)
                finalTwoStepLongPressMaps.remove(it)
            }

            val newLayout = layout.copyWithKeys(finalKeys).copy(
                flickKeyMaps = finalFlickMaps,
                circularFlickKeyMaps = finalCircularFlickMaps,
                twoStepFlickKeyMaps = finalTwoStepMaps,
                longPressFlickKeyMaps = finalLongPressFlickMaps,
                twoStepLongPressKeyMaps = finalTwoStepLongPressMaps
            )
            currentState.copy(layout = newLayout)
        }
    }

    private fun applyUpdatedMappings(
        keyId: String,
        newKeyData: KeyData,
        flickMap: Map<FlickDirection, FlickAction>,
        twoStepMap: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>,
        longPressFlickMap: Map<FlickDirection, String>,
        twoStepLongPressMap: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>,
        circularFlickMaps: List<Map<CircularFlickDirection, FlickAction>>,
        finalFlickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>>,
        finalCircularFlickMaps: MutableMap<String, List<Map<CircularFlickDirection, FlickAction>>>,
        finalTwoStepMaps: MutableMap<String, Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>>,
        finalLongPressFlickMaps: MutableMap<String, Map<FlickDirection, String>>,
        finalTwoStepLongPressMaps: MutableMap<String, Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>>
    ) {
        when (newKeyData.keyType) {
            KeyType.TWO_STEP_FLICK -> {
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

    /**
     * Drag-swap two KeyItems by exchanging their [GridPlacement]s.
     *
     * Source of truth is `layout.items` + `GridPlacement` — KeyData.row /
     * KeyData.column are NOT rewritten here, because they cannot represent
     * half-cell offsets used by QWERTY-family templates and would corrupt
     * the visual placement.
     *
     * The swap is rejected (no-op) if it would produce overlaps or
     * out-of-bounds placements.
     */
    fun swapKeys(draggedKeyId: String, targetKeyId: String) {
        _uiState.update { currentState ->
            val layout = currentState.layout
            if (draggedKeyId == targetKeyId) return@update currentState

            val swapped = layout.swapKeyPlacements(draggedKeyId, targetKeyId)
            // swapKeyPlacements returns the original layout if the swap is
            // invalid (overlap, out of bounds, missing id).
            if (swapped === layout) return@update currentState
            currentState.copy(layout = swapped)
        }
    }

    fun addSpacer(
        rowUnits: Int,
        columnUnits: Int,
        rowSpanUnits: Int,
        columnSpanUnits: Int
    ): Boolean {
        val currentState = _uiState.value
        val layout = currentState.layout
        val spacer = SpacerItem(
            id = "spacer_${UUID.randomUUID()}",
            placement = GridPlacement(
                rowUnits = rowUnits,
                columnUnits = columnUnits,
                rowSpanUnits = rowSpanUnits,
                columnSpanUnits = columnSpanUnits
            )
        )
        val updatedItems = layout.items + spacer
        if (!isValidPlacementUpdate(layout, updatedItems)) return false
        _uiState.value = currentState.copy(layout = layout.copyWithItems(updatedItems))
        return true
    }

    fun updateSpacerPlacement(spacerId: String, placement: GridPlacement): Boolean {
        val currentState = _uiState.value
        val layout = currentState.layout
        var found = false
        val updatedItems = layout.items.map { item ->
            if (item is SpacerItem && item.id == spacerId) {
                found = true
                item.copy(placement = placement)
            } else {
                item
            }
        }
        if (!found || !isValidPlacementUpdate(layout, updatedItems)) return false
        _uiState.value = currentState.copy(layout = layout.copyWithItems(updatedItems))
        return true
    }

    fun deleteSpacer(spacerId: String): Boolean {
        val currentState = _uiState.value
        val layout = currentState.layout
        val updatedItems = layout.items.filterNot { it is SpacerItem && it.id == spacerId }
        if (updatedItems.size == layout.items.size) return false
        _uiState.value = currentState.copy(layout = layout.copyWithItems(updatedItems))
        return true
    }

    private fun isValidPlacementUpdate(
        layout: KeyboardLayout,
        items: List<KeyboardLayoutItem>
    ): Boolean {
        return !hasPlacementIssues(
            items = items,
            rowUnitCount = layout.rowUnitCount,
            columnUnitCount = layout.columnUnitCount
        )
    }

    /**
     * Cell-grid overlap test for [updateKeyAndMappings] (which works on
     * KeyData.row/column for the simple, single-key edit flow).
     *
     * Drag-swap uses [com.kazumaproject.custom_keyboard.data.hasPlacementIssues]
     * on GridPlacement instead — see [swapKeys].
     */
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

    private fun trimItemsToBounds(
        items: List<KeyboardLayoutItem>,
        rowUnitCount: Int,
        columnUnitCount: Int
    ): List<KeyboardLayoutItem> {
        return items.mapNotNull { item ->
            val p = item.placement
            if (p.rowUnits >= rowUnitCount || p.columnUnits >= columnUnitCount) {
                return@mapNotNull null
            }
            val newPlacement = p.copy(
                rowSpanUnits = minOf(p.rowSpanUnits, rowUnitCount - p.rowUnits),
                columnSpanUnits = minOf(p.columnSpanUnits, columnUnitCount - p.columnUnits)
            )
            if (newPlacement.rowSpanUnits <= 0 || newPlacement.columnSpanUnits <= 0) {
                null
            } else {
                item.withPlacementAndApproximateKeyData(newPlacement)
            }
        }
    }

    private fun deleteUnitRange(
        items: List<KeyboardLayoutItem>,
        startUnits: Int,
        endUnits: Int,
        isRow: Boolean
    ): List<KeyboardLayoutItem> {
        val removedUnits = endUnits - startUnits
        return items.mapNotNull { item ->
            val p = item.placement
            val itemStart = if (isRow) p.rowUnits else p.columnUnits
            val itemSpan = if (isRow) p.rowSpanUnits else p.columnSpanUnits
            val itemEnd = itemStart + itemSpan

            val newStart: Int
            val newSpan: Int
            when {
                itemEnd <= startUnits -> {
                    newStart = itemStart
                    newSpan = itemSpan
                }
                itemStart >= endUnits -> {
                    newStart = itemStart - removedUnits
                    newSpan = itemSpan
                }
                else -> {
                    val remainingBefore = maxOf(0, startUnits - itemStart)
                    val remainingAfter = maxOf(0, itemEnd - endUnits)
                    newStart = if (itemStart < startUnits) itemStart else startUnits
                    newSpan = remainingBefore + remainingAfter
                }
            }

            if (newSpan <= 0) {
                null
            } else {
                val newPlacement = if (isRow) {
                    p.copy(rowUnits = newStart, rowSpanUnits = newSpan)
                } else {
                    p.copy(columnUnits = newStart, columnSpanUnits = newSpan)
                }
                item.withPlacementAndApproximateKeyData(newPlacement)
            }
        }
    }

    private fun KeyboardLayoutItem.withPlacementAndApproximateKeyData(
        placement: GridPlacement
    ): KeyboardLayoutItem {
        return when (this) {
            is SpacerItem -> copy(placement = placement)
            is KeyItem -> copy(
                keyData = keyData.copy(
                    row = placement.rowUnits / 2,
                    column = placement.columnUnits / 2,
                    rowSpan = (placement.rowSpanUnits + 1) / 2,
                    colSpan = (placement.columnSpanUnits + 1) / 2
                ),
                placement = placement
            )
        }
    }

    fun updateIsRomaji(isRomaji: Boolean) {
        _uiState.update { it.copy(isRomaji = isRomaji) }
    }

    fun updateIsDirectMode(isDirectMode: Boolean) {
        _uiState.update { it.copy(isDirectMode = isDirectMode) }
    }

    fun onDoneNavigating() {
        _uiState.update { it.copy(navigateBack = false) }
    }

    fun applyTemplate(templateLayout: KeyboardLayout) {
        val keysWithEnsuredIds = templateLayout.keys.map { key ->
            val keyWithId = if (key.keyId == null) key.copy(keyId = UUID.randomUUID().toString()) else key
            if (
                !keyWithId.isSpecialKey &&
                keyWithId.keyType == KeyType.NORMAL &&
                keyWithId.label.isNotBlank() &&
                keyWithId.action == null
            ) {
                keyWithId.copy(action = KeyAction.Text(keyWithId.label))
            } else {
                keyWithId
            }
        }

        val labelToIdMap = keysWithEnsuredIds
            .filter { it.label.isNotEmpty() && it.keyId != null }
            .associate { it.label to it.keyId!! }

        val reKeyedFlickMaps = templateLayout.flickKeyMaps.mapNotNull { (labelKey, flickActions) ->
            val newKeyId = labelToIdMap[labelKey]
            if (newKeyId != null) newKeyId to flickActions else null
        }.toMap()

        val reKeyedCircularFlickMaps = templateLayout.circularFlickKeyMaps.mapNotNull { (labelKey, flickActions) ->
            val newKeyId = labelToIdMap[labelKey]
            if (newKeyId != null) newKeyId to flickActions else null
        }.toMap()

        val reKeyedTwoStepMaps = templateLayout.twoStepFlickKeyMaps.mapNotNull { (labelKey, map) ->
            val newKeyId = labelToIdMap[labelKey]
            if (newKeyId != null) newKeyId to map else null
        }.toMap()

        val reKeyedLongPressFlickMaps = templateLayout.longPressFlickKeyMaps.mapNotNull { (labelKey, map) ->
            val newKeyId = labelToIdMap[labelKey]
            if (newKeyId != null) newKeyId to map else null
        }.toMap()

        val reKeyedTwoStepLongPressMaps = templateLayout.twoStepLongPressKeyMaps.mapNotNull { (labelKey, map) ->
            val newKeyId = labelToIdMap[labelKey]
            if (newKeyId != null) newKeyId to map else null
        }.toMap()

        val finalLayoutBase = if (templateLayout.usesFlexiblePlacement()) {
            val keysByOriginalId = templateLayout.keys
                .zip(keysWithEnsuredIds)
                .mapNotNull { (original, updated) ->
                    original.keyId?.let { it to updated }
                }
                .toMap()
            val updatedItems = templateLayout.items.map { item ->
                when (item) {
                    is SpacerItem -> item
                    is KeyItem -> {
                        val updatedKeyData = keysByOriginalId[item.keyData.keyId] ?: item.keyData
                        item.copy(
                            id = updatedKeyData.keyId ?: item.id,
                            keyData = updatedKeyData
                        )
                    }
                }
            }
            templateLayout.copyWithItems(updatedItems)
        } else {
            templateLayout.copyWithKeys(keysWithEnsuredIds)
        }

        val finalLayout = finalLayoutBase.copy(
            flickKeyMaps = reKeyedFlickMaps,
            circularFlickKeyMaps = reKeyedCircularFlickMaps,
            twoStepFlickKeyMaps = reKeyedTwoStepMaps,
            longPressFlickKeyMaps = reKeyedLongPressFlickMaps,
            twoStepLongPressKeyMaps = reKeyedTwoStepLongPressMaps
        )

        _uiState.update { currentState ->
            currentState.copy(
                layout = finalLayout,
                isDirectMode = finalLayout.isDirectMode
            )
        }
    }

    suspend fun getLayoutsForExport(): List<FullKeyboardLayout> {
        return repository.getAllFullLayoutsForExport()
    }

    /**
     * Import 用 entrypoint。
     *
     * 受け取るのは外部 JSON から正規化済みの [ImportableKeyboardLayout] であり、
     * Room の Relation 用 [FullKeyboardLayout] ではない。
     * これにより spacers などの新フィールドが将来欠損していても、
     * Repository / DAO 層には null が伝搬しない設計になっている。
     */
    fun importLayouts(layouts: List<ImportableKeyboardLayout>) {
        viewModelScope.launch {
            repository.importLayouts(layouts)
        }
    }
}
