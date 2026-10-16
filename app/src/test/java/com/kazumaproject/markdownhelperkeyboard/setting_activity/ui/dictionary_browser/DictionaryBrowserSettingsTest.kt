package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictionaryBrowserSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun managementAndBothSettingsSearchesExposeTheBrowser() {
        val management = SettingDestinations.management(context).single { it.key == "dictionary_browser_preference" }
        assertEquals(R.id.dictionaryBrowserFragment, SettingDestinations.destinationId(management.destination))
        val current = SettingSearchIndex.searchable(context, SettingSearchScope.NEW_HOME)
            .filter { it.key == "dictionary_browser_preference" }
        assertTrue(current.isNotEmpty())
        assertTrue(current.all { SettingDestinations.destinationId(it.destination) == R.id.dictionaryBrowserFragment })
        val legacy = SettingSearchIndex.searchable(context, SettingSearchScope.LEGACY_TABS)
            .single { it.key == "dictionary_browser_preference" }
        assertEquals(R.id.dictionaryPreferenceFragment, legacy.legacyTarget?.destinationId)
        assertEquals(R.id.dictionaryBrowserFragment, SettingDestinations.routeDestinationId(legacy.key))
        assertTrue(SettingDestinations.isManagementDestinationKey(legacy.key))
    }
}
