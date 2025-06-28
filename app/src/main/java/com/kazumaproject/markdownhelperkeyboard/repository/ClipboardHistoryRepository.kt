package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryDao
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * クリップボード履歴のデータ操作を抽象化するリポジトリクラス。
 * ViewModelなどアプリの他の部分はこのリポジトリを介してデータにアクセスします。
 *
 * @property dao Hilt/DaggerなどのDIフレームワークによって注入されるDAOのインスタンス。
 */
class ClipboardHistoryRepository @Inject constructor(
    private val dao: ClipboardHistoryDao
) {

    /**
     * すべてのクリップボード履歴をFlowとして提供します。
     * UIはこのFlowを監視することで、データベースの変更を自動的に反映できます。
     */
    val allHistory: Flow<List<ClipboardHistoryItem>> = dao.getAllHistory()

    /**
     * 新しいクリップボード履歴アイテムをデータベースに挿入します。
     *
     * @param item 保存するClipboardHistoryItem
     */
    suspend fun insert(item: ClipboardHistoryItem) {
        dao.insert(item)
    }

    /**
     * 指定されたIDの履歴アイテムをデータベースから削除します。
     *
     * @param id 削除するアイテムのID
     */
    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    /**
     * 現在のクリップボード履歴のスナップショット（一度きりのリスト）を取得します。
     * バックグラウンド処理などで、一度だけ最新のリストが必要な場合に使用します。
     *
     * @return ClipboardHistoryItemのリスト
     */
    suspend fun getHistorySnapshot(): List<ClipboardHistoryItem> {
        return dao.getAllHistorySuspended()
    }
}
