package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KeyboardVisualStyleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    @Before fun setUp() {
        prefs.edit().clear().commit()
        AppPreference.init(context)
        AppPreference.custom_theme_bg_color = 0xff123456.toInt()
        AppPreference.custom_theme_key_color = 0xff345678.toInt()
        AppPreference.custom_theme_special_key_color = 0xff56789a.toInt()
        AppPreference.custom_theme_key_text_color = 0xff789abc.toInt()
        AppPreference.custom_theme_special_key_text_color = 0xff9abcde.toInt()
        AppPreference.liquid_glass_preference = true
        AppPreference.liquid_glass_key_alpha = 123
        AppPreference.custom_theme_border_enable = true
    }

    @Test fun eachSkinPreservesTheExistingImeAppearancePolicy() {
        for (mode in listOf("default", "custom")) for (skin in KeyboardSkinId.entries) {
            AppPreference.theme_mode = mode
            AppPreference.keyboardSkin = skin
            checkStyle(skin)
        }
    }

    @Test fun previewingAnotherSkinUsesItsPaletteWithoutChangingPreferences() {
        AppPreference.theme_mode = "default"
        AppPreference.keyboardSkin = KeyboardSkinId.DEFAULT
        val saved = prefs.all.toMap()
        for (skin in KeyboardSkinId.entries) checkStyle(skin)
        assertEquals(saved, prefs.all)
    }

    private fun checkStyle(skin: KeyboardSkinId) {
        // The established IME snapshot is the reference, including its skin overrides.
        val expected = ImePreferencesSnapshot.from(AppPreference).copy(keyboardSkin = skin)
            .withKeyboardSkinAppearance().appearance
        val actual = KeyboardVisualStyle.read(context, skin)
        assertEquals("$skin theme mode", expected.keyboardThemeMode, actual.mode)
        assertEquals("$skin background", expected.customThemeBgColor, actual.background)
        assertEquals("$skin character keys", expected.customThemeKeyColor, actual.key)
        assertEquals("$skin special keys", expected.customThemeSpecialKeyColor, actual.special)
        assertEquals("$skin text", expected.customThemeKeyTextColor, actual.text)
        assertEquals("$skin special text", expected.customThemeSpecialKeyTextColor, actual.specialText)
        assertEquals("$skin glass", expected.liquidGlassThemePreference, actual.glass)
        assertEquals("$skin alpha", expected.liquidGlassKeyBlurRadiousPreference, actual.keyAlpha)
        assertEquals("$skin borders", expected.customKeyBorderEnablePreference ?: false, actual.border)
    }
}
