package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceKeyResolverTest {

    @Test
    fun directModeUsesHalfWidthSpaceWhenPreferenceIsEnabled() {
        val space = resolveEmptySpaceForCurrentMode(
            isCustomLayoutDirectMode = true,
            customDirectModeSpaceHankakuPreference = true,
            isFlick = false,
            currentInputMode = InputMode.ModeJapanese
        )

        assertEquals(" ", space)
    }

    @Test
    fun directModeUsesFullWidthSpaceWhenPreferenceIsDisabled() {
        val space = resolveEmptySpaceForCurrentMode(
            isCustomLayoutDirectMode = true,
            customDirectModeSpaceHankakuPreference = false,
            isFlick = true,
            currentInputMode = InputMode.ModeEnglish
        )

        assertEquals("　", space)
    }

    @Test
    fun nonDirectJapaneseFlickKeepsHalfWidthSpace() {
        val space = resolveEmptySpaceForCurrentMode(
            isCustomLayoutDirectMode = false,
            customDirectModeSpaceHankakuPreference = false,
            isFlick = true,
            currentInputMode = InputMode.ModeJapanese
        )

        assertEquals(" ", space)
    }

    @Test
    fun nonDirectJapaneseTapKeepsFullWidthSpace() {
        val space = resolveEmptySpaceForCurrentMode(
            isCustomLayoutDirectMode = false,
            customDirectModeSpaceHankakuPreference = true,
            isFlick = false,
            currentInputMode = InputMode.ModeJapanese
        )

        assertEquals("　", space)
    }

    @Test
    fun nonDirectEnglishAndNumberKeepHalfWidthSpace() {
        listOf(InputMode.ModeEnglish, InputMode.ModeNumber).forEach { inputMode ->
            val space = resolveEmptySpaceForCurrentMode(
                isCustomLayoutDirectMode = false,
                customDirectModeSpaceHankakuPreference = false,
                isFlick = false,
                currentInputMode = inputMode
            )

            assertEquals(" ", space)
        }
    }
}
