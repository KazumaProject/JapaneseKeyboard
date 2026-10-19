package com.kazumaproject.markdownhelperkeyboard.morse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MorseSettingsEntryPointTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun oldTabAndNewIndependentCategoryOpenTheSamePreferences() {
        val legacy = SettingTabRegistry.createTabs().single { it.key == SettingTabRegistry.TAB_MORSE }
        assertEquals(R.id.morsePreferenceFragment, legacy.destinationId)
        assertEquals(R.xml.pref_morse, legacy.xmlRes)
        val category = SettingDestinations.categories(context).single { it.category == SettingCategory.MORSE }
        assertEquals(R.id.morsePreferenceFragment, SettingDestinations.destinationId(category.destination))
    }
}
