package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Places a deterministic image on the system clipboard for real-device IME testing and verifies
 * that the same clipboard decoding path used by the Gemma toolbar can read it.
 */
@RunWith(AndroidJUnit4::class)
class GemmaImeClipboardInstrumentedTest {

    @Test
    fun placesReadableImageOnClipboard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageDirectory = File(context.cacheDir, "images").apply { mkdirs() }
        val imageFile = File(imageDirectory, "gemma_ime_clipboard_test.png")
        val bitmap = Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText(
                "HELLO 42",
                bitmap.width / 2f,
                bitmap.height / 2f + 32f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 96f
                    textAlign = Paint.Align.CENTER
                },
            )
        }
        FileOutputStream(imageFile).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile,
        )
        val clipboardUtil = ClipboardUtil(context)
        clipboardUtil.setClipBoardUri(uri, label = "Gemma IME test image", isSensitive = false)

        // Android blocks clipboard reads from background instrumentation. Decode the exact URI
        // here; the subsequent end-to-end check reads it through ClipboardUtil while this package
        // is the active IME, where clipboard access is permitted.
        context.contentResolver.openInputStream(uri).use { input ->
            assertTrue(input != null && BitmapFactory.decodeStream(input) != null)
        }
    }

    @Test
    fun replacesClipboardImageWithPlainText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Gemma IME test", "plain text only"))
        assertTrue(clipboard.primaryClipDescription?.hasMimeType("text/plain") == true)
    }
}
