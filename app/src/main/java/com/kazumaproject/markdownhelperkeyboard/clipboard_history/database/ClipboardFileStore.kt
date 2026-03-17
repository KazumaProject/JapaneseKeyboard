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


    /**
     * 【新規】指定したサイズに収まるように縮小して読み込む (一覧表示用)
     */
    fun readImageThumbnail(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        return try {
            // 1. サイズだけを読み込む (inJustDecodeBounds = true)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            // 2. 縮小率 (Sample Size) を計算する
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // 3. 実際にデコードする
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 適切な縮小率を計算するロジック
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
