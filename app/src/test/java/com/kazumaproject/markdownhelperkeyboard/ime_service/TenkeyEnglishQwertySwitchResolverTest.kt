package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenkeyEnglishQwertySwitchResolverTest {
    @Test
    fun tabletGojuonUsesTabletPreferenceWhenEnabled() {
        assertTrue(
            TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                isTablet = true,
                tabletGojuonLayoutPreference = true,
                tabletTenkeyQwertySwitchEnglish = true,
                tenkeyQwertySwitchEnglish = false
            )
        )
    }

    @Test
    fun tabletGojuonDoesNotUsePhonePreferenceWhenTabletPreferenceIsDisabled() {
        assertFalse(
            TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                isTablet = true,
                tabletGojuonLayoutPreference = true,
                tabletTenkeyQwertySwitchEnglish = false,
                tenkeyQwertySwitchEnglish = true
            )
        )
    }

    @Test
    fun tabletTenkeyUsesTabletPreferenceWhenGojuonLayoutIsOff() {
        assertTrue(
            TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                isTablet = true,
                tabletGojuonLayoutPreference = false,
                tabletTenkeyQwertySwitchEnglish = true,
                tenkeyQwertySwitchEnglish = false
            )
        )
    }

    @Test
    fun tabletTenkeyDoesNotUsePhonePreferenceWhenGojuonLayoutIsOff() {
        assertFalse(
            TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                isTablet = true,
                tabletGojuonLayoutPreference = false,
                tabletTenkeyQwertySwitchEnglish = false,
                tenkeyQwertySwitchEnglish = true
            )
        )
    }

    @Test
    fun phoneTenkeyUsesExistingPhonePreference() {
        assertTrue(
            TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                isTablet = false,
                tabletGojuonLayoutPreference = false,
                tabletTenkeyQwertySwitchEnglish = false,
                tenkeyQwertySwitchEnglish = true
            )
        )
        assertFalse(
            TenkeyEnglishQwertySwitchResolver.shouldSwitchEnglishToQwerty(
                isTablet = false,
                tabletGojuonLayoutPreference = false,
                tabletTenkeyQwertySwitchEnglish = true,
                tenkeyQwertySwitchEnglish = false
            )
        )
    }

    @Test
    fun activeSurfaceHelpersSeparateTabletGojuonAndTabletTenkey() {
        assertTrue(
            TenkeyEnglishQwertySwitchResolver.isTabletGojuonSurface(
                isTablet = true,
                tabletGojuonLayoutPreference = true
            )
        )
        assertTrue(
            TenkeyEnglishQwertySwitchResolver.isTabletTenkeySurface(
                isTablet = true,
                tabletGojuonLayoutPreference = false
            )
        )
        assertFalse(
            TenkeyEnglishQwertySwitchResolver.isTabletGojuonSurface(
                isTablet = true,
                tabletGojuonLayoutPreference = false
            )
        )
    }
}
