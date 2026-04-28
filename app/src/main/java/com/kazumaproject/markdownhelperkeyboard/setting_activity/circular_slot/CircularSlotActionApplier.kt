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
        val editableKeyIdentifiers = layout.keys
            .filter { key ->
                !key.isSpecialKey &&
                    key.label.isNotBlank() &&
                    key.keyType == KeyType.CIRCULAR_FLICK
            }
            .map { key -> key.keyId ?: key.label }
            .toSet()

        val modeSettings = settings.filter { setting ->
            setting.mode == mode &&
                setting.keyIdentifier in editableKeyIdentifiers &&
                setting.slot in editableSlots
        }

        if (modeSettings.isEmpty()) return layout

        val nextCircularMaps = layout.circularFlickKeyMaps.toMutableMap()
        modeSettings
            .groupBy { it.keyIdentifier }
            .forEach { (keyIdentifier, keySettings) ->
                val maps = nextCircularMaps[keyIdentifier]?.ifEmpty { null }
                    ?: layout.keys
                        .firstOrNull { (it.keyId ?: it.label) == keyIdentifier }
                        ?.label
                        ?.let { label -> nextCircularMaps[label] }
                    ?: return@forEach

                val updatedMaps = maps.map { stateMap ->
                    val mutable = stateMap.toMutableMap()
                    keySettings.forEach { setting ->
                        mutable[setting.slot] = setting.toFlickAction()
                    }
                    mutable.toMap()
                }
                nextCircularMaps[keyIdentifier] = updatedMaps
            }

        return layout.copy(circularFlickKeyMaps = nextCircularMaps)
    }

    private fun CircularSlotActionSetting.toFlickAction(): FlickAction {
        return when (actionType) {
            CircularSlotActionType.NONE -> FlickAction.Input("", label = "")
            CircularSlotActionType.INPUT_TEXT -> FlickAction.Input(value.orEmpty(), label = value.orEmpty())
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
