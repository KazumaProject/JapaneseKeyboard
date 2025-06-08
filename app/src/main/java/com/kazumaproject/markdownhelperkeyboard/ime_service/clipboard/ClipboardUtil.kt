package com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build

class ClipboardUtil(private val context: Context) {

    fun isClipboardTextEmpty(): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return true

        val clipData = clipboard.primaryClip ?: return true

        for (i in 0 until clipData.itemCount) {
            val text = clipData.getItemAt(i).text
            if (!text.isNullOrBlank()) return false
        }

        return true // No valid text found
    }

    fun setClipBoard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("copied text", text)
        clipboard.setPrimaryClip(clip)
    }

    fun clearClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    fun getFirstClipboardTextOrNull(): String? {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip ?: return null

        for (i in 0 until clipData.itemCount) {
            val text = clipData.getItemAt(i).text?.toString()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    fun getClipboardImageBitmap(): Bitmap? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return null

        val clipData = clipboard.primaryClip ?: return null
        if (clipData.itemCount == 0) return null

        val item = clipData.getItemAt(0)
        val uri = item.uri ?: return null

        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null && mimeType.startsWith("image/")) {
            return try {
                val inputStream = context.contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        return null
    }
}
