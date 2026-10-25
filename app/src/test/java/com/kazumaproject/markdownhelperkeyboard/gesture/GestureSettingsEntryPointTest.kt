package com.kazumaproject.markdownhelperkeyboard.gesture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GestureSettingsEntryPointTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun bothSettingsHomesAndBothSearchScopesReachTheSameGestureSettings() {
        val tab = SettingTabRegistry.createTabs().single { it.key == SettingTabRegistry.TAB_GESTURE }
        assertEquals(R.id.gesturePreferenceFragment, tab.destinationId)
        assertEquals(R.xml.pref_gesture, tab.xmlRes)
        assertTrue(tab.fragmentFactory() is GesturePreferenceFragment)
        assertEquals(1, SettingDestinations.categories(context).count { it.category == SettingCategory.GESTURE })
        for (scope in listOf(SettingSearchScope.NEW_HOME, SettingSearchScope.LEGACY_TABS)) {
            val entries = SettingSearchIndex.searchable(context, scope)
            assertTrue(entries.any { it.key.contains("gesture") })
            for (key in listOf("gesture_controls", "gesture_sets")) {
                assertTrue(key + " missing from " + scope, entries.any { it.key == key || it.key == "legacy_" + key })
            }
        }
    }
    @Test fun splitSettingsNormalizeUnsupportedGestureSelections() {
        val prefs = context.getSharedPreferences("gesture-split-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString(SplitKeyboardSettings.typeKey(SplitSlot.MAIN), "GESTURE").commit()
        assertEquals(KeyboardType.TENKEY, SplitKeyboardSettings(prefs).selection(SplitSlot.MAIN).type)
        assertEquals(KeyboardType.TENKEY, SplitKeyboardSelection(KeyboardType.GESTURE).resolved(emptySet()).type)
        assertThrows(IllegalArgumentException::class.java) { SplitKeyboardSettings(prefs).saveSelection(SplitSlot.MAIN, SplitKeyboardSelection(KeyboardType.GESTURE)) }
    }
}
