package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.assertEquals
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
            RestartInputModePersistence(KeyboardType.SUMIRE, "english"),
            persistenceValue(
                target = target,
                sumireRestoreEnabled = true,
                currentInputMode = InputMode.ModeEnglish
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
        currentInputMode: InputMode
    ): RestartInputModePersistence? {
        return RestartInputModePreference.resolvePersistenceValue(
            currentInputType = currentInputType,
            passwordTypesWithoutNumber = passwordTypesWithoutNumber,
            numberTypes = numberTypes,
            target = target,
            tenkeyRestoreEnabled = tenkeyRestoreEnabled,
            sumireRestoreEnabled = sumireRestoreEnabled,
            currentInputMode = currentInputMode
        )
    }
}
