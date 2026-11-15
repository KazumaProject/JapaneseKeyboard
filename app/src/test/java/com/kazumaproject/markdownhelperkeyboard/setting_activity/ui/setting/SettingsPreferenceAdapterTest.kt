package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsPreferenceAdapterTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MarkdownKeyboard)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
    }

    private fun adapter(preference: Preference): SettingsPreferenceAdapter {
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        screen.addPreference(preference)
        return SettingsPreferenceAdapter(screen)
    }

    private fun parent() = RecyclerView(context).apply { layoutManager = LinearLayoutManager(context) }

    @Test fun conventionalNavigationAndEditableRowsHaveNoTrailingArrow() {
        AppPreference.setting_use_new_home_screen_preference = false
        for (preference in listOf(Preference(context), ListPreference(context), EditTextPreference(context))) {
            val adapter = adapter(preference)
            val holder = adapter.onCreateViewHolder(parent(), adapter.getItemViewType(0))
            adapter.onBindViewHolder(holder, 0)
            val widget = holder.findViewById(android.R.id.widget_frame) as ViewGroup
            assertFalse("${preference.javaClass.simpleName} should have no trailing arrow", widget.visibility == View.VISIBLE && widget.childCount > 0)
        }
    }

    @Test fun conventionalSwitchKeepsItsNativeWidgetAndSavedState() {
        AppPreference.setting_use_new_home_screen_preference = false
        val preference = SwitchPreferenceCompat(context).apply {
            key = "settings_adapter_test_switch"
            widgetLayoutResource = androidx.preference.R.layout.preference_widget_switch_compat
        }
        val adapter = adapter(preference)
        preference.isChecked = true
        val holder = adapter.onCreateViewHolder(parent(), adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        val widget = holder.findViewById(android.R.id.widget_frame) as ViewGroup
        assertEquals(View.VISIBLE, widget.visibility)
        assertEquals(1, widget.childCount)
        assertTrue((widget.getChildAt(0) as android.widget.CompoundButton).isChecked)
    }

    @Test fun newSettingsKeepTheirNavigationArrow() {
        AppPreference.setting_use_new_home_screen_preference = true
        val adapter = adapter(Preference(context))
        val holder = adapter.onCreateViewHolder(parent(), adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        val widget = holder.findViewById(android.R.id.widget_frame) as ViewGroup
        assertEquals(View.VISIBLE, widget.visibility)
        assertEquals(1, widget.childCount)
    }

    @Test fun rebindingANewSettingsRowInConventionalModeHidesItsArrow() {
        AppPreference.setting_use_new_home_screen_preference = true
        val adapter = adapter(Preference(context))
        val holder = adapter.onCreateViewHolder(parent(), adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        AppPreference.setting_use_new_home_screen_preference = false
        adapter.onBindViewHolder(holder, 0)
        assertEquals(View.GONE, holder.findViewById(android.R.id.widget_frame)!!.visibility)
    }
}
