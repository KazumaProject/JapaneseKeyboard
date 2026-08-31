package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenkeyEnglishQwertySwitchResolverTest {
    @Test
    fun tabletTenkeyFamilyUsesTabletPreference() {
        assertTrue(
            TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                isTablet = true,
                tabletTenkeyQwertySwitchEnglish = true,
                tenkeyQwertySwitchEnglish = false,
            )
        )
        assertFalse(
            TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                isTablet = true,
                tabletTenkeyQwertySwitchEnglish = false,
                tenkeyQwertySwitchEnglish = true,
            )
        )
    }

    @Test
    fun phoneTenkeyFamilyUsesPhonePreference() {
        assertTrue(
            TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                isTablet = false,
                tabletTenkeyQwertySwitchEnglish = false,
                tenkeyQwertySwitchEnglish = true,
            )
        )
        assertFalse(
            TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                isTablet = false,
                tabletTenkeyQwertySwitchEnglish = true,
                tenkeyQwertySwitchEnglish = false,
            )
        )
    }
}
