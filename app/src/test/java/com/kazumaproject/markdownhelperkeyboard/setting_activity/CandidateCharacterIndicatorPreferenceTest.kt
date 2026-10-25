package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidateCharacterIndicatorPreferenceTest {
    @Test fun bothScreensPersistTheSameIndependentDefaultOffSwitchAndExposeItInSearch() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().commit()
        AppPreference.init(context)
        assertFalse(AppPreference.candidate_character_type_indicators_preference)
        assertFalse(ImePreferencesSnapshot.from(AppPreference).showCharacterIndicators)
        for (xml in listOf(R.xml.pref_candidate_conversion, R.xml.pref_common_legacy)) {
            val manager = PreferenceManager(context)
            val screen = manager.inflateFromResource(context, xml, null)
            manager.setPreferences(screen)
            val toggle = requireNotNull(screen.findPreference<SwitchPreferenceCompat>(AppPreference.CANDIDATE_CHARACTER_INDICATORS_KEY))
            assertNull(toggle.dependency)
            screen.onAttached()
            assertTrue(toggle.isEnabled)
            toggle.isChecked = true
            AppPreference.init(context)
            assertTrue(AppPreference.candidate_character_type_indicators_preference)
            assertTrue(ImePreferencesSnapshot.from(AppPreference).showCharacterIndicators)
            toggle.isChecked = false
            screen.onDetached()
        }
        for (scope in listOf(SettingSearchScope.NEW_HOME, SettingSearchScope.LEGACY_TABS)) {
            assertTrue(SettingSearchIndex.searchable(context, scope).any { it.key == AppPreference.CANDIDATE_CHARACTER_INDICATORS_KEY })
        }
    }
}
