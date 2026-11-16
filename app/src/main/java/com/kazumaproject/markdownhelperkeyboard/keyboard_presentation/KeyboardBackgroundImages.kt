package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.lang.ref.WeakReference
import java.security.MessageDigest

/** Shares decoded image pixels between the IME and previews without retaining unused images. */
internal object KeyboardBackgroundImages {
    private val images = LinkedHashMap<String, WeakReference<Bitmap>>()
    fun load(context: Context, uri: String): Bitmap? {
        val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } ?: return null
        // Content identity also handles providers which replace an image at the same URI.
        val key = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return synchronized(images) {
            images[key]?.get()?.takeUnless { it.isRecycled } ?: BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also {
                images[key] = WeakReference(it)
                while (images.size > 2) images.remove(images.keys.first())
            }
        }
    }
}
