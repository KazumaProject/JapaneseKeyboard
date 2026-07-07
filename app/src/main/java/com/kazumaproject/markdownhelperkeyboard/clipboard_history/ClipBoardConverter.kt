import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardFileStore
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemType

/**
 * 拡張関数: Entity -> UI/DTOモデル
 * 実データが必要なため ClipboardFileStore を引数に取ります
 */
fun ClipboardHistoryItem.toClipboardItem(fileStore: ClipboardFileStore): ClipboardItem {
    return when (this.itemType) {
        ItemType.TEXT -> {
            val content = fileStore.readText(this.contentPath) ?: this.preview
            ClipboardItem.Text(
                id = this.id,
                text = content,
                isPinned = this.isPinned
            )
        }

        ItemType.IMAGE -> {
            val bitmap = fileStore.readImage(this.contentPath)
            if (bitmap != null) {
                ClipboardItem.Image(
                    id = this.id,
                    bitmap = bitmap,
                    isPinned = this.isPinned
                )
            } else {
                ClipboardItem.Empty
            }
        }
    }
}
