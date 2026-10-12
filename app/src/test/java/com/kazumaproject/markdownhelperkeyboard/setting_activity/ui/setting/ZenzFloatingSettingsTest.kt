package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ZenzFloatingSettingsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun initializePreferences() { AppPreference.init(context) }

    @Test fun newAndLegacySettingsShareTheZenzPreferenceKeys() {
        val key = "zenz_floating_candidates_enabled"
        val modernItems = SettingSearchIndex.searchable(context)
        val legacyItems = SettingSearchIndex.legacySearchable(context)
        if (!AppVariantConfig.hasZenz) {
            assertFalse(modernItems.any { it.key == key })
            assertFalse(legacyItems.any { it.key == key })
            return
        }
        val modern = modernItems.first { it.key == key }
        val legacy = legacyItems.first { it.key == key }
        val toggle = modern.destination as SettingDestinationType.SwitchPreference
        assertFalse(toggle.defaultValue)
        assertEquals(R.id.zenzPreferenceFragment, toggle.destinationId)
        assertEquals(key, legacy.legacyTarget?.preferenceKey)
        assertEquals(SettingTabRegistry.TAB_ZENZ, legacy.legacyTarget?.tabKey)
        assertEquals(R.xml.pref_zenz, SettingTabRegistry.createTabs().first { it.key == SettingTabRegistry.TAB_ZENZ }.xmlRes)
    }
    @Test fun resetNavigatesToTheActionInsteadOfOpeningAnEditor() {
        val key = "zenz_floating_candidates_reset"
        val items = SettingSearchIndex.destinationsForKeys(context, listOf(key))
        if (!AppVariantConfig.hasZenz) {
            assertTrue(items.isEmpty())
            return
        }
        val item = items.first { it.key == key }
        val action = item.destination as SettingDestinationType.NavDestination
        assertEquals(R.id.zenzPreferenceFragment, action.destinationId)
        assertEquals(key, action.highlightPreferenceKey)
    }
}
