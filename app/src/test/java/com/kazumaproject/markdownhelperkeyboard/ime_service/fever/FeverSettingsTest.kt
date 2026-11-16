package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeverSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    @Before fun setup() { prefs.edit().clear().commit(); AppPreference.init(context) }
    @Test fun defaultsAndAllPersistentSettingsRoundTrip() {
        assertEquals(FeverSettings(), FeverSettings.read(prefs))
        prefs.edit().putBoolean(FeverSettings.FULL_SCREEN, true).putInt(FeverSettings.INTENSITY, 1)
            .putBoolean(FeverSettings.SOUND, false).putBoolean(FeverSettings.VIBRATION, false)
            .putInt(FeverSettings.VOLUME, 23).putInt(FeverSettings.HIT_RATE, 100)
            .putInt(FeverSettings.COMBO_SECONDS, 8).putBoolean(FeverSettings.KEYWORDS_ENABLED, false).commit()
        FeverSettings.saveKeywords(prefs, listOf("  yes ", "好き", "yes", ""))
        assertEquals(FeverSettings(true, 1, false, false, 23, 100, 8000, false, listOf("yes", "好き")), FeverSettings.read(prefs))
        FeverSettings.saveKeywords(prefs, emptyList()); assertTrue(FeverSettings.read(prefs).keywords.isEmpty())
    }
    @Test fun numericBoundsAndMalformedKeywordDataAreHandled() {
        prefs.edit().putInt(FeverSettings.HIT_RATE, 999).putInt(FeverSettings.COMBO_SECONDS, -1)
            .putInt(FeverSettings.INTENSITY, 20).putInt(FeverSettings.VOLUME, -2)
            .putString(FeverSettings.KEYWORDS, "bad json").commit()
        val s = FeverSettings.read(prefs)
        assertEquals(100, s.hitRate); assertEquals(1000L, s.comboTimeoutMillis)
        assertEquals(2, s.intensity); assertEquals(0, s.volume)
        assertEquals(FeverSettings.DEFAULT_KEYWORDS, s.keywords)
    }
    @Test fun bothSettingsSearchScopesExposeTheSameDestination() {
        prefs.edit().putString("keyboard_skin_preference", "fever").commit()
        for (scope in SettingSearchScope.entries) {
            val item = SettingSearchIndex.searchable(context, scope).single { it.key == FeverSettings.ROUTE }
            assertTrue(item.keywords.any { it.contains("パチンコ") })
        }
    }
}
