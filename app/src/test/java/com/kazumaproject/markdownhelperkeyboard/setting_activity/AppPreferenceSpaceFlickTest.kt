package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceSpaceFlickTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun defaultsPreserveTenkeyAndOptInRomaji() {
        assertTrue(AppPreference.tenkey_space_flick_preference)
        assertFalse(AppPreference.qwerty_romaji_space_flick_preference)
    }

    @Test
    fun newAndLegacySearchResolveSplitCategoriesAndDefaults() {
        val expected = mapOf(
            "tenkey_space_flick_preference" to (R.id.kanaPreferenceFragment to true),
            "qwerty_romaji_space_flick_preference" to (R.id.qwertyRomajiPreferenceFragment to false)
        )
        val newItems = SettingSearchIndex.searchable(context)
        for ((key, target) in expected) {
            val setting = newItems.first { it.key == key }.destination as SettingDestinationType.SwitchPreference
            assertEquals(target.second, setting.defaultValue)
        }
        for (items in listOf(newItems, SettingSearchIndex.legacySearchable(context))) {
            for ((key, target) in expected) {
                val item = items.first { it.key == key }
                assertEquals(target.first, SettingDestinations.destinationId(item.destination))
            }
            val english = items.first { it.key == "qwerty_english_direct_input_preference" }
            assertEquals(R.id.qwertyEnglishPreferenceFragment, SettingDestinations.destinationId(english.destination))
            val width = items.first { it.key == "qwerty_romaji_zenkaku_space_preference" }
            assertEquals(R.id.qwertyRomajiPreferenceFragment, SettingDestinations.destinationId(width.destination))
            assertFalse(items.any { it.key == "qwerty_english_space_flick_preference" })
        }
        val favorites = SettingDestinations.frequentCandidates(context).map { it.key }
        assertTrue(favorites.containsAll(expected.keys))
    }

    @Test
    fun switchesPersistIndependentlyWithoutChangingExistingWidths() {
        AppPreference.space_hankaku_preference = true
        AppPreference.qwerty_enable_zenkaku_space_preference = true
        AppPreference.tenkey_space_flick_preference = false
        AppPreference.qwerty_romaji_space_flick_preference = true
        AppPreference.init(context)
        assertFalse(AppPreference.tenkey_space_flick_preference)
        assertTrue(AppPreference.qwerty_romaji_space_flick_preference)
        val snapshot = ImePreferencesSnapshot.from(AppPreference)
        assertFalse(snapshot.tenkeySpaceFlickPreference)
        assertTrue(snapshot.qwertyRomajiSpaceFlickPreference)
        assertTrue(AppPreference.space_hankaku_preference == true)
        assertTrue(AppPreference.qwerty_enable_zenkaku_space_preference == true)
        AppPreference.tenkey_space_flick_preference = true
        assertTrue(AppPreference.qwerty_romaji_space_flick_preference)
    }
}
