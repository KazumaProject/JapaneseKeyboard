package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.graphics.*
import kotlin.math.ceil

/**
 * Six native color-font stickers rasterized once per effect view. Rendering a transformed COLRv1
 * glyph every frame rebuilds font atlas images on RenderThread; a fixed high-resolution source
 * keeps all existing trajectories/scales/opacity without that work. No input text is cached.
 */
internal class FeverStickerCache(private val textSize: Float) {
    private val glyphs = arrayOf("✨", "💖", "🌠", "🚀", "💎", "😍")
    private data class Sticker(val bitmap: Bitmap, val left: Float, val top: Float)
    private val images = arrayOfNulls<Sticker>(6)
    private val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER; this.textSize = this@FeverStickerCache.textSize
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()

    fun draw(canvas: Canvas, kind: Int, size: Float, alpha: Int) {
        val sticker = images[kind] ?: run {
            // Rasterize with generous bearings, then keep only ink plus a transparent filter margin.
            val side = ceil(textSize * 2).toInt()
            val original = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val center = side / 2f
            Canvas(original).drawText(glyphs[kind], center,
                center - (sourcePaint.ascent() + sourcePaint.descent()) / 2, sourcePaint)
            val pixels = IntArray(side * side)
            original.getPixels(pixels, 0, side, 0, 0, side, side)
            var left = side; var top = side; var right = 0; var bottom = 0
            for (y in 0 until side) for (x in 0 until side) {
                if (pixels[y * side + x] ushr 24 != 0) {
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
                }
            }
            val bitmap: Bitmap
            if (right > left && bottom > top) {
                left = (left - 2).coerceAtLeast(0); top = (top - 2).coerceAtLeast(0)
                right = (right + 2).coerceAtMost(side); bottom = (bottom + 2).coerceAtMost(side)
                bitmap = Bitmap.createBitmap(original, left, top, right - left, bottom - top)
                if (bitmap !== original) original.recycle() // This temporary source was never submitted to a GPU.
            } else { bitmap = original; left = 0; top = 0 }
            Sticker(bitmap, left - center, top - center).also { images[kind] = it }
        }
        val scale = size / textSize
        destination.set(sticker.left * scale, sticker.top * scale,
            (sticker.left + sticker.bitmap.width) * scale, (sticker.top + sticker.bitmap.height) * scale)
        imagePaint.alpha = alpha
        canvas.drawBitmap(sticker.bitmap, null, destination, imagePaint)
    }

    fun clear() { images.fill(null) }
    internal val bytes get() = images.sumOf { it?.bitmap?.allocationByteCount ?: 0 }
}
