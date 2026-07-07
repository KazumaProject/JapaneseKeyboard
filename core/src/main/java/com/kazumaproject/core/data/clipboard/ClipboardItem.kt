package com.kazumaproject.core.data.clipboard

import android.graphics.Bitmap

sealed class ClipboardItem {
    /** * テキストコンテンツを保持します。
     * @param id データベース上のユニークID
     */
    data class Text(
        val id: Long,
        val text: String,
        val isPinned: Boolean = false
    ) : ClipboardItem()

    /** * 画像コンテンツ (Bitmap) を保持します。
     * @param id データベース上のユニークID
     */
    data class Image(
        val id: Long,
        val bitmap: Bitmap,
        val isPinned: Boolean = false
    ) : ClipboardItem()

    /** クリップボードが空、またはサポートされていないコンテンツの場合を表します。 */
    data object Empty : ClipboardItem()
}
