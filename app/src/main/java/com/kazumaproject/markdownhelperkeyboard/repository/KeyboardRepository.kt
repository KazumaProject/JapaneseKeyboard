package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickMapping
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FullKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.KeyDefinition
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
     * 【バグ修正版】インポート処理
     */
    suspend fun importLayouts(layouts: List<FullKeyboardLayout>) {
        for (fullLayout in layouts) {
            // 名前の重複をチェック
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

            // ★★★キーとフリックの関連情報を保持したMapを作成★★★
            val flicksMap = fullLayout.keysWithFlicks.associate { keyWithFlicks ->
                keyWithFlicks.key.keyIdentifier to keyWithFlicks.flicks
            }

            // ★★★修正したDAOメソッドを呼び出す★★★
            dao.insertFullKeyboardLayout(layoutToInsert, keysToInsert, flicksMap)
        }
    }

    fun convertLayout(dbLayout: KeyboardLayout): KeyboardLayout {
        val uuidToTapCharMap = dbLayout.flickKeyMaps.mapNotNull { (uuid, flickActionStates) ->
            val tapAction = flickActionStates.firstOrNull()?.get(FlickDirection.TAP)
            if (tapAction is FlickAction.Input) {
                uuid to tapAction.char
            } else {
                null
            }
        }.toMap()
        val uuidToFinalLabelMap = dbLayout.keys.associate { keyData ->
            val uuid = keyData.keyId
            val finalLabel = if (keyData.label.isNotEmpty()) {
                keyData.label
            } else {
                uuidToTapCharMap[uuid]
            }
            uuid to finalLabel
        }.filterValues { it != null } as Map<String, String>
        val newFlickKeyMaps = dbLayout.flickKeyMaps
            .mapNotNull { (uuid, flickActions) ->
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) {
                    finalLabel to flickActions
                } else {
                    null
                }
            }
            .toMap()
        val newKeys = dbLayout.keys.map { keyData ->
            if (keyData.isSpecialKey) {
                keyData
            } else {
                val finalLabel = uuidToFinalLabelMap[keyData.keyId]
                if (finalLabel != null) {
                    keyData.copy(label = finalLabel)
                } else {
                    keyData
                }
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
     * 【バグ修正版】レイアウトの保存処理
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
        Timber.d("saveLayout db: $dbLayout")
        val (keys, flicksMap) = convertToDbModel(layout)

        if (id != null && id > 0) {
            // 更新の場合は、古いキーとフリックを削除してから新しいものを挿入
            dao.updateLayout(dbLayout)
            dao.deleteKeysAndFlicksForLayout(id)
        }

        // 新規作成・更新ともに、最終的に修正版のDAOメソッドを呼び出す
        dao.insertFullKeyboardLayout(dbLayout, keys, flicksMap)
    }

    suspend fun deleteLayout(id: Long) {
        dao.deleteLayout(id)
    }

    suspend fun duplicateLayout(id: Long) {
        val originalLayout = dao.getFullLayoutOneShot(id) ?: return

        // 新しいレイアウト名の生成ロジックは変更なし
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
            layoutId = 0, // 新規作成なのでIDを0にする
            name = finalName,
            createdAt = System.currentTimeMillis()
        )

        // ★★★★★ 以下を修正 ★★★★★

        // 1. キーの新しいインスタンスを作成する
        //    主キー(keyId)と外部キー(ownerLayoutId)を0にリセットし、
        //    Roomに新しいレコードとして扱わせる
        val newKeys = originalLayout.keysWithFlicks.map { keyWithFlicks ->
            keyWithFlicks.key.copy(keyId = 0, ownerLayoutId = 0)
        }

        // 2. フリックの新しいインスタンスを作成する
        //    各フリック情報も外部キー(ownerKeyId)を0にリセットする
        val newFlicksMap = originalLayout.keysWithFlicks.associate { keyWithFlicks ->
            val newFlicks = keyWithFlicks.flicks.map { flick ->
                flick.copy(ownerKeyId = 0)
            }
            // keyIdentifierはキーとフリックを関連付けるためそのまま使う
            keyWithFlicks.key.keyIdentifier to newFlicks
        }

        // 3. 新しく作ったインスタンスでデータベースに登録する
        dao.insertFullKeyboardLayout(newLayoutInfo, newKeys, newFlicksMap)
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
        val keys = dbLayout.keysWithFlicks.map { keyWithFlicks ->
            val dbKey = keyWithFlicks.key
            val actionObject = if (dbKey.isSpecialKey) {
                KeyActionMapper.toKeyAction(dbKey.action)
            } else {
                null
            }
            KeyData(
                label = dbKey.label,
                row = dbKey.row,
                column = dbKey.column,
                isFlickable = dbKey.keyType != KeyType.NORMAL,
                keyType = dbKey.keyType,
                rowSpan = dbKey.rowSpan,
                colSpan = dbKey.colSpan,
                isSpecialKey = dbKey.isSpecialKey,
                drawableResId = dbKey.drawableResId,
                keyId = dbKey.keyIdentifier,
                action = actionObject
            )
        }
        return KeyboardLayout(
            keys = keys,
            flickKeyMaps = flickMaps,
            columnCount = dbLayout.layout.columnCount,
            rowCount = dbLayout.layout.rowCount,
            isRomaji = dbLayout.layout.isRomaji
        )
    }

    private fun convertToDbModel(uiLayout: KeyboardLayout): Pair<List<KeyDefinition>, Map<String, List<FlickMapping>>> {
        val keys = mutableListOf<KeyDefinition>()
        val flicksMap = mutableMapOf<String, MutableList<FlickMapping>>()
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
                    drawableResId = keyData.drawableResId,
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
        }
        return Pair(keys, flicksMap)
    }
}
