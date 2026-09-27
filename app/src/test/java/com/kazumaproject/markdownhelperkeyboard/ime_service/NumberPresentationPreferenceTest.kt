package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.converter.number.NumberPresentationConfig
import com.kazumaproject.markdownhelperkeyboard.converter.number.NumberStyle
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NumberPresentationPreferenceTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun additionsAreEnabledByDefaultAndSnapshotCarriesAllSixStyleOrderChoices() {
        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertTrue(snapshot.numberPresentationConfig.additionsEnabled)
        assertEquals(NumberPresentationConfig.DEFAULT_STYLE_ORDER, snapshot.numberPresentationConfig.styleOrder)
    }

    @Test
    fun disabledSettingAndSelectedStyleOrderReachOneImmutableSnapshot() {
        AppPreference.number_candidate_additions_preference = false
        AppPreference.number_style_order_preference = "kanji_full_half"

        val snapshot = ImePreferencesSnapshot.from(AppPreference)

        assertEquals(false, snapshot.numberPresentationConfig.additionsEnabled)
        assertEquals(
            listOf(NumberStyle.KANJI, NumberStyle.FULL_WIDTH, NumberStyle.HALF_WIDTH),
            snapshot.numberPresentationConfig.styleOrder,
        )
    }
}
