package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeverSettingsVisibilityTest {
    @Test fun bothSearchScopesAndFrequentChoicesFollowSkinChangesWithoutRestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        for (skin in listOf(KeyboardSkinId.DEFAULT, KeyboardSkinId.FEVER,
            KeyboardSkinId.CUPERTINO_DARK, KeyboardSkinId.FEVER)) {
            prefs.edit().putString(KeyboardSkinId.PREFERENCE_KEY, skin.preferenceValue).commit()
            val expected = skin == KeyboardSkinId.FEVER
            assertEquals(expected, SettingDestinations.frequentCandidates(context).any { it.key == "fever_settings" })
            for (scope in SettingSearchScope.entries) {
                assertEquals("$skin $scope", expected,
                    SettingSearchIndex.searchable(context, scope).any { it.key == "fever_settings" })
            }
        }
    }
}
