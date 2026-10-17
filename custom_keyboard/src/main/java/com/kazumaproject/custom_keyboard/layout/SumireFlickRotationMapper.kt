package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.TfbiFlickRotation
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

/**
 * Moves voiced/semivoiced candidates to the selected side of their first-flick vowel axis.
 * Cardinal candidates and their child stages (including yoon and long vowels) stay fixed.
 */
internal object SumireFlickRotationMapper {
    private val diacriticLabels = setOf("か", "さ", "た", "は")

    fun apply(layout: KeyboardLayout, rotation: TfbiFlickRotation): KeyboardLayout {
        if (rotation == TfbiFlickRotation.DEFAULT) return layout
        return layout.copy(
            twoStepFlickKeyMaps = layout.twoStepFlickKeyMaps.mapValues { (label, firstMap) ->
                if (label !in diacriticLabels) firstMap else firstMap.mapValues { (first, secondMap) ->
                    if (shouldSwap(first, rotation)) swapMap(secondMap, first) else secondMap
                }
            }
        )
    }

    private fun shouldSwap(first: TfbiFlickDirection, rotation: TfbiFlickRotation): Boolean =
        when (rotation) {
            TfbiFlickRotation.DEFAULT -> false
            TfbiFlickRotation.CLOCKWISE -> first == TfbiFlickDirection.LEFT ||
                    first == TfbiFlickDirection.UP || first == TfbiFlickDirection.DOWN
            TfbiFlickRotation.COUNTERCLOCKWISE -> first == TfbiFlickDirection.RIGHT
        }





    private fun <T> swapMap(
        map: Map<TfbiFlickDirection, T>,
        first: TfbiFlickDirection
    ): Map<TfbiFlickDirection, T> = map.entries.associate { (direction, value) ->
        swapDiagonal(direction, first) to value
    }

    private fun swapDiagonal(
        direction: TfbiFlickDirection,
        first: TfbiFlickDirection
    ): TfbiFlickDirection = when (first) {
        TfbiFlickDirection.LEFT -> when (direction) {
            TfbiFlickDirection.UP_LEFT -> TfbiFlickDirection.DOWN_LEFT
            TfbiFlickDirection.DOWN_LEFT -> TfbiFlickDirection.UP_LEFT
            else -> direction
        }
        TfbiFlickDirection.RIGHT -> when (direction) {
            TfbiFlickDirection.UP_RIGHT -> TfbiFlickDirection.DOWN_RIGHT
            TfbiFlickDirection.DOWN_RIGHT -> TfbiFlickDirection.UP_RIGHT
            else -> direction
        }
        TfbiFlickDirection.UP -> when (direction) {
            TfbiFlickDirection.UP_LEFT -> TfbiFlickDirection.UP_RIGHT
            TfbiFlickDirection.UP_RIGHT -> TfbiFlickDirection.UP_LEFT
            else -> direction
        }
        TfbiFlickDirection.DOWN -> when (direction) {
            TfbiFlickDirection.DOWN_LEFT -> TfbiFlickDirection.DOWN_RIGHT
            TfbiFlickDirection.DOWN_RIGHT -> TfbiFlickDirection.DOWN_LEFT
            else -> direction
        }
        else -> direction
    }
}
