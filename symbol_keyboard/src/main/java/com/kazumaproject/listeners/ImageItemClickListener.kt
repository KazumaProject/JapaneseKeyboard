package com.kazumaproject.listeners

import android.graphics.Bitmap

fun interface ImageItemClickListener {
    fun onImageClick(bitmap: Bitmap)
}
