package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectType

internal object KeyboardTouchEffectSettingVisibility {
    const val LIQUID_INK_DENSITY_KEY =
        "keyboard_touch_effect_liquid_ink_density_preference"
    const val AURORA_INK_DENSITY_KEY =
        "keyboard_touch_effect_aurora_ink_density_preference"

    fun isVisibleForEffect(preferenceKey: String, effectType: String): Boolean {
        val normalizedEffect = KeyboardTouchEffectType.normalize(effectType)
        return when (preferenceKey) {
            LIQUID_INK_DENSITY_KEY -> KeyboardTouchEffectType.isLiquidInk(normalizedEffect)
            AURORA_INK_DENSITY_KEY -> KeyboardTouchEffectType.isAuroraInk(normalizedEffect)
            else -> true
        }
    }

    fun isVisibleForEffect(destination: SettingDestination, effectType: String): Boolean =
        isVisibleForEffect(destination.key, effectType)
}
