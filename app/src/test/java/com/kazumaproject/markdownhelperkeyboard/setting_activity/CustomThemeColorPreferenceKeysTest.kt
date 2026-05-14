package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CustomThemeColorPreferenceKeysTest {

    @Test
    fun customThemeCandidateAndShortcutKeysAreUnique() {
        val keys = listOf(
            CustomThemeColorPreferenceKeys.CANDIDATE_TEXT_COLOR,
            CustomThemeColorPreferenceKeys.CANDIDATE_ITEM_BG_COLOR,
            CustomThemeColorPreferenceKeys.CANDIDATE_ITEM_PRESSED_BG_COLOR,
            CustomThemeColorPreferenceKeys.SHORTCUT_ICON_COLOR,
        )

        assertEquals(keys.size, keys.toSet().size)
        assertEquals("custom_theme_candidate_text_color", keys[0])
        assertEquals("custom_theme_candidate_item_bg_color", keys[1])
        assertEquals("custom_theme_candidate_item_pressed_bg_color", keys[2])
        assertEquals("custom_theme_shortcut_icon_color", keys[3])
    }

    @Test
    fun customThemeCandidateDefaultsKeepCompatibility() {
        assertEquals(Color.TRANSPARENT, AppPreference.DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_BG_COLOR)
        assertFalse(
            "Pressed background default should be an opaque fallback when resource resolution is unavailable.",
            AppPreference.DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_PRESSED_BG_COLOR == Color.TRANSPARENT
        )
    }
}
