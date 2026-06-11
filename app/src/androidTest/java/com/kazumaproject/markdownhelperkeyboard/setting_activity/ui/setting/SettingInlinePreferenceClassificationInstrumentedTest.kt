package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class SettingInlinePreferenceClassificationInstrumentedTest {

    private lateinit var context: Context
    private lateinit var controller: SettingCardEditorController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        controller = SettingCardEditorController(context)
    }

    @Test
    fun switchPreferenceXmlItemIsClassifiedAsSwitchPreference() {
        val destination = destinationForKey("keyboard_floating_preference")
        val target = destination.destination as SettingDestinationType.SwitchPreference

        assertEquals("keyboard_floating_preference", target.preferenceKey)
        assertFalse(target.defaultValue)
        assertEquals(R.id.keyboardDisplayPreferenceFragment, target.destinationId)
    }

    @Test
    fun listPreferenceXmlItemIsClassifiedWithEntriesAndEntryValues() {
        val destination = destinationForKey("candidate_column_preference")
        val target = destination.destination as SettingDestinationType.ListPreference
        val entries = context.resources.getStringArray(target.entriesResId)
        val entryValues = context.resources.getStringArray(target.entryValuesResId)

        assertEquals("candidate_column_preference", target.preferenceKey)
        assertEquals("1", target.defaultValue)
        assertTrue(entries.isNotEmpty())
        assertEquals(entries.size, entryValues.size)
    }

    @Test
    fun suminagashiInkEffectIsClassifiedAsSwitchPreference() {
        val destination = destinationForKey("suminagashi_ink_effect_preference")
        val target = destination.destination as SettingDestinationType.SwitchPreference

        assertEquals("suminagashi_ink_effect_preference", target.preferenceKey)
        assertFalse(target.defaultValue)
        assertEquals(R.id.keyboardDisplayPreferenceFragment, target.destinationId)
    }

    @Test
    fun suminagashiInkColorModeIsClassifiedAsListPreference() {
        val destination = destinationForKey("suminagashi_ink_color_mode_preference")
        val target = destination.destination as SettingDestinationType.ListPreference
        val entries = context.resources.getStringArray(target.entriesResId)
        val entryValues = context.resources.getStringArray(target.entryValuesResId)

        assertEquals("suminagashi_ink_color_mode_preference", target.preferenceKey)
        assertEquals("random", target.defaultValue)
        assertEquals(entries.size, entryValues.size)
        assertEquals(listOf("random", "fixed"), entryValues.toList())
    }

    @Test
    fun seekBarPreferenceXmlItemIsClassifiedWithBoundsAndDefaultValue() {
        val destination = destinationForKey("flick_sensitivity_preference")
        val target = destination.destination as SettingDestinationType.SeekBarPreference

        assertEquals("flick_sensitivity_preference", target.preferenceKey)
        assertEquals(1, target.min)
        assertEquals(200, target.max)
        assertEquals(5, target.increment)
        assertEquals(100, target.defaultValue)
    }

    @Test
    fun editTextPreferenceXmlItemIsClassifiedAsEditTextPreference() {
        assumeTrue(AppVariantConfig.hasZenz)

        val destination = destinationForKey("zenz_profile_string_preference")
        val target = destination.destination as SettingDestinationType.EditTextPreference

        assertEquals("zenz_profile_string_preference", target.preferenceKey)
        assertEquals("", target.defaultValue)
        assertFalse(target.obscureValue)
    }

    @Test
    fun managementKeysAreNotClassifiedAsDirectInlineEditors() {
        val userDictionary =
            destinationForKey("user_dictionary_preference").destination
        val candidateTabOrder =
            destinationForKey("candidate_tab_order_preference").destination

        assertTrue(userDictionary is SettingDestinationType.ManagementDestination)
        assertTrue(candidateTabOrder is SettingDestinationType.ManagementDestination)
    }

    @Test
    fun directEditExceptionKeysStayLimitedToXmlUnavailablePlainPreferences() {
        assertEquals(
            setOf("long_press_timeout_preference"),
            SettingDestinations.inlineEditExceptionKeysForTesting,
        )
        assertTrue(
            destinationForKey("long_press_timeout_preference").destination
                is SettingDestinationType.IntPreferenceDialog
        )
    }

    @Test
    fun frequentAndSearchUseTheSameXmlClassification() {
        val key = "keyboard_floating_preference"
        val indexed = destinationForKey(key)
        val frequent = SettingDestinations.frequentCandidates(context)
            .first { it.key == key }
        val searchable = SettingSearchIndex.searchable(context)
            .first { it.key == key }

        assertEquals(indexed.destination, frequent.destination)
        assertEquals(indexed.destination, searchable.destination)
    }

    @Test
    fun switchClickSavesSharedPreferenceAndUpdatesCardValueLabel() {
        val destination = destinationForKey("keyboard_floating_preference")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        var changed = false

        assertEquals(
            context.getString(R.string.setting_status_disabled),
            controller.currentValueLabel(destination),
        )

        val handled = controller.handleClick(destination) {
            changed = true
        }

        assertTrue(handled)
        assertTrue(changed)
        assertTrue(preferences.getBoolean("keyboard_floating_preference", false))
        assertEquals(
            context.getString(R.string.setting_status_enabled),
            controller.currentValueLabel(destination),
        )
    }

    @Test
    fun listPreferenceDisplayUsesEntryLabelAndFallsBackToRawUnknownValue() {
        val destination = destinationForKey("candidate_column_preference")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val target = destination.destination as SettingDestinationType.ListPreference
        val entries = context.resources.getStringArray(target.entriesResId)

        preferences.edit().putString("candidate_column_preference", "2").commit()
        assertEquals(entries[1], controller.currentValueLabel(destination))

        preferences.edit().putString("candidate_column_preference", "unexpected").commit()
        assertEquals("unexpected", controller.currentValueLabel(destination))
    }

    @Test
    fun routeOrPlainNavigationItemsRemainNavigationDestinations() {
        assertNotEquals(
            SettingDestinationType.SwitchPreference::class,
            destinationForKey("keyboard_screen_preference").destination::class,
        )
        assertTrue(
            destinationForKey("keyboard_screen_preference").destination
                is SettingDestinationType.NavDestination
        )
    }

    @Test
    fun legacySearchCatalogDoesNotIncludeNewHomeRoutes() {
        val legacyKeys = SettingSearchIndex.searchable(context, SettingSearchScope.LEGACY_TABS)
            .map { it.key }

        assertFalse(legacyKeys.any { it.startsWith("setting_route_") })
        assertFalse(legacyKeys.contains("setting_route_clipboard_shortcut"))
        assertFalse(legacyKeys.contains("setting_route_legacy_settings"))
    }

    @Test
    fun legacySearchCatalogMapsIntegratedShortcutSettingToLegacyCommon() {
        val destination = SettingSearchIndex.legacyDestinationsForKeys(
            context,
            listOf("shortcut_toolbar_integrated_in_suggestion_preference"),
        ).single()
        val target = destination.legacyTarget

        assertNotNull(target)
        requireNotNull(target)
        assertEquals(SettingTabRegistry.TAB_COMMON, target.tabKey)
        assertEquals(R.id.legacyCommonPreferenceFragment, target.destinationId)
        assertEquals(
            "shortcut_toolbar_integrated_in_suggestion_preference",
            target.preferenceKey,
        )
        assertTrue(target.relatedPreferenceKeys.contains("shortcut_toolbar_visibility_preference"))
        assertNotEquals(R.id.clipboardShortcutPreferenceFragment, target.destinationId)
    }

    @Test
    fun legacySearchFindsIntegratedShortcutFromShortcutQuery() {
        val results = SettingSearchIndex.search(
            context = context,
            destinations = SettingSearchIndex.searchable(context, SettingSearchScope.LEGACY_TABS),
            query = "shortcut",
        )

        assertTrue(
            results.any { it.key == "shortcut_toolbar_integrated_in_suggestion_preference" }
        )
    }

    @Test
    fun legacySearchLocationUsesLegacyTabLocation() {
        val destination = SettingSearchIndex.legacyDestinationsForKeys(
            context,
            listOf("shortcut_toolbar_integrated_in_suggestion_preference"),
        ).single()

        assertEquals(
            context.getString(
                R.string.setting_search_legacy_location,
                context.getString(R.string.category_common),
            ),
            destination.location,
        )
    }

    @Test
    fun newHomeSearchCatalogStillIncludesRoutes() {
        val newHomeKeys = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .map { it.key }

        assertTrue(newHomeKeys.contains("setting_route_clipboard_shortcut"))
    }

    @Test
    fun legacyPreferenceResultFilterKeepsTargetAndDependencyOnly() {
        val manager = PreferenceManager(context)
        val screen = manager.createPreferenceScreen(context)
        val category = PreferenceCategory(context).apply {
            title = "Common"
        }
        screen.addPreference(category)
        category.addPreference(
            SwitchPreferenceCompat(context).apply {
                key = "shortcut_toolbar_visibility_preference"
            }
        )
        category.addPreference(
            SwitchPreferenceCompat(context).apply {
                key = "shortcut_toolbar_integrated_in_suggestion_preference"
                dependency = "shortcut_toolbar_visibility_preference"
            }
        )
        category.addPreference(
            Preference(context).apply {
                key = "unrelated_preference"
            }
        )

        LegacyPreferenceResultFilter.filterPreferenceScreen(
            screen = screen,
            targetKey = "shortcut_toolbar_integrated_in_suggestion_preference",
            relatedKeys = emptyList(),
        )

        val remainingKeys = collectPreferenceKeys(screen)
        assertTrue(remainingKeys.contains("shortcut_toolbar_visibility_preference"))
        assertTrue(remainingKeys.contains("shortcut_toolbar_integrated_in_suggestion_preference"))
        assertFalse(remainingKeys.contains("unrelated_preference"))
    }

    @Test
    fun newRealPreferenceKeysArePresentInLegacyXml() {
        val newKeys = realPreferenceKeys(newSettingXmlResources())
        val legacyKeys = realPreferenceKeys(legacySettingXmlResources())
        val unexpectedMissing = newKeys - legacyKeys - allowedNewOnlyPreferenceKeys

        assertEquals(emptySet<String>(), unexpectedMissing)
    }

    private fun destinationForKey(key: String): SettingDestination =
        SettingSearchIndex.destinationsForKeys(context, listOf(key))
            .first { it.key == key }

    private fun collectPreferenceKeys(group: PreferenceGroup): Set<String> =
        buildSet {
            for (index in 0 until group.preferenceCount) {
                val preference = group.getPreference(index)
                preference.key?.let(::add)
                if (preference is PreferenceGroup) {
                    addAll(collectPreferenceKeys(preference))
                }
            }
        }

    private fun realPreferenceKeys(xmlResources: List<Int>): Set<String> {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        return xmlResources.flatMap { xmlRes ->
            val parser = context.resources.getXml(xmlRes)
            parser.use {
                buildList {
                    while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                            val key = parser.getAttributeValue(androidNamespace, "key")
                            if (
                                !key.isNullOrBlank() &&
                                !key.startsWith("setting_route_") &&
                                parser.name.substringAfterLast('.') !in
                                setOf("PreferenceCategory", "PreferenceScreen")
                            ) {
                                add(key)
                            }
                        }
                        parser.next()
                    }
                }
            }
        }.toSet()
    }

    private fun newSettingXmlResources(): List<Int> = buildList {
        add(R.xml.pref_common)
        add(R.xml.pref_keyboard_display)
        add(R.xml.pref_input_method)
        add(R.xml.pref_candidate_conversion)
        add(R.xml.pref_clipboard_shortcut)
        add(R.xml.pref_operation_feedback)
        add(R.xml.pref_general_info)
        add(R.xml.pref_advanced)
        add(R.xml.pref_dictionary)
        add(R.xml.pref_kana)
        add(R.xml.pref_qwerty)
        add(R.xml.pref_sumire)
        add(R.xml.pref_custom)
        add(R.xml.pref_tablet)
        add(R.xml.pref_hardware_keyboard)
        if (AppVariantConfig.hasZenz || AppVariantConfig.hasGemma) {
            add(R.xml.pref_ai_conversion)
        }
        if (AppVariantConfig.hasZenz) {
            add(R.xml.pref_zenz)
        }
        if (AppVariantConfig.hasGemma) {
            add(R.xml.pref_gemma)
        }
    }

    private fun legacySettingXmlResources(): List<Int> = buildList {
        add(R.xml.pref_common_legacy)
        add(R.xml.pref_dictionary)
        add(R.xml.pref_kana)
        add(R.xml.pref_qwerty)
        add(R.xml.pref_sumire)
        add(R.xml.pref_custom)
        add(R.xml.pref_tablet)
        add(R.xml.pref_hardware_keyboard)
        if (AppVariantConfig.hasZenz) {
            add(R.xml.pref_zenz)
        }
        if (AppVariantConfig.hasGemma) {
            add(R.xml.pref_gemma)
        }
    }

    private val allowedNewOnlyPreferenceKeys = emptySet<String>()
}
