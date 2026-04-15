package com.kazumaproject.markdownhelperkeyboard.clipboard_history.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardFileStore
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemType
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.dto.ClipboardHistoryDto
import com.kazumaproject.markdownhelperkeyboard.repository.ClipboardHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class ClipboardHistoryViewModel @Inject constructor(
    private val repository: ClipboardHistoryRepository,
    private val fileStore: ClipboardFileStore
) : ViewModel() {

    val allHistory = repository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 消さずに維持: 単純なメタデータ更新
    fun update(item: ClipboardHistoryItem) = viewModelScope.launch {
        repository.update(item)
    }

    // 本文内容の更新（ファイル上書きとプレビュー生成を含む）
    fun updateTextContent(item: ClipboardHistoryItem, newText: String) = viewModelScope.launch {
        repository.updateTextContent(item, newText)
    }

    // ファイルも削除するように repository 経由で実行
    fun delete(id: Long) = viewModelScope.launch {
        repository.deleteById(id)
    }

    fun deleteAll() = viewModelScope.launch {
        repository.deleteAll()
    }

    // --- 実データの取得 ---

    suspend fun getFullText(item: ClipboardHistoryItem): String {
        return repository.getFullText(item)
    }

    // Fragmentのダイアログ表示用
    suspend fun getFullImage(item: ClipboardHistoryItem): Bitmap? {
        val content = repository.getFullContent(item)
        return if (content is ClipboardItem.Image) content.bitmap else null
    }

    // --- インポート / エクスポート ---

    /**
     * JSONから実データ(Base64)を復元し、ファイル保存し直してDBへ追加
     */
    fun importFromJson(jsonString: String): Int {
        val type = object : TypeToken<List<ClipboardHistoryDto>>() {}.type
        val dtoList: List<ClipboardHistoryDto> = try {
            Gson().fromJson(jsonString, type)
        } catch (e: Exception) {
            emptyList()
        }

        viewModelScope.launch {
            dtoList.forEach { dto ->
                val clipboardItem = when (dto.itemType) {
                    ItemType.TEXT -> {
                        // 新規作成扱いのため id には 0L を指定
                        if (dto.textData != null) ClipboardItem.Text(
                            id = 0L,
                            text = dto.textData
                        ) else null
                    }

                    ItemType.IMAGE -> {
                        dto.imageDataBase64?.let { base64 ->
                            // 新規作成扱いのため id には 0L を指定
                            ClipboardItem.Image(id = 0L, bitmap = base64ToBitmap(base64))
                        }
                    }
                }
                clipboardItem?.let { repository.insertClipboardItem(it, dto.isPinned) }
            }
        }
        return dtoList.size
    }

    /**
     * ファイルから実データを取得し、JSONとして書き出す
     */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val currentList = repository.getHistorySnapshot()
        val dtoList = currentList.map { entity ->
            val fullContent = repository.getFullContent(entity)
            ClipboardHistoryDto(
                itemType = entity.itemType,
                textData = if (fullContent is ClipboardItem.Text) fullContent.text else null,
                imageDataBase64 = if (fullContent is ClipboardItem.Image) bitmapToBase64(fullContent.bitmap) else null,
                timestamp = entity.timestamp,
                isPinned = entity.isPinned
            )
        }
        Gson().toJson(dtoList)
    }

    // --- ユーティリティ (Base64) ---

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun base64ToBitmap(base64Str: String): Bitmap {
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }
}
