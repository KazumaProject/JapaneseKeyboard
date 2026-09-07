package com.kazumaproject.core.domain.skin

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardSkinIdTest {
    @Test fun missingOrUnavailableSkinKeepsLegacyKeyboard() {
        listOf(null, "", "removed_skin", "custom", "CUPERTINO_LIGHT").forEach {
            assertEquals(KeyboardSkinId.DEFAULT, KeyboardSkinId.fromPreference(it))
        }
    }

    @Test fun storedIdsRoundTripWithoutUsingEnumNamesOrOrdinals() {
        KeyboardSkinId.entries.forEach {
            assertEquals(it, KeyboardSkinId.fromPreference(it.preferenceValue))
        }
        assertEquals(3, KeyboardSkinId.entries.map { it.preferenceValue }.toSet().size)
    }
}
