package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.custom_keyboard.data.KeyboardInputMode

internal data class ModeKeymapGuideSettings(
    val japanese: Boolean = false,
    val english: Boolean = false,
    val number: Boolean = false
) {
    fun isEnabled(mode: KeyboardInputMode): Boolean {
        return when (mode) {
            KeyboardInputMode.HIRAGANA -> japanese
            KeyboardInputMode.ENGLISH -> english
            KeyboardInputMode.SYMBOLS -> number
        }
    }
}

internal enum class FlickGuideSurface {
    Sumire,
    Custom,
    Other
}

internal data class FlickGuideRuntimeConfig(
    val enabled: Boolean,
    val allowMultiCharacterLabels: Boolean
)

internal fun resolveFlickGuideRuntimeConfig(
    surface: FlickGuideSurface,
    mode: KeyboardInputMode,
    sumireSettings: ModeKeymapGuideSettings,
    customGuideEnabled: Boolean
): FlickGuideRuntimeConfig {
    return when (surface) {
        FlickGuideSurface.Sumire -> FlickGuideRuntimeConfig(
            enabled = sumireSettings.isEnabled(mode),
            allowMultiCharacterLabels = true
        )

        FlickGuideSurface.Custom -> FlickGuideRuntimeConfig(
            enabled = customGuideEnabled,
            allowMultiCharacterLabels = false
        )

        FlickGuideSurface.Other -> FlickGuideRuntimeConfig(
            enabled = false,
            allowMultiCharacterLabels = false
        )
    }
}
