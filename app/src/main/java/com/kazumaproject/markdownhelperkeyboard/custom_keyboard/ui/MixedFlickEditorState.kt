package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.os.Bundle
import androidx.core.os.bundleOf
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyIconRef
import com.kazumaproject.custom_keyboard.data.KeyIconType

internal val specialFlickDirections = listOf(
    FlickDirection.TAP,
    FlickDirection.UP,
    FlickDirection.DOWN,
    FlickDirection.UP_LEFT,
    FlickDirection.UP_RIGHT
)

internal sealed interface MixedFlickSlot {
    data object Unassigned : MixedFlickSlot
    data class Text(val output: String = "") : MixedFlickSlot
    data class Action(
        val action: KeyAction? = null,
        val iconResourceName: String? = null
    ) : MixedFlickSlot {
        val icon: KeyIconRef?
            get() = iconResourceName?.let { KeyIconRef(KeyIconType.DRAWABLE_RESOURCE_NAME, it) }
    }

    fun toFlickAction(): FlickAction? = when (this) {
        Unassigned -> null
        is Text -> output.takeIf(String::isNotEmpty)?.let { FlickAction.Input(it) }
        is Action -> action?.let { FlickAction.Action(it, icon = icon) }
    }

    companion object {
        fun fromFlickAction(action: FlickAction?): MixedFlickSlot = when (action) {
            is FlickAction.Input -> if (action.char.isEmpty()) Unassigned else Text(action.char)
            is FlickAction.Action -> when (val keyAction = action.action) {
                is KeyAction.Text -> Text(keyAction.text)
                is KeyAction.InputText -> Text(keyAction.text)
                else -> Action(
                    keyAction,
                    action.icon?.takeIf { it.type == KeyIconType.DRAWABLE_RESOURCE_NAME }?.value
                )
            }
            null -> Unassigned
        }
    }
}

internal class MixedFlickEditorState(
    val slots: MutableMap<FlickDirection, MixedFlickSlot>,
    var label: String,
    var selectedDirection: FlickDirection = FlickDirection.TAP
) {
    fun canSave(validTargetIds: Set<String>): Boolean =
        slots[FlickDirection.TAP]?.toFlickAction() != null &&
            slots.values.all { (it as? MixedFlickSlot.Action)?.action.isValidMoveToCustomKeyboardTarget(validTargetIds) }

    fun toBundle(): Bundle = bundleOf(
        "label" to label,
        "selectedDirection" to selectedDirection.name,
        "slots" to Bundle().apply {
            slots.forEach { (direction, slot) ->
                val value = when (slot) {
                    MixedFlickSlot.Unassigned -> null
                    is MixedFlickSlot.Text -> bundleOf("text" to slot.output)
                    is MixedFlickSlot.Action -> bundleOf(
                        "action" to KeyActionMapper.fromKeyAction(slot.action),
                        "iconResourceName" to slot.iconResourceName,
                        "pendingTarget" to (slot.action == KeyAction.MoveToCustomKeyboard(""))
                    )
                }
                putBundle(direction.name, value)
            }
        }
    )

    companion object {
        fun fromKey(key: KeyData, flickMap: Map<FlickDirection, FlickAction>): MixedFlickEditorState {
            val slots = specialFlickDirections.associateWith { direction ->
                val alternate = when (direction) {
                    FlickDirection.UP_LEFT -> flickMap[FlickDirection.UP_LEFT_FAR]
                    FlickDirection.UP_RIGHT -> flickMap[FlickDirection.UP_RIGHT_FAR]
                    else -> null
                }
                val fallback = key.action?.takeIf { direction == FlickDirection.TAP }
                    ?.let { FlickAction.Action(it) }
                val saved = flickMap[direction] ?: alternate
                MixedFlickSlot.fromFlickAction(
                    saved?.takeUnless { it is FlickAction.Input && it.char.isEmpty() } ?: fallback
                )
            }.toMutableMap()
            return MixedFlickEditorState(slots, key.label.takeUnless { key.isSpecialKey }.orEmpty())
        }

        fun fromBundle(bundle: Bundle): MixedFlickEditorState {
            val savedSlots = bundle.getBundle("slots")
            return MixedFlickEditorState(
                slots = specialFlickDirections.associateWith { direction ->
                    val slot = savedSlots?.getBundle(direction.name)
                    when {
                        slot == null -> MixedFlickSlot.Unassigned
                        slot.containsKey("text") -> MixedFlickSlot.Text(slot.getString("text").orEmpty())
                        else -> MixedFlickSlot.Action(
                            action = if (slot.getBoolean("pendingTarget")) {
                                KeyAction.MoveToCustomKeyboard("")
                            } else {
                                KeyActionMapper.toKeyAction(slot.getString("action"))
                            },
                            iconResourceName = slot.getString("iconResourceName")
                        )
                    }
                }.toMutableMap(),
                label = bundle.getString("label").orEmpty(),
                selectedDirection = specialFlickDirections.firstOrNull {
                    it.name == bundle.getString("selectedDirection")
                } ?: FlickDirection.TAP
            )
        }
    }
}
