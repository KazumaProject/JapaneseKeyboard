package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.content.Context

fun Context.copyFile(filename: String) {
    this.assets.open(filename).use { stream ->
        this.openFileOutput(filename,Context.MODE_PRIVATE).use {
            stream.copyTo(it)
        }
    }
}