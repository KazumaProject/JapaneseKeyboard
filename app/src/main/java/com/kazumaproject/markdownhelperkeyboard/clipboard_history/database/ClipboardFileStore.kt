package com.kazumaproject.markdownhelperkeyboard.clipboard_history.database

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardFileStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val rootDir = File(context.filesDir, "clipboard_history")
    private val textDir = File(rootDir, "text")
    private val imageDir = File(rootDir, "image")

    init {
        if (!textDir.exists()) textDir.mkdirs()
        if (!imageDir.exists()) imageDir.mkdirs()
    }

    fun saveText(content: String): String {
        val fileName = "clipboard_${UUID.randomUUID()}.txt"
        val file = File(textDir, fileName)
        file.writeText(content)
        return file.absolutePath
    }

    fun readText(path: String): String? {
        val file = File(path)
        return if (file.exists()) file.readText() else null
    }

    fun saveImage(bitmap: Bitmap): String {
        val fileName = "clipboard_${UUID.randomUUID()}.png"
        val file = File(imageDir, fileName)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    fun readImage(path: String): Bitmap? {
        val file = File(path)
        return if (file.exists()) BitmapFactory.decodeFile(path) else null
    }

    fun deleteFile(path: String?) {
        if (path == null) return
        val file = File(path)
        if (file.exists()) file.delete()
    }

    fun deleteAllFiles() {
        textDir.deleteRecursively()
        imageDir.deleteRecursively()
        textDir.mkdirs()
        imageDir.mkdirs()
    }
}
