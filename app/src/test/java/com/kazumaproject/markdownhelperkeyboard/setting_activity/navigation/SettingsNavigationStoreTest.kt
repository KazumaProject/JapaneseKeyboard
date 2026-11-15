package com.kazumaproject.markdownhelperkeyboard.setting_activity.navigation

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsNavigationStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    @Before fun setup() { prefs.edit().clear().commit(); AppPreference.init(context) }
    @Test fun defaultsRetainEveryExistingManagementBottomTab() {
        val store = SettingsNavigationStore(context)
        assertEquals(SettingsNavigationStore.DEFAULT_BOTTOM, store.read().bottom)
        assertEquals(SettingsNavigationStore.DEFAULT_DRAWER, store.read().drawer)
        assertEquals(listOf(R.id.navigation_setting, R.id.navigation_learn_dictionary,
            R.id.navigation_user_dictionary, R.id.userTemplateFragment, R.id.keyboardListFragment), store.bottom().map { it.destination })
        assertEquals(store.catalog.size, store.catalog.map { it.destination }.distinct().size)
    }
    @Test fun editsPersistAcrossSettingsModesAndBackupWithoutRenamingDestinations() {
        val store = SettingsNavigationStore(context)
        val edited = store.defaults.copy(drawer = listOf("custom", "home"),
            bottom = listOf("custom", "learn", "home"))
        store.save(edited)
        prefs.edit().putBoolean("setting_use_new_home_screen_preference", false).commit()
        assertEquals(edited, SettingsNavigationStore(context).read())
        val backup = AppPreference.exportAllToJson()
        prefs.edit().clear().commit(); AppPreference.importAllFromJson(backup)
        assertEquals(edited, SettingsNavigationStore(context).read())
        assertEquals(context.getString(R.string.learn_dictionary_fragment_title), store.bottom()[1].title)
    }
    @Test fun removedShortcutsStayAbsentButRemainAvailableToAdd() {
        val store = SettingsNavigationStore(context)
        store.save(SettingsNavigationConfig(emptyList(), emptyList()))
        assertEquals(emptyList<SettingsNavigationItem>(), store.bottom())
        assertTrue(store.catalog.map { it.key }.containsAll(SettingsNavigationStore.DEFAULT_BOTTOM))
        assertEquals(emptyList<String>(), SettingsNavigationStore(context).read().drawer)
    }
    @Test fun unavailableKeysSurviveEditingAndInvalidJsonFallsBackToDefaults() {
        val store = SettingsNavigationStore(context)
        val config = store.defaults.copy(bottom = listOf("future-screen", "home", "home", "learn"))
        store.save(config)
        assertEquals(listOf("future-screen", "home", "learn"), store.read().bottom)
        assertEquals(listOf("home", "learn"), store.bottom().map { it.key })
        prefs.edit().putString(SettingsNavigationStore.KEY, "{").commit()
        assertEquals(store.defaults, store.read())
    }
}
