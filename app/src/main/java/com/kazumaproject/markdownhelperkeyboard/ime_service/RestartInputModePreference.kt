package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

internal object RestartInputModePreference {
    const val JAPANESE = "japanese"
    const val ENGLISH = "english"
    const val NUMBER = "number"

    fun toPreferenceValue(inputMode: InputMode): String {
        return when (inputMode) {
            InputMode.ModeJapanese -> JAPANESE
            InputMode.ModeEnglish -> ENGLISH
            InputMode.ModeNumber -> NUMBER
        }
    }

    fun fromPreferenceValue(value: String): InputMode {
        return when (value) {
            JAPANESE -> InputMode.ModeJapanese
            ENGLISH -> InputMode.ModeEnglish
            NUMBER -> InputMode.ModeNumber
            else -> InputMode.ModeJapanese
        }
    }

    fun resolvePersistenceTarget(
        currentKeyboardType: KeyboardType?,
        activeTenKeyQwertyMode: TenKeyQWERTYMode,
        previousTenKeyQWERTYMode: TenKeyQWERTYMode?
    ): KeyboardType? {
        return when (currentKeyboardType) {
            KeyboardType.TENKEY -> KeyboardType.TENKEY
            KeyboardType.SUMIRE -> KeyboardType.SUMIRE
            else -> {
                if (activeTenKeyQwertyMode != TenKeyQWERTYMode.TenKeyQWERTY) {
                    return null
                }
                when (previousTenKeyQWERTYMode) {
                    TenKeyQWERTYMode.Default -> KeyboardType.TENKEY
                    TenKeyQWERTYMode.Sumire -> KeyboardType.SUMIRE
                    else -> null
                }
            }
        }
    }

    fun resolvePersistenceValue(
        currentInputType: InputTypeForIME,
        passwordTypesWithoutNumber: Set<InputTypeForIME>,
        numberTypes: Set<InputTypeForIME>,
        target: KeyboardType?,
        tenkeyRestoreEnabled: Boolean,
        sumireRestoreEnabled: Boolean,
        currentInputMode: InputMode
    ): RestartInputModePersistence? {
        if (currentInputType in passwordTypesWithoutNumber) return null
        if (currentInputType in numberTypes) return null

        val value = toPreferenceValue(currentInputMode)
        return when {
            target == KeyboardType.TENKEY && tenkeyRestoreEnabled ->
                RestartInputModePersistence(KeyboardType.TENKEY, value)

            target == KeyboardType.SUMIRE && sumireRestoreEnabled ->
                RestartInputModePersistence(KeyboardType.SUMIRE, value)

            else -> null
        }
    }

    fun resolveRestoredMode(
        type: KeyboardType,
        tenkeyRestoreEnabled: Boolean,
        sumireRestoreEnabled: Boolean,
        tenkeyLastInputModePreference: String,
        sumireLastInputModePreference: String
    ): InputMode? {
        return when {
            type == KeyboardType.TENKEY && tenkeyRestoreEnabled ->
                fromPreferenceValue(tenkeyLastInputModePreference)

            type == KeyboardType.SUMIRE && sumireRestoreEnabled ->
                fromPreferenceValue(sumireLastInputModePreference)

            else -> null
        }
    }
}

internal data class RestartInputModePersistence(
    val target: KeyboardType,
    val value: String
)

internal fun InputMode.toRestartPreferenceValue(): String {
    return RestartInputModePreference.toPreferenceValue(this)
}

internal fun inputModeFromRestartPreferenceValue(value: String): InputMode {
    return RestartInputModePreference.fromPreferenceValue(value)
}
