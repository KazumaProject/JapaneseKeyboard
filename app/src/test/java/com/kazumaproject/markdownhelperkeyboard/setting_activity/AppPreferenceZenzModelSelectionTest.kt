package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceZenzModelSelectionTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun changingAndReselectingModelsInvalidatesTheSelection() {
        AppPreference.zenz_model_uri_preference = "content://models/first"
        val first = AppPreference.zenz_model_selection_version
        assertTrue(first.isNotBlank())

        AppPreference.zenz_model_uri_preference = "content://models/second"
        val second = AppPreference.zenz_model_selection_version
        assertNotEquals(first, second)

        AppPreference.zenz_model_uri_preference = "content://models/second"
        assertNotEquals(second, AppPreference.zenz_model_selection_version)
        assertEquals("content://models/second", AppPreference.zenz_model_uri_preference)
    }

    @Test
    fun legacySelectionsGainAnIdentityWhenReselected() {
        preferences.edit().putString("zenz_model_uri_preference", "content://models/legacy").commit()
        assertEquals("content://models/legacy", AppPreference.zenz_model_uri_preference)
        assertEquals("", AppPreference.zenz_model_selection_version)

        AppPreference.zenz_model_uri_preference = "content://models/legacy"

        assertTrue(AppPreference.zenz_model_selection_version.isNotBlank())
    }

    @Test
    fun returningToBundledPersistsTheNewSelection() {
        AppPreference.zenz_model_uri_preference = "content://models/custom"
        val custom = AppPreference.zenz_model_selection_version

        AppPreference.zenz_model_uri_preference = ""
        val bundled = AppPreference.zenz_model_selection_version
        AppPreference.init(context)

        assertEquals("", AppPreference.zenz_model_uri_preference)
        assertTrue(bundled.isNotBlank())
        assertNotEquals(custom, bundled)
        assertEquals(bundled, AppPreference.zenz_model_selection_version)
    }

    @Test
    fun uriObserversSeeTheUpdatedSelectionIdentity() {
        var observedVersion: String? = null
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "zenz_model_uri_preference") {
                observedVersion = AppPreference.zenz_model_selection_version
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            AppPreference.zenz_model_uri_preference = "content://models/new"
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(AppPreference.zenz_model_selection_version.isNotBlank())
            assertEquals(AppPreference.zenz_model_selection_version, observedVersion)
        } finally {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}
