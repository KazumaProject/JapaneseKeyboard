package com.kazumaproject.markdownhelperkeyboard.setting_activity

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceFlickGuideTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun flickGuidePreferences_useExistingDisplayDefaults() {
        assertEquals(9, AppPreference.flick_guide_text_size_sp_preference)
        assertEquals(1, AppPreference.flick_guide_max_characters_preference)
    }

    @Test
    fun zeroQuerySuggestionPreference_defaultOff() {
        assertFalse(AppPreference.zero_query_suggestion_preference)
    }
}
