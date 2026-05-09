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
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.copyWithItems
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
import com.kazumaproject.custom_keyboard.data.swapKeyPlacements
import com.kazumaproject.custom_keyboard.data.usesFlexiblePlacement
import com.kazumaproject.custom_keyboard.data.withCanonicalFlexibleBounds
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.ImportableKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export.KeyboardLayoutImportResult
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.CursorSource
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.FlexiblePlacementSolver
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.GridSpan
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.HalfRowPlacement
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionPolicy
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionTarget
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.InsertionTargetNavigator
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.KeyboardEditorMode
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.NudgeDirection
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.PlacementCursor
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.PlacementOperation
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.PlacementPreviewStatus
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.placement.PlacementStrategy
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
    val isDirectMode: Boolean = false,
    val editorMode: KeyboardEditorMode = KeyboardEditorMode.Normal,
    val placementCursor: PlacementCursor? = null,
    val previewLayout: KeyboardLayout? = null,
    val previewMovedItemIds: Set<String> = emptySet(),
    val previewInsertedItemId: String? = null,
    val previewStrategy: PlacementStrategy? = null,
    val previewStatus: PlacementPreviewStatus = PlacementPreviewStatus.None,
    val selectedItemId: String? = null,
    val insertionPolicy: InsertionPolicy = InsertionPolicy.PreferHorizontal,
    val halfRowPlacement: HalfRowPlacement = HalfRowPlacement.Upper
)

data class LayoutTemplate(val nameResId: Int, val layout: KeyboardLayout)

fun shouldShowKeyboardEditorStructuralControls(layout: KeyboardLayout): Boolean =
    keyboardEditorCapabilities(layout).showGridStructuralControls

