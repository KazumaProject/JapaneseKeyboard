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

    @Before fun clearExpansionState() {
        context.getSharedPreferences(CollapsiblePreferenceCategory.STATE_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun inflate(beforeAttach: (PreferenceScreen) -> Unit = {}): PreferenceScreen {
        val manager = PreferenceManager(context)
        val screen = manager.inflateFromResource(context, R.xml.pref_dictionary, null)
        manager.setPreferences(screen)
        beforeAttach(screen)
        screen.onAttached()
        return screen
    }

    private fun descendants(group: PreferenceGroup): List<Preference> = buildList {
        for (index in 0 until group.preferenceCount) {
            val child = group.getPreference(index)
            add(child)
            if (child is PreferenceGroup) addAll(descendants(child))
        }
    }

    @Test fun allLeavesRemainSearchableWhileGroupsStartCollapsed() {
        val screen = inflate()
        val groups = descendants(screen).filterIsInstance<CollapsiblePreferenceCategory>()
        assertEquals(28, groups.size)
        groups.forEach { group ->
            assertFalse(group.key, group.isExpanded)
            for (index in 0 until group.preferenceCount) assertFalse(group.getPreference(index).isVisible)
        }
        ClassifiedDictionaries.all.forEach { spec -> assertNotNull(screen.findPreference<SwitchPreferenceCompat>(spec.preferenceKey)) }
        val search = SettingSearchIndex.searchable(context)
        assertTrue(search.map { it.key }.containsAll(ClassifiedDictionaries.all.map { it.preferenceKey }))
        assertFalse(search.any { it.key.startsWith("classified_group_") })
    }

    @Test fun nestedExpansionPersistsIndependentlyWithoutChangingSwitches() {
        val screen = inflate()
        val facility = screen.findPreference<CollapsiblePreferenceCategory>("classified_group_facility")!!
        val religious = screen.findPreference<CollapsiblePreferenceCategory>("classified_group_facility_religious")!!
        val shrine = screen.findPreference<SwitchPreferenceCompat>("optional_bundled_enabled_CLASSIFIED_FACILITY_RELIGIOUS_SHRINE")!!
        shrine.isChecked = true
        facility.performClick()
        assertTrue(religious.isVisible)
        assertFalse(shrine.isVisible)
        religious.performClick()
        assertTrue(shrine.isVisible)
        facility.performClick()
        assertFalse(religious.isVisible)
        assertTrue(shrine.isChecked)
        val restored = inflate()
        assertFalse(restored.findPreference<CollapsiblePreferenceCategory>(facility.key)!!.isExpanded)
        assertTrue(restored.findPreference<CollapsiblePreferenceCategory>(religious.key)!!.isExpanded)
    }

    @Test fun navigationExpansionSurvivesDeferredAttachmentWithoutOverwritingSavedState() {
        val screen = inflate { pending ->
            pending.findPreference<CollapsiblePreferenceCategory>("classified_group_facility")!!
                .setExpanded(true, persist = false)
        }
        val facility = screen.findPreference<CollapsiblePreferenceCategory>("classified_group_facility")!!
        assertTrue(facility.isExpanded)
        assertFalse(inflate().findPreference<CollapsiblePreferenceCategory>(facility.key)!!.isExpanded)
    }

    @Test fun legacyFilteringKeepsNestedTargetAndAncestors() {
        val screen = inflate()
        val key = "optional_bundled_enabled_CLASSIFIED_FACILITY_RELIGIOUS_SHRINE"
        LegacyPreferenceResultFilter.filterPreferenceScreen(screen, key, emptyList())
        assertNotNull(screen.findPreference<Preference>(key))
        assertNotNull(screen.findPreference<Preference>("classified_group_facility_religious"))
        assertEquals(1, descendants(screen).filterIsInstance<SwitchPreferenceCompat>().size)
    }
}
