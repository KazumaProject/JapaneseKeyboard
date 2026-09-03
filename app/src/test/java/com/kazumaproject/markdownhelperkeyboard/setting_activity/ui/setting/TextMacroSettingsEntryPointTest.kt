package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextMacroSettingsEntryPointTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun newHomeAndLegacySettingsExposeOneMacroManagementEntryPoint() {
        val categories = SettingDestinations.categories(context)
        assertTrue(categories.none { it.key == "setting_route_text_macro" })

        val tabs = SettingTabRegistry.createTabs()
        val tab = tabs
            .first { it.key == SettingTabRegistry.TAB_TEXT_MACRO }
        assertEquals(R.id.textMacroFragment, tab.destinationId)
        assertNull(tab.xmlRes)
        assertNotNull(tab.fragmentFactory())
        assertEquals(
            tabs.indexOfFirst { it.key == SettingTabRegistry.TAB_DICTIONARY } + 1,
            tabs.indexOf(tab),
        )

        val management = SettingDestinations.management(context)
        assertEquals(
            management.indexOfFirst { it.key == "setting_management_user_template" } + 1,
            management.indexOfFirst { it.key == "setting_management_text_macro" },
        )
        val macroManagementEntries = management.filter {
            SettingDestinations.destinationId(it.destination) == R.id.textMacroFragment
        }
        assertEquals(1, macroManagementEntries.size)

        val newHomeMacroEntries = SettingSearchIndex.searchable(
            context,
            SettingSearchScope.NEW_HOME,
        ).filter {
            SettingDestinations.destinationId(it.destination) == R.id.textMacroFragment
        }
        assertEquals(1, newHomeMacroEntries.size)
        assertEquals("setting_management_text_macro", newHomeMacroEntries.single().key)

        val legacyMacroEntries = SettingSearchIndex.searchable(
            context,
            SettingSearchScope.LEGACY_TABS,
        ).filter {
            SettingDestinations.destinationId(it.destination) == R.id.textMacroFragment
        }
        assertEquals(1, legacyMacroEntries.size)
        assertEquals("legacy_text_macro", legacyMacroEntries.single().key)

        val macroCandidateSetting = SettingSearchIndex.searchable(
            context,
            SettingSearchScope.NEW_HOME,
        ).single { it.key == "text_macro_candidate_preference" }
        assertEquals(
            R.id.dictionaryPreferenceFragment,
            SettingDestinations.destinationId(macroCandidateSetting.destination),
        )
        assertEquals(
            context.getString(R.string.text_macro_candidate_title),
            macroCandidateSetting.title,
        )

        val legacySearchResult = SettingSearchIndex.legacySearchable(context)
            .first { it.key == "legacy_text_macro" }
        assertEquals(SettingCategory.TEXT_MACRO, legacySearchResult.category)
        assertTrue(legacySearchResult.legacyTarget?.filterResultMode == false)
    }
}
