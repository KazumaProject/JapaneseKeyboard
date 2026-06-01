package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestartInputModePreferenceTest {

    private val passwordTypesWithoutNumber = setOf(
        InputTypeForIME.TextWebPassword,
        InputTypeForIME.TextPassword,
        InputTypeForIME.TextVisiblePassword
    )
    private val numberTypes = setOf(
        InputTypeForIME.Number,
        InputTypeForIME.NumberDecimal,
        InputTypeForIME.NumberPassword,
        InputTypeForIME.NumberSigned,
        InputTypeForIME.Phone,
        InputTypeForIME.Date,
        InputTypeForIME.Datetime,
        InputTypeForIME.Time
    )

    @Test
    fun unknownPreferenceValueFallsBackToJapanese() {
        assertEquals(
            InputMode.ModeJapanese,
            inputModeFromRestartPreferenceValue("unknown")
        )
    }

    @Test
    fun inputModeConvertsToStableRestartPreferenceValue() {
        assertEquals("japanese", InputMode.ModeJapanese.toRestartPreferenceValue())
        assertEquals("english", InputMode.ModeEnglish.toRestartPreferenceValue())
        assertEquals("number", InputMode.ModeNumber.toRestartPreferenceValue())
    }

    @Test
    fun stableRestartPreferenceValueConvertsToInputMode() {
        assertEquals(
            InputMode.ModeJapanese,
            inputModeFromRestartPreferenceValue("japanese")
        )
        assertEquals(
            InputMode.ModeEnglish,
            inputModeFromRestartPreferenceValue("english")
        )
        assertEquals(
            InputMode.ModeNumber,
            inputModeFromRestartPreferenceValue("number")
        )
    }

    @Test
    fun presentationConvertsToStableRestartPreferenceValue() {
        assertEquals(
            "native",
            RestartInputModePresentation.Native.toRestartPreferenceValue()
        )
        assertEquals(
            "sumire_qwerty_proxy",
            RestartInputModePresentation.SumireQwertyProxy.toRestartPreferenceValue()
        )
        assertEquals(
            "tenkey_qwerty_number_proxy",
            RestartInputModePresentation.TenkeyQwertyNumberProxy.toRestartPreferenceValue()
        )
    }

    @Test
    fun stableRestartPreferenceValueConvertsToPresentation() {
        assertEquals(
            RestartInputModePresentation.Native,
            restartInputModePresentationFromPreferenceValue("native")
        )
        assertEquals(
            RestartInputModePresentation.SumireQwertyProxy,
            restartInputModePresentationFromPreferenceValue("sumire_qwerty_proxy")
        )
        assertEquals(
            RestartInputModePresentation.TenkeyQwertyNumberProxy,
            restartInputModePresentationFromPreferenceValue("tenkey_qwerty_number_proxy")
        )
        assertEquals(
            RestartInputModePresentation.Native,
            restartInputModePresentationFromPreferenceValue("unknown")
        )
    }

    @Test
    fun tenkeyRestoreOffDoesNotPersist() {
        assertNull(
            persistenceValue(
                target = KeyboardType.TENKEY,
                tenkeyRestoreEnabled = false,
                currentInputMode = InputMode.ModeEnglish
            )
        )
    }

    @Test
    fun sumireRestoreOffDoesNotPersist() {
        assertNull(
            persistenceValue(
                target = KeyboardType.SUMIRE,
                sumireRestoreEnabled = false,
                currentInputMode = InputMode.ModeEnglish
            )
        )
    }

    @Test
    fun restoreOffDoesNotResolveRestartInputModeState() {
        assertNull(
            RestartInputModePreference.resolveRestoredState(
                type = KeyboardType.TENKEY,
                tenkeyRestoreEnabled = false,
                sumireRestoreEnabled = false,
                tenkeyLastInputModePreference = "number",
                tenkeyLastInputModePresentationPreference = "tenkey_qwerty_number_proxy",
                sumireLastInputModePreference = "english",
                sumireLastInputModePresentationPreference = "sumire_qwerty_proxy"
            )
        )
    }

    @Test
    fun tenkeyRestoreOnPersistsCurrentMode() {
        assertEquals(
            RestartInputModePersistence(KeyboardType.TENKEY, "english"),
            persistenceValue(
                target = KeyboardType.TENKEY,
                tenkeyRestoreEnabled = true,
                currentInputMode = InputMode.ModeEnglish
            )
        )
    }

    @Test
    fun sumireRestoreOnPersistsCurrentMode() {
        assertEquals(
            RestartInputModePersistence(KeyboardType.SUMIRE, "number"),
            persistenceValue(
                target = KeyboardType.SUMIRE,
                sumireRestoreEnabled = true,
                currentInputMode = InputMode.ModeNumber
            )
        )
    }

    @Test
    fun passwordInputTypeDoesNotPersistRestartInputMode() {
        assertNull(
            persistenceValue(
                currentInputType = InputTypeForIME.TextPassword,
                target = KeyboardType.TENKEY,
                tenkeyRestoreEnabled = true,
                currentInputMode = InputMode.ModeEnglish
            )
        )
    }

    @Test
    fun numberInputTypeDoesNotPersistRestartInputMode() {
        assertNull(
            persistenceValue(
                currentInputType = InputTypeForIME.Number,
                target = KeyboardType.SUMIRE,
                sumireRestoreEnabled = true,
                currentInputMode = InputMode.ModeNumber
            )
        )
    }

    @Test
    fun tenkeyRestartRestoreReturnsSavedModeOnlyWhenEnabled() {
        assertEquals(
            InputMode.ModeNumber,
            RestartInputModePreference.resolveRestoredMode(
                type = KeyboardType.TENKEY,
                tenkeyRestoreEnabled = true,
                sumireRestoreEnabled = false,
                tenkeyLastInputModePreference = "number",
                sumireLastInputModePreference = "english"
            )
        )
        assertNull(
            RestartInputModePreference.resolveRestoredMode(
                type = KeyboardType.TENKEY,
                tenkeyRestoreEnabled = false,
                sumireRestoreEnabled = false,
                tenkeyLastInputModePreference = "number",
                sumireLastInputModePreference = "english"
            )
        )
    }

    @Test
    fun sumireRestartRestoreReturnsSavedModeOnlyWhenEnabled() {
        assertEquals(
            InputMode.ModeEnglish,
            RestartInputModePreference.resolveRestoredMode(
                type = KeyboardType.SUMIRE,
                tenkeyRestoreEnabled = false,
                sumireRestoreEnabled = true,
                tenkeyLastInputModePreference = "number",
                sumireLastInputModePreference = "english"
            )
        )
        assertNull(
            RestartInputModePreference.resolveRestoredMode(
                type = KeyboardType.QWERTY,
                tenkeyRestoreEnabled = true,
                sumireRestoreEnabled = true,
                tenkeyLastInputModePreference = "number",
                sumireLastInputModePreference = "english"
            )
        )
    }

    @Test
    fun tenkeyTemporaryEnglishQwertySurfacePersistsAsTenkey() {
        val target = RestartInputModePreference.resolvePersistenceTarget(
            currentKeyboardType = KeyboardType.QWERTY,
            activeTenKeyQwertyMode = TenKeyQWERTYMode.TenKeyQWERTY,
            previousTenKeyQWERTYMode = TenKeyQWERTYMode.Default
        )

        assertEquals(KeyboardType.TENKEY, target)
        assertEquals(
            RestartInputModePersistence(KeyboardType.TENKEY, "english"),
            persistenceValue(
                target = target,
                tenkeyRestoreEnabled = true,
                currentInputMode = InputMode.ModeEnglish
            )
        )
    }

    @Test
    fun sumireTemporaryEnglishQwertySurfacePersistsAsSumire() {
        val target = RestartInputModePreference.resolvePersistenceTarget(
            currentKeyboardType = KeyboardType.QWERTY,
            activeTenKeyQwertyMode = TenKeyQWERTYMode.TenKeyQWERTY,
            previousTenKeyQWERTYMode = TenKeyQWERTYMode.Sumire
        )

        assertEquals(KeyboardType.SUMIRE, target)
        assertEquals(
            RestartInputModePersistence(
                KeyboardType.SUMIRE,
                "english",
                "sumire_qwerty_proxy"
            ),
            persistenceValue(
                target = target,
                sumireRestoreEnabled = true,
                currentInputMode = InputMode.ModeEnglish,
                presentation = RestartInputModePresentation.SumireQwertyProxy
            )
        )
    }

    @Test
    fun sumireEnglishQwertySurfacePersistsAsSumireQwertyProxy() {
        val state = persistenceState(
            currentKeyboardType = KeyboardType.SUMIRE,
            activeTenKeyQwertyMode = TenKeyQWERTYMode.TenKeyQWERTY,
            previousTenKeyQWERTYMode = TenKeyQWERTYMode.Sumire,
            qwertyReturnSource = RestartInputModeQwertyReturnSource.Sumire,
            currentInputMode = InputMode.ModeEnglish,
            sumireEnglishQwertyPreference = true,
            isQwertyViewVisible = true
        )

        assertEquals(
            RestartInputModeState(
                KeyboardType.SUMIRE,
                InputMode.ModeEnglish,
                RestartInputModePresentation.SumireQwertyProxy
            ),
            state
        )
        assertEquals(
            RestartInputModePersistence(
                KeyboardType.SUMIRE,
                "english",
                "sumire_qwerty_proxy"
            ),
            persistenceValue(
                target = state?.keyboardType,
                sumireRestoreEnabled = true,
                currentInputMode = state?.inputMode ?: InputMode.ModeJapanese,
                presentation = state?.presentation ?: RestartInputModePresentation.Native
            )
        )
    }

    @Test
    fun sumireEnglishQwertyProxyRestoredStateReadsFixedPresentationValue() {
        assertEquals(
            RestartInputModeState(
                KeyboardType.SUMIRE,
                InputMode.ModeEnglish,
                RestartInputModePresentation.SumireQwertyProxy
            ),
            RestartInputModePreference.resolveRestoredState(
                type = KeyboardType.SUMIRE,
                tenkeyRestoreEnabled = false,
                sumireRestoreEnabled = true,
                tenkeyLastInputModePreference = "number",
                tenkeyLastInputModePresentationPreference = "native",
                sumireLastInputModePreference = "english",
                sumireLastInputModePresentationPreference = "sumire_qwerty_proxy"
            )
        )
    }

    @Test
    fun tenkeyNumberQwertySurfacePersistsAsTenkeyQwertyNumberProxy() {
        val state = persistenceState(
            currentKeyboardType = KeyboardType.TENKEY,
            activeTenKeyQwertyMode = TenKeyQWERTYMode.TenKeyQWERTY,
            qwertyReturnSource = RestartInputModeQwertyReturnSource.TenKeyNumber,
            currentInputMode = InputMode.ModeNumber,
            tenkeyUseThreeStateKeyboard = true,
            isQwertyViewVisible = true,
            isQwertyNumberLayout = true
        )

        assertEquals(
            RestartInputModeState(
                KeyboardType.TENKEY,
                InputMode.ModeNumber,
                RestartInputModePresentation.TenkeyQwertyNumberProxy
            ),
            state
        )
        assertEquals(
            RestartInputModePersistence(
                KeyboardType.TENKEY,
                "number",
                "tenkey_qwerty_number_proxy"
            ),
            persistenceValue(
                target = state?.keyboardType,
                tenkeyRestoreEnabled = true,
                currentInputMode = state?.inputMode ?: InputMode.ModeJapanese,
                presentation = state?.presentation ?: RestartInputModePresentation.Native
            )
        )
    }

    @Test
    fun tenkeyNumberQwertyProxyRestoredStateReadsFixedPresentationValue() {
        assertEquals(
            RestartInputModeState(
                KeyboardType.TENKEY,
                InputMode.ModeNumber,
                RestartInputModePresentation.TenkeyQwertyNumberProxy
            ),
            RestartInputModePreference.resolveRestoredState(
                type = KeyboardType.TENKEY,
                tenkeyRestoreEnabled = true,
                sumireRestoreEnabled = false,
                tenkeyLastInputModePreference = "number",
                tenkeyLastInputModePresentationPreference = "tenkey_qwerty_number_proxy",
                sumireLastInputModePreference = "english",
                sumireLastInputModePresentationPreference = "sumire_qwerty_proxy"
            )
        )
    }

    @Test
    fun tenkeyEnglishQwertySurfacePersistsAsNativePresentation() {
        val state = persistenceState(
            currentKeyboardType = KeyboardType.TENKEY,
            activeTenKeyQwertyMode = TenKeyQWERTYMode.TenKeyQWERTY,
            previousTenKeyQWERTYMode = TenKeyQWERTYMode.Default,
            qwertyReturnSource = RestartInputModeQwertyReturnSource.TenKeyDefault,
            currentInputMode = InputMode.ModeEnglish,
            isQwertyViewVisible = true
        )

        assertEquals(
            RestartInputModeState(
                KeyboardType.TENKEY,
                InputMode.ModeEnglish,
                RestartInputModePresentation.Native
            ),
            state
        )
    }

    @Test
    fun legacyInputModeOnlySavedStateRestoresAsNativePresentation() {
        assertEquals(
            RestartInputModeState(
                KeyboardType.TENKEY,
                InputMode.ModeEnglish,
                RestartInputModePresentation.Native
            ),
            RestartInputModePreference.resolveRestoredState(
                type = KeyboardType.TENKEY,
                tenkeyRestoreEnabled = true,
                sumireRestoreEnabled = false,
                tenkeyLastInputModePreference = "english",
                tenkeyLastInputModePresentationPreference = "native",
                sumireLastInputModePreference = "number",
                sumireLastInputModePresentationPreference = "native"
            )
        )
    }

    @Test
    fun invalidPresentationFallsBackToNative() {
        assertEquals(
            RestartInputModeState(
                KeyboardType.SUMIRE,
                InputMode.ModeJapanese,
                RestartInputModePresentation.Native
            ),
            RestartInputModePreference.resolveRestoredState(
                type = KeyboardType.SUMIRE,
                tenkeyRestoreEnabled = false,
                sumireRestoreEnabled = true,
                tenkeyLastInputModePreference = "number",
                tenkeyLastInputModePresentationPreference = "tenkey_qwerty_number_proxy",
                sumireLastInputModePreference = "japanese",
                sumireLastInputModePresentationPreference = "sumire_qwerty_proxy"
            )
        )
    }

    @Test
    fun restartPreferenceValuesDoNotUseClassOrObjectNames() {
        assertNotEquals(
            InputMode.ModeEnglish::class.simpleName,
            InputMode.ModeEnglish.toRestartPreferenceValue()
        )
        assertNotEquals(
            InputMode.ModeNumber.toString(),
            InputMode.ModeNumber.toRestartPreferenceValue()
        )
        assertEquals(
            setOf("japanese", "english", "number"),
            setOf(
                InputMode.ModeJapanese.toRestartPreferenceValue(),
                InputMode.ModeEnglish.toRestartPreferenceValue(),
                InputMode.ModeNumber.toRestartPreferenceValue()
            )
        )
    }

    @Test
    fun customAndNumberQwertyModesAreNotPersistenceTargets() {
        assertNull(
            RestartInputModePreference.resolvePersistenceTarget(
                currentKeyboardType = KeyboardType.CUSTOM,
                activeTenKeyQwertyMode = TenKeyQWERTYMode.Custom,
                previousTenKeyQWERTYMode = TenKeyQWERTYMode.Custom
            )
        )
        assertNull(
            RestartInputModePreference.resolvePersistenceTarget(
                currentKeyboardType = KeyboardType.QWERTY,
                activeTenKeyQwertyMode = TenKeyQWERTYMode.Number,
                previousTenKeyQWERTYMode = TenKeyQWERTYMode.Number
            )
        )
    }

    private fun persistenceValue(
        currentInputType: InputTypeForIME = InputTypeForIME.Text,
        target: KeyboardType?,
        tenkeyRestoreEnabled: Boolean = false,
        sumireRestoreEnabled: Boolean = false,
        currentInputMode: InputMode,
        presentation: RestartInputModePresentation = RestartInputModePresentation.Native
    ): RestartInputModePersistence? {
        return RestartInputModePreference.resolvePersistenceValue(
            currentInputType = currentInputType,
            passwordTypesWithoutNumber = passwordTypesWithoutNumber,
            numberTypes = numberTypes,
            target = target,
            tenkeyRestoreEnabled = tenkeyRestoreEnabled,
            sumireRestoreEnabled = sumireRestoreEnabled,
            currentInputMode = currentInputMode,
            presentation = presentation
        )
    }

    private fun persistenceState(
        currentKeyboardType: KeyboardType?,
        activeTenKeyQwertyMode: TenKeyQWERTYMode,
        previousTenKeyQWERTYMode: TenKeyQWERTYMode? = null,
        qwertyReturnSource: RestartInputModeQwertyReturnSource =
            RestartInputModeQwertyReturnSource.None,
        currentInputMode: InputMode,
        tenkeyUseThreeStateKeyboard: Boolean = false,
        sumireEnglishQwertyPreference: Boolean = false,
        isQwertyViewVisible: Boolean = false,
        isQwertyNumberLayout: Boolean = false
    ): RestartInputModeState? {
        return RestartInputModePreference.resolveStateForPersistence(
            currentKeyboardType = currentKeyboardType,
            activeTenKeyQwertyMode = activeTenKeyQwertyMode,
            previousTenKeyQWERTYMode = previousTenKeyQWERTYMode,
            qwertyReturnSource = qwertyReturnSource,
            currentInputMode = currentInputMode,
            tenkeyUseThreeStateKeyboard = tenkeyUseThreeStateKeyboard,
            sumireEnglishQwertyPreference = sumireEnglishQwertyPreference,
            isQwertyViewVisible = isQwertyViewVisible,
            isQwertyNumberLayout = isQwertyNumberLayout
        )
    }
}
