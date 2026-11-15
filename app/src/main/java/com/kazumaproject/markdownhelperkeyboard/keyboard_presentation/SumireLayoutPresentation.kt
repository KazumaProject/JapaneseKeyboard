package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts.DeleteKeyFlickSettings
import com.kazumaproject.markdownhelperkeyboard.setting_activity.circular_slot.CircularSlotActionApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionDisplayMetadata
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionDisplayOverrideApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyPlacementOverrideApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity

/** Resolves the real built-in layout, including user special-key overrides. */
internal object SumireLayoutPresentation {
    fun create(
        context: Context,
        mode: KeyboardInputMode,
        dynamicStates: Map<String, Int>,
        actionOverrides: List<SumireSpecialKeyActionOverrideEntity>,
        placementOverrides: List<SumireSpecialKeyPlacementOverrideEntity>,
    ): KeyboardLayout {
        val prefs = AppPreference
        val layoutType = prefs.sumire_input_method
        val style = prefs.sumire_keyboard_style
        val base = KeyboardDefaultLayouts.createFinalLayout(
            mode = mode, dynamicKeyStates = dynamicStates, inputLayoutType = layoutType,
            inputStyle = style,
            deleteKeyFlickSettings = DeleteKeyFlickSettings(prefs.delete_key_left_flick_preference,
                prefs.delete_key_up_flick_preference, prefs.delete_key_down_flick_preference),
            tfbiFlickRotation = prefs.sumire_flick_rotation_preference,
            highlightSelectedMode = prefs.sumire_mode_key_highlight_preference,
        )
        val circular = CircularSlotActionApplier.apply(base, mode, prefs.getCircularSlotActionSettings())
        val repository = com.kazumaproject.markdownhelperkeyboard.sumire_popup.SumirePopupRepository(context)
        val popups = if (circular.keys.none { it.popupMapConfig != null }) circular else
            circular.copyWithKeys(circular.keys.map { key ->
                key.popupMapConfig?.let { key.copy(popupMapConfig = repository.load(it.keyId)) } ?: key
            })
        val metadata = KeyActionMapper.getDisplayActions(context).map {
            SumireSpecialKeyActionDisplayMetadata(it.action, it.displayName, it.iconResId)
        }
        return SumireSpecialKeyPlacementOverrideApplier.apply(
            SumireSpecialKeyActionDisplayOverrideApplier.apply(popups, layoutType, mode.name, actionOverrides, metadata),
            layoutType, mode.name, placementOverrides,
        )
    }
}
