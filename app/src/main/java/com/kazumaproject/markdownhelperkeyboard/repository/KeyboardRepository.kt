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

    suspend fun getLayoutName(id: Long): String? = dao.getLayoutName(id)

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
        val (keys, flicksMap) = convertToDbModel(layout)

        if (id == null || id == 0L || id == -1L) {
            dao.insertFullKeyboardLayout(dbLayout, keys, flicksMap)
        } else {
            dao.updateLayout(dbLayout)
            dao.deleteKeysAndFlicksForLayout(id)
            val keysWithLayoutId = keys.map { it.copy(ownerLayoutId = id) }
            val newKeyIds = dao.insertKeys(keysWithLayoutId)
            val flicksWithKeyIds = mapFlicksToNewKeys(keysWithLayoutId, newKeyIds, flicksMap)
            if (flicksWithKeyIds.isNotEmpty()) {
                dao.insertFlickMappings(flicksWithKeyIds)
            }
        }
    }

    suspend fun deleteLayout(id: Long) {
        dao.deleteLayout(id)
    }

    suspend fun duplicateLayout(id: Long) {
        val originalLayout = dao.getFullLayoutOneShot(id) ?: return

        // 新しいレイアウト名を作成
        val newLayoutInfo = originalLayout.layout.copy(
            layoutId = 0, // 新規作成なのでIDを0に
            name = originalLayout.layout.name + " (コピー)",
            createdAt = System.currentTimeMillis()
        )

        // キーとフリックの情報を抽出
        val keys = originalLayout.keysWithFlicks.map { it.key }
        val flicksMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            keyWithFlicks.key.keyIdentifier to keyWithFlicks.flicks
        }

        // 新規レイアウトとして保存
        dao.insertFullKeyboardLayout(newLayoutInfo, keys, flicksMap)
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
                .toSortedMap() // 状態インデックス順にソート
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

    private fun convertToDbModel(uiLayout: KeyboardLayout): Pair<List<KeyDefinition>, Map<String, List<FlickMapping>>> {
        val keys = mutableListOf<KeyDefinition>()
        val flicksMap = mutableMapOf<String, MutableList<FlickMapping>>()

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
        }
        return Pair(keys, flicksMap)
    }
}

// ヘルパー関数群（リポジトリの外部、または別のファイルに定義）
private suspend fun KeyboardLayoutDao.insertFullKeyboardLayout(
    layout: CustomKeyboardLayout,
    keys: List<KeyDefinition>,
    flicksMap: Map<String, List<FlickMapping>>
) {
    val layoutId = insertLayout(layout)
    val keysWithLayoutId = keys.map { it.copy(ownerLayoutId = layoutId) }
    val newKeyIds = insertKeys(keysWithLayoutId)
    val flicksWithKeyIds = mapFlicksToNewKeys(keysWithLayoutId, newKeyIds, flicksMap)
    if (flicksWithKeyIds.isNotEmpty()) {
        insertFlickMappings(flicksWithKeyIds)
    }
}

private fun mapFlicksToNewKeys(
    dbKeys: List<KeyDefinition>,
    newKeyIds: List<Long>,
    flicksMap: Map<String, List<FlickMapping>>
): List<FlickMapping> {
    val finalFlicks = mutableListOf<FlickMapping>()
    val identifierToIdMap =
        dbKeys.mapIndexed { index, key -> key.keyIdentifier to newKeyIds[index] }.toMap()

    identifierToIdMap.forEach { (identifier, realKeyId) ->
        flicksMap[identifier]?.forEach { flick ->
            finalFlicks.add(flick.copy(ownerKeyId = realKeyId))
        }
    }
    return finalFlicks
}

