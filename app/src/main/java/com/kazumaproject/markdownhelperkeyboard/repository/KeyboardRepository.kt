package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyMargin
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyVisualStyle
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.toCircularFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CircularFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.LongPressFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepLongPressMappingEntity
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toDbStrings
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toFlickAction
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private data class DbKeyboardLayoutParts(
    val keys: List<KeyDefinition>,
    val flicksMap: Map<String, List<FlickMapping>>,
    val circularFlicksMap: Map<String, List<CircularFlickMapping>>,
    val twoStepMap: Map<String, List<TwoStepFlickMapping>>,
    val longPressFlicksMap: Map<String, List<LongPressFlickMapping>>,
    val twoStepLongPressMap: Map<String, List<TwoStepLongPressMappingEntity>>
)

fun ensureStableIdsForLayouts(
    layouts: List<CustomKeyboardLayout>,
    generateStableId: () -> String = { UUID.randomUUID().toString() }
): List<CustomKeyboardLayout> {
    return layouts.map { layout ->
        if (layout.stableId.isBlank()) {
            layout.copy(stableId = generateStableId())
        } else {
            layout
        }
    }
}

@Singleton
class KeyboardRepository @Inject constructor(
    private val dao: KeyboardLayoutDao
) {

    // -----------------------------
    // Export / Import
    // -----------------------------

    suspend fun getAllFullLayoutsForExport(): List<FullKeyboardLayout> {
        return dao.getAllFullLayoutsOneShot()
    }

    /**
     * インポート処理（TWO_STEP_FLICK 含む）
     * - 名前衝突回避
     * - createdAt は import 時刻
     * - sortOrder は「最上位に積む」(max+1) を順に付与
     */
    suspend fun importLayouts(layouts: List<FullKeyboardLayout>) {
        // まとめて import するときに max を毎回 DB に聞かない
        var currentMaxOrder = dao.getMaxSortOrder()

        for (fullLayout in layouts) {
            var newName = fullLayout.layout.name
            var nameExists = dao.findLayoutByName(newName) != null
            var counter = 1
            while (nameExists) {
                newName = "${fullLayout.layout.name} (${counter})"
                nameExists = dao.findLayoutByName(newName) != null
                counter++
            }

            currentMaxOrder += 1
            val importedStableId = fullLayout.layout.stableId
            val stableIdToInsert = if (importedStableId.isNullOrBlank() ||
                dao.findLayoutByStableId(importedStableId) != null
            ) {
                UUID.randomUUID().toString()
            } else {
                importedStableId
            }

            val layoutToInsert = fullLayout.layout.copy(
                layoutId = 0,
                name = newName,
                createdAt = System.currentTimeMillis(),
                sortOrder = currentMaxOrder,
                stableId = stableIdToInsert
            )

            val keysToInsert = fullLayout.keysWithFlicks.map { it.key }

            val flicksMap = fullLayout.keysWithFlicks.associate { keyWithFlicks ->
                keyWithFlicks.key.keyIdentifier to keyWithFlicks.flicks
            }

            val circularFlicksMap = fullLayout.keysWithFlicks.associate { keyWithFlicks ->
                keyWithFlicks.key.keyIdentifier to keyWithFlicks.circularFlicks
            }

            val twoStepMap = fullLayout.keysWithFlicks.associate { keyWithFlicks ->
                keyWithFlicks.key.keyIdentifier to keyWithFlicks.twoStepFlicks
            }

            val longPressFlicksMap = fullLayout.keysWithFlicks.associate { keyWithFlicks ->
                keyWithFlicks.key.keyIdentifier to keyWithFlicks.longPressFlicks
            }

            val twoStepLongPressMap = fullLayout.keysWithFlicks.associate { keyWithFlicks ->
                keyWithFlicks.key.keyIdentifier to keyWithFlicks.twoStepLongPressFlicks
            }

            dao.insertFullKeyboardLayout(
                layoutToInsert,
                keysToInsert,
                flicksMap,
                circularFlicksMap,
                twoStepMap,
                longPressFlicksMap,
                twoStepLongPressMap
            )
        }
    }

    // -----------------------------
    // Sorting (Drag & Drop persistence)
    // -----------------------------

    /**
     * RecyclerView で表示されている順（上→下）の layoutId リストを受け取り、
     * DB の sortOrder を振り直して永続化する。
     *
     * 例: adapter.currentList.map { it.layoutId } を渡す。
     */
    suspend fun updateLayoutOrder(layoutIdsInDisplayOrder: List<Long>) {
        dao.updateLayoutOrdersInDisplayOrder(layoutIdsInDisplayOrder)
    }

    private suspend fun nextTopSortOrder(): Int {
        return dao.getMaxSortOrder() + 1
    }

    // -----------------------------
    // Queries
    // -----------------------------

    fun getLayouts(): Flow<List<CustomKeyboardLayout>> = dao.getLayoutsList()

    suspend fun getLayoutsNotFlow(): List<CustomKeyboardLayout> =
        dao.getLayoutsListNotFlow()

    suspend fun ensureStableIds() {
        val currentLayouts = dao.getLayoutsListNotFlow()
        val ensuredLayouts = ensureStableIdsForLayouts(currentLayouts)
        currentLayouts.zip(ensuredLayouts)
            .filter { (current, ensured) -> current.stableId != ensured.stableId }
            .forEach { (_, ensured) -> dao.updateLayout(ensured) }
    }

    suspend fun getLayoutsNotFlowEnsuringStableIds(): List<CustomKeyboardLayout> {
        ensureStableIds()
        return dao.getLayoutsListNotFlow()
    }

    suspend fun getLayoutName(id: Long): String? = dao.getLayoutName(id)

    fun getFullLayout(id: Long): Flow<KeyboardLayout> {
        return dao.getFullLayoutById(id).map { dbLayout ->
            convertToUiModel(dbLayout)
        }
    }

    fun getAllCustomKeyboardLayouts(): Flow<List<KeyboardLayout>> {
        return dao.getAllFullLayouts().map { dbLayouts ->
            dbLayouts.map { dbLayout ->
                convertToUiModel(dbLayout)
            }
        }
    }

    suspend fun doesNameExist(name: String, currentId: Long?): Boolean {
        val foundLayout = dao.findLayoutByName(name)
        return when {
            foundLayout == null -> false
            foundLayout.layoutId == currentId -> false
            else -> true
        }
    }

    // -----------------------------
    // Save / Delete / Duplicate
    // -----------------------------

    /**
     * レイアウトの保存処理（TWO_STEP_FLICK 含む）
     *
     * 重要:
     * - 既存レイアウトの createdAt / sortOrder を維持する（編集で順序が壊れないように）
     * - 新規作成は sortOrder = max+1 として最上位に追加
     *
     * 注意:
     * - dao.insertLayout() が REPLACE の場合、既存保存時は親(row)が置換されます。
     *   ただし layoutId を明示しているので ID は維持されます。
     *   また FK の ON DELETE CASCADE がある場合、子テーブルは置換に伴い一度削除され、
     *   直後に insertFullKeyboardLayout 内で再挿入される想定です。
     */
    suspend fun saveLayout(layout: KeyboardLayout, name: String, id: Long?) {
        Timber.d("saveLayout: $layout")

        val existing = if (id != null && id > 0) {
            dao.getFullLayoutOneShot(id)?.layout
        } else {
            null
        }

        val createdAtToKeep = existing?.createdAt ?: System.currentTimeMillis()
        val sortOrderToKeep = existing?.sortOrder ?: nextTopSortOrder()
        val stableIdToKeep = existing?.stableId
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        val dbLayout = CustomKeyboardLayout(
            layoutId = id ?: 0,
            name = name,
            columnCount = layout.columnCount,
            rowCount = layout.rowCount,
            isRomaji = layout.isRomaji,
            createdAt = createdAtToKeep,
            sortOrder = sortOrderToKeep,
            stableId = stableIdToKeep
        )

        val parts = convertToDbModel(layout)

        dao.insertFullKeyboardLayout(
            dbLayout,
            parts.keys,
            parts.flicksMap,
            parts.circularFlicksMap,
            parts.twoStepMap,
            parts.longPressFlicksMap,
            parts.twoStepLongPressMap
        )
    }

    suspend fun deleteLayout(id: Long) {
        dao.deleteLayout(id)
    }

    /**
     * レイアウト複製:
     * - 名前衝突回避
     * - createdAt は複製時刻
     * - sortOrder は最上位へ (max+1)
     */
    suspend fun duplicateLayout(id: Long) {
        val originalLayout = dao.getFullLayoutOneShot(id) ?: return

        val baseName = originalLayout.layout.name + " (コピー)"
        var finalName = baseName
        var counter = 2
        while (dao.findLayoutByName(finalName) != null) {
            finalName = "$baseName ($counter)"
            counter++
        }

        val newLayoutInfo = originalLayout.layout.copy(
            layoutId = 0,
            name = finalName,
            createdAt = System.currentTimeMillis(),
            sortOrder = nextTopSortOrder(),
            stableId = UUID.randomUUID().toString()
        )

        val newKeys = originalLayout.keysWithFlicks.map { keyWithFlicks ->
            keyWithFlicks.key.copy(keyId = 0, ownerLayoutId = 0)
        }

        val newFlicksMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newFlicks = keyWithFlicks.flicks.map { flick ->
                flick.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newFlicks
        }

        val newCircularFlicksMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newFlicks = keyWithFlicks.circularFlicks.map { flick ->
                flick.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newFlicks
        }

        val newTwoStepMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newTwoStep = keyWithFlicks.twoStepFlicks.map { m ->
                m.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newTwoStep
        }

        val newLongPressFlicksMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newLongPressFlicks = keyWithFlicks.longPressFlicks.map { m ->
                m.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newLongPressFlicks
        }

        val newTwoStepLongPressMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newTwoStepLongPress = keyWithFlicks.twoStepLongPressFlicks.map { mapping ->
                mapping.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newTwoStepLongPress
        }

        dao.insertFullKeyboardLayout(
            newLayoutInfo,
            newKeys,
            newFlicksMap,
            newCircularFlicksMap,
            newTwoStepMap,
            newLongPressFlicksMap,
            newTwoStepLongPressMap
        )
    }

    // -----------------------------
    // Convert models
    // -----------------------------

    fun convertLayout(dbLayout: KeyboardLayout): KeyboardLayout {
        val uuidToTapCharMap = dbLayout.flickKeyMaps.mapNotNull { (uuid, flickActionStates) ->
            val tapAction = flickActionStates.firstOrNull()?.get(FlickDirection.TAP)
            if (tapAction is FlickAction.Input) uuid to tapAction.char else null
        }.toMap()

        val uuidToFinalLabelMap = dbLayout.keys.mapNotNull { keyData ->
            val uuid = keyData.keyId ?: return@mapNotNull null
            val finalLabel =
                if (keyData.label.isNotEmpty()) keyData.label else uuidToTapCharMap[uuid]
            if (finalLabel != null) uuid to finalLabel else null
        }.toMap()

        val newFlickKeyMaps = dbLayout.flickKeyMaps
            .mapNotNull { (uuid, flickActions) ->
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) finalLabel to flickActions else null
            }
            .toMap()

        val newLongPressFlickKeyMaps = dbLayout.longPressFlickKeyMaps
            .mapNotNull { (uuid, longPressMap) ->
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) finalLabel to longPressMap else null
            }
            .toMap()

        val newTwoStepFlickKeyMaps = dbLayout.twoStepFlickKeyMaps
            .mapNotNull { (uuid, twoStepMap) ->
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) finalLabel to twoStepMap else null
            }
            .toMap()

        val newTwoStepLongPressKeyMaps = dbLayout.twoStepLongPressKeyMaps
            .mapNotNull { (uuid, twoStepLongPressMap) ->
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) finalLabel to twoStepLongPressMap else null
            }
            .toMap()

        val newKeys = dbLayout.keys.map { keyData ->
            if (keyData.isSpecialKey) {
                keyData
            } else {
                val finalLabel = uuidToFinalLabelMap[keyData.keyId]
                if (finalLabel != null) keyData.copy(label = finalLabel) else keyData
            }
        }

        return dbLayout.copy(
            keys = newKeys,
            flickKeyMaps = newFlickKeyMaps,
            twoStepFlickKeyMaps = newTwoStepFlickKeyMaps,
            longPressFlickKeyMaps = newLongPressFlickKeyMaps,
            twoStepLongPressKeyMaps = newTwoStepLongPressKeyMaps
        )
    }

    private fun convertToUiModel(dbLayout: FullKeyboardLayout): KeyboardLayout {
        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> =
            dbLayout.keysWithFlicks.associate { keyWithFlicks ->
                val identifier = keyWithFlicks.key.keyIdentifier

                val flicksByState = keyWithFlicks.flicks
                    .groupBy { it.stateIndex }
                    .mapValues { (_, stateFlicks) ->
                        stateFlicks.associate { flick ->
                            flick.flickDirection to flick.toFlickAction()
                        }
                    }
                    .toSortedMap()
                    .values
                    .toList()

                identifier to flicksByState
            }

        val circularFlickMaps: Map<String, List<Map<CircularFlickDirection, FlickAction>>> =
            dbLayout.keysWithFlicks.associate { keyWithFlicks ->
                val identifier = keyWithFlicks.key.keyIdentifier

                val flicksByState = keyWithFlicks.circularFlicks
                    .groupBy { it.stateIndex }
                    .mapValues { (_, stateFlicks) ->
                        stateFlicks.associate { flick ->
                            flick.circularDirection to flick.toFlickAction()
                        }
                    }
                    .toSortedMap()
                    .values
                    .toList()

                identifier to flicksByState
            }.filterValues { it.isNotEmpty() }

        val twoStepMaps: Map<String, Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>> =
            dbLayout.keysWithFlicks.mapNotNull { keyWithFlicks ->
                val identifier = keyWithFlicks.key.keyIdentifier
                if (keyWithFlicks.twoStepFlicks.isEmpty()) return@mapNotNull null

                val firstMap = keyWithFlicks.twoStepFlicks
                    .groupBy { it.firstDirection }
                    .mapValues { (_, list) ->
                        list.associate { it.secondDirection to it.output }
                    }

                identifier to firstMap
            }.toMap()

        val longPressFlickMaps: Map<String, Map<FlickDirection, String>> =
            dbLayout.keysWithFlicks.mapNotNull { keyWithFlicks ->
                val identifier = keyWithFlicks.key.keyIdentifier
                if (keyWithFlicks.longPressFlicks.isEmpty()) return@mapNotNull null

                identifier to keyWithFlicks.longPressFlicks.associate { mapping ->
                    mapping.flickDirection to mapping.output
                }
            }.toMap()

        val twoStepLongPressMaps: Map<String, Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>> =
            dbLayout.keysWithFlicks.mapNotNull { keyWithFlicks ->
                val identifier = keyWithFlicks.key.keyIdentifier
                if (keyWithFlicks.twoStepLongPressFlicks.isEmpty()) return@mapNotNull null

                val firstMap = keyWithFlicks.twoStepLongPressFlicks
                    .groupBy { it.firstDirection }
                    .mapValues { (_, list) ->
                        list.associate { it.secondDirection to it.output }
                    }

                identifier to firstMap
            }.toMap()

        val keys: List<KeyData> = dbLayout.keysWithFlicks.map { keyWithFlicks ->
            val dbKey = keyWithFlicks.key

            val actionObject: KeyAction? = if (dbKey.isSpecialKey) {
                KeyActionMapper.toKeyAction(dbKey.action)
            } else {
                null
            }

            // DB に保存されているキー単位のマージンがあれば、それを KeyVisualStyle として復元する。
            // 各方向は nullable なので、軸ごとに「指定されていない」状態を保持できる。
            val visualStyle = KeyVisualStyle(
                margin = KeyMargin(
                    leftDp = dbKey.marginLeftDp,
                    topDp = dbKey.marginTopDp,
                    rightDp = dbKey.marginRightDp,
                    bottomDp = dbKey.marginBottomDp
                )
            )

            if (actionObject == null) {
                KeyData(
                    label = dbKey.label,
                    row = dbKey.row,
                    column = dbKey.column,
                    isFlickable = dbKey.keyType != KeyType.NORMAL,
                    keyType = dbKey.keyType,
                    rowSpan = dbKey.rowSpan,
                    colSpan = dbKey.colSpan,
                    isSpecialKey = dbKey.isSpecialKey,
                    drawableResId = null,
                    keyId = dbKey.keyIdentifier,
                    action = null,
                    visualStyle = visualStyle
                )
            } else {
                KeyData(
                    label = dbKey.label,
                    row = dbKey.row,
                    column = dbKey.column,
                    isFlickable = dbKey.keyType != KeyType.NORMAL,
                    keyType = dbKey.keyType,
                    rowSpan = dbKey.rowSpan,
                    colSpan = dbKey.colSpan,
                    isSpecialKey = true,
                    drawableResId = when (actionObject) {
                        KeyAction.Backspace -> com.kazumaproject.core.R.drawable.backspace_24px
                        KeyAction.ChangeInputMode -> com.kazumaproject.core.R.drawable.backspace_24px
                        KeyAction.Convert -> com.kazumaproject.core.R.drawable.henkan
                        KeyAction.Copy -> com.kazumaproject.core.R.drawable.content_copy_24dp
                        KeyAction.Delete -> com.kazumaproject.core.R.drawable.backspace_24px
                        KeyAction.Enter -> com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
                        KeyAction.MoveCursorLeft -> com.kazumaproject.core.R.drawable.baseline_arrow_left_24
                        KeyAction.MoveCursorRight -> com.kazumaproject.core.R.drawable.baseline_arrow_right_24
                        KeyAction.MoveCustomKeyboardTab -> com.kazumaproject.core.R.drawable.keyboard_command_key_24px
                        is KeyAction.MoveToCustomKeyboard -> com.kazumaproject.core.R.drawable.keyboard_24px
                        KeyAction.Paste -> com.kazumaproject.core.R.drawable.content_paste_24px
                        KeyAction.SelectAll -> com.kazumaproject.core.R.drawable.text_select_start_24dp
                        KeyAction.SelectLeft -> com.kazumaproject.core.R.drawable.baseline_arrow_left_24
                        KeyAction.SelectRight -> com.kazumaproject.core.R.drawable.baseline_arrow_right_24
                        KeyAction.ShiftKey -> com.kazumaproject.core.R.drawable.shift_24px
                        KeyAction.ShowEmojiKeyboard -> com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24
                        KeyAction.Space -> com.kazumaproject.core.R.drawable.baseline_space_bar_24
                        KeyAction.SwitchToEnglishLayout -> com.kazumaproject.core.R.drawable.input_mode_english_custom
                        KeyAction.SwitchToKanaLayout -> com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom
                        KeyAction.SwitchToNextIme -> com.kazumaproject.core.R.drawable.language_24dp
                        KeyAction.SwitchToNumberLayout -> com.kazumaproject.core.R.drawable.input_mode_number_select_custom
                        KeyAction.ToggleCase -> com.kazumaproject.core.R.drawable.english_small
                        KeyAction.ToggleDakuten -> com.kazumaproject.core.R.drawable.kana_small_custom
                        KeyAction.ToggleKatakana -> com.kazumaproject.core.R.drawable.katakana
                        KeyAction.VoiceInput -> com.kazumaproject.core.R.drawable.settings_voice_24px
                        else -> null
                    },
                    keyId = dbKey.keyIdentifier,
                    action = actionObject,
                    visualStyle = visualStyle
                )
            }
        }

        return KeyboardLayout(
            keys = keys,
            flickKeyMaps = flickMaps,
            columnCount = dbLayout.layout.columnCount,
            rowCount = dbLayout.layout.rowCount,
            isRomaji = dbLayout.layout.isRomaji,
            circularFlickKeyMaps = circularFlickMaps.ifEmpty {
                flickMaps.mapValues { (_, states) ->
                    states.map { stateMap ->
                        stateMap.mapKeys { (direction, _) ->
                            direction.toCircularFlickDirection()
                        }
                    }
                }
            },
            twoStepFlickKeyMaps = twoStepMaps,
            longPressFlickKeyMaps = longPressFlickMaps,
            twoStepLongPressKeyMaps = twoStepLongPressMaps
        )
    }

    private fun convertToDbModel(
        uiLayout: KeyboardLayout
    ): DbKeyboardLayoutParts {

        val keys = mutableListOf<KeyDefinition>()
        val flicksMap = mutableMapOf<String, MutableList<FlickMapping>>()
        val circularFlicksMap = mutableMapOf<String, MutableList<CircularFlickMapping>>()
        val twoStepMap = mutableMapOf<String, MutableList<TwoStepFlickMapping>>()
        val longPressFlicksMap = mutableMapOf<String, MutableList<LongPressFlickMapping>>()
        val twoStepLongPressMap = mutableMapOf<String, MutableList<TwoStepLongPressMappingEntity>>()

        uiLayout.keys.forEach { keyData ->
            val keyIdentifier = keyData.keyId ?: UUID.randomUUID().toString()

            val actionString: String? = if (keyData.isSpecialKey) {
                KeyActionMapper.fromKeyAction(keyData.action)
            } else {
                null
            }

            // KeyVisualStyle.margin を nullable column 4 つに分解して保存する。
            // 軸ごとに「指定なし(null)」を保てるので、再読み込み時にデフォルト余白へフォールバックできる。
            val margin = keyData.visualStyle.margin

            keys.add(
                KeyDefinition(
                    keyId = 0,
                    ownerLayoutId = 0,
                    label = keyData.label,
                    row = keyData.row,
                    column = keyData.column,
                    rowSpan = keyData.rowSpan,
                    colSpan = keyData.colSpan,
                    keyType = keyData.keyType,
                    isSpecialKey = keyData.isSpecialKey,
                    drawableResId = null,
                    keyIdentifier = keyIdentifier,
                    action = actionString,
                    marginLeftDp = margin.leftDp,
                    marginTopDp = margin.topDp,
                    marginRightDp = margin.rightDp,
                    marginBottomDp = margin.bottomDp
                )
            )

            uiLayout.flickKeyMaps[keyIdentifier]?.forEachIndexed { stateIndex, stateMap ->
                stateMap.forEach { (direction, flickAction) ->
                    val (actionType, actionValue) = flickAction.toDbStrings()
                    val flick = FlickMapping(
                        ownerKeyId = 0,
                        stateIndex = stateIndex,
                        flickDirection = direction,
                        actionType = actionType,
                        actionValue = actionValue
                    )
                    flicksMap.getOrPut(keyIdentifier) { mutableListOf() }.add(flick)
                }
            }

            uiLayout.circularFlickKeyMaps[keyIdentifier]?.forEachIndexed { stateIndex, stateMap ->
                stateMap.forEach { (direction, flickAction) ->
                    val (actionType, actionValue) = flickAction.toDbStrings()
                    val flick = CircularFlickMapping(
                        ownerKeyId = 0,
                        stateIndex = stateIndex,
                        circularDirection = direction,
                        actionType = actionType,
                        actionValue = actionValue
                    )
                    circularFlicksMap.getOrPut(keyIdentifier) { mutableListOf() }.add(flick)
                }
            }

            val twoStep = uiLayout.twoStepFlickKeyMaps[keyIdentifier]
            twoStep?.forEach { (first, secondMap) ->
                secondMap.forEach { (second, output) ->
                    val mapping = TwoStepFlickMapping(
                        ownerKeyId = 0,
                        firstDirection = first,
                        secondDirection = second,
                        output = output
                    )
                    twoStepMap.getOrPut(keyIdentifier) { mutableListOf() }.add(mapping)
                }
            }

            val longPressFlicks = uiLayout.longPressFlickKeyMaps[keyIdentifier]
            longPressFlicks?.forEach { (direction, output) ->
                if (output.isNotEmpty()) {
                    val mapping = LongPressFlickMapping(
                        ownerKeyId = 0,
                        flickDirection = direction,
                        output = output
                    )
                    longPressFlicksMap.getOrPut(keyIdentifier) { mutableListOf() }.add(mapping)
                }
            }

            val twoStepLongPress = uiLayout.twoStepLongPressKeyMaps[keyIdentifier]
            twoStepLongPress?.forEach { (first, secondMap) ->
                secondMap.forEach { (second, output) ->
                    if (output.isNotEmpty()) {
                        val mapping = TwoStepLongPressMappingEntity(
                            ownerKeyId = 0,
                            firstDirection = first,
                            secondDirection = second,
                            output = output
                        )
                        twoStepLongPressMap.getOrPut(keyIdentifier) { mutableListOf() }.add(mapping)
                    }
                }
            }
        }

        // Map<String, MutableList<...>> -> Map<String, List<...>> にして返す
        return DbKeyboardLayoutParts(
            keys = keys,
            flicksMap = flicksMap.mapValues { it.value.toList() },
            circularFlicksMap = circularFlicksMap.mapValues { it.value.toList() },
            twoStepMap = twoStepMap.mapValues { it.value.toList() },
            longPressFlicksMap = longPressFlicksMap.mapValues { it.value.toList() },
            twoStepLongPressMap = twoStepLongPressMap.mapValues { it.value.toList() }
        )
    }
}
