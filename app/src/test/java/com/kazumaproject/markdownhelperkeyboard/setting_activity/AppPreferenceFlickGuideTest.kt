package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceFlickGuideTest {

    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun flickGuidePreferences_useExistingDisplayDefaults() {
        assertEquals(9, AppPreference.flick_guide_text_size_sp_preference)
        assertEquals(1, AppPreference.flick_guide_max_characters_preference)
        assertFalse(AppPreference.tenkey_keymap_guide_layout ?: true)
        assertFalse(AppPreference.tenkey_keymap_guide_english)
        assertFalse(AppPreference.tenkey_keymap_guide_number)
        assertFalse(AppPreference.sumire_keymap_guide_japanese)
        assertFalse(AppPreference.sumire_keymap_guide_english)
        assertFalse(AppPreference.sumire_keymap_guide_number)
        assertFalse(AppPreference.flick_keymap_guide_layout ?: true)
    }

    @Test
    fun modeGuidePreferences_areStoredIndependently() {
        AppPreference.tenkey_keymap_guide_layout = true
        AppPreference.tenkey_keymap_guide_english = false
        AppPreference.tenkey_keymap_guide_number = true
        AppPreference.sumire_keymap_guide_japanese = false
        AppPreference.sumire_keymap_guide_english = true
        AppPreference.sumire_keymap_guide_number = false
        AppPreference.flick_keymap_guide_layout = true

        assertTrue(AppPreference.tenkey_keymap_guide_layout == true)
        assertFalse(AppPreference.tenkey_keymap_guide_english)
        assertTrue(AppPreference.tenkey_keymap_guide_number)
        assertFalse(AppPreference.sumire_keymap_guide_japanese)
        assertTrue(AppPreference.sumire_keymap_guide_english)
        assertFalse(AppPreference.sumire_keymap_guide_number)
        assertTrue(AppPreference.flick_keymap_guide_layout == true)
    }

    @Test
    fun legacySharedFlickGuide_migratesToSumireJapaneseWithoutChangingCustom() {
        preferences.edit()
            .clear()
            .putBoolean(AppPreference.CUSTOM_KEYMAP_GUIDE_KEY, true)
            .commit()

        AppPreference.init(context)

        assertTrue(AppPreference.sumire_keymap_guide_japanese)
        assertFalse(AppPreference.sumire_keymap_guide_english)
        assertFalse(AppPreference.sumire_keymap_guide_number)
        assertTrue(AppPreference.flick_keymap_guide_layout == true)
    }

    @Test
    fun migration_doesNotOverwriteExistingSumireJapaneseValue() {
        preferences.edit()
            .clear()
            .putBoolean(AppPreference.CUSTOM_KEYMAP_GUIDE_KEY, true)
            .putBoolean(AppPreference.SUMIRE_KEYMAP_GUIDE_JAPANESE_KEY, false)
            .commit()

        AppPreference.init(context)

        assertFalse(AppPreference.sumire_keymap_guide_japanese)
        assertTrue(AppPreference.flick_keymap_guide_layout == true)
    }

    @Test
    fun zeroQuerySuggestionPreference_defaultOff() {
        assertFalse(AppPreference.zero_query_suggestion_preference)
    }

    @Test
    fun flickThresholdShape_defaultsToRadialAndRejectsUnknownValues() {
        assertEquals(
            FlickThresholdShape.Radial.preferenceValue,
            AppPreference.flick_threshold_shape_preference
        )

        AppPreference.flick_threshold_shape_preference = "unknown"

        assertEquals(
            FlickThresholdShape.Radial.preferenceValue,
            AppPreference.flick_threshold_shape_preference
        )
    }
}
