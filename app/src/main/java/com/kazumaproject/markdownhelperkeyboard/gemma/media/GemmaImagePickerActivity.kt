package com.kazumaproject.markdownhelperkeyboard.gemma.media

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Permission-free bridge between the IME service and Android's system photo picker.
 *
 * The picker grants temporary access only to the photo selected by the user. This activity copies
 * that photo into the app's private cache before returning it to the keyboard process.
 */
class GemmaImagePickerActivity : ComponentActivity() {
    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            finishWithResult(RESULT_CANCELLED)
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val selectedFile = runCatching {
                withContext(Dispatchers.IO) { copySelectedImage(uri) }
            }.getOrNull()
            if (selectedFile == null) {
                finishWithResult(RESULT_ERROR)
            } else {
                finishWithResult(RESULT_SELECTED, selectedFile.absolutePath)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resultReceiver() == null) {
            finish()
            return
        }
        if (savedInstanceState == null) {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    private fun copySelectedImage(uri: Uri): File {
        val extension = contentResolver.getType(uri)
            ?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
            ?.takeIf { value -> value.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?: "img"
        val directory = File(cacheDir, MEDIA_CACHE_DIRECTORY).apply { mkdirs() }
        val target = File(directory, "device_${System.currentTimeMillis()}.$extension")
        try {
            val input = checkNotNull(contentResolver.openInputStream(uri))
            input.use { source ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= MAX_IMAGE_BYTES)
                        output.write(buffer, 0, read)
                    }
                }
            }
            check(target.length() > 0L)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0)
            return target
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun finishWithResult(resultCode: Int, path: String? = null) {
        resultReceiver()?.send(
            resultCode,
            Bundle().apply {
                if (path != null) putString(KEY_IMAGE_PATH, path)
            },
        )
        finish()
    }

    @Suppress("DEPRECATION")
    private fun resultReceiver(): ResultReceiver? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }
    }

    companion object {
        const val EXTRA_RESULT_RECEIVER =
            "com.kazumaproject.markdownhelperkeyboard.extra.GEMMA_IMAGE_RESULT_RECEIVER"
        const val KEY_IMAGE_PATH =
            "com.kazumaproject.markdownhelperkeyboard.extra.GEMMA_IMAGE_PATH"
        const val RESULT_SELECTED = 1
        const val RESULT_CANCELLED = 0
        const val RESULT_ERROR = -1

        private const val MEDIA_CACHE_DIRECTORY = "gemma_media"
        private const val MAX_IMAGE_BYTES = 50L * 1024L * 1024L
    }
}
