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
    fun newHomeAndLegacySettingsExposeDedicatedMacroEntryPoint() {
        val categories = SettingDestinations.categories(context)
        val category = categories
            .first { it.key == "setting_route_text_macro" }

        assertEquals(SettingCategory.TEXT_MACRO, category.category)
        assertEquals(
            R.id.textMacroFragment,
            SettingDestinations.destinationId(category.destination),
        )
        assertEquals(
            context.getString(R.string.setting_category_text_macro_title),
            category.title,
        )
        assertEquals(
            categories.indexOfFirst { it.key == "setting_route_dictionary" } + 1,
            categories.indexOf(category),
        )

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

        val legacySearchResult = SettingSearchIndex.legacySearchable(context)
            .first { it.key == "legacy_text_macro" }
        assertEquals(SettingCategory.TEXT_MACRO, legacySearchResult.category)
        assertTrue(legacySearchResult.legacyTarget?.filterResultMode == false)
    }
}
