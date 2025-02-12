package com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

class ClipboardUtil(private val context: Context) {
    fun isClipboardEmpty(): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return !clipboard.hasPrimaryClip() || clipboard.primaryClip?.itemCount == 0
    }

    fun clearClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    fun getAllClipboardTexts(): List<Candidate> {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val texts = mutableListOf<String>()

        if (clipboardManager.hasPrimaryClip()) {
            val clipData = clipboardManager.primaryClip
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i).text?.let {
                        if (it.isNotBlank() && it.isNotEmpty()) texts.add(it.toString())
                    }
                }
            }
        }
        return texts.map {
            Candidate(
                string = it,
                type = (28).toByte(),
                it.length.toUByte(),
                0
            )
        }
    }
}