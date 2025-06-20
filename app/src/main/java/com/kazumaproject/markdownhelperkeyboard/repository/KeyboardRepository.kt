package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.data.KeyDefinition
import com.kazumaproject.markdownhelperkeyboard.cutsom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.database.toDbStrings
import com.kazumaproject.markdownhelperkeyboard.database.toFlickAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardRepository @Inject constructor(
    private val dao: KeyboardLayoutDao
) {

    fun getLayouts(): Flow<List<CustomKeyboardLayout>> = dao.getLayoutsList()

    fun getFullLayout(id: Long): Flow<KeyboardLayout> {
        return dao.getFullLayoutById(id).map { dbLayout ->
            convertToUiModel(dbLayout)
        }
    }

    suspend fun saveLayout(layout: KeyboardLayout, name: String, id: Long?) {
        val dbLayout = CustomKeyboardLayout(
            layoutId = id ?: 0,
            name = name,
            columnCount = layout.columnCount,
            rowCount = layout.rowCount
        )
        val (keys, flicks) = convertToDbModel(layout)

        if (id == null || id == 0L) {
            dao.insertFullKeyboardLayout(dbLayout, keys, flicks)
        } else {
            dao.updateLayout(dbLayout)
            dao.deleteKeysAndFlicksForLayout(id)
            val keysWithLayoutId = keys.map { it.copy(ownerLayoutId = id) }
            val newKeyIds = dao.insertKeys(keysWithLayoutId)
            val flicksWithKeyIds = mapFlicksToNewKeys(keysWithLayoutId, newKeyIds, flicks)
            dao.insertFlickMappings(flicksWithKeyIds)
        }
    }

    suspend fun deleteLayout(id: Long) {
        dao.deleteLayout(id)
    }

    private fun convertToUiModel(dbLayout: FullKeyboardLayout): KeyboardLayout {
        //val keyIdMap = dbLayout.keysWithFlicks.associate { it.key.keyId to it.key.keyIdentifier }
        val flickMaps = dbLayout.keysWithFlicks.associate { keyWithFlicks ->
            val identifier = keyWithFlicks.key.keyIdentifier
            val flicksByState = keyWithFlicks.flicks.groupBy { it.stateIndex }
                .mapValues { (_, stateFlicks) ->
                    stateFlicks.associate { flick ->
                        flick.flickDirection to flick.toFlickAction()
                    }
                }
                .values.toList() // Map<Int, Map> to List<Map>
            identifier to flicksByState
        }

        val keys = dbLayout.keysWithFlicks.map { keyWithFlicks ->
            KeyData(
                label = keyWithFlicks.key.label,
                row = keyWithFlicks.key.row,
                column = keyWithFlicks.key.column,
                isFlickable = keyWithFlicks.key.keyType != KeyType.NORMAL,
                keyType = keyWithFlicks.key.keyType,
                rowSpan = keyWithFlicks.key.rowSpan,
                colSpan = keyWithFlicks.key.colSpan,
                isSpecialKey = keyWithFlicks.key.isSpecialKey,
                drawableResId = keyWithFlicks.key.drawableResId,
                keyId = keyWithFlicks.key.keyIdentifier
            )
        }

        return KeyboardLayout(
            keys = keys,
            flickKeyMaps = flickMaps,
            columnCount = dbLayout.layout.columnCount,
            rowCount = dbLayout.layout.rowCount
        )
    }

    private fun convertToDbModel(uiLayout: KeyboardLayout): Pair<List<KeyDefinition>, List<FlickMapping>> {
        val keys = mutableListOf<KeyDefinition>()
        val flicks = mutableListOf<FlickMapping>()

        uiLayout.keys.forEach { keyData ->
            val keyIdentifier = keyData.keyId ?: UUID.randomUUID().toString()
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
                    drawableResId = keyData.drawableResId,
                    keyIdentifier = keyIdentifier
                )
            )

            uiLayout.flickKeyMaps[keyIdentifier]?.forEachIndexed { stateIndex, stateMap ->
                stateMap.forEach { (direction, flickAction) ->
                    val (actionType, actionValue) = flickAction.toDbStrings()
                    flicks.add(
                        FlickMapping(
                            ownerKeyId = keyIdentifier.hashCode()
                                .toLong(), // 仮のIDとしてIdentifierのハッシュコードを使う
                            stateIndex = stateIndex,
                            flickDirection = direction,
                            actionType = actionType,
                            actionValue = actionValue
                        )
                    )
                }
            }
        }
        return Pair(keys, flicks)
    }
}

// ヘルパー関数
private suspend fun KeyboardLayoutDao.insertFullKeyboardLayout(
    layout: CustomKeyboardLayout,
    keys: List<KeyDefinition>,
    flicks: List<FlickMapping>
) {
    val layoutId = insertLayout(layout)
    val keysWithLayoutId = keys.map { it.copy(ownerLayoutId = layoutId) }
    val newKeyIds = insertKeys(keysWithLayoutId)
    val flicksWithKeyIds = mapFlicksToNewKeys(keysWithLayoutId, newKeyIds, flicks)
    insertFlickMappings(flicksWithKeyIds)
}

private fun mapFlicksToNewKeys(
    dbKeys: List<KeyDefinition>,
    newKeyIds: List<Long>,
    allFlicks: List<FlickMapping>
): List<FlickMapping> {
    val flicksWithKeyIds = mutableListOf<FlickMapping>()
    val identifierToIdMap =
        dbKeys.mapIndexed { index, key -> key.keyIdentifier to newKeyIds[index] }.toMap()

    allFlicks.forEach { flick ->
        val realOwnerKeyId = identifierToIdMap[flick.ownerKeyId.toString().hashCode().toString()]
        if (realOwnerKeyId != null) {
            flicksWithKeyIds.add(flick.copy(ownerKeyId = realOwnerKeyId))
        }
    }
    return flicksWithKeyIds
}
