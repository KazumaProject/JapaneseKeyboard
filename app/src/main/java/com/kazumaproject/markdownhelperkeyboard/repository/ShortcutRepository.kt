package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.short_cut.data.ShortcutItem
import com.kazumaproject.markdownhelperkeyboard.short_cut.database.ShortcutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutRepository @Inject constructor(
    private val shortcutDao: ShortcutDao
) {

    /**
     * 現在有効なショートカットのリストを監視可能なFlowとして返します。
     * DBの変更（並び替えや追加削除）をリアルタイムで検知できます。
     */
    val enabledShortcutsFlow: Flow<List<ShortcutType>> = shortcutDao.getAllShortcutsFlow()
        .map { items ->
            // Entity -> Enum 変換
            items.mapNotNull { item ->
                ShortcutType.fromId(item.typeId)
            }
        }

    /**
     * ショートカットのリストを保存（更新）します。
     * リストの順番がそのまま sortOrder として保存されます。
     *
     * @param shortcuts ユーザーが選択・並び替えたショートカットのリスト
     */
    suspend fun updateShortcuts(shortcuts: List<ShortcutType>) {
        withContext(Dispatchers.IO) {
            // Enum -> Entity 変換 (indexをsortOrderにする)
            val itemsToSave = shortcuts.mapIndexed { index, type ->
                ShortcutItem(
                    typeId = type.id,
                    sortOrder = index
                )
            }

            // トランザクション的に処理（Daoに @Transaction メソッドを作っても良いが、ここではシンプルに実装）
            // 既存の並びを削除して、新しい順序で全挿入する
            shortcutDao.deleteAll()
            shortcutDao.insertAll(itemsToSave)
        }
    }

    /**
     * データが空の場合にデフォルトのショートカットを初期化します。
     * Serviceの起動時などに呼び出します。
     */
    suspend fun initDefaultShortcutsIfNeeded() {
        withContext(Dispatchers.IO) {
            val currentItems = shortcutDao.getAllShortcuts()
            if (currentItems.isEmpty()) {
                val defaultShortcuts = listOf(
                    ShortcutType.SETTINGS,
                    ShortcutType.EMOJI,
                    ShortcutType.TEMPLATE,
                    ShortcutType.COPY,
                    ShortcutType.PASTE,
                    ShortcutType.KEYBOARD_PICKER
                )
                updateShortcuts(defaultShortcuts)
            }
        }
    }

    /**
     * 設定画面用：全ての種類のショートカットと、現在の有効/無効状態を取得します。
     * 戻り値: Pair<ShortcutType, Boolean> のリスト (Booleanがtrueなら現在有効)
     */
    suspend fun getAllShortcutsWithStatus(): List<Pair<ShortcutType, Boolean>> {
        return withContext(Dispatchers.IO) {
            val enabledItems = shortcutDao.getAllShortcuts()
            val enabledTypes = enabledItems.mapNotNull { ShortcutType.fromId(it.typeId) }.toSet()
            val allTypes = ShortcutType.values()

            val result = mutableListOf<Pair<ShortcutType, Boolean>>()

            // 1. まず有効なものを順序通りに追加
            enabledItems.forEach { item ->
                ShortcutType.fromId(item.typeId)?.let { type ->
                    result.add(type to true)
                }
            }

            // 2. 次に無効なもの（まだリストに含まれていないもの）を追加
            allTypes.forEach { type ->
                if (!enabledTypes.contains(type)) {
                    result.add(type to false)
                }
            }

            result
        }
    }
}
