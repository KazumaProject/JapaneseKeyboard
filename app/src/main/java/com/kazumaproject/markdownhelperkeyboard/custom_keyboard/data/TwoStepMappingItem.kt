package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import java.util.UUID

data class TwoStepMappingItem(
    val id: String = UUID.randomUUID().toString(),
    val first: TfbiFlickDirection,
    val second: TfbiFlickDirection,
    val output: String
) {
    companion object {
        val ALLOWED_TWO_STEP_PAIRS: Set<Pair<TfbiFlickDirection, TfbiFlickDirection>> = setOf(
            Pair(TfbiFlickDirection.TAP, TfbiFlickDirection.TAP),
            Pair(TfbiFlickDirection.UP_LEFT, TfbiFlickDirection.UP_LEFT),
            Pair(TfbiFlickDirection.DOWN_LEFT, TfbiFlickDirection.DOWN_LEFT),
            Pair(TfbiFlickDirection.UP_RIGHT, TfbiFlickDirection.UP_RIGHT),
            Pair(TfbiFlickDirection.DOWN_RIGHT, TfbiFlickDirection.DOWN_RIGHT),
            Pair(TfbiFlickDirection.LEFT, TfbiFlickDirection.LEFT),
            Pair(TfbiFlickDirection.LEFT, TfbiFlickDirection.UP_LEFT),
            Pair(TfbiFlickDirection.LEFT, TfbiFlickDirection.DOWN_LEFT),
            Pair(TfbiFlickDirection.RIGHT, TfbiFlickDirection.RIGHT),
            Pair(TfbiFlickDirection.RIGHT, TfbiFlickDirection.UP_RIGHT),
            Pair(TfbiFlickDirection.RIGHT, TfbiFlickDirection.DOWN_RIGHT),
            Pair(TfbiFlickDirection.UP, TfbiFlickDirection.UP),
            Pair(TfbiFlickDirection.UP, TfbiFlickDirection.UP_LEFT),
            Pair(TfbiFlickDirection.UP, TfbiFlickDirection.UP_RIGHT),
            Pair(TfbiFlickDirection.DOWN, TfbiFlickDirection.DOWN),
            Pair(TfbiFlickDirection.DOWN, TfbiFlickDirection.DOWN_LEFT),
            Pair(TfbiFlickDirection.DOWN, TfbiFlickDirection.DOWN_RIGHT)
        )

        val CARDINAL_DIRECTIONS = setOf(
            TfbiFlickDirection.TAP,
            TfbiFlickDirection.UP,
            TfbiFlickDirection.DOWN,
            TfbiFlickDirection.LEFT,
            TfbiFlickDirection.RIGHT
        )
    }
}
