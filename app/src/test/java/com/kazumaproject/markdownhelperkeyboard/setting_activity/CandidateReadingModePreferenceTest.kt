package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidateReadingModePreferenceTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)

    @Before fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun bothSettingsScreensOfferTheSameModesAndDependOnTheReadingSwitch() {
        for (resource in listOf(R.xml.pref_candidate_conversion, R.xml.pref_common_legacy)) {
            val manager = PreferenceManager(context)
            val screen = manager.inflateFromResource(context, resource, null)
            manager.setPreferences(screen)
            val mode = screen.findPreference<ListPreference>(AppPreference.LIVE_CONVERSION_CANDIDATE_YOMI_MODE_KEY)!!
            val show = screen.findPreference<SwitchPreferenceCompat>("live_conversion_candidate_yomi_preference")!!
            assertEquals(show.parent, mode.parent)
            assertEquals(show.key, mode.dependency)
            val keys = (0 until mode.parent!!.preferenceCount).map { mode.parent!!.getPreference(it).key }
            assertEquals(keys.indexOf(show.key) + 1, keys.indexOf(mode.key))
            assertEquals(listOf("whole", "ruby"), mode.entryValues.map(CharSequence::toString))
            assertEquals("whole", mode.value)
            screen.onAttached()
            assertFalse(mode.isEnabled)
            screen.findPreference<SwitchPreferenceCompat>("live_conversion_preference")!!.isChecked = true
            show.isChecked = true
            assertTrue(mode.isEnabled)
            mode.value = "ruby"
            AppPreference.init(context)
            assertEquals("ruby", AppPreference.live_conversion_candidate_yomi_mode)
            screen.onDetached()
            AppPreference.live_conversion_candidate_yomi_mode = "whole"
            show.isChecked = false
        }
    }

    @Test fun modeDefaultsToOriginalPersistsAndReachesImeSnapshot() {
        assertEquals("whole", AppPreference.live_conversion_candidate_yomi_mode)
        AppPreference.live_conversion_candidate_yomi_mode = "ruby"
        AppPreference.init(context)
        assertEquals("ruby", AppPreference.live_conversion_candidate_yomi_mode)
        assertEquals("ruby", ImePreferencesSnapshot.from(AppPreference).liveConversionCandidateYomiMode)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(AppPreference.LIVE_CONVERSION_CANDIDATE_YOMI_MODE_KEY, "unknown").commit()
        assertEquals("whole", AppPreference.live_conversion_candidate_yomi_mode)
    }
}
