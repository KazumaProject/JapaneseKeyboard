package com.kazumaproject.custom_keyboard.view

import com.kazumaproject.custom_keyboard.data.PopupActionType
import com.kazumaproject.custom_keyboard.data.PopupMap
import com.kazumaproject.custom_keyboard.data.distanceTo
import kotlin.math.atan2
import kotlin.math.roundToInt

internal object FlickGuideLabelMapper {
    private val displayDirections = listOf(
        TfbiFlickDirection.TAP,
        TfbiFlickDirection.UP,
        TfbiFlickDirection.UP_RIGHT,
        TfbiFlickDirection.RIGHT,
        TfbiFlickDirection.DOWN_RIGHT,
        TfbiFlickDirection.DOWN,
        TfbiFlickDirection.DOWN_LEFT,
        TfbiFlickDirection.LEFT,
        TfbiFlickDirection.UP_LEFT
    )

    fun sanitizeGuideText(value: String, maxCodePoints: Int): String? {
        if (value.isEmpty()) return null
        val max = maxCodePoints.coerceIn(1, 4)
        val actualCodePoints = value.codePointCount(0, value.length)
        val takeCodePoints = minOf(max, actualCodePoints)
        val endIndex = value.offsetByCodePoints(0, takeCodePoints)
        return value.substring(0, endIndex)
    }

    fun buildTwoStepRootGuideLabels(
        twoStepMap: Map<TfbiFlickDirection, Map<TfbiFlickDirection, String>>,
        maxCodePoints: Int
    ): AutoSizeButton.FlickGuideLabels {
        fun labelFor(direction: TfbiFlickDirection): String {
            val secondMap = twoStepMap[direction] ?: return ""
            val direct = secondMap[direction].orEmpty()
            val fallback = secondMap[TfbiFlickDirection.TAP].orEmpty()
            return sanitizeGuideText(direct.ifEmpty { fallback }, maxCodePoints).orEmpty()
        }

        return labelsFromDirections(displayDirections.associateWith { labelFor(it) })
    }

    fun buildFreeFlickRootGuideLabels(map: PopupMap, maxCodePoints: Int): AutoSizeButton.FlickGuideLabels {
        val tap = sanitizeGuideText(map.tapText, maxCodePoints).orEmpty()
        val labels = mutableMapOf(TfbiFlickDirection.TAP to tap)
        // The key has one guide slot per direction. Prefer the closest region in key-relative coordinates.
        for (region in map.regions.sortedBy { it.distanceTo(0f, 0f, 1f, 1f) }) {
            val action = region.action
            if (action.type == PopupActionType.BACK ||
                (action.type == PopupActionType.TEXT && action.text.isEmpty())
            ) {
                continue
            }
            val label = sanitizeGuideText(region.displayLabel(), maxCodePoints) ?: continue
            val bounds = region.bounds
            val direction = directionForOffset(
                (bounds.left + bounds.right) / 2f,
                (bounds.top + bounds.bottom) / 2f
            )
            labels.putIfAbsent(direction, label)
        }
        return labelsFromDirections(labels)
    }

    fun directionForOffset(x: Float, y: Float): TfbiFlickDirection {
        val octant = (atan2(y, x) * 4 / Math.PI).roundToInt()
        return when (octant.mod(8)) {
            0 -> TfbiFlickDirection.RIGHT
            1 -> TfbiFlickDirection.DOWN_RIGHT
            2 -> TfbiFlickDirection.DOWN
            3 -> TfbiFlickDirection.DOWN_LEFT
            4 -> TfbiFlickDirection.LEFT
            5 -> TfbiFlickDirection.UP_LEFT
            6 -> TfbiFlickDirection.UP
            else -> TfbiFlickDirection.UP_RIGHT
        }
    }

    private fun labelsFromDirections(
        labels: Map<TfbiFlickDirection, String>
    ): AutoSizeButton.FlickGuideLabels {
        return AutoSizeButton.FlickGuideLabels(
            tap = labels[TfbiFlickDirection.TAP].orEmpty(),
            up = labels[TfbiFlickDirection.UP].orEmpty(),
            upRight = labels[TfbiFlickDirection.UP_RIGHT].orEmpty(),
            right = labels[TfbiFlickDirection.RIGHT].orEmpty(),
            downRight = labels[TfbiFlickDirection.DOWN_RIGHT].orEmpty(),
            down = labels[TfbiFlickDirection.DOWN].orEmpty(),
            downLeft = labels[TfbiFlickDirection.DOWN_LEFT].orEmpty(),
            left = labels[TfbiFlickDirection.LEFT].orEmpty(),
            upLeft = labels[TfbiFlickDirection.UP_LEFT].orEmpty()
        )
    }
}
