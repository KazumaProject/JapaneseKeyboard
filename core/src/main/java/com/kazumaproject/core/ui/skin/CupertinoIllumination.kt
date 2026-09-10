package com.kazumaproject.core.ui.skin

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Shader
import kotlin.math.roundToInt

/** Cache each immutable material field; opening a popup only creates its local shader matrix. */
internal object CupertinoIllumination {
    private const val SIZE = 64
    private val bitmaps = mutableMapOf<FloatArray, Bitmap>()
    private val powers = Array(SIZE) { position ->
        DoubleArray(7).also { values ->
            values[0] = 1.0
            val coordinate = (position + .5) / SIZE * 2 - 1
            for (degree in 1..6) values[degree] = values[degree - 1] * coordinate
        }
    }

    fun shader(coefficients: FloatArray, width: Int, height: Int, channels: Int = 1): Shader {
        val bitmap = synchronized(bitmaps) {
            bitmaps.getOrPut(coefficients) {
                val pixels = IntArray(SIZE * SIZE)
                for (row in 0 until SIZE) for (column in 0 until SIZE) {
                    val values = DoubleArray(3)
                    var index = 0
                    for (i in 0..6) for (j in 0..6-i) {
                        val basis = powers[column][i] * powers[row][j]
                        for (channel in 0 until channels) values[channel] += coefficients[index++] * basis
                    }
                    fun channel(index: Int) = values[if (channels == 1) 0 else index].roundToInt().coerceIn(0, 255)
                    pixels[row * SIZE + column] = Color.rgb(channel(0), channel(1), channel(2))
                }
                Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
            }
        }
        return BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(width.toFloat() / SIZE, height.toFloat() / SIZE) })
        }
    }
}
