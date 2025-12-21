package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.TwoStepFlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toDbStrings
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.toFlickAction
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardRepository @Inject constructor(
    private val dao: KeyboardLayoutDao
) {

    suspend fun getAllFullLayoutsForExport(): List<FullKeyboardLayout> {
        return dao.getAllFullLayoutsOneShot()
    }

    /**
     * インポート処理（TWO_STEP_FLICK 含む）
     */
    suspend fun importLayouts(layouts: List<FullKeyboardLayout>) {
        for (fullLayout in layouts) {
            var newName = fullLayout.layout.name
            var nameExists = dao.findLayoutByName(newName) != null
            var counter = 1
            while (nameExists) {
                newName = "${fullLayout.layout.name} (${counter})"
                nameExists = dao.findLayoutByName(newName) != null
                counter++
            }

            val layoutToInsert = fullLayout.layout.copy(
                layoutId = 0,
                name = newName,
                createdAt = System.currentTimeMillis()
            )

            val keysToInsert = fullLayout.keysWithFlicks.map { it.key }

            val flicksMap = fullLayout.keysWithFlicks.associate { keyWithFlicks ->
                keyWithFlicks.key.keyIdentifier to keyWithFlicks.flicks
            }

            val twoStepMap = fullLayout.keysWithFlicks.associate { keyWithFlicks ->
                keyWithFlicks.key.keyIdentifier to keyWithFlicks.twoStepFlicks
            }

            dao.insertFullKeyboardLayout(layoutToInsert, keysToInsert, flicksMap, twoStepMap)
        }
    }

    fun convertLayout(dbLayout: KeyboardLayout): KeyboardLayout {
        val uuidToTapCharMap = dbLayout.flickKeyMaps.mapNotNull { (uuid, flickActionStates) ->
            val tapAction = flickActionStates.firstOrNull()?.get(FlickDirection.TAP)
            if (tapAction is FlickAction.Input) uuid to tapAction.char else null
        }.toMap()

        val uuidToFinalLabelMap = dbLayout.keys.associate { keyData ->
            val uuid = keyData.keyId
            val finalLabel =
                if (keyData.label.isNotEmpty()) keyData.label else uuidToTapCharMap[uuid]
            uuid to finalLabel
        }.filterValues { it != null } as Map<String, String>

        val newFlickKeyMaps = dbLayout.flickKeyMaps
            .mapNotNull { (uuid, flickActions) ->
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) finalLabel to flickActions else null
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
            flickKeyMaps = newFlickKeyMaps
        )
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

    fun getLayouts(): Flow<List<CustomKeyboardLayout>> = dao.getLayoutsList()

    suspend fun getLayoutsNotFlow(): List<CustomKeyboardLayout> =
        dao.getLayoutsListNotFlow()

    suspend fun getLayoutName(id: Long): String? = dao.getLayoutName(id)

    fun getFullLayout(id: Long): Flow<KeyboardLayout> {
        return dao.getFullLayoutById(id).map { dbLayout ->
            convertToUiModel(dbLayout)
        }
    }

    /**
     * レイアウトの保存処理（TWO_STEP_FLICK 含む）
     */
    suspend fun saveLayout(layout: KeyboardLayout, name: String, id: Long?) {
        Timber.d("saveLayout: $layout")

        val dbLayout = CustomKeyboardLayout(
            layoutId = id ?: 0,
            name = name,
            columnCount = layout.columnCount,
            rowCount = layout.rowCount,
            isRomaji = layout.isRomaji
        )

        val (keys, flicksMap, twoStepMap) = convertToDbModel(layout)

        if (id != null && id > 0) {
            dao.updateLayout(dbLayout)
            dao.deleteKeysAndFlicksForLayout(id)
        }

        dao.insertFullKeyboardLayout(dbLayout, keys, flicksMap, twoStepMap)
    }

    suspend fun deleteLayout(id: Long) {
        dao.deleteLayout(id)
    }

    suspend fun duplicateLayout(id: Long) {
        val originalLayout = dao.getFullLayoutOneShot(id) ?: return

        val newName = originalLayout.layout.name + " (コピー)"
        var nameExists = dao.findLayoutByName(newName) != null
        var counter = 2
        var finalName = newName
        while (nameExists) {
            finalName = "$newName ($counter)"
            nameExists = dao.findLayoutByName(finalName) != null
            counter++
        }

        val newLayoutInfo = originalLayout.layout.copy(
            layoutId = 0,
            name = finalName,
            createdAt = System.currentTimeMillis()
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

        val newTwoStepMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newTwoStep = keyWithFlicks.twoStepFlicks.map { m ->
                m.copy(ownerKeyId = 0)
            }
            keyWithFlicks.key.keyIdentifier to newTwoStep
        }

        dao.insertFullKeyboardLayout(newLayoutInfo, newKeys, newFlicksMap, newTwoStepMap)
    }

    private fun iconResForAction(action: KeyAction): Int? = when (action) {
        KeyAction.Backspace -> com.kazumaproject.core.R.drawable.backspace_24px
        KeyAction.ChangeInputMode -> com.kazumaproject.core.R.drawable.backspace_24px
        KeyAction.Convert -> com.kazumaproject.core.R.drawable.henkan
        KeyAction.Copy -> com.kazumaproject.core.R.drawable.content_copy_24dp
        KeyAction.Delete -> com.kazumaproject.core.R.drawable.backspace_24px
        KeyAction.Enter -> com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
        KeyAction.MoveCursorLeft -> com.kazumaproject.core.R.drawable.baseline_arrow_left_24
        KeyAction.MoveCursorRight -> com.kazumaproject.core.R.drawable.baseline_arrow_right_24
        KeyAction.MoveCustomKeyboardTab -> com.kazumaproject.core.R.drawable.keyboard_command_key_24px
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
        else -> null
    }

    private fun FlickAction.enrichIconIfNeeded(): FlickAction = when (this) {
        is FlickAction.Action -> {
            if (this.drawableResId != null) this
            else this.copy(drawableResId = iconResForAction(this.action))
        }

        else -> this
    }

    private fun convertToUiModel(dbLayout: FullKeyboardLayout): KeyboardLayout {
        val flickMaps = dbLayout.keysWithFlicks.associate { keyWithFlicks ->
            val identifier = keyWithFlicks.key.keyIdentifier
            val flicksByState = keyWithFlicks.flicks.groupBy { it.stateIndex }
                .mapValues { (_, stateFlicks) ->
                    stateFlicks.associate { flick ->
                        flick.flickDirection to flick.toFlickAction()
                    }
                }
                .toSortedMap()
                .values.toList()

            identifier to flicksByState
        }

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

        val keys = dbLayout.keysWithFlicks.map { keyWithFlicks ->
            val dbKey = keyWithFlicks.key
            val actionObject = if (dbKey.isSpecialKey) {
                KeyActionMapper.toKeyAction(dbKey.action)
            } else {
                null
            }

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
                    action = null
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
                        else -> null
                    },
                    keyId = dbKey.keyIdentifier,
                    action = actionObject
                )
            }
        }

        if (twoStepMaps.isNotEmpty()) {
            return KeyboardLayout(
                keys = keys,
                flickKeyMaps = flickMaps,
                columnCount = dbLayout.layout.columnCount,
                rowCount = dbLayout.layout.rowCount,
                isRomaji = dbLayout.layout.isRomaji,
                twoStepFlickKeyMaps = twoStepMaps
            )
        } else {
            return KeyboardLayout(
                keys = keys,
                flickKeyMaps = flickMaps,
                columnCount = dbLayout.layout.columnCount,
                rowCount = dbLayout.layout.rowCount,
                isRomaji = dbLayout.layout.isRomaji,
                twoStepFlickKeyMaps = twoStepMaps
            )
        }
    }

    private fun convertToDbModel(
        uiLayout: KeyboardLayout
    ): Triple<List<KeyDefinition>, Map<String, List<FlickMapping>>, Map<String, List<TwoStepFlickMapping>>> {

        val keys = mutableListOf<KeyDefinition>()
        val flicksMap = mutableMapOf<String, MutableList<FlickMapping>>()
        val twoStepMap = mutableMapOf<String, MutableList<TwoStepFlickMapping>>()

        uiLayout.keys.forEach { keyData ->
            val keyIdentifier = keyData.keyId ?: UUID.randomUUID().toString()

            val actionString: String? = if (keyData.isSpecialKey) {
                KeyActionMapper.fromKeyAction(keyData.action)
            } else {
                null
            }

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
                    action = actionString
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
        }

        return Triple(keys, flicksMap, twoStepMap)
    }
}
