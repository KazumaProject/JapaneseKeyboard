package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

internal object RestartInputModePreference {
    const val JAPANESE = "japanese"
    const val ENGLISH = "english"
    const val NUMBER = "number"
    const val NATIVE = "native"
    const val SUMIRE_QWERTY_PROXY = "sumire_qwerty_proxy"
    const val TENKEY_QWERTY_NUMBER_PROXY = "tenkey_qwerty_number_proxy"

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

    fun toPreferenceValue(presentation: RestartInputModePresentation): String {
        return when (presentation) {
            RestartInputModePresentation.Native -> NATIVE
            RestartInputModePresentation.SumireQwertyProxy -> SUMIRE_QWERTY_PROXY
            RestartInputModePresentation.TenkeyQwertyNumberProxy -> TENKEY_QWERTY_NUMBER_PROXY
        }
    }

    fun presentationFromPreferenceValue(value: String): RestartInputModePresentation {
        return when (value) {
            NATIVE -> RestartInputModePresentation.Native
            SUMIRE_QWERTY_PROXY -> RestartInputModePresentation.SumireQwertyProxy
            TENKEY_QWERTY_NUMBER_PROXY -> RestartInputModePresentation.TenkeyQwertyNumberProxy
            else -> RestartInputModePresentation.Native
        }
    }

    fun resolvePersistenceTarget(
        currentKeyboardType: KeyboardType?,
        activeTenKeyQwertyMode: TenKeyQWERTYMode,
        previousTenKeyQWERTYMode: TenKeyQWERTYMode?,
        qwertyReturnSource: RestartInputModeQwertyReturnSource =
            RestartInputModeQwertyReturnSource.None
    ): KeyboardType? {
        return when (currentKeyboardType) {
            KeyboardType.TENKEY -> KeyboardType.TENKEY
            KeyboardType.SUMIRE -> KeyboardType.SUMIRE
            else -> {
                if (activeTenKeyQwertyMode != TenKeyQWERTYMode.TenKeyQWERTY) {
                    return null
                }
                when (qwertyReturnSource) {
                    RestartInputModeQwertyReturnSource.TenKeyDefault,
                    RestartInputModeQwertyReturnSource.TenKeyNumber -> return KeyboardType.TENKEY

                    RestartInputModeQwertyReturnSource.Sumire -> return KeyboardType.SUMIRE
                    RestartInputModeQwertyReturnSource.None,
                    RestartInputModeQwertyReturnSource.Custom -> Unit
                }
                when (previousTenKeyQWERTYMode) {
                    TenKeyQWERTYMode.Default -> KeyboardType.TENKEY
                    TenKeyQWERTYMode.Sumire -> KeyboardType.SUMIRE
                    else -> null
                }
            }
        }
    }

    fun resolveStateForPersistence(
        currentKeyboardType: KeyboardType?,
        activeTenKeyQwertyMode: TenKeyQWERTYMode,
        previousTenKeyQWERTYMode: TenKeyQWERTYMode?,
        qwertyReturnSource: RestartInputModeQwertyReturnSource,
        currentInputMode: InputMode,
        tenkeyUseThreeStateKeyboard: Boolean,
        sumireEnglishQwertyPreference: Boolean,
        isQwertyViewVisible: Boolean,
        isQwertyNumberLayout: Boolean
    ): RestartInputModeState? {
        val target = resolvePersistenceTarget(
            currentKeyboardType = currentKeyboardType,
            activeTenKeyQwertyMode = activeTenKeyQwertyMode,
            previousTenKeyQWERTYMode = previousTenKeyQWERTYMode,
            qwertyReturnSource = qwertyReturnSource
        ) ?: return null

        val presentation = resolveCurrentPresentation(
            target = target,
            currentInputMode = currentInputMode,
            activeTenKeyQwertyMode = activeTenKeyQwertyMode,
            qwertyReturnSource = qwertyReturnSource,
            previousTenKeyQWERTYMode = previousTenKeyQWERTYMode,
            tenkeyUseThreeStateKeyboard = tenkeyUseThreeStateKeyboard,
            sumireEnglishQwertyPreference = sumireEnglishQwertyPreference,
            isQwertyViewVisible = isQwertyViewVisible,
            isQwertyNumberLayout = isQwertyNumberLayout
        )
        return RestartInputModeState(target, currentInputMode, presentation)
    }

    private fun resolveCurrentPresentation(
        target: KeyboardType,
        currentInputMode: InputMode,
        activeTenKeyQwertyMode: TenKeyQWERTYMode,
        qwertyReturnSource: RestartInputModeQwertyReturnSource,
        previousTenKeyQWERTYMode: TenKeyQWERTYMode?,
        tenkeyUseThreeStateKeyboard: Boolean,
        sumireEnglishQwertyPreference: Boolean,
        isQwertyViewVisible: Boolean,
        isQwertyNumberLayout: Boolean
    ): RestartInputModePresentation {
        val isQwertyProxySurface =
            isQwertyViewVisible && activeTenKeyQwertyMode == TenKeyQWERTYMode.TenKeyQWERTY
        val returnsToSumire =
            qwertyReturnSource == RestartInputModeQwertyReturnSource.Sumire ||
                    previousTenKeyQWERTYMode == TenKeyQWERTYMode.Sumire
        return when {
            target == KeyboardType.SUMIRE &&
                    currentInputMode == InputMode.ModeEnglish &&
                    sumireEnglishQwertyPreference &&
                    isQwertyProxySurface &&
                    returnsToSumire -> RestartInputModePresentation.SumireQwertyProxy

            target == KeyboardType.TENKEY &&
                    currentInputMode == InputMode.ModeNumber &&
                    tenkeyUseThreeStateKeyboard &&
                    isQwertyProxySurface &&
                    isQwertyNumberLayout ->
                RestartInputModePresentation.TenkeyQwertyNumberProxy

            else -> RestartInputModePresentation.Native
        }
    }

