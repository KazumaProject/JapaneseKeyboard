package com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.kazumaproject.core.data.clipboard.ClipboardItem
import timber.log.Timber

/**
 * クリップボードの操作を補助するユーティリティクラス。
 *
 * @property context アプリケーションコンテキスト
 */
class ClipboardUtil(private val context: Context) {

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /**
     * クリップボードの主要なコンテンツを取得します。
     * 画像の取得を最優先で試み、失敗した場合にテキストの取得を試みます。
     * これにより、画像とテキストが混在している場合でも、画像を優先的に扱うことができます。
     *
     * @return 取得したコンテンツを表す [ClipboardItem]。（[ClipboardItem.Image]、[ClipboardItem.Text]、または [ClipboardItem.Empty]）
     */
    fun getPrimaryClipContent(): ClipboardItem {
        if (!clipboard.hasPrimaryClip()) {
            return ClipboardItem.Empty
        }

        // 1. 画像の取得を最優先で試みる
        getClipboardImageBitmap()?.let { bitmap ->
            return ClipboardItem.Image(id = 0, bitmap)
        }

        // 2. 画像が取得できなかった場合、テキストの取得を試みる
        getFirstClipboardTextOrNull()?.let { text ->
            if (text.isNotBlank()) {
                return ClipboardItem.Text(id = 0, text)
            }
        }

        // 3. 画像も有効なテキストも見つからなかった場合
        return ClipboardItem.Empty
    }

    /**
     * クリップボードにテキストが存在するかどうかを確認します。
     * @return テキストが存在しない場合はtrue、存在する場合はfalse。
     */
    fun isClipboardTextEmpty(): Boolean {
        if (!clipboard.hasPrimaryClip()) return true
        val clipData = clipboard.primaryClip ?: return true
        for (i in 0 until clipData.itemCount) {
            val text = clipData.getItemAt(i).text
            if (!text.isNullOrBlank()) return false
        }
        return true
    }

    /**
     * 指定されたテキストをクリップボードにコピーします。
     * @param text コピーするテキスト。
     */
    fun setClipBoard(text: String) {
        val clip = ClipData.newPlainText("copied text", text)
        clipboard.setPrimaryClip(clip)
    }

    /**
     * クリップボードの内容を消去します。
     */
    fun clearClipboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    /**
     * クリップボードから最初のテキスト項目を取得します。
     * @return クリップボードのテキスト。存在しない場合はnull。
     */
    fun getFirstClipboardTextOrNull(): String? {
        if (!clipboard.hasPrimaryClip()) return null
        val clipData = clipboard.primaryClip ?: return null
        for (i in 0 until clipData.itemCount) {
            val text = clipData.getItemAt(i).text?.toString()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    /**
     * クリップボードから画像を取得します。
     * アプリによってはMIMEタイプが正しく設定されていない場合があるため、
     * MIMEタイプのチェックは参考程度とし、URIからのデコードを試みます。
     *
     * @return 成功した場合はBitmapオブジェクト、失敗した場合はnull。
     */
    fun getClipboardImageBitmap(): Bitmap? {
        if (!clipboard.hasPrimaryClip()) {
            return null
        }
        val clipData = clipboard.primaryClip ?: return null
        if (clipData.itemCount == 0) {
            return null
        }

        for (i in 0 until clipData.itemCount) {
            val item = clipData.getItemAt(i)
            val uri = item.uri
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        return BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    Timber.e("ClipboardUtil", "URIからのBitmapデコードに失敗しました: $uri", e)
                    continue // 次のアイテムを試す
                }
            }
        }

        return null
    }
}
