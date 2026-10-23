package com.kazumaproject.markdownhelperkeyboard.setting_activity

import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

/** A blank keyboard inherits the normal selection; background modes are mutually exclusive. */
data class OrientationKeyboardSettings(
    val keyboard: String = "",
    val background: String = "common",
    val imageUri: String = "",
    val videoUri: String = ""
) {
    fun keyboardType(order: List<KeyboardType>): KeyboardType? =
        KeyboardType.entries.firstOrNull { it.name == keyboard && it in order }

    fun image(common: String): String = when (background) {
        "common" -> common
        "image" -> imageUri
        else -> ""
    }

    fun video(common: String): String = when (background) {
        "common" -> common
        "video" -> videoUri
        else -> ""
    }

    companion object {
        fun key(landscape: Boolean, field: String): String =
            "orientation_${if (landscape) "landscape" else "portrait"}_${field}_preference"
    }
}