    fun resolvePersistenceValue(
        currentInputType: InputTypeForIME,
        passwordTypesWithoutNumber: Set<InputTypeForIME>,
        numberTypes: Set<InputTypeForIME>,
        target: KeyboardType?,
        tenkeyRestoreEnabled: Boolean,
        sumireRestoreEnabled: Boolean,
        currentInputMode: InputMode,
        presentation: RestartInputModePresentation = RestartInputModePresentation.Native
    ): RestartInputModePersistence? {
        if (currentInputType in passwordTypesWithoutNumber) return null
        if (currentInputType in numberTypes) return null

        val value = toPreferenceValue(currentInputMode)
        val presentationValue = toPreferenceValue(
            normalizePresentation(target, currentInputMode, presentation)
        )
        return when {
            target == KeyboardType.TENKEY && tenkeyRestoreEnabled ->
                RestartInputModePersistence(KeyboardType.TENKEY, value, presentationValue)

            target == KeyboardType.SUMIRE && sumireRestoreEnabled ->
                RestartInputModePersistence(KeyboardType.SUMIRE, value, presentationValue)

            else -> null
        }
    }

    fun resolveRestoredState(
        type: KeyboardType,
        tenkeyRestoreEnabled: Boolean,
        sumireRestoreEnabled: Boolean,
        tenkeyLastInputModePreference: String,
        tenkeyLastInputModePresentationPreference: String,
        sumireLastInputModePreference: String,
        sumireLastInputModePresentationPreference: String
    ): RestartInputModeState? {
        val inputMode = when {
            type == KeyboardType.TENKEY && tenkeyRestoreEnabled ->
                fromPreferenceValue(tenkeyLastInputModePreference)

            type == KeyboardType.SUMIRE && sumireRestoreEnabled ->
                fromPreferenceValue(sumireLastInputModePreference)

            else -> return null
        }
        val presentationPreference = when (type) {
            KeyboardType.TENKEY -> tenkeyLastInputModePresentationPreference
            KeyboardType.SUMIRE -> sumireLastInputModePresentationPreference
            else -> NATIVE
        }
        val presentation = normalizePresentation(
            type = type,
            inputMode = inputMode,
            presentation = presentationFromPreferenceValue(presentationPreference)
        )
        return RestartInputModeState(type, inputMode, presentation)
    }

    fun resolveRestoredMode(
        type: KeyboardType,
        tenkeyRestoreEnabled: Boolean,
        sumireRestoreEnabled: Boolean,
        tenkeyLastInputModePreference: String,
        sumireLastInputModePreference: String
    ): InputMode? {
        return resolveRestoredState(
            type = type,
            tenkeyRestoreEnabled = tenkeyRestoreEnabled,
            sumireRestoreEnabled = sumireRestoreEnabled,
            tenkeyLastInputModePreference = tenkeyLastInputModePreference,
            tenkeyLastInputModePresentationPreference = NATIVE,
            sumireLastInputModePreference = sumireLastInputModePreference,
            sumireLastInputModePresentationPreference = NATIVE
        )?.inputMode
    }

    private fun normalizePresentation(
        type: KeyboardType?,
        inputMode: InputMode,
        presentation: RestartInputModePresentation
    ): RestartInputModePresentation {
        return when (presentation) {
            RestartInputModePresentation.Native -> RestartInputModePresentation.Native
            RestartInputModePresentation.SumireQwertyProxy -> {
                if (type == KeyboardType.SUMIRE && inputMode == InputMode.ModeEnglish) {
                    RestartInputModePresentation.SumireQwertyProxy
                } else {
                    RestartInputModePresentation.Native
                }
            }

            RestartInputModePresentation.TenkeyQwertyNumberProxy -> {
                if (type == KeyboardType.TENKEY && inputMode == InputMode.ModeNumber) {
                    RestartInputModePresentation.TenkeyQwertyNumberProxy
                } else {
                    RestartInputModePresentation.Native
                }
            }
        }
    }
}

internal sealed class RestartInputModePresentation {
    data object Native : RestartInputModePresentation()
    data object SumireQwertyProxy : RestartInputModePresentation()
    data object TenkeyQwertyNumberProxy : RestartInputModePresentation()
}

internal data class RestartInputModeState(
    val keyboardType: KeyboardType,
    val inputMode: InputMode,
    val presentation: RestartInputModePresentation
)

internal enum class RestartInputModeQwertyReturnSource {
    None,
    TenKeyDefault,
    Sumire,
    TenKeyNumber,
    Custom
}

internal data class RestartInputModePersistence(
    val target: KeyboardType,
    val value: String,
    val presentationValue: String = RestartInputModePreference.NATIVE
)

internal fun InputMode.toRestartPreferenceValue(): String {
    return RestartInputModePreference.toPreferenceValue(this)
}

internal fun inputModeFromRestartPreferenceValue(value: String): InputMode {
    return RestartInputModePreference.fromPreferenceValue(value)
}

internal fun RestartInputModePresentation.toRestartPreferenceValue(): String {
    return RestartInputModePreference.toPreferenceValue(this)
}

internal fun restartInputModePresentationFromPreferenceValue(
    value: String
): RestartInputModePresentation {
    return RestartInputModePreference.presentationFromPreferenceValue(value)
}
