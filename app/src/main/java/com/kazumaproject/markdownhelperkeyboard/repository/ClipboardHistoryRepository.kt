package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardFileStore
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryDao
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * クリップボード履歴のデータ操作を抽象化するリポジトリクラス。
 * DB (軽量メタデータ) と FileStore (重い実データ) の整合性を一元管理します。
 */
class ClipboardHistoryRepository @Inject constructor(
    private val dao: ClipboardHistoryDao,
    private val fileStore: ClipboardFileStore
) {

    /**
     * すべてのクリップボード履歴(メタデータ)をFlowとして提供します。
     */
    val allHistory: Flow<List<ClipboardHistoryItem>> = dao.getAllHistory()

    /**
     * 【重要】IMEService から呼ばれる保存メソッド
     * 実データをファイルへ保存し、DBにパスとプレビューを記録します。
     */
    suspend fun insertFromClipboard(item: ClipboardItem, isPinned: Boolean = false) =
        withContext(Dispatchers.IO) {
            val historyItem = when (item) {
                is ClipboardItem.Text -> {
                    val path = fileStore.saveText(item.text)
                    ClipboardHistoryItem(
                        itemType = ItemType.TEXT,
                        preview = createTextPreview(item.text),
                        contentPath = path,
                        isPinned = isPinned
                    )
                }

                is ClipboardItem.Image -> {
                    val path = fileStore.saveImage(item.bitmap)
                    ClipboardHistoryItem(
                        itemType = ItemType.IMAGE,
                        preview = "[画像]",
                        contentPath = path,
                        isPinned = isPinned
                    )
                }

                else -> null
            }
            historyItem?.let { dao.insert(it) }
        }

    /**
     * insertFromClipboard と同じ処理を別名でも提供（ViewModel等からの呼び出し用）
     */
    suspend fun insertClipboardItem(item: ClipboardItem, isPinned: Boolean = false) =
        insertFromClipboard(item, isPinned)

    /**
     * 単純なEntityの更新
     */
    suspend fun update(item: ClipboardHistoryItem) = withContext(Dispatchers.IO) {
        dao.update(item)
    }

    suspend fun setPinned(id: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        dao.setPinned(id, isPinned)
    }

    /**
     * 本文テキストの取得
     */
    suspend fun getFullText(item: ClipboardHistoryItem): String = withContext(Dispatchers.IO) {
        if (item.itemType != ItemType.TEXT) return@withContext ""
        return@withContext fileStore.readText(item.contentPath) ?: item.preview
    }

    /**
     * 本文テキストの編集・更新（ファイル上書き + DBプレビュー更新）
     */
    suspend fun updateTextContent(item: ClipboardHistoryItem, newFullText: String) =
        withContext(Dispatchers.IO) {
            if (item.itemType != ItemType.TEXT) return@withContext

            fileStore.deleteFile(item.contentPath)
            val newPath = fileStore.saveText(newFullText)

            val updatedItem = item.copy(
                preview = createTextPreview(newFullText),
                contentPath = newPath,
                timestamp = System.currentTimeMillis()
            )
            dao.update(updatedItem)
        }

    /**
     * 履歴のメタデータから実データを読み出します（貼り付け時などに使用）。
     */
    suspend fun getFullContent(item: ClipboardHistoryItem): ClipboardItem =
        withContext(Dispatchers.IO) {
            return@withContext when (item.itemType) {
                ItemType.TEXT -> {
                    val text = fileStore.readText(item.contentPath) ?: item.preview
                    ClipboardItem.Text(id = item.id, text = text, isPinned = item.isPinned)
                }

                ItemType.IMAGE -> {
                    val bitmap = fileStore.readImage(item.contentPath)
                    if (bitmap != null) {
                        ClipboardItem.Image(id = item.id, bitmap = bitmap, isPinned = item.isPinned)
                    } else {
                        ClipboardItem.Empty
                    }
                }
            }
        }

    suspend fun getFullContentById(id: Long): ClipboardItem = withContext(Dispatchers.IO) {
        val item = dao.getById(id) ?: return@withContext ClipboardItem.Empty
        return@withContext getFullContent(item)
    }

    /**
     * 一覧表示用の縮小された画像（サムネイル）を取得します。
     */
    suspend fun getThumbnail(
        item: ClipboardHistoryItem,
        targetWidth: Int = 300,
        targetHeight: Int = 300
    ): ClipboardItem =
        withContext(Dispatchers.IO) {
            if (item.itemType != ItemType.IMAGE) return@withContext ClipboardItem.Empty

            // FileStore にサムネイル読み込み機能を実装するか、ここでデコードを制御する
            val bitmap = fileStore.readImageThumbnail(item.contentPath, targetWidth, targetHeight)
            return@withContext if (bitmap != null) {
                ClipboardItem.Image(id = item.id, bitmap = bitmap, isPinned = item.isPinned)
            } else {
                ClipboardItem.Empty
            }
        }


    /**
     * 指定されたIDの履歴とファイルを削除します。
     */
    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        val path = dao.getContentPathById(id)
        fileStore.deleteFile(path)
        dao.deleteById(id)
    }

    suspend fun deleteExpiredUnpinnedItems(retentionHours: Int): Int = withContext(Dispatchers.IO) {
        val retentionMillis = retentionHours.coerceIn(1, 72) * 60L * 60L * 1000L
        val threshold = System.currentTimeMillis() - retentionMillis
        val expiredItems = dao.getExpiredUnpinnedItems(threshold)
        expiredItems.forEach { item ->
            fileStore.deleteFile(item.contentPath)
            dao.deleteById(item.id)
        }
        return@withContext expiredItems.size
    }

    /**
     * すべての履歴とファイルを削除します。
     */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        fileStore.deleteAllFiles()
        dao.deleteAll()
    }

    /**
     * 最新の履歴アイテムを取得します（重複チェック用）。
     */
    suspend fun getLatestItem(): ClipboardHistoryItem? = withContext(Dispatchers.IO) {
        return@withContext dao.getLatestItem()
    }

    /**
     * 現在の履歴のスナップショットを取得します。
     */
    suspend fun getHistorySnapshot(): List<ClipboardHistoryItem> = withContext(Dispatchers.IO) {
        return@withContext dao.getAllHistorySuspended()
    }

    /**
     * プレビュー文字列を生成します。
     */
    private fun createTextPreview(text: String): String {
        return text.take(150)
            .replace(Regex("[\\n\\r\\s]+"), " ")
            .trim()
            .let { if (text.length > 150) "$it..." else it }
    }
}
