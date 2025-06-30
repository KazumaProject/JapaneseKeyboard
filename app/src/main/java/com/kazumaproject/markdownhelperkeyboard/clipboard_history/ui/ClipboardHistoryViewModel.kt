package com.kazumaproject.markdownhelperkeyboard.clipboard_history.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.dto.ClipboardHistoryDto
import com.kazumaproject.markdownhelperkeyboard.repository.ClipboardHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class ClipboardHistoryViewModel @Inject constructor(
    private val repository: ClipboardHistoryRepository
) : ViewModel() {

    val allHistory = repository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun update(item: ClipboardHistoryItem) = viewModelScope.launch {
        repository.update(item)
    }

    fun delete(id: Long) = viewModelScope.launch {
        repository.deleteById(id)
    }

    fun deleteAll() = viewModelScope.launch {
        repository.deleteAll()
    }

    fun importFromJson(jsonString: String): Int {
        val type = object : TypeToken<List<ClipboardHistoryDto>>() {}.type
        val dtoList: List<ClipboardHistoryDto> = Gson().fromJson(jsonString, type)

        val historyItems = dtoList.map { dto ->
            ClipboardHistoryItem(
                id = 0, // Roomが自動生成
                itemType = dto.itemType,
                textData = dto.textData,
                imageData = dto.imageDataBase64?.let { base64ToBitmap(it) },
                timestamp = dto.timestamp
            )
        }

        viewModelScope.launch {
            repository.insertAll(historyItems)
        }
        return historyItems.size
    }

    fun exportToJson(): String {
        val dtoList = allHistory.value.map { item ->
            ClipboardHistoryDto(
                itemType = item.itemType,
                textData = item.textData,
                imageDataBase64 = item.imageData?.let { bitmapToBase64(it) },
                timestamp = item.timestamp
            )
        }
        return Gson().toJson(dtoList)
    }

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
