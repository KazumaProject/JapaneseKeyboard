package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.gojuon_size_setting.GojuonSizeSettingsFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingDestinations
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchIndex
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingTabRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GojuonSizeSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    @Test fun newAndLegacySearchOpenTheGojuonSizingScreen() {
        val entry = SettingSearchIndex.searchable(context).single { it.key == "gojuon_keyboard_size_preference" }
        assertEquals(R.id.gojuonSizeSettingsFragment, SettingDestinations.destinationId(entry.destination))
        val legacy = SettingSearchIndex.legacySearchable(context).single { it.key == entry.key }
        assertEquals(SettingTabRegistry.TAB_GOJUON, legacy.legacyTarget?.tabKey)
        assertEquals(R.id.gojuonPreferenceFragment, SettingDestinations.destinationId(legacy.destination))
        val tab = SettingTabRegistry.createTabs().single { it.key == SettingTabRegistry.TAB_GOJUON }
        assertEquals(R.xml.pref_gojuon, tab.xmlRes)
        assertEquals(R.id.gojuonPreferenceFragment, tab.destinationId)
        assertEquals(R.id.gojuonPreferenceFragment,
            SettingDestinations.routeDestinationId("setting_route_gojuon_preferences"))
    }

    @Test fun openingPreviewDoesNotSaveAndEditingResetsOnlyGojuonSettings() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.Theme_MarkdownKeyboard)
        val activity = controller.setup().get()
        val fragment = GojuonSizeSettingsFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        val root = fragment.requireView()
        assertNull(root.findViewById<View>(R.id.sizingGuide))
        assertEquals(18f, root.findViewById<Slider>(R.id.sizingCenter).value, 0f)
        for (mode in KeyboardInputMode.entries) assertNull(AppPreference.modeKeySize("gojuon", mode, false))
        root.findViewById<MaterialButtonToggleGroup>(R.id.sizingModes).check(R.id.sizingEnglish)
        val slider = root.findViewById<Slider>(R.id.sizingCenter)
        slider.requestFocus()
        slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        val saved = AppPreference.modeKeySize("gojuon", KeyboardInputMode.ENGLISH, false)
        assertNotNull(saved)
        val key = root.findViewById<TextView>(com.kazumaproject.gojuon_keyboard.R.id.key_51)
        assertEquals(saved!! * context.resources.displayMetrics.scaledDensity, key.textSize, .01f)
        AppPreference.setModeKeySize("tenkey", KeyboardInputMode.ENGLISH, false, 25f)
        AppPreference.gojuon_key_width_scale_percent = 80
        root.findViewById<View>(R.id.sizingResetMode).performClick()
        assertNull(AppPreference.modeKeySize("gojuon", KeyboardInputMode.ENGLISH, false))
        assertEquals(80, AppPreference.gojuon_key_width_scale_percent)
        assertEquals(25f, AppPreference.modeKeySize("tenkey", KeyboardInputMode.ENGLISH, false)!!, 0f)
        root.findViewById<View>(R.id.sizingResetAll).performClick()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(100, AppPreference.gojuon_key_width_scale_percent)
        assertEquals(25f, AppPreference.modeKeySize("tenkey", KeyboardInputMode.ENGLISH, false)!!, 0f)
        controller.pause().stop().destroy()
    }
}
