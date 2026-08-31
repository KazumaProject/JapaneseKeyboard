package com.kazumaproject.markdownhelperkeyboard.ime_service

internal object TenkeyEnglishQwertySwitchResolver {
    fun shouldSwitchEnglishToQwerty(
        isTablet: Boolean?,
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
