package com.kazumaproject.custom_keyboard.data

enum class CircularFlickDirection {
    TAP,
    SLOT_0,
    SLOT_1,
    SLOT_2,
    SLOT_3,
    SLOT_4,
    SLOT_5,
    SLOT_6;

    companion object {
        fun slots(count: Int): List<CircularFlickDirection> {
            return when (count.coerceIn(4, 7)) {
                4 -> listOf(SLOT_0, SLOT_1, SLOT_2, SLOT_3)
                5 -> listOf(SLOT_0, SLOT_1, SLOT_2, SLOT_3, SLOT_4)
                6 -> listOf(SLOT_0, SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5)
                else -> listOf(SLOT_0, SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5, SLOT_6)
            }
        }
    }
}

fun normalizeAngle(angle: Float): Float {
    var normalized = angle % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}

fun buildEvenCircularRanges(
    count: Int,
    startOffset: Float = 270f
): Map<CircularFlickDirection, Pair<Float, Float>> {
    val normalizedCount = count.coerceIn(4, 7)
    val sweep = 360f / normalizedCount
    val firstStart = normalizeAngle(startOffset - sweep / 2f)
    return CircularFlickDirection.slots(normalizedCount).mapIndexed { index, direction ->
        direction to (normalizeAngle(firstStart + sweep * index) to sweep)
    }.toMap()
}

fun getDirectionForAngle(
    angle: Float,
    ranges: Map<CircularFlickDirection, Pair<Float, Float>>
): CircularFlickDirection {
    val normalizedAngle = normalizeAngle(angle)
    return ranges.entries.firstOrNull { (_, range) ->
        isAngleInCircularRange(normalizedAngle, range)
    }?.key ?: CircularFlickDirection.TAP
}

fun isAngleInCircularRange(
    angle: Float,
    segment: Pair<Float, Float>
): Boolean {
    val start = normalizeAngle(segment.first)
    val sweep = segment.second
    if (sweep >= 360f) return true
    val end = start + sweep
    val normalizedAngle = normalizeAngle(angle)
    return if (end > 360f) {
        normalizedAngle >= start || normalizedAngle < normalizeAngle(end)
    } else {
        normalizedAngle >= start && normalizedAngle < end
    }
}

fun FlickDirection.toCircularFlickDirection(): CircularFlickDirection {
    return when (this) {
        FlickDirection.TAP -> CircularFlickDirection.TAP
        FlickDirection.UP -> CircularFlickDirection.SLOT_0
        FlickDirection.UP_RIGHT_FAR -> CircularFlickDirection.SLOT_1
        FlickDirection.DOWN -> CircularFlickDirection.SLOT_2
        FlickDirection.UP_LEFT_FAR -> CircularFlickDirection.SLOT_3
        FlickDirection.UP_RIGHT -> CircularFlickDirection.SLOT_4
        FlickDirection.UP_LEFT -> CircularFlickDirection.SLOT_5
    }
}

fun CircularFlickDirection.toLegacyFlickDirection(): FlickDirection {
    return when (this) {
        CircularFlickDirection.TAP -> FlickDirection.TAP
        CircularFlickDirection.SLOT_0 -> FlickDirection.UP
        CircularFlickDirection.SLOT_1 -> FlickDirection.UP_RIGHT_FAR
        CircularFlickDirection.SLOT_2 -> FlickDirection.DOWN
        CircularFlickDirection.SLOT_3 -> FlickDirection.UP_LEFT_FAR
        CircularFlickDirection.SLOT_4 -> FlickDirection.UP_RIGHT
        CircularFlickDirection.SLOT_5 -> FlickDirection.UP_LEFT
        CircularFlickDirection.SLOT_6 -> FlickDirection.UP_LEFT
    }
}

fun Map<FlickDirection, FlickAction>.toCircularFlickMap(): Map<CircularFlickDirection, FlickAction> {
    return entries.associate { (direction, action) ->
        direction.toCircularFlickDirection() to action
    }
}

fun Map<String, List<Map<FlickDirection, FlickAction>>>.toCircularFlickKeyMaps():
    Map<String, List<Map<CircularFlickDirection, FlickAction>>> {
    return mapValues { (_, states) -> states.map { it.toCircularFlickMap() } }
}
