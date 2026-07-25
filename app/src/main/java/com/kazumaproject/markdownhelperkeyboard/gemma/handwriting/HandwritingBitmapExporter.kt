package com.kazumaproject.markdownhelperkeyboard.gemma.handwriting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object HandwritingBitmapExporter {
    private const val DEFAULT_WIDTH = 768
    private const val DEFAULT_HEIGHT = 384
    private const val MAX_INK_WIDTH_RATIO = 0.90f
    private const val MAX_INK_HEIGHT_RATIO = 0.60f

    fun createBitmap(
        strokes: List<HandwritingStroke>,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        penSizeDp: Int = GemmaHandwritingSettings.DEFAULT_PEN_SIZE_DP,
        strokeColor: Int = Color.BLACK,
    ): Bitmap {
        require(strokes.isNotEmpty()) { "At least one handwriting stroke is required." }
        require(width > 0 && height > 0) { "Bitmap dimensions must be positive." }

        val allPoints = strokes.flatMap(HandwritingStroke::points)
        require(allPoints.isNotEmpty()) { "At least one handwriting point is required." }

        val minX = allPoints.minOf { it.x }
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOf { it.y }
        val maxY = allPoints.maxOf { it.y }
        val sourceWidth = max(maxX - minX, 0.04f)
        val sourceHeight = max(maxY - minY, 0.04f)
        val availableWidth = max(1f, width * MAX_INK_WIDTH_RATIO)
        val availableHeight = max(1f, height * MAX_INK_HEIGHT_RATIO)
        val scale = min(availableWidth / sourceWidth, availableHeight / sourceHeight)
        val sourceCenterX = (minX + maxX) / 2f
        val sourceCenterY = (minY + maxY) / 2f
        val offsetX = width / 2f - sourceCenterX * scale
        val offsetY = height / 2f - sourceCenterY * scale

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(contrastingBackgroundFor(strokeColor))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = strokeColor
            val configuredPenScale = sqrt(
                penSizeDp.coerceIn(
                    GemmaHandwritingSettings.MIN_PEN_SIZE_DP,
                    GemmaHandwritingSettings.MAX_PEN_SIZE_DP,
                ).toFloat() / GemmaHandwritingSettings.DEFAULT_PEN_SIZE_DP,
            ).coerceIn(MIN_CONFIGURED_PEN_SCALE, MAX_CONFIGURED_PEN_SCALE)
            strokeWidth = (
                min(width, height) *
                    DEFAULT_STROKE_WIDTH_RATIO *
                    configuredPenScale
                ).coerceAtLeast(MIN_STROKE_WIDTH_PX)
        }

        strokes.forEach { stroke ->
            drawStroke(
                canvas = canvas,
                paint = paint,
                points = stroke.points,
                mapX = { point -> point * scale + offsetX },
                mapY = { point -> point * scale + offsetY },
            )
        }
        return bitmap
    }

    fun writePng(
        strokes: List<HandwritingStroke>,
        target: File,
        penSizeDp: Int = GemmaHandwritingSettings.DEFAULT_PEN_SIZE_DP,
        strokeColor: Int = Color.BLACK,
    ): File {
        target.parentFile?.mkdirs()
        val bitmap = createBitmap(
            strokes = strokes,
            penSizeDp = penSizeDp,
            strokeColor = strokeColor,
        )
        try {
            FileOutputStream(target).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Failed to encode handwriting image."
                }
            }
        } finally {
            bitmap.recycle()
        }
        return target
    }

    private fun contrastingBackgroundFor(strokeColor: Int): Int {
        val red = Color.red(strokeColor) / 255.0
        val green = Color.green(strokeColor) / 255.0
        val blue = Color.blue(strokeColor) / 255.0
        val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
        return if (luminance > 0.55) Color.BLACK else Color.WHITE
    }

    internal fun drawStroke(
        canvas: Canvas,
        paint: Paint,
        points: List<HandwritingPoint>,
        mapX: (Float) -> Float,
        mapY: (Float) -> Float,
    ) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            val point = points.first()
            canvas.drawCircle(
                mapX(point.x),
                mapY(point.y),
                paint.strokeWidth / 2f,
                paint.apply { style = Paint.Style.FILL },
            )
            paint.style = Paint.Style.STROKE
            return
        }

        val path = Path()
        val first = points.first()
        path.moveTo(mapX(first.x), mapY(first.y))
        if (points.size == 2) {
            val last = points.last()
            path.lineTo(mapX(last.x), mapY(last.y))
        } else {
            for (index in 1 until points.lastIndex) {
                val current = points[index]
                val next = points[index + 1]
                path.quadTo(
                    mapX(current.x),
                    mapY(current.y),
                    (mapX(current.x) + mapX(next.x)) / 2f,
                    (mapY(current.y) + mapY(next.y)) / 2f,
                )
            }
            val last = points.last()
            path.lineTo(mapX(last.x), mapY(last.y))
        }
        canvas.drawPath(path, paint)
    }

    private const val DEFAULT_STROKE_WIDTH_RATIO = 0.018f
    private const val MIN_CONFIGURED_PEN_SCALE = 0.65f
    private const val MAX_CONFIGURED_PEN_SCALE = 1.50f
    private const val MIN_STROKE_WIDTH_PX = 2f
}