@HiltViewModel
class KeyboardEditorViewModel @Inject constructor(
    private val repository: KeyboardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState = _uiState.asStateFlow()

    private var currentEditingId: Long? = null
    private val placementSolver = FlexiblePlacementSolver()
    private val placementNavigator = InsertionTargetNavigator()

    val availableTemplates: List<LayoutTemplate> = listOf(
        LayoutTemplate(
            R.string.template_flick_kana_cursor,
            KeyboardDefaultLayouts.createFlickKanaTemplateLayout(true)
        ),
        LayoutTemplate(
            R.string.template_flick_english_cursor,
            KeyboardDefaultLayouts.createFlickEnglishTemplateLayout(
                isDefaultKey = true,
                isUpperCase = false
            )
        ),
        LayoutTemplate(
            R.string.template_qwerty,
            KeyboardDefaultLayouts.createQwertyTemplateLayout()
        ),
        LayoutTemplate(
            R.string.template_empty_5x4_flexible,
            KeyboardDefaultLayouts.createEmpty5x4FlexibleTemplateLayout()
        ),
        LayoutTemplate(R.string.template_number, KeyboardDefaultLayouts.createNumberTemplateLayout())
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
                return@launch
            }

            // 重要: 既存レイアウトを保存する場合でも、ここで親 (keyboard_layouts 行) を
            //       一度 delete して作り直してはいけない。
            //       親 row を delete すると、stableId / sortOrder / createdAt の identity
            //       が失われ、MoveToCustomKeyboard(stableId) が「削除済みのカスタムキーボード」
            //       として扱われる原因になる。
            //       Repository 側で「新規作成」「既存更新」を分けるので、ViewModel は
            //       現在の UI state を渡すだけでよい。
            val layoutToSave = currentState.layout.copy(
                isRomaji = currentState.isRomaji,
                isDirectMode = currentState.isDirectMode
            ).canonicalizeIfFlexible()

            val saveResult = runCatching {
                repository.saveLayout(
                    layout = layoutToSave,
                    name = currentState.name,
                    id = idToSave
                )
            }
            saveResult
                .onFailure { e ->
                    Timber.e(e, "saveLayout failed for id=%s", idToSave)
                }
                .onSuccess {
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

    fun updateInsertionPolicy(policy: InsertionPolicy) {
        val state = _uiState.value
        _uiState.value = state.copy(
            insertionPolicy = policy,
            editorMode = state.editorMode.withPolicy(policy),
            placementCursor = state.placementCursor?.copy(policy = policy)
        )
        val cursor = _uiState.value.placementCursor ?: return
        if (_uiState.value.editorMode != KeyboardEditorMode.Normal) {
            updatePlacementCursor(cursor.target, cursor.source)
        }
    }

    fun setHalfRowPlacement(value: HalfRowPlacement) {
        val state = _uiState.value
        _uiState.value = state.copy(halfRowPlacement = value)
        val cursor = _uiState.value.placementCursor ?: return
        if (_uiState.value.editorMode != KeyboardEditorMode.Normal) {
            updatePlacementCursor(cursor.target, cursor.source)
        }
    }

    fun addRow() {
        _uiState.update { currentState ->
            val layout = currentState.layout
            val newRowCount = layout.rowCount + 1
            if (layout.usesFlexiblePlacement()) {
                val expanded = layout.copy(rowUnitCount = layout.rowUnitCount + 2)
                    .withCanonicalFlexibleBounds(minimumRowUnits = layout.rowUnitCount + 2)
                return@update currentState.copy(layout = expanded)
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
                val newLayout = layout.copy(rowUnitCount = newRowUnitCount)
                    .copyWithItems(newItems)
                    .withCanonicalFlexibleBounds(minimumRowUnits = newRowUnitCount)
                return@update currentState.copy(layout = newLayout.takeIfValidFlexible() ?: layout)
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
                val expanded = layout.copy(columnUnitCount = layout.columnUnitCount + 2)
                    .withCanonicalFlexibleBounds(minimumColumnUnits = layout.columnUnitCount + 2)
                return@update currentState.copy(layout = expanded)
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
                val newLayout = layout.copy(columnUnitCount = newColumnUnitCount)
                    .copyWithItems(newItems)
                    .withCanonicalFlexibleBounds(minimumColumnUnits = newColumnUnitCount)
                return@update currentState.copy(layout = newLayout.takeIfValidFlexible() ?: layout)
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
                val newRowUnitCount = newRowCount * 2
                val newLayout = layout.copy(rowUnitCount = newRowUnitCount)
                    .copyWithItems(newItems)
                    .withCanonicalFlexibleBounds(minimumRowUnits = newRowUnitCount)
                return@update currentState.copy(layout = newLayout.takeIfValidFlexible() ?: layout)
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
                val newColumnUnitCount = newColumnCount * 2
                val newLayout = layout.copy(columnUnitCount = newColumnUnitCount)
                    .copyWithItems(newItems)
                    .withCanonicalFlexibleBounds(minimumColumnUnits = newColumnUnitCount)
                return@update currentState.copy(layout = newLayout.takeIfValidFlexible() ?: layout)
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

    fun enterNewKeyPlacementMode(
        span: GridSpan,
        policy: InsertionPolicy = _uiState.value.insertionPolicy
    ) {
        _uiState.update {
            it.copy(
                editorMode = KeyboardEditorMode.PlacingNewKey(span, policy),
                placementCursor = null,
                previewLayout = null,
                previewMovedItemIds = emptySet(),
                previewInsertedItemId = null,
                previewStrategy = null,
                previewStatus = PlacementPreviewStatus.None,
                selectedItemId = null
            )
        }
    }

    fun enterHalfKeyPlacementMode() {
        enterNewKeyPlacementMode(GridSpan(rowSpanUnits = 1, columnSpanUnits = 1))
    }

    fun enterOneKeyPlacementMode() {
        enterNewKeyPlacementMode(GridSpan(rowSpanUnits = 2, columnSpanUnits = 2))
    }

    fun enterSpacerPlacementMode(
        span: GridSpan,
        policy: InsertionPolicy = _uiState.value.insertionPolicy
    ) {
        _uiState.update {
            it.copy(
                editorMode = KeyboardEditorMode.PlacingSpacer(span, policy),
                placementCursor = null,
                previewLayout = null,
                previewMovedItemIds = emptySet(),
                previewInsertedItemId = null,
                previewStrategy = null,
                previewStatus = PlacementPreviewStatus.None,
                selectedItemId = null
            )
        }
    }

    fun enterMoveItemMode(
        itemId: String,
        policy: InsertionPolicy = _uiState.value.insertionPolicy
    ) {
        val item = _uiState.value.layout.items.firstOrNull { it.id == itemId } ?: return
        _uiState.update {
            it.copy(
                editorMode = KeyboardEditorMode.MovingExistingItem(itemId, policy),
                placementCursor = PlacementCursor(
                    target = InsertionTarget.EmptyArea(item.placement),
                    span = GridSpan(item.placement.rowSpanUnits, item.placement.columnSpanUnits),
                    policy = policy,
                    source = CursorSource.Tap
                ),
                previewLayout = null,
                previewMovedItemIds = emptySet(),
                previewInsertedItemId = null,
                previewStrategy = null,
                previewStatus = PlacementPreviewStatus.None,
                selectedItemId = null
            )
        }
    }

    fun updatePlacementCursorFromPointer(target: InsertionTarget) {
        updatePlacementCursor(target, CursorSource.PointerMove)
    }

    fun holdPlacementCursorFromTap(target: InsertionTarget) {
        updatePlacementCursor(target, CursorSource.Tap)
    }

    fun holdPlacementCursorFromDrop(target: InsertionTarget) {
        updatePlacementCursor(target, CursorSource.Drop)
    }

    fun nudgePlacementCursor(direction: NudgeDirection) {
        val state = _uiState.value
        val cursor = state.placementCursor ?: return
        val nextTarget = placementNavigator.nudge(state.layout, cursor.target, direction)
        updatePlacementCursor(nextTarget, CursorSource.Nudge)
    }

    fun cyclePlacementCursorTarget() {
        val state = _uiState.value
        val cursor = state.placementCursor ?: return
        val nextTarget = placementNavigator.cycle(state.layout, cursor.target)
        updatePlacementCursor(nextTarget, CursorSource.CycleTarget)
    }

    fun confirmPlacementPreview(): Boolean {
        val state = _uiState.value
        val preview = state.previewLayout?.canonicalizeIfFlexible()?.takeIfValidFlexible() ?: return false
        _uiState.value = state.copy(
            layout = preview,
            editorMode = KeyboardEditorMode.Normal,
            placementCursor = null,
            previewLayout = null,
            previewMovedItemIds = emptySet(),
            previewInsertedItemId = null,
            previewStrategy = null,
            previewStatus = PlacementPreviewStatus.None,
            selectedItemId = null
        )
        return true
    }

    fun cancelPlacementPreview() {
        _uiState.update {
            it.copy(
                editorMode = KeyboardEditorMode.Normal,
                placementCursor = null,
                previewLayout = null,
                previewMovedItemIds = emptySet(),
                previewInsertedItemId = null,
                previewStrategy = null,
                previewStatus = PlacementPreviewStatus.None,
                selectedItemId = null
            )
        }
    }

    fun clearSelectedItemForDeletion() {
        _uiState.update { it.copy(selectedItemId = null) }
    }

    fun selectItem(itemId: String?) {
        _uiState.update { state ->
            val selectedItemId = itemId?.takeIf { selectedId ->
                state.layout.items.any { it.matchesEditorItemId(selectedId) }
            }
            state.copy(selectedItemId = selectedItemId)
        }
    }

    fun onKeyTapped(keyId: String): Boolean {
        return onKeyTappedForSelectionOrEdit(keyId)
    }

    fun onKeyTappedForSelectionOrEdit(keyId: String): Boolean {
        val state = _uiState.value
        if (state.editorMode != KeyboardEditorMode.Normal) return false
        val wasAlreadySelected = state.selectedItemId?.let { selectedId ->
            state.layout.items.any { item ->
                item.matchesEditorItemId(selectedId) && item.matchesEditorItemId(keyId)
            }
        } == true
        if (wasAlreadySelected) {
            selectKeyForEditing(keyId)
        }
        selectItem(keyId)
        return wasAlreadySelected
    }

    fun onSpacerTapped(spacerId: String) {
        if (_uiState.value.editorMode == KeyboardEditorMode.Normal) {
            selectItem(spacerId)
        }
    }

    fun deleteSelectedItem(): Boolean {
        val currentState = _uiState.value
        if (currentState.editorMode != KeyboardEditorMode.Normal) return false
        val selectedId = currentState.selectedItemId ?: return false
        val layout = currentState.layout
        val removedItem = layout.items.firstOrNull { it.matchesEditorItemId(selectedId) } ?: return false
        val updatedItems = layout.items.filterNot { it.matchesEditorItemId(selectedId) }
        if (updatedItems.size == layout.items.size) return false
        val baseAfterDeletion = layout.copyWithItems(updatedItems)
        val layoutAfterDeletion = if (layout.usesFlexiblePlacement()) {
            baseAfterDeletion
                .compactFlexibleGapAfterDeletion(
                    removedPlacement = removedItem.placement,
                    preferredPolicy = currentState.insertionPolicy
                )
                .compactFlexibleOuterBoundsAfterDeletion()
                .takeIfValidFlexible()
                ?: baseAfterDeletion
                    .compactFlexibleOuterBoundsAfterDeletion()
                    .takeIfValidFlexible()
                ?: return false
        } else {
            baseAfterDeletion.canonicalizeIfFlexible()
        }
        val cleanedLayout = layoutAfterDeletion.removeMappingsForDeletedItem(removedItem)
        _uiState.value = currentState.copy(layout = cleanedLayout, selectedItemId = null)
        return true
    }

    private fun KeyboardLayoutItem.matchesEditorItemId(selectedId: String): Boolean =
        id == selectedId || (this is KeyItem && keyData.keyId == selectedId)

    private fun KeyboardLayout.removeMappingsForDeletedItem(item: KeyboardLayoutItem): KeyboardLayout {
        if (item !is KeyItem) return this
        val removedKeyIds = buildSet {
            add(item.id)
            item.keyData.keyId?.takeIf { it.isNotBlank() }?.let(::add)
        }
        if (removedKeyIds.isEmpty()) return this
        return copy(
            flickKeyMaps = flickKeyMaps.filterKeys { it !in removedKeyIds },
            circularFlickKeyMaps = circularFlickKeyMaps.filterKeys { it !in removedKeyIds },
            twoStepFlickKeyMaps = twoStepFlickKeyMaps.filterKeys { it !in removedKeyIds },
            longPressFlickKeyMaps = longPressFlickKeyMaps.filterKeys { it !in removedKeyIds },
            twoStepLongPressKeyMaps = twoStepLongPressKeyMaps.filterKeys { it !in removedKeyIds },
            hierarchicalFlickMaps = hierarchicalFlickMaps.filterKeys { it !in removedKeyIds }
        )
    }

    private fun updatePlacementCursor(target: InsertionTarget, source: CursorSource) {
        val state = _uiState.value
        val mode = state.editorMode
        if (mode == KeyboardEditorMode.Normal) return
        if (!state.layout.usesFlexiblePlacement()) return
        val safeTarget = sanitizeInsertionTarget(state, target)
        val placementTarget = adjustHalfRowHalfCellTarget(state, safeTarget, mode)
        val cursor = PlacementCursor(
            target = placementTarget,
            span = mode.span(),
            policy = mode.policy(),
            source = source
        )
        val operation = operationForMode(mode, cursor.span) ?: return
        val result = placementSolver.solve(
            committedLayout = state.layout,
            operation = operation,
            target = placementTarget,
            policy = cursor.policy
        )
        val preview = result.layout.canonicalizeIfFlexible().takeIfValidFlexible() ?: return
        _uiState.value = state.copy(
            placementCursor = cursor,
            previewLayout = preview,
            previewMovedItemIds = result.movedItemIds,
            previewInsertedItemId = result.insertedItemId,
            previewStrategy = result.strategy,
            previewStatus = PlacementPreviewStatus.Previewing(
                strategy = result.strategy,
                insertedItemId = result.insertedItemId,
                movedItemIds = result.movedItemIds
            )
        )
    }

    private fun adjustHalfRowHalfCellTarget(
        state: EditorUiState,
        target: InsertionTarget,
        mode: KeyboardEditorMode
    ): InsertionTarget {
        if (!state.layout.usesFlexiblePlacement()) return target
        val (span, policy) = when (mode) {
            is KeyboardEditorMode.PlacingNewKey -> mode.span to mode.policy
            is KeyboardEditorMode.PlacingSpacer -> mode.span to mode.policy
            KeyboardEditorMode.Normal,
            is KeyboardEditorMode.MovingExistingItem -> return target
        }
        if (policy != InsertionPolicy.PreferHorizontal) return target
        if (span != GridSpan(rowSpanUnits = 1, columnSpanUnits = 1)) return target

        val rowTop = target.baseRowUnits(state.layout)?.let { (it / 2) * 2 } ?: 0
        val rowUnits = when (state.halfRowPlacement) {
            HalfRowPlacement.Upper -> rowTop
            HalfRowPlacement.Lower -> rowTop + 1
        }
        val columnUnits = target.baseColumnUnitsForRow(state.layout, rowUnits)
        return InsertionTarget.EmptyArea(
            GridPlacement(
                rowUnits = rowUnits,
                columnUnits = columnUnits,
                rowSpanUnits = 1,
                columnSpanUnits = 1
            )
        )
    }

    private fun InsertionTarget.baseRowUnits(layout: KeyboardLayout): Int? {
        return when (this) {
            is InsertionTarget.BeforeItem -> layout.items.firstOrNull { it.id == itemId }?.placement?.rowUnits
            is InsertionTarget.AfterItem -> layout.items.firstOrNull { it.id == itemId }?.placement?.rowUnits
            is InsertionTarget.AboveRowGroup -> topRowUnits
            is InsertionTarget.BelowRowGroup -> topRowUnits
            is InsertionTarget.RowEnd -> topRowUnits
            is InsertionTarget.NewBottomRow -> layout.items.maxOfOrNull {
                it.placement.rowUnits + it.placement.rowSpanUnits
            } ?: layout.rowUnitCount
            is InsertionTarget.EmptyArea -> placement.rowUnits
        }
    }

    private fun InsertionTarget.baseColumnUnitsForRow(layout: KeyboardLayout, rowUnits: Int): Int {
        return when (this) {
            is InsertionTarget.BeforeItem -> layout.items.firstOrNull { it.id == itemId }?.placement?.columnUnits ?: 0
            is InsertionTarget.AfterItem -> layout.items.firstOrNull { it.id == itemId }?.placement?.let {
                it.columnUnits + it.columnSpanUnits
            } ?: 0
            is InsertionTarget.RowEnd -> layout.items
                .filter { item ->
                    val p = item.placement
                    p.rowUnits <= rowUnits && rowUnits < p.rowUnits + p.rowSpanUnits
                }
                .maxOfOrNull { it.placement.columnUnits + it.placement.columnSpanUnits }
                ?: 0
            is InsertionTarget.NewBottomRow -> columnUnits.coerceAtLeast(0)
            is InsertionTarget.EmptyArea -> placement.columnUnits
            is InsertionTarget.AboveRowGroup,
            is InsertionTarget.BelowRowGroup -> 0
        }
    }

    private fun operationForMode(mode: KeyboardEditorMode, span: GridSpan): PlacementOperation? {
        return when (mode) {
            KeyboardEditorMode.Normal -> null
            is KeyboardEditorMode.PlacingNewKey -> PlacementOperation.Insert(createPlacementKey(span))
            is KeyboardEditorMode.PlacingSpacer -> PlacementOperation.Insert(createPlacementSpacer(span))
            is KeyboardEditorMode.MovingExistingItem -> PlacementOperation.MoveExisting(mode.itemId)
        }
    }

    private fun sanitizeInsertionTarget(
        state: EditorUiState,
        target: InsertionTarget
    ): InsertionTarget {
        val committedItemIds = state.layout.items.map { it.id }.toSet()
        return when (target) {
            is InsertionTarget.BeforeItem -> {
                if (target.itemId in committedItemIds) {
                    target
                } else {
                    state.previewLayout
                        ?.items
                        ?.firstOrNull { it.id == target.itemId }
                        ?.let { InsertionTarget.EmptyArea(it.placement) }
                        ?: fallbackInsertionTarget(state.layout)
                }
            }

            is InsertionTarget.AfterItem -> {
                if (target.itemId in committedItemIds) {
                    target
                } else {
                    state.previewLayout
                        ?.items
                        ?.firstOrNull { it.id == target.itemId }
                        ?.let { InsertionTarget.EmptyArea(it.placement) }
                        ?: fallbackInsertionTarget(state.layout)
                }
            }

            else -> target
        }
    }

    private fun fallbackInsertionTarget(layout: KeyboardLayout): InsertionTarget {
        val lastRow = layout.items.maxOfOrNull { it.placement.rowUnits } ?: return InsertionTarget.EmptyArea(
            GridPlacement(0, 0, 1, 1)
        )
        return InsertionTarget.RowEnd(lastRow)
    }

    private fun createPlacementKey(span: GridSpan): KeyItem {
        val id = "key_${UUID.randomUUID()}"
        val keyData = KeyData(
            label = "",
            row = 0,
            column = 0,
            isFlickable = false,
            action = null,
            rowSpan = (span.rowSpanUnits + 1) / 2,
            colSpan = (span.columnSpanUnits + 1) / 2,
            keyType = KeyType.NORMAL,
            keyId = id
        )
        return KeyItem(id, keyData, GridPlacement(0, 0, span.rowSpanUnits, span.columnSpanUnits))
    }

    private fun createPlacementSpacer(span: GridSpan): SpacerItem =
        SpacerItem(
            id = "spacer_${UUID.randomUUID()}",
            placement = GridPlacement(0, 0, span.rowSpanUnits, span.columnSpanUnits)
        )

    private fun KeyboardEditorMode.span(): GridSpan = when (this) {
        KeyboardEditorMode.Normal -> GridSpan(1, 1)
        is KeyboardEditorMode.PlacingNewKey -> span
        is KeyboardEditorMode.PlacingSpacer -> span
        is KeyboardEditorMode.MovingExistingItem -> {
            val item = _uiState.value.layout.items.first { it.id == itemId }
            GridSpan(item.placement.rowSpanUnits, item.placement.columnSpanUnits)
        }
    }

    private fun KeyboardEditorMode.policy(): InsertionPolicy = when (this) {
        KeyboardEditorMode.Normal -> InsertionPolicy.Auto2D
        is KeyboardEditorMode.PlacingNewKey -> policy
        is KeyboardEditorMode.PlacingSpacer -> policy
        is KeyboardEditorMode.MovingExistingItem -> policy
    }

    private fun KeyboardEditorMode.withPolicy(policy: InsertionPolicy): KeyboardEditorMode = when (this) {
        KeyboardEditorMode.Normal -> this
        is KeyboardEditorMode.PlacingNewKey -> copy(policy = policy)
        is KeyboardEditorMode.PlacingSpacer -> copy(policy = policy)
        is KeyboardEditorMode.MovingExistingItem -> copy(policy = policy)
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

                val canonicalLayout = layout.copyWithItems(updatedItems).withCanonicalFlexibleBounds()
                if (hasPlacementIssues(updatedItems, canonicalLayout.rowUnitCount, canonicalLayout.columnUnitCount)) {
                    return@update currentState
                }

                val newLayout = canonicalLayout.copy(
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
        val updatedLayout = layout.layoutWithValidFlexibleItems(updatedItems) ?: return false
        _uiState.value = currentState.copy(layout = updatedLayout)
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
        val updatedLayout = if (found) layout.layoutWithValidFlexibleItems(updatedItems) else null
        if (updatedLayout == null) return false
        _uiState.value = currentState.copy(layout = updatedLayout)
        return true
    }

    fun deleteSpacer(spacerId: String): Boolean {
        val currentState = _uiState.value
        val layout = currentState.layout
        val removedSpacer = layout.items.filterIsInstance<SpacerItem>()
            .firstOrNull { it.id == spacerId } ?: return false
        val updatedItems = layout.items.filterNot { it is SpacerItem && it.id == spacerId }
        if (updatedItems.size == layout.items.size) return false
        val baseAfterDeletion = layout.copyWithItems(updatedItems)
        val updatedLayout = if (layout.usesFlexiblePlacement()) {
            baseAfterDeletion
                .compactFlexibleGapAfterDeletion(
                    removedPlacement = removedSpacer.placement,
                    preferredPolicy = currentState.insertionPolicy
                )
                .compactFlexibleOuterBoundsAfterDeletion()
                .takeIfValidFlexible()
                ?: baseAfterDeletion
                    .compactFlexibleOuterBoundsAfterDeletion()
                    .takeIfValidFlexible()
                ?: return false
        } else {
            baseAfterDeletion.canonicalizeIfFlexible()
        }
        _uiState.value = currentState.copy(
            layout = updatedLayout,
            selectedItemId = currentState.selectedItemId.takeUnless { it == spacerId }
        )
        return true
    }

    private fun KeyboardLayout.layoutWithValidFlexibleItems(
        items: List<KeyboardLayoutItem>
    ): KeyboardLayout? {
        val updatedLayout = copyWithItems(items).withCanonicalFlexibleBounds()
        return if (updatedLayout.isValidPlacementUpdate(items)) updatedLayout else null
    }

    private fun KeyboardLayout.isValidPlacementUpdate(
        items: List<KeyboardLayoutItem>
    ): Boolean {
        return !hasPlacementIssues(
            items = items,
            rowUnitCount = rowUnitCount,
            columnUnitCount = columnUnitCount
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

    private fun KeyboardLayout.canonicalizeIfFlexible(): KeyboardLayout =
        if (usesFlexiblePlacement()) withCanonicalFlexibleBounds() else this

    private fun KeyboardLayout.compactFlexibleGapAfterDeletion(
        removedPlacement: GridPlacement,
        preferredPolicy: InsertionPolicy
    ): KeyboardLayout {
        val horizontalCandidate = compactFlexibleGapHorizontally(removedPlacement)
        val verticalCandidate = compactFlexibleGapVertically(removedPlacement)
        val canCompactHorizontally = horizontalCandidate !== this && horizontalCandidate.takeIfValidFlexible() != null
        val canCompactVertically = verticalCandidate !== this && verticalCandidate.takeIfValidFlexible() != null

        return when {
            canCompactHorizontally && canCompactVertically && preferredPolicy == InsertionPolicy.PreferVertical -> {
                verticalCandidate
            }
            canCompactHorizontally && canCompactVertically -> {
                horizontalCandidate
            }
            canCompactHorizontally -> {
                horizontalCandidate
            }
            canCompactVertically -> {
                verticalCandidate
            }
            else -> {
                this
            }
        }
    }

    private fun KeyboardLayout.compactFlexibleGapHorizontally(
        removedPlacement: GridPlacement
    ): KeyboardLayout {
        val removedRight = removedPlacement.columnUnits + removedPlacement.columnSpanUnits
        var moved = false
        val compactedItems = items.map { item ->
            val p = item.placement
            if (
                rangesOverlap(
                    p.rowUnits,
                    p.rowSpanUnits,
                    removedPlacement.rowUnits,
                    removedPlacement.rowSpanUnits
                ) &&
                p.columnUnits >= removedRight
            ) {
                moved = true
                item.withPlacementAndApproximateKeyData(
                    p.copy(columnUnits = (p.columnUnits - removedPlacement.columnSpanUnits).coerceAtLeast(0))
                )
            } else {
                item
            }
        }
        return if (moved) copyWithItems(compactedItems) else this
    }

    private fun KeyboardLayout.compactFlexibleGapVertically(
        removedPlacement: GridPlacement
    ): KeyboardLayout {
        val removedBottom = removedPlacement.rowUnits + removedPlacement.rowSpanUnits
        var moved = false
        val compactedItems = items.map { item ->
            val p = item.placement
            if (
                rangesOverlap(
                    p.columnUnits,
                    p.columnSpanUnits,
                    removedPlacement.columnUnits,
                    removedPlacement.columnSpanUnits
                ) &&
                p.rowUnits >= removedBottom
            ) {
                moved = true
                item.withPlacementAndApproximateKeyData(
                    p.copy(rowUnits = (p.rowUnits - removedPlacement.rowSpanUnits).coerceAtLeast(0))
                )
            } else {
                item
            }
        }
        return if (moved) copyWithItems(compactedItems) else this
    }

    private fun rangesOverlap(startA: Int, spanA: Int, startB: Int, spanB: Int): Boolean {
        val endA = startA + spanA
        val endB = startB + spanB
        return startA < endB && startB < endA
    }

    private fun KeyboardLayout.compactFlexibleOuterBoundsAfterDeletion(): KeyboardLayout {
        val maxRight = items.maxOfOrNull { item ->
            item.placement.columnUnits + item.placement.columnSpanUnits
        } ?: 1
        val maxBottom = items.maxOfOrNull { item ->
            item.placement.rowUnits + item.placement.rowSpanUnits
        } ?: 1
        val qwertyTemplate = KeyboardDefaultLayouts.createQwertyTemplateLayout()
        val isQwertyFamilyLayout = items.any { item ->
            item.id.startsWith("qwerty_") ||
                    (item is KeyItem && item.keyData.keyId?.startsWith("qwerty_") == true)
        }
        val minimumColumnUnits = when {
            isQwertyFamilyLayout -> qwertyTemplate.columnUnitCount
            isFlexiblePlacementLayout -> columnUnitCount
            else -> 1
        }
        val minimumRowUnits = when {
            isQwertyFamilyLayout -> qwertyTemplate.rowUnitCount
            isFlexiblePlacementLayout -> rowUnitCount
            else -> 1
        }
        val newColumnUnitCount = maxOf(maxRight, minimumColumnUnits, 1)
        val newRowUnitCount = maxOf(maxBottom, minimumRowUnits, 1)
        return copy(
            columnUnitCount = newColumnUnitCount,
            rowUnitCount = newRowUnitCount,
            columnCount = (newColumnUnitCount + 1) / 2,
            rowCount = (newRowUnitCount + 1) / 2
        )
    }

    private fun KeyboardLayout.takeIfValidFlexible(): KeyboardLayout? {
        return takeUnless { hasPlacementIssues(items, rowUnitCount, columnUnitCount) }
    }

    fun updateIsRomaji(isRomaji: Boolean) {
        _uiState.update { it.copy(isRomaji = isRomaji) }
    }

    fun updateIsDirectMode(isDirectMode: Boolean) {
        _uiState.update { it.copy(isDirectMode = isDirectMode) }
    }

    fun setCurrentLayoutUsageMode(layoutId: Long?, usageMode: KeyboardLayoutUsageMode) {
        _uiState.update { state ->
            state.copy(layout = state.layout.copy(usageMode = usageMode))
        }
        val existingLayoutId = layoutId ?: return
        viewModelScope.launch {
            runCatching {
                repository.setCurrentLayoutUsageMode(existingLayoutId, usageMode)
            }.onFailure { e ->
                Timber.e(e, "setCurrentLayoutUsageMode failed layoutId=%s usageMode=%s", existingLayoutId, usageMode)
            }
        }
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
            templateLayout.copyWithItems(updatedItems).withCanonicalFlexibleBounds()
        } else {
            templateLayout.copyWithKeys(keysWithEnsuredIds)
        }

        val finalLayout = finalLayoutBase.copy(
            flickKeyMaps = reKeyedFlickMaps,
            circularFlickKeyMaps = reKeyedCircularFlickMaps,
            twoStepFlickKeyMaps = reKeyedTwoStepMaps,
            longPressFlickKeyMaps = reKeyedLongPressFlickMaps,
            twoStepLongPressKeyMaps = reKeyedTwoStepLongPressMaps,
            usageMode = _uiState.value.layout.usageMode
        )

        _uiState.update { currentState ->
            currentState.copy(
                layout = finalLayout,
                isDirectMode = finalLayout.isDirectMode,
                editorMode = KeyboardEditorMode.Normal,
                placementCursor = null,
                previewLayout = null,
                previewMovedItemIds = emptySet(),
                previewInsertedItemId = null,
                previewStrategy = null,
                previewStatus = PlacementPreviewStatus.None,
                selectedItemId = null
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
    suspend fun importLayouts(layouts: List<ImportableKeyboardLayout>): KeyboardLayoutImportResult {
        return repository.importLayouts(layouts)
    }
}
