package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingReclassificationTest {
    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)

    @Before fun reset() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences(CollapsiblePreferenceCategory.STATE_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
        AppPreference.init(context)
    }

    private fun inflate(xml: Int): PreferenceScreen {
        val manager = PreferenceManager(context)
        val screen = manager.inflateFromResource(context, xml, null)
        manager.setPreferences(screen)
        screen.onAttached()
        return screen
    }

    @Test fun categoryOrderAndOwnershipAreStable() {
        val cases = listOf(
            R.xml.pref_common_legacy to listOf("selection", "size", "touch", "editing", "candidates", "clipboard", "symbols", "feedback", "privacy", "app").map { "settings_common_$it" },
            R.xml.pref_theme to listOf("skin", "colors", "text", "background", "corners", "effects").map { "settings_theme_$it" },
            R.xml.pref_conversion_engine to listOf("conversion", "live", "prediction", "dictionaries", "correction", "candidates", "search").map { "settings_engine_$it" },
        )
        cases.forEach { (xml, expected) ->
            val screen = inflate(xml)
            assertEquals(expected, (0 until screen.preferenceCount).map { screen.getPreference(it).key })
            (0 until screen.preferenceCount).forEach { index -> assertFalse((screen.getPreference(index) as CollapsiblePreferenceCategory).isExpanded) }
        }
        val common = inflate(R.xml.pref_common_legacy)
        val theme = inflate(R.xml.pref_theme)
        val engine = inflate(R.xml.pref_conversion_engine)
        val candidate = inflate(R.xml.pref_candidate_display)
        val destinations = mapOf(
            "custom_romaji_preference" to (common to "settings_common_selection"),
            "floating_cursor_enabled" to (common to "settings_common_editing"),
            "composing_guide_text_enabled" to (common to "settings_common_candidates"),
            "inline_suggestion_enabled_preference" to (common to "settings_common_privacy"),
            "composing_guide_text_size_setting" to (theme to "settings_theme_text"),
            "candidate_character_type_labels_preference" to (candidate to null),
            "keyboard_background_image_clear_preference" to (theme to "settings_theme_background"),
            "round_corner_keyboard_preference" to (theme to "settings_theme_corners"),
            "delete_long_press_conversion_behavior" to (engine to "settings_engine_conversion"),
            "live_conversion_preference" to (engine to "settings_engine_live"),
            "zero_query_suggestion_preference" to (engine to "settings_engine_prediction"),
            "conversion_candidates_romaji_enable_preference" to (engine to "settings_engine_candidates"),
            "conversion_beam_width_preference" to (engine to "settings_engine_search"),
        )
        destinations.forEach { (key, expected) -> assertEquals(key, expected.second, expected.first.findPreference<Preference>(key)?.parent?.key) }
        assertNull(theme.findPreference<Preference>("composing_guide_text_size_setting")?.dependency)
        assertNull(candidate.findPreference<Preference>("live_conversion_candidate_yomi_preference")?.dependency)
        assertNull(engine.findPreference<Preference>("delete_long_press_conversion_behavior")?.dependency)
    }

    @Test fun sameScreenDependenciesAndSavedValuesSurviveScreenInflation() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("live_conversion_preference", false).putInt("n_best_preference", 7).commit()
        val screen = inflate(R.xml.pref_conversion_engine)
        val live = screen.findPreference<SwitchPreferenceCompat>("live_conversion_preference")!!
        val length = screen.findPreference<Preference>("live_conversion_start_length_preference")!!
        assertFalse(length.isEnabled)
        live.isChecked = true
        assertTrue(length.isEnabled)
        assertEquals(7, prefs.getInt("n_best_preference", 0))
    }

    @Test fun bothSearchesAndSavedKeysResolveToOneCanonicalEditor() {
        val expected = mapOf(
            "composing_guide_text_size_setting" to R.id.keyboardThemeFragment,
            "round_corner_keyboard_preference" to R.id.keyboardThemeFragment,
            "candidate_character_type_labels_preference" to R.id.candidateDisplayPreferenceFragment,
            "live_conversion_candidate_yomi_preference" to R.id.candidateDisplayPreferenceFragment,
            "candidate_column_preference" to R.id.candidateDisplayPreferenceFragment,
            "show_dictionary_candidate_labels_preference" to R.id.candidateDisplayPreferenceFragment,
            "theme_custom_candidate_item_bg_color" to R.id.candidateDisplayPreferenceFragment,
            "candidate_view_height_landscape_setting_fragment_preference" to R.id.candidateDisplayPreferenceFragment,
            "live_conversion_preference" to R.id.conversionEnginePreferenceFragment,
            "custom_romaji_preference" to R.id.legacyCommonPreferenceFragment,
        )
        for (scope in SettingSearchScope.entries) {
            val destinations = SettingSearchIndex.searchable(context, scope)
            expected.forEach { (key, destination) ->
                val matches = destinations.filter { it.key == key }
                assertEquals(key, 1, matches.size)
                assertEquals(key, destination, SettingDestinations.destinationId(matches.single().destination))
            }
        }
        expected.forEach { (key, destination) ->
            val saved = SettingSearchIndex.destinationsForKeys(context, listOf(key)).single()
            assertEquals(destination, SettingDestinations.destinationId(saved.destination))
            assertTrue(saved.destination is SettingDestinationType.NavDestination)
        }
    }
}
