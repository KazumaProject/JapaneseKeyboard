package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
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

    private fun destinationForKey(key: String): SettingDestination =
        SettingSearchIndex.destinationsForKeys(context, listOf(key))
            .first { it.key == key }
}
