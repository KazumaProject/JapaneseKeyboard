package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardSkinAppearanceTest {
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun defaultReturnsOriginalSnapshot() {
        val saved = ImePreferencesSnapshot.from(AppPreference)
        assertSame(saved, saved.withKeyboardSkinAppearance())
    }

    @Test fun skinsOverrideOnlyAppearanceAndDefaultRestoresCustomSettings() {
        AppPreference.theme_mode = "custom"
        AppPreference.custom_theme_key_color = 0xff765432.toInt()
        val baseline = ImePreferencesSnapshot.from(AppPreference)
        val appearanceFields = setOf("keyboardThemeMode", "customThemeBgColor", "customThemeKeyColor",
            "customThemeSpecialKeyColor", "customThemeKeyTextColor", "customThemeSpecialKeyTextColor",
            "customThemeCandidateTextColor", "customThemeCandidateItemBgColor",
            "customThemeCandidateItemPressedBgColor", "customThemeCandidateEmptyPopupBgColor",
            "customThemeCandidateEmptyPopupTextColor", "customThemeShortcutIconColor",
            "liquidGlassThemePreference", "liquidGlassKeyBlurRadiousPreference",
            "keyboardTouchEffectTypePreference", "customKeyBorderEnablePreference")
        for (id in listOf(KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK)) {
            AppPreference.keyboardSkin = id
            val saved = ImePreferencesSnapshot.from(AppPreference)
            val effective = saved.withKeyboardSkinAppearance()
            assertEquals("custom", effective.keyboardThemeMode)
            assertFalse(effective.liquidGlassThemePreference)
            assertFalse(effective.customKeyBorderEnablePreference)
            ImePreferencesSnapshot::class.java.declaredFields.filter {
                !java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name !in appearanceFields
            }.forEach { field ->
                field.isAccessible = true
                assertEquals("Input/layout field ${field.name} changed", field.get(saved), field.get(effective))
            }
            assertEquals(0xff765432.toInt(), AppPreference.custom_theme_key_color)
        }
        AppPreference.keyboardSkin = KeyboardSkinId.DEFAULT
        assertEquals(baseline, ImePreferencesSnapshot.from(AppPreference).withKeyboardSkinAppearance())
    }
}
