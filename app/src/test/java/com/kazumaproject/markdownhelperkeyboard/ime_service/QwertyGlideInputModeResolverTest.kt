package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyGlideInputModeResolverTest {
    @Test
    fun preferenceDefaultFalseKeepsGlideModeFalse() {
        assertFalse(
            QwertyGlideInputModeResolver.resolve(
                qwertyGlideInputPreference = false,
                isQwertySurfaceActive = true,
                currentQwertyRomajiModeForSession = false
            )
        )
    }

    @Test
    fun preferenceOnDoesNotRequireRomajiModeMutation() {
        val romajiMode = true
        val result = QwertyGlideInputModeResolver.resolve(
            qwertyGlideInputPreference = true,
            isQwertySurfaceActive = true,
            currentQwertyRomajiModeForSession = romajiMode
        )

        assertTrue(romajiMode)
        assertFalse(result)
    }

    @Test
    fun preferenceOnQwertyActiveEnglishModeEnablesGlideMode() {
        assertTrue(
            QwertyGlideInputModeResolver.resolve(
                qwertyGlideInputPreference = true,
                isQwertySurfaceActive = true,
                currentQwertyRomajiModeForSession = false
            )
        )
    }

    @Test
    fun preferenceOnQwertyActiveRomajiModeDisablesGlideMode() {
        assertFalse(
            QwertyGlideInputModeResolver.resolve(
                qwertyGlideInputPreference = true,
                isQwertySurfaceActive = true,
                currentQwertyRomajiModeForSession = true
            )
        )
    }

    @Test
    fun inactiveQwertySurfaceDisablesGlideMode() {
        assertFalse(
            QwertyGlideInputModeResolver.resolve(
                qwertyGlideInputPreference = true,
                isQwertySurfaceActive = false,
                currentQwertyRomajiModeForSession = false
            )
        )
    }

    @Test
    fun switchingRomajiOffRestoresGlideModeWhenPreferenceIsOn() {
        val romajiOn = QwertyGlideInputModeResolver.resolve(
            qwertyGlideInputPreference = true,
            isQwertySurfaceActive = true,
            currentQwertyRomajiModeForSession = true
        )
        val romajiOff = QwertyGlideInputModeResolver.resolve(
            qwertyGlideInputPreference = true,
            isQwertySurfaceActive = true,
            currentQwertyRomajiModeForSession = false
        )

        assertFalse(romajiOn)
        assertTrue(romajiOff)
    }
}
