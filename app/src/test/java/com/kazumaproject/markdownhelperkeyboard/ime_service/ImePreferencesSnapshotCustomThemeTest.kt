package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertTrue
import org.junit.Test

class ImePreferencesSnapshotCustomThemeTest {

    @Test
    fun snapshotContainsCandidateAndShortcutThemeColors() {
        val fieldNames = ImePreferencesSnapshot::class.java.declaredFields
            .map { it.name }
            .toSet()

        assertTrue(fieldNames.contains("customThemeCandidateTextColor"))
        assertTrue(fieldNames.contains("customThemeCandidateItemBgColor"))
        assertTrue(fieldNames.contains("customThemeCandidateItemPressedBgColor"))
        assertTrue(fieldNames.contains("customThemeShortcutIconColor"))
    }
}
