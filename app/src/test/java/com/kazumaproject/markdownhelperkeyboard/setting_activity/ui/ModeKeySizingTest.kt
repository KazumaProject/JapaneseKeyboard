package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModeKeySizingTest {
    private val japanese = KeyboardInputMode.HIRAGANA
    private val english = KeyboardInputMode.ENGLISH
    private val numbers = KeyboardInputMode.SYMBOLS

    @Before fun initialize() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun legacyValuesAreResolvedWithoutWritingOverrides() {
        AppPreference.key_letter_size = 2.5f
        AppPreference.flick_key_text_size_sp = 21f
        AppPreference.flick_guide_text_size_sp_preference = 13
        assertEquals(19f, ModeKeySizing.tenkeyCenter(japanese, false), 0f)
        assertEquals(14f, ModeKeySizing.tenkeyCenter(english, false), 0f)
        assertEquals(18f, ModeKeySizing.tenkeyCenter(numbers, false), 0f)
        assertEquals(13f, ModeKeySizing.tenkeyCenter(japanese, true), 0f)
        assertEquals(7.8f, ModeKeySizing.tenkeyGuide(japanese), 0.001f)
        assertEquals(21f, ModeKeySizing.sumireCenter(japanese), 0f)
        assertEquals(13f, ModeKeySizing.sumireGuide(japanese), 0f)
        for (surface in listOf("tenkey", "sumire")) for (mode in KeyboardInputMode.entries) {
            assertNull(AppPreference.modeKeySize(surface, mode, false))
            assertNull(AppPreference.modeKeySize(surface, mode, true))
        }
    }

    @Test fun modesSurfacesAndGuideSizesRemainIndependent() {
        AppPreference.setModeKeySize("tenkey", japanese, false, 24f)
        AppPreference.setModeKeySize("tenkey", japanese, true, 10f)
        AppPreference.setModeKeySize("tenkey", english, false, 18f)
        AppPreference.setModeKeySize("sumire", japanese, false, 28f)
        assertEquals(24f, ModeKeySizing.tenkeyCenter(japanese, true), 0f)
        assertEquals(10f, ModeKeySizing.tenkeyGuide(japanese), 0f)
        assertEquals(18f, ModeKeySizing.tenkeyCenter(english, false), 0f)
        assertEquals(6.6f, ModeKeySizing.tenkeyGuide(english), .001f)
        assertEquals(16f, ModeKeySizing.tenkeyCenter(numbers, false), 0f)
        assertEquals(28f, ModeKeySizing.sumireCenter(japanese), 0f)
        assertEquals(9f, ModeKeySizing.sumireGuide(japanese), 0f)
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppPreference.init(context)
        assertEquals(24f, ModeKeySizing.tenkeyCenter(japanese, true), 0f)
    }

    @Test fun resettingOneModeAndOneSurfaceKeepsOtherSettings() {
        for (surface in listOf("tenkey", "sumire")) for (mode in KeyboardInputMode.entries) {
            AppPreference.setModeKeySize(surface, mode, false, 22f)
            AppPreference.setModeKeySize(surface, mode, true, 12f)
        }
        AppPreference.resetModeKeySizes("tenkey", english)
        assertNull(AppPreference.modeKeySize("tenkey", english, false))
        assertNull(AppPreference.modeKeySize("tenkey", english, true))
        assertEquals(22f, ModeKeySizing.tenkeyCenter(japanese, false), 0f)
        assertEquals(22f, ModeKeySizing.sumireCenter(english), 0f)
        AppPreference.resetModeKeySizes("tenkey")
        assertNull(AppPreference.modeKeySize("tenkey", japanese, false))
        assertEquals(12f, ModeKeySizing.sumireGuide(numbers), 0f)
    }

    @Test fun explicitSizesUseSupportedBounds() {
        AppPreference.setModeKeySize("tenkey", japanese, false, 99f)
        AppPreference.setModeKeySize("sumire", english, false, 1f)
        AppPreference.setModeKeySize("tenkey", numbers, true, 99f)
        assertEquals(40f, ModeKeySizing.tenkeyCenter(japanese, false), 0f)
        assertEquals(8f, ModeKeySizing.sumireCenter(english), 0f)
        assertEquals(16f, ModeKeySizing.tenkeyGuide(numbers), 0f)
    }
}
