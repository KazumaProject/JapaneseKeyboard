package com.kazumaproject.markdownhelperkeyboard.setting_activity.backup

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.CustomThemeColorPreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CustomThemeBackupTest {
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        AppPreference.init(context)
        preferences.edit().clear().apply()
    }

    @Test
    fun roundTripRestoresTheThemeWithoutChangingOtherSettings() {
        AppPreference.theme_mode = "custom"
        AppPreference.keyboardSkin = KeyboardSkinId.DEFAULT
        AppPreference.custom_theme_bg_color = Color.TRANSPARENT
        AppPreference.custom_theme_key_color = Color.BLUE
        AppPreference.custom_theme_special_key_color = Color.YELLOW
        AppPreference.custom_theme_key_text_color = Color.WHITE
        AppPreference.custom_theme_special_key_text_color = Color.MAGENTA
        AppPreference.custom_theme_candidate_text_color = Color.GREEN
        AppPreference.custom_theme_shortcut_icon_color = Color.RED
        AppPreference.custom_theme_border_enable = true
        AppPreference.custom_theme_border_color = Color.WHITE
        AppPreference.custom_theme_border_width = 8
        AppPreference.keyboard_corner_round_preference = true
        AppPreference.keyboard_corner_radius_dp_preference = 40
        AppPreference.liquid_glass_preference = true
        AppPreference.liquid_glass_blur_radius = 100
        AppPreference.liquid_glass_key_alpha = 160
        AppPreference.key_popup_use_custom_color = true
        AppPreference.key_popup_background_color = Color.CYAN
        AppPreference.custom_theme_input_color_enable = true
        AppPreference.custom_theme_pre_edit_text_color = Color.RED
        val unrelated = "keyboard_background_image_uri_preference"
        preferences.edit().putString(unrelated, "content://source/background").apply()
        val original = preferences.all

        val json = AppPreference.exportCustomThemeToJson()
        val theme = JsonParser.parseString(json).asJsonObject.getAsJsonObject("preferences")
        assertFalse(theme.has(unrelated))

        preferences.edit().clear().putString(unrelated, "content://destination/background").apply()
        AppPreference.keyboardSkin = KeyboardSkinId.CUPERTINO_DARK
        AppPreference.importCustomThemeFromJson(json.byteInputStream())

        assertEquals(original + (unrelated to "content://destination/background"), preferences.all)
        assertEquals("custom", AppPreference.theme_mode)
        assertEquals(Color.TRANSPARENT, AppPreference.custom_theme_bg_color)
        assertEquals(KeyboardSkinId.DEFAULT, AppPreference.keyboardSkin)
        assertEquals(8, AppPreference.custom_theme_border_width)
        assertEquals(160, AppPreference.liquid_glass_key_alpha)
    }

    @Test
    fun restoringAnUnsetColorRemovesTheOverrideAndUsesTheThemeFallback() {
        AppPreference.custom_theme_key_text_color = Color.RED
        val json = AppPreference.exportCustomThemeToJson()
        AppPreference.custom_theme_key_text_color = Color.WHITE
        AppPreference.custom_theme_candidate_text_color = Color.GREEN

        AppPreference.importCustomThemeFromJson(json.byteInputStream())

        assertFalse(preferences.contains(CustomThemeColorPreferenceKeys.CANDIDATE_TEXT_COLOR))
        assertEquals(Color.RED, AppPreference.custom_theme_candidate_text_color)
    }

    @Test
    fun rejectsInvalidAndUnrelatedBackupsBeforeWritingPreferences() {
        AppPreference.theme_mode = "custom"
        val exported = JsonParser.parseString(AppPreference.exportCustomThemeToJson()).asJsonObject
        AppPreference.theme_mode = "default"
        val original = preferences.all
        val invalidFiles = listOf(
            "{",
            AppPreference.exportAllToJson(),
            exported.deepCopy().apply { addProperty("version", 2) }.toString(),
            exported.deepCopy().apply {
                getAsJsonObject("preferences").addProperty("liquid_glass_key_alpha_preference", 256)
            }.toString(),
            exported.deepCopy().apply {
                getAsJsonObject("preferences").addProperty("liquid_glass_preference", "true")
            }.toString(),
            exported.deepCopy().apply {
                getAsJsonObject("preferences").addProperty("unrelated_preference", true)
            }.toString(),
        )

        for (json in invalidFiles) {
            assertThrows(RuntimeException::class.java) {
                AppPreference.importCustomThemeFromJson(json.byteInputStream())
            }
            assertEquals(original, preferences.all)
        }
    }

    @Test
    fun rejectsThemeFilesOver64KiBBeforeWritingPreferences() {
        AppPreference.theme_mode = "custom"
        val json = AppPreference.exportCustomThemeToJson()
        val atLimit = json + " ".repeat(64 * 1024 - json.encodeToByteArray().size)

        AppPreference.theme_mode = "default"
        AppPreference.importCustomThemeFromJson(atLimit.byteInputStream())
        assertEquals("custom", AppPreference.theme_mode)

        AppPreference.theme_mode = "default"
        val original = preferences.all
        assertThrows(IllegalArgumentException::class.java) {
            AppPreference.importCustomThemeFromJson((atLimit + " ").byteInputStream())
        }
        assertEquals(original, preferences.all)
    }
}
