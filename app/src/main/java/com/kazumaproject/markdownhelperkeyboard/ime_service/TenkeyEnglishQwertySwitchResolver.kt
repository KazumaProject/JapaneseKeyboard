package com.kazumaproject.markdownhelperkeyboard.ime_service

internal object TenkeyEnglishQwertySwitchResolver {
    fun isTabletGojuonSurface(
        isTablet: Boolean?,
        tabletGojuonLayoutPreference: Boolean?
    ): Boolean {
        return isTablet == true && tabletGojuonLayoutPreference == true
    }

    fun isTabletTenkeySurface(
        isTablet: Boolean?,
        tabletGojuonLayoutPreference: Boolean?
    ): Boolean {
        return isTablet == true && tabletGojuonLayoutPreference != true
    }

    fun shouldSwitchEnglishToQwerty(
        isTablet: Boolean?,
        tabletGojuonLayoutPreference: Boolean?,
        tabletTenkeyQwertySwitchEnglish: Boolean,
        tenkeyQwertySwitchEnglish: Boolean
    ): Boolean {
        return if (isTablet == true) {
            tabletTenkeyQwertySwitchEnglish
        } else {
            tenkeyQwertySwitchEnglish
        }
    }
}
