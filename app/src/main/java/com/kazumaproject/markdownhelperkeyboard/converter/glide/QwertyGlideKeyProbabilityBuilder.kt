package com.kazumaproject.markdownhelperkeyboard.converter.glide

import com.kazumaproject.qwerty_keyboard.glide.QwertyKeyboardProximityInfo
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln

data class PointKeyProbability(
    val char: Char,
    val cost: Float
)

class QwertyGlideKeyProbabilityBuilder(
    private val options: QwertyGlideDecodeOptions = QwertyGlideDecodeOptions()
) {
    fun build(
        stroke: NormalizedGlideStroke,
        proximityInfo: QwertyKeyboardProximityInfo
    ): List<List<PointKeyProbability>> {
        val keyByChar = proximityInfo.keys.associateBy { it.char }
        val scale = hypot(
            proximityInfo.averageKeyWidth / proximityInfo.keyboardWidth.coerceAtLeast(1).toFloat(),
            proximityInfo.averageKeyHeight / proximityInfo.keyboardHeight.coerceAtLeast(1).toFloat()
        ).coerceAtLeast(0.01f)

        return stroke.points.map { point ->
            proximityInfo.keys
                .asSequence()
                .map { key ->
                    val keyX = key.centerX / proximityInfo.keyboardWidth.coerceAtLeast(1).toFloat()
                    val keyY = key.centerY / proximityInfo.keyboardHeight.coerceAtLeast(1).toFloat()
                    val distance = hypot(point.x - keyX, point.y - keyY)
                    val neighborBoost = key.neighborChars
                        .mapNotNull { keyByChar[it] }
                        .minOfOrNull { neighbor ->
                            val nx = neighbor.centerX / proximityInfo.keyboardWidth.coerceAtLeast(1).toFloat()
                            val ny = neighbor.centerY / proximityInfo.keyboardHeight.coerceAtLeast(1).toFloat()
                            hypot(point.x - nx, point.y - ny)
                        }
                        ?.takeIf { it < distance }
                        ?.let { 0.08f }
                        ?: 0f
                    val probability = exp(-((distance / scale) * (distance / scale)).toDouble())
                        .toFloat()
                        .coerceAtLeast(0.0001f)
                    key.char to (-ln(probability) - neighborBoost).coerceAtLeast(0f)
                }
                .sortedBy { (_, cost) -> cost }
                .take(options.pointKeyTopK)
                .map { (char, cost) -> PointKeyProbability(char, cost) }
                .toList()
        }
    }
}
