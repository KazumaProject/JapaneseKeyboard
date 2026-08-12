package com.kazumaproject.custom_keyboard.data

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/**
 * The coordinate grid shared by the guide renderer and the guide gesture resolver.
 *
 * Keeping this mapping in one place is important: the marker is drawn in this 3x3 grid, so
 * classification must use exactly the same cells.
 */
internal object TfbiGuideGrid {
    val directionLayout = listOf(
        listOf(TfbiFlickDirection.UP_LEFT, TfbiFlickDirection.UP, TfbiFlickDirection.UP_RIGHT),
        listOf(TfbiFlickDirection.LEFT, TfbiFlickDirection.TAP, TfbiFlickDirection.RIGHT),
        listOf(TfbiFlickDirection.DOWN_LEFT, TfbiFlickDirection.DOWN, TfbiFlickDirection.DOWN_RIGHT)
    )

    fun directionAt(column: Int, row: Int): TfbiFlickDirection =
        directionLayout[row][column]

    fun cellOf(direction: TfbiFlickDirection): Pair<Int, Int> =
        directionLayout
            .asSequence()
            .withIndex()
            .flatMap { (row, directions) ->
                directions.withIndex().map { (column, value) ->
                    Triple(row, column, value)
                }
            }
            .firstOrNull { it.third == direction }
            ?.let { it.second to it.first }
            ?: (1 to 1)
}
