package com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard

import android.content.ClipboardManager
import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

object ClipboardUtil {
    fun getAllClipboardTexts(context: Context): List<Candidate> {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val texts = mutableListOf<String>()

        if (clipboardManager.hasPrimaryClip()) {
            val clipData = clipboardManager.primaryClip
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i).text?.let { texts.add(it.toString()) }
                }
            }
        }
        return texts.map {
            Candidate(
                string = it,
                type = (26).toByte(),
                it.length.toUByte(),
                0
            )
        }
    }
}