package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertTrue
import org.junit.Test

class ImePreferencesSnapshotCustomThemeTest {

    @Test
    fun snapshotContainsCandidateAndShortcutThemeColors() {
        val fieldNames = ImePreferencesSnapshot::class.java.methods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") }
            .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar) }
            .toSet()

        assertTrue(fieldNames.contains("customThemeCandidateTextColor"))
        assertTrue(fieldNames.contains("customThemeCandidateItemBgColor"))
        assertTrue(fieldNames.contains("customThemeCandidateItemPressedBgColor"))
        assertTrue(fieldNames.contains("customThemeCandidateEmptyPopupBgColor"))
        assertTrue(fieldNames.contains("customThemeCandidateEmptyPopupTextColor"))
        assertTrue(fieldNames.contains("customThemeShortcutIconColor"))
    }
}
