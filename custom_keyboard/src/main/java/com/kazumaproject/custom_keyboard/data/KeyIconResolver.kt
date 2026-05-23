package com.kazumaproject.custom_keyboard.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import java.io.File

object KeyIconResolver {
    const val USER_ICON_DIRECTORY = "custom_key_icons"

    private val bitmapCache = object : LruCache<String, android.graphics.Bitmap>(32) {}

    fun hasIcon(keyData: KeyData): Boolean =
        keyData.isSpecialKey &&
                (keyData.icon?.isOverride() == true || keyData.drawableResId != null)

    fun hasIconOverride(keyData: KeyData): Boolean =
        keyData.icon?.isOverride() == true

    fun shouldTintIcon(icon: KeyIconRef?, fallbackResId: Int?): Boolean {
        return when (icon?.type) {
            KeyIconType.USER_IMAGE_FILE -> false
            KeyIconType.DRAWABLE_RESOURCE_NAME -> true
            KeyIconType.ACTION_DEFAULT,
            null -> fallbackResId != null
        }
    }

    fun shouldTintIcon(keyData: KeyData): Boolean =
        shouldTintIcon(keyData.icon, keyData.drawableResId)

    fun setImage(imageView: ImageView, keyData: KeyData) {
        val drawable = resolveDrawable(imageView.context, keyData.icon, keyData.drawableResId)
        if (drawable != null) {
            imageView.setImageDrawable(drawable)
        } else {
            imageView.setImageDrawable(null)
        }
    }

    fun resolveDrawable(context: Context, icon: KeyIconRef?, fallbackResId: Int?): Drawable? {
        val overrideDrawable = when (icon?.type) {
            KeyIconType.DRAWABLE_RESOURCE_NAME ->
                KeyIconBuiltInDrawable.resolve(icon.value)
                    ?.let { AppCompatResources.getDrawable(context, it) }

            KeyIconType.USER_IMAGE_FILE ->
                icon.value?.let { loadUserImageDrawable(context, it) }

            KeyIconType.ACTION_DEFAULT,
            null -> null
        }
        if (overrideDrawable != null) return overrideDrawable
        return fallbackResId?.let { AppCompatResources.getDrawable(context, it) }
    }

    private fun loadUserImageDrawable(context: Context, relativePath: String): Drawable? {
        val safePath = relativePath
            .takeIf { it.startsWith("$USER_ICON_DIRECTORY/") }
            ?.takeIf { !it.contains("..") }
            ?: return null
        val file = File(context.filesDir, safePath)
        if (!file.isFile) return null
        val key = file.absolutePath + ":" + file.lastModified()
        val bitmap = bitmapCache.get(key) ?: BitmapFactory.decodeFile(file.absolutePath)
            ?.also { bitmapCache.put(key, it) }
            ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }
}
