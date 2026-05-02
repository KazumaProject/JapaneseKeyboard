package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.KeyAction
import timber.log.Timber

enum class CircularFlickSlotActionType {
    NONE,
    INPUT,
    SWITCH_MAP,
    EMOJI_KEYBOARD
}

object CircularFlickSlotActionMapper {
    const val SWITCH_MAP_LABEL = "⇄"
    const val EMOJI_KEYBOARD_LABEL = "絵"

    fun fromFlickAction(
        direction: CircularFlickDirection,
        action: FlickAction?
    ): Pair<CircularFlickSlotActionType, String> {
        return when (action) {
            null -> CircularFlickSlotActionType.NONE to ""
            is FlickAction.Input -> CircularFlickSlotActionType.INPUT to action.char
            is FlickAction.Action -> when {
                action.action == KeyAction.MoveCustomKeyboardTab &&
                    action.label == SWITCH_MAP_LABEL -> CircularFlickSlotActionType.SWITCH_MAP to ""

                action.action == KeyAction.ShowEmojiKeyboard -> CircularFlickSlotActionType.EMOJI_KEYBOARD to ""

                else -> {
                    Timber.w(
                        "Unsupported circular flick slot action ignored in KeyEditorFragment: direction=%s, action=%s",
                        direction,
                        action
                    )
                    CircularFlickSlotActionType.NONE to ""
                }
            }
        }
    }

    fun toFlickAction(
        actionType: CircularFlickSlotActionType,
        output: String
    ): FlickAction? {
        return when (actionType) {
            CircularFlickSlotActionType.NONE -> null
            CircularFlickSlotActionType.INPUT -> output
                .takeIf { it.isNotEmpty() }
                ?.let { FlickAction.Input(it) }

            CircularFlickSlotActionType.SWITCH_MAP -> FlickAction.Action(
                KeyAction.MoveCustomKeyboardTab,
                label = SWITCH_MAP_LABEL
            )

            CircularFlickSlotActionType.EMOJI_KEYBOARD -> FlickAction.Action(
                KeyAction.ShowEmojiKeyboard,
                label = EMOJI_KEYBOARD_LABEL
            )
        }
    }
}
