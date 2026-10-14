package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.ime_service.voice_input.VoiceInputSpaceReplacement
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceInputPreferenceTest {
    private lateinit var context: Context
    private val keys = listOf(
        AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_ENABLED_KEY,
        AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_KEY,
        AppPreference.VOICE_INPUT_SPACE_CUSTOM_TEXT_KEY,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun defaultsPersistAndSnapshotRestoresFromBackup() {
        assertFalse(AppPreference.voice_input_settings.enabled)
        assertEquals(VoiceInputSpaceReplacement.REMOVE, AppPreference.voice_input_settings.replacement)
        AppPreference.voice_input_space_replacement_enabled = true
        AppPreference.voice_input_space_replacement = "custom"
        AppPreference.voice_input_space_custom_text = " ・ "
        val expected = AppPreference.voice_input_settings
        assertEquals(expected, ImePreferencesSnapshot.from(AppPreference).voiceInputSettings)
        val backup = AppPreference.exportAllToJson()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.importAllFromJson(backup)
        AppPreference.init(context)
        assertEquals(expected, AppPreference.voice_input_settings)
        AppPreference.voice_input_space_replacement_enabled = false
        assertEquals(expected.copy(enabled = false), AppPreference.voice_input_settings)
        AppPreference.voice_input_space_replacement = "unknown"
        assertEquals(VoiceInputSpaceReplacement.REMOVE, AppPreference.voice_input_settings.replacement)
    }

    @Test
    fun bothEntryPointsAndSearchOpenTheSharedScreenWithDependencies() {
        val tab = SettingTabRegistry.createTabs().single { it.key == SettingTabRegistry.TAB_VOICE_INPUT }
        assertEquals(R.xml.pref_voice_input, tab.xmlRes)
        assertEquals(R.id.voiceInputPreferenceFragment, tab.destinationId)
        assertTrue(tab.fragmentFactory() is VoiceInputPreferenceFragment)
        val category = SettingDestinations.categories(context).single { it.category == SettingCategory.VOICE_INPUT }
        assertEquals(tab.destinationId, SettingDestinations.destinationId(category.destination))
        for (scope in SettingSearchScope.entries) {
            val results = SettingSearchIndex.searchable(context, scope)
            for (key in keys) {
                val item = results.single { it.key == key }
                assertEquals(SettingCategory.VOICE_INPUT, item.category)
                assertEquals(tab.destinationId, SettingDestinations.destinationId(item.destination))
                assertEquals(key, SettingDestinations.highlightPreferenceKey(item.destination))
                assertTrue(item.destination is SettingDestinationType.NavDestination)
                if (scope == SettingSearchScope.LEGACY_TABS) {
                    assertTrue(requireNotNull(item.legacyTarget).relatedPreferenceKeys.containsAll(keys))
                }
            }
            assertTrue(SettingSearchIndex.search(context, results, "voice").any { it.key in keys })
        }
        val candidates = SettingDestinations.frequentCandidates(context)
        assertTrue(candidates.map { it.key }.containsAll(keys))
        assertTrue(FrequentSettingFilters.filters(context, candidates).any { it.category == SettingCategory.VOICE_INPUT })
    }

    @Test
    fun preferenceWidgetsRespectEnablementAndRetainSelectionAcrossOffOn() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        val activity = controller.create().get()
        val fragment = VoiceInputPreferenceFragment()
        activity.supportFragmentManager.beginTransaction().add(fragment, "voice").commitNow()
        fragment.setPreferencesFromResource(R.xml.pref_voice_input, null)
        val ready = VoiceInputPreferenceFragment::class.java.getDeclaredMethod(
            "onPreferencesReady", Bundle::class.java, String::class.java,
        ).apply { isAccessible = true }
        fragment.preferenceScreen.onAttached()
        ready.invoke(fragment, null, null)
        val enabled = fragment.findPreference<SwitchPreferenceCompat>(keys[0])!!
        val replacement = fragment.findPreference<ListPreference>(keys[1])!!
        val custom = fragment.findPreference<EditTextPreference>(keys[2])!!
        assertFalse(enabled.isChecked)
        assertEquals("remove", replacement.value)
        assertFalse(replacement.isEnabled)
        assertFalse(custom.isEnabled)
        assertTrue(enabled.callChangeListener(true))
        enabled.isChecked = true
        assertTrue(replacement.isEnabled)
        assertFalse(custom.isEnabled)
        assertTrue(replacement.callChangeListener("custom"))
        replacement.value = "custom"
        assertTrue(custom.isEnabled)
        custom.text = " ・ "
        assertTrue(custom.summary.toString().contains(" ・ "))
        assertTrue(enabled.callChangeListener(false))
        enabled.isChecked = false
        assertFalse(replacement.isEnabled)
        assertFalse(custom.isEnabled)
        assertEquals("custom", replacement.value)
        assertEquals(" ・ ", custom.text)
        assertTrue(enabled.callChangeListener(true))
        enabled.isChecked = true
        assertTrue(custom.isEnabled)
        assertTrue(replacement.callChangeListener("comma"))
        replacement.value = "comma"
        assertFalse(custom.isEnabled)
        controller.destroy()
    }
}
