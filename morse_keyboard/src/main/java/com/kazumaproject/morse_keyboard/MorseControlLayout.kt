package com.kazumaproject.morse_keyboard

/** Stable identifiers are persisted rather than screen positions or translated names. */
enum class MorseControl(val command: MorseCommand? = null) {
    BOUNDARY, DELETE(MorseCommand.DELETE), CONVERT(MorseCommand.CONVERT), ENTER(MorseCommand.ENTER),
    SMALL(MorseCommand.SMALL_KANA), KEYBOARD(MorseCommand.NEXT_KEYBOARD), SPACE(MorseCommand.SPACE),
    COMMIT(MorseCommand.COMMIT), LEFT(MorseCommand.CURSOR_LEFT), RIGHT(MorseCommand.CURSOR_RIGHT),
    MAP(MorseCommand.NEXT_MAP), DAKUTEN(MorseCommand.DAKUTEN), HANDAKUTEN(MorseCommand.HANDAKUTEN);

    val labelResource: Int get() = when (this) {
        BOUNDARY -> R.string.morse_boundary
        DELETE -> R.string.morse_delete
        CONVERT -> R.string.morse_convert
        ENTER -> R.string.morse_enter
        SMALL -> R.string.morse_small
        KEYBOARD -> R.string.morse_keyboard
        SPACE -> R.string.morse_space
        COMMIT -> R.string.morse_commit
        LEFT -> R.string.morse_left
        RIGHT -> R.string.morse_right
        MAP -> R.string.morse_map
        DAKUTEN -> R.string.morse_dakuten
        HANDAKUTEN -> R.string.morse_handakuten
    }
    val required: Boolean get() = this == BOUNDARY || this == KEYBOARD
}

enum class MorseControlPlacement { BOTTOM, TOP, LEFT, RIGHT, BOTH_SIDES;
    val isSide: Boolean get() = this == LEFT || this == RIGHT || this == BOTH_SIDES
}

data class MorseControlLayout(
    val rows: List<List<MorseControl>> = listOf(listOf(MorseControl.BOUNDARY, MorseControl.DELETE,
        MorseControl.CONVERT, MorseControl.ENTER, MorseControl.SMALL, MorseControl.KEYBOARD)),
    val abovePads: Boolean = false,
    val placement: MorseControlPlacement = if (abovePads) MorseControlPlacement.TOP else MorseControlPlacement.BOTTOM,
) {
    fun validate() {
        require(rows.size in 1..2 && rows.all { it.size in 1..6 }) { "Use one or two rows of up to six keys" }
        val keys = rows.flatten()
        require(keys.distinct().size == keys.size) { "Duplicate control" }
        require(MorseControl.entries.filter { it.required }.all { it in keys }) { "Keep divide and keyboard controls" }
    }
}
