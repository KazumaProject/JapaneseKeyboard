package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class CandidateReadingSizePreferenceTest {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard
    )

    @Before fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun bothSettingsScreensPlaceTheSizeNextToReadingAndPersistTheSameValue() {
        assertEquals(14, AppPreference.live_conversion_candidate_yomi_size)
        for (resource in listOf(R.xml.pref_candidate_conversion, R.xml.pref_common_legacy)) {
            val screen = PreferenceManager(context).inflateFromResource(context, resource, null) as PreferenceScreen
            val size = screen.findPreference<SeekBarPreference>(AppPreference.LIVE_CONVERSION_CANDIDATE_YOMI_SIZE_KEY)!!
            val show = screen.findPreference<androidx.preference.Preference>("live_conversion_candidate_yomi_preference")!!
            assertEquals(show.parent, size.parent)
            val group = size.parent!!
            val keys = (0 until group.preferenceCount).map { group.getPreference(it).key }
            assertEquals(keys.indexOf(show.key) + 1, keys.indexOf(size.key))
            assertEquals(show.key, size.dependency)
            assertEquals(1, size.min)
            com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.CandidateReadingSizeLimits.configurePreference(context, size)
            val maximum = com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.CandidateReadingSizeLimits.maximumSp(context)
            assertEquals(maximum, size.max)
            size.value = maximum
            AppPreference.init(context)
            assertEquals(maximum, AppPreference.live_conversion_candidate_yomi_size)
        }
    }

    @Test fun readingSizeSurvivesInitializationAndBoundsRestoredValues() {
        AppPreference.live_conversion_candidate_yomi_size = 18
        AppPreference.init(context)
        assertEquals(18, AppPreference.live_conversion_candidate_yomi_size)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putInt(AppPreference.LIVE_CONVERSION_CANDIDATE_YOMI_SIZE_KEY, 99).commit()
        assertEquals(24, AppPreference.live_conversion_candidate_yomi_size)
        prefs.edit().putInt(AppPreference.LIVE_CONVERSION_CANDIDATE_YOMI_SIZE_KEY, 1).commit()
        assertEquals(1, AppPreference.live_conversion_candidate_yomi_size)
    }
}
