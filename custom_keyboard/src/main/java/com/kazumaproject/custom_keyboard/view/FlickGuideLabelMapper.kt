package com.kazumaproject.custom_keyboard.view


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
