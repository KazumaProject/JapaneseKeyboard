package com.kazumaproject.markdownhelperkeyboard.clipboard_history

import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemType

fun ClipboardHistoryItem.toClipboardItem(): ClipboardItem {
    return when (this.itemType) {
        ItemType.TEXT -> ClipboardItem.Text(
            id = this.id, // <<< id を渡す
            text = this.textData ?: ""
        )

        ItemType.IMAGE -> {
            this.imageData?.let { bitmap ->
                ClipboardItem.Image(
                    id = this.id, // <<< id を渡す
                    bitmap = bitmap
                )
            } ?: ClipboardItem.Empty // Bitmapが何らかの理由でnullならEmpty扱い
        }
    }
}

/**
 *【参考】
 * 逆方向の変換（ClipboardItem → ClipboardHistoryItem）です。
 * こちらはDBに新規挿入する際に使われるため、idはRoomが自動生成するので不要です。
 * そのため、こちらの関数は修正の必要はありません。
 */
fun ClipboardItem.toHistoryItem(): ClipboardHistoryItem? = when (this) {
    is ClipboardItem.Text -> ClipboardHistoryItem(
        itemType = ItemType.TEXT,
        textData = this.text,
        imageData = null
    )

    is ClipboardItem.Image -> ClipboardHistoryItem(
        itemType = ItemType.IMAGE,
        textData = null,
        imageData = this.bitmap
    )

    is ClipboardItem.Empty -> null
}
