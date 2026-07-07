package com.kazumaproject.markdownhelperkeyboard.setting_activity.circular_slot

import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout

object CircularSlotActionApplier {

    private val editableSlots = setOf(
        CircularFlickDirection.SLOT_4,
        CircularFlickDirection.SLOT_5,
        CircularFlickDirection.SLOT_6
    )

    fun apply(
        layout: KeyboardLayout,
        mode: KeyboardInputMode,
        settings: List<CircularSlotActionSetting>
    ): KeyboardLayout {
        val editableKeys = layout.keys
            .filter { key ->
                !key.isSpecialKey &&
                    key.label.isNotBlank() &&
                    key.keyType == KeyType.CIRCULAR_FLICK
            }

        val editableKeyIdentifiers = editableKeys
            .map { key -> key.keyId ?: key.label }
            .toSet()

        val modeSettings = settings.filter { setting ->
            setting.mode == mode &&
                setting.keyIdentifier in editableKeyIdentifiers &&
                setting.slot in editableSlots
        }

        val nextCircularMaps = layout.circularFlickKeyMaps.toMutableMap()
        val settingsByKey = modeSettings.groupBy { it.keyIdentifier }

        editableKeys.forEach { key ->
            val keyIdentifier = key.keyId ?: key.label
            val mapKey = when {
                nextCircularMaps.containsKey(keyIdentifier) -> keyIdentifier
                nextCircularMaps.containsKey(key.label) -> key.label
                else -> return@forEach
            }
            val keySettings = settingsByKey[keyIdentifier].orEmpty()
            val maps = nextCircularMaps[mapKey].orEmpty()

            val updatedMaps = maps.map { stateMap ->
                val mutable = stateMap.toMutableMap()
                editableSlots.forEach { slot -> mutable.remove(slot) }
                keySettings.forEach { setting ->
                    val action = setting.toFlickAction()
                    if (action == null) {
                        mutable.remove(setting.slot)
                    } else {
                        mutable[setting.slot] = action
                    }
                }
                mutable.toMap()
            }
            nextCircularMaps[mapKey] = updatedMaps
        }

        return layout.copy(circularFlickKeyMaps = nextCircularMaps)
    }

    private fun CircularSlotActionSetting.toFlickAction(): FlickAction? {
        return when (actionType) {
            CircularSlotActionType.NONE -> null
            CircularSlotActionType.INPUT_TEXT -> value
                ?.takeIf { it.isNotEmpty() }
                ?.let { FlickAction.Input(it, label = it) }

            CircularSlotActionType.SHOW_EMOJI_KEYBOARD -> FlickAction.Action(
                KeyAction.ShowEmojiKeyboard,
                label = "絵"
            )

            CircularSlotActionType.SWITCH_TO_NEXT_IME -> FlickAction.Action(
                KeyAction.SwitchToNextIme,
                label = "IME"
            )

            CircularSlotActionType.SWITCH_TO_KANA_LAYOUT -> FlickAction.Action(
                KeyAction.SwitchToKanaLayout,
                label = "かな"
            )

            CircularSlotActionType.SWITCH_TO_ENGLISH_LAYOUT -> FlickAction.Action(
                KeyAction.SwitchToEnglishLayout,
                label = "英"
            )

            CircularSlotActionType.SWITCH_TO_NUMBER_LAYOUT -> FlickAction.Action(
                KeyAction.SwitchToNumberLayout,
                label = "数"
            )

            CircularSlotActionType.SWITCH_MAP -> FlickAction.Action(
                KeyAction.MoveCustomKeyboardTab,
                label = "⇄"
            )
        }
    }
}
