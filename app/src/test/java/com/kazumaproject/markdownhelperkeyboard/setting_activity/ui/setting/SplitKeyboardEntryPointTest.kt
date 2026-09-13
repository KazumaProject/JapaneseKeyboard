package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardSettings
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitSlot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplitKeyboardEntryPointTest {
    @Test fun bothSettingsScreensAndSearchExposeSplitPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(SettingDestinations.categories(context).any { it.key == "setting_route_split_keyboard" })
        assertEquals(R.xml.pref_split_keyboard, SettingTabRegistry.createTabs().single { it.key == SettingTabRegistry.TAB_SPLIT }.xmlRes)
        val keys = SplitSlot.entries.flatMap { listOf(SplitKeyboardSettings.typeKey(it), SplitKeyboardSettings.customKey(it)) } + SplitKeyboardSettings.CANDIDATES
        SettingSearchScope.entries.forEach { scope ->
            val results = SettingSearchIndex.searchable(context, scope)
            keys.forEach { key -> assertTrue("$scope missing $key", results.any { it.key == key }) }
        }
    }
}
