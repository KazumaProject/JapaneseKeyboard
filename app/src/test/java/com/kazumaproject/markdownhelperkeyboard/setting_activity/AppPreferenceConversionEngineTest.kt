package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceConversionEngineTest {

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun defaultIsLegacySumire() {
        assertEquals("legacy_sumire", AppPreference.conversion_engine_preference)
    }

    @Test
    fun storesMozcKotlin() {
        AppPreference.conversion_engine_preference = "mozc_kotlin"

        assertEquals("mozc_kotlin", AppPreference.conversion_engine_preference)
    }

    @Test
    fun invalidValueFallsBackToLegacySumire() {
        AppPreference.conversion_engine_preference = "future_engine"

        assertEquals("legacy_sumire", AppPreference.conversion_engine_preference)
    }
}
