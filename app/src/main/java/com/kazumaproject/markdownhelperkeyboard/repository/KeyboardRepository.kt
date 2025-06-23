package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
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

    suspend fun importLayouts(layouts: List<FullKeyboardLayout>) {
        for (fullLayout in layouts) {
            var newName = fullLayout.layout.name
            var nameExists = dao.findLayoutByName(newName) != null
            var counter = 1
            // Handle name conflicts by appending a number
            while (nameExists) {
                newName = "${fullLayout.layout.name} (${counter})"
                nameExists = dao.findLayoutByName(newName) != null
                counter++
            }

            val layoutToInsert = fullLayout.layout.copy(
                layoutId = 0, // Ensure new ID is generated
                name = newName,
                createdAt = System.currentTimeMillis()
            )
            val keysToInsert = fullLayout.keysWithFlicks.map { it.key.copy(keyId = 0) }
            val flicksToInsert = fullLayout.keysWithFlicks.flatMap { it.flicks }

            dao.insertFullKeyboardLayout(layoutToInsert, keysToInsert, flicksToInsert)
        }
    }

    /**
     * DBから取得したレイアウトを、flickKeyMapsとKeyDataのlabelが同期したレイアウトに変換する。
     * - 既存のKeyData.labelが空でなければ、そのlabelをflickKeyMapsのキーとしても使用する。
     * - 既存のKeyData.labelが空の場合は、タップアクションの文字をlabelとflickKeyMapsのキーの両方に使用する。
     *
     * @param dbLayout DBから取得した、UUIDキーを持つレイアウト。
     * @return flickKeyMapsのキーとKeyData.labelが完全に同期された、新しいKeyboardLayout。
     */
    fun convertLayout(dbLayout: KeyboardLayout): KeyboardLayout {

        // ステップ1: まず、UUIDとタップ文字の対応表を作成する
        val uuidToTapCharMap = dbLayout.flickKeyMaps.mapNotNull { (uuid, flickActionStates) ->
            val tapAction = flickActionStates.firstOrNull()?.get(FlickDirection.TAP)
            if (tapAction is FlickAction.Input) {
                uuid to tapAction.char
            } else {
                null
            }
        }.toMap()

        // ステップ2: 「UUID」から「最終的に使われるべきラベル」への対応表を作成する
        // このマップが、KeyData.labelとflickKeyMapsのキーの「唯一の正しい情報源」となる。
        val uuidToFinalLabelMap = dbLayout.keys.associate { keyData ->
            val uuid = keyData.keyId
            // 既存のlabelが空でなければそれを優先、空ならタップ文字を使う
            val finalLabel = if (keyData.label.isNotEmpty()) {
                keyData.label
            } else {
                uuidToTapCharMap[uuid]
            }
            uuid to finalLabel
        }.filterValues { it != null } as Map<String, String>


        // ステップ3: 「最終ラベル」をキーとする、新しいflickKeyMapsを作成する
        val newFlickKeyMaps = dbLayout.flickKeyMaps
            .mapNotNull { (uuid, flickActions) ->
                // このUUIDに対応する最終ラベルを取得
                val finalLabel = uuidToFinalLabelMap[uuid]
                if (finalLabel != null) {
                    // キーをUUIDから「最終ラベル」に差し替える
                    finalLabel to flickActions
                } else {
                    null
                }
            }
            .toMap()

        // ステップ4: 「最終ラベル」をlabelとして設定した、新しいkeysリストを作成する
        val newKeys = dbLayout.keys.map { keyData ->
            if (keyData.isSpecialKey) {
                keyData
            } else {
                // このキーの最終ラベルを取得して設定する
                val finalLabel = uuidToFinalLabelMap[keyData.keyId]
                if (finalLabel != null) {
                    keyData.copy(label = finalLabel)
                } else {
                    // 該当するラベルがなければ元のまま
                    keyData
                }
            }
        }

        // ステップ5: 完全に同期された新しいレイアウトを返す
        return dbLayout.copy(
            keys = newKeys,
            flickKeyMaps = newFlickKeyMaps
        )
    }

    /**
     * ユーザーが作成した全てのカスタムキーボードを、表示可能な KeyboardLayout のリストとして効率的に取得します。
     */
    fun getAllCustomKeyboardLayouts(): Flow<List<KeyboardLayout>> {
        // 1. DAOの新しいメソッドを呼び出し、DBから全てのレイアウト情報を一度に取得
        return dao.getAllFullLayouts().map { dbLayouts ->
            // 2. 各レイアウトを、UIで使える KeyboardLayout に変換
            dbLayouts.map { dbLayout ->
                convertToUiModel(dbLayout)
            }
        }
    }

    /**
     * 指定された名前が、編集中のレイアウトIDを除いて、既に存在するかどうかを確認します。
     * @param name チェックする名前
     * @param currentId 現在編集中のレイアウトのID（新規作成の場合はnull）
     * @return 存在する場合は true
     */
    suspend fun doesNameExist(name: String, currentId: Long?): Boolean {
        val foundLayout = dao.findLayoutByName(name)
        return when {
            // 名前が見つからなかった -> 重複なし
            foundLayout == null -> false
            // 見つかったが、それが現在編集中のレイアウト自身だった -> 重複なし
            foundLayout.layoutId == currentId -> false
            // 見つかって、それが別のレイアウトだった -> 重複あり
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

    suspend fun saveLayout(layout: KeyboardLayout, name: String, id: Long?) {
        Timber.d("saveLayout: $layout")
        val dbLayout = CustomKeyboardLayout(
            layoutId = id ?: 0,
            name = name,
            columnCount = layout.columnCount,
            rowCount = layout.rowCount
        )
        Timber.d("saveLayout db: $dbLayout")
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
                .toSortedMap()
                .values.toList()
            identifier to flicksByState
        }

        val keys = dbLayout.keysWithFlicks.map { keyWithFlicks ->
            val dbKey = keyWithFlicks.key

            // DBのaction文字列をKeyActionオブジェクトに復元する
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
                action = actionObject // ★★★★★ 正しくactionオブジェクトを復元する
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

            // isSpecialKeyがtrueの場合、keyData.actionオブジェクトを文字列に変換する
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

            // ViewModelのflickKeyMapsのキーはUUIDなので、keyIdentifierで検索する
            val flickActionsForThisKey = uiLayout.flickKeyMaps[keyIdentifier]
            flickActionsForThisKey?.forEachIndexed { stateIndex, stateMap ->
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



