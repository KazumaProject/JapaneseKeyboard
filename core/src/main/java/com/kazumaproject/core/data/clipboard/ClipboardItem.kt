package com.kazumaproject.core.data.clipboard

import android.graphics.Bitmap

sealed class ClipboardItem {
    /** テキストコンテンツを保持します。 */
    data class Text(val text: String) : ClipboardItem()

    /** 画像コンテンツ (Bitmap) を保持します。 */
    data class Image(val bitmap: Bitmap) : ClipboardItem()

    /** クリップボードが空、またはサポートされていないコンテンツの場合を表します。 */
    // If you're on Kotlin 1.9+, you can keep `data object Empty : ClipboardItem()`
    data object Empty : ClipboardItem()
}
