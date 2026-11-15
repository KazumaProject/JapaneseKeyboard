package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CollapsibleDictionarySettingsTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)
    private val expansion get() = context.getSharedPreferences(CollapsiblePreferenceCategory.STATE_PREFERENCES, Context.MODE_PRIVATE)
    @Before fun reset() { expansion.edit().clear().commit() }
    private fun inflate(newHome: Boolean): PreferenceScreen {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("setting_use_new_home_screen_preference", newHome).commit()
        val manager = PreferenceManager(context)
        return manager.inflateFromResource(context, R.xml.pref_dictionary, null).also { manager.setPreferences(it); it.onAttached() }
    }
    private fun descendants(group: PreferenceGroup): List<Preference> = buildList {
        for (index in 0 until group.preferenceCount) {
            val child = group.getPreference(index); add(child)
            if (child is PreferenceGroup) addAll(descendants(child))
        }
    }
    @Test fun bothModesShowAllGroupsAndKeepEveryDictionarySearchable() {
        for (newHome in listOf(false, true)) {
            val screen = inflate(newHome)
            val groups = descendants(screen).filterIsInstance<CollapsiblePreferenceCategory>()
            assertEquals(28, groups.size)
            groups.forEach { group ->
                assertTrue(group.key, group.isExpanded); assertFalse(group.key, group.isSelectable)
                assertEquals(0, group.widgetLayoutResource)
                for (index in 0 until group.preferenceCount) assertTrue(group.getPreference(index).isVisible)
            }
            ClassifiedDictionaries.all.forEach { spec -> assertNotNull(screen.findPreference<SwitchPreferenceCompat>(spec.preferenceKey)) }
            val keys = SettingSearchIndex.searchable(context).map { it.key }
            assertTrue(keys.containsAll(ClassifiedDictionaries.all.map { it.preferenceKey }))
            assertFalse(keys.any { it.startsWith("classified_group_") })
        }
    }
    @Test fun storedCollapsedStateAndClicksCannotHideItemsOrChangeSavedValues() {
        for (newHome in listOf(false, true)) {
            expansion.edit().putBoolean("classified_group_facility", false).putBoolean("classified_group_facility_religious", false).commit()
            val before = expansion.all.toMap()
            val screen = inflate(newHome)
            val group = screen.findPreference<CollapsiblePreferenceCategory>("classified_group_facility")!!
            val shrine = screen.findPreference<SwitchPreferenceCompat>("optional_bundled_enabled_CLASSIFIED_FACILITY_RELIGIOUS_SHRINE")!!
            shrine.isChecked = true
            group.performClick(); group.setExpanded(false)
            assertTrue(group.isExpanded); assertTrue(shrine.isVisible); assertTrue(shrine.isChecked)
            val restored = inflate(newHome)
            assertTrue(restored.findPreference<SwitchPreferenceCompat>(shrine.key)!!.isVisible)
            assertTrue(shrine.isChecked)
            assertEquals(before, expansion.all)
        }
    }
    @Test fun conditionalVisibilityRemainsIndependentOfHeadingAndSearchRequests() {
        for (newHome in listOf(false, true)) {
            val screen = inflate(newHome)
            val group = screen.findPreference<CollapsiblePreferenceCategory>("classified_group_facility_religious")!!
            val shrine = screen.findPreference<SwitchPreferenceCompat>("optional_bundled_enabled_CLASSIFIED_FACILITY_RELIGIOUS_SHRINE")!!
            val value = shrine.isChecked
            shrine.conditionVisible = false; group.setExpanded(true, persist = false)
            assertFalse(shrine.isVisible)
            shrine.conditionVisible = true; group.setExpanded(false)
            assertTrue(shrine.isVisible); assertEquals(value, shrine.isChecked)
        }
    }
    @Test fun dynamicallyAddedDictionaryActionsAreVisibleImmediately() {
        for (newHome in listOf(false, true)) {
            val screen = inflate(newHome)
            DictionaryCategoryActions(screen, { false }, { _, _ -> }).bind()
            val group = screen.findPreference<CollapsiblePreferenceCategory>("classified_group_facility")!!
            val action = screen.findPreference<Preference>("classified_group_facility_bulk_toggle")!!
            assertTrue(action.isVisible); group.performClick(); assertTrue(action.isVisible)
        }
    }
    @Test fun legacyFilteringKeepsTheNestedTargetAndItsHeadings() {
        val screen = inflate(false)
        val key = "optional_bundled_enabled_CLASSIFIED_FACILITY_RELIGIOUS_SHRINE"
        LegacyPreferenceResultFilter.filterPreferenceScreen(screen, key, emptyList())
        assertNotNull(screen.findPreference<Preference>(key))
        assertNotNull(screen.findPreference<Preference>("classified_group_facility_religious"))
        assertEquals(1, descendants(screen).filterIsInstance<SwitchPreferenceCompat>().size)
    }
}
