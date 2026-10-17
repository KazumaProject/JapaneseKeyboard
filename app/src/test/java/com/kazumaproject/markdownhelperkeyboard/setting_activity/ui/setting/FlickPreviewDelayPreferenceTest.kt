package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.FlickPreviewDelaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickPreviewDelayPreferenceTest {
    private lateinit var context: Context
    private val resources = listOf(R.xml.pref_operation_feedback, R.xml.pref_common_legacy)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        AppPreference.flick_editor_preview_preference = true
    }

    @Test
    fun touchChangesInBothSettingsSynchronizeTheThumbLabelAndSavedDelay() {
        resources.forEach { xml ->
            val preference = inflateDelay(xml)
            val row = FrameLayout(preference.context)
            val slider = SeekBar(preference.context).apply { id = androidx.preference.R.id.seekbar }
            val label = TextView(preference.context).apply { id = androidx.preference.R.id.seekbar_value }
            row.addView(slider)
            row.addView(label)
            preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row))
            val listener = ReflectionHelpers.getField<SeekBar.OnSeekBarChangeListener>(
                preference, "mSeekBarChangeListener",
            )

            mapOf(7 to 5, 8 to 10, 499 to 500, 0 to 0).forEach { (raw, expected) ->
                listener.onStartTrackingTouch(slider)
                slider.progress = raw
                listener.onProgressChanged(slider, raw, true)
                listener.onStopTrackingTouch(slider)

                assertEquals("thumb in XML $xml for $raw", expected, slider.progress)
                assertEquals(expected.toString(), label.text.toString())
                assertEquals(expected, preference.value)
                assertEquals(expected, PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(FlickPreviewDelaySettings.KEY, -1))
                assertEquals(expected, AppPreference.flick_editor_preview_delay_ms)
            }
        }
    }

    @Test
    fun openingEitherSettingsScreenNormalizesAnExistingNonStepValue() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        resources.forEach { xml ->
            preferences.edit().putInt(FlickPreviewDelaySettings.KEY, 7).commit()
            assertEquals(5, inflateDelay(xml).value)
            assertEquals(5, preferences.getInt(FlickPreviewDelaySettings.KEY, -1))
        }
    }

    @Test
    fun settingCardShowsTheNormalizedValueAndSavesTheSameStepsAndDefault() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().putInt(FlickPreviewDelaySettings.KEY, 7).commit()
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val themed = ContextThemeWrapper(controller.get(), R.style.Theme_MarkdownKeyboard)
            val editor = SettingCardEditorController(themed)
            val destination = SettingSearchIndex.searchable(themed)
                .first {
                    it.key == FlickPreviewDelaySettings.KEY &&
                        it.destination is SettingDestinationType.SeekBarPreference
                }
            assertEquals(5, (destination.destination as SettingDestinationType.SeekBarPreference).increment)
            assertEquals("5", editor.currentValueLabel(destination))

            assertTrue(editor.handleClick(destination) {})
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val slider = requireNotNull(findSeekBar(dialog.window!!.decorView))
            assertEquals(1, slider.progress) // The dialog uses an index into 0, 5, 10, ...
            slider.progress = 2
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(10, preferences.getInt(FlickPreviewDelaySettings.KEY, -1))
            assertEquals(10, AppPreference.flick_editor_preview_delay_ms)
            assertEquals("10", editor.currentValueLabel(destination))

            assertTrue(editor.handleClick(destination) {})
            shadowOf(Looper.getMainLooper()).idle()
            val resetDialog = ShadowDialog.getLatestDialog() as AlertDialog
            resetDialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            resetDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, preferences.getInt(FlickPreviewDelaySettings.KEY, -1))
            assertEquals(0, AppPreference.flick_editor_preview_delay_ms)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun inflateDelay(xml: Int): SeekBarPreference {
        val themed = ContextThemeWrapper(context, R.style.Theme_MarkdownKeyboard)
        val manager = PreferenceManager(themed)
        val screen = manager.inflateFromResource(themed, xml, null)
        return requireNotNull(screen.findPreference<SeekBarPreference>(FlickPreviewDelaySettings.KEY))
            .also { it.configureFlickPreviewDelay() }
    }

    private fun findSeekBar(view: View): SeekBar? {
        if (view is SeekBar) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findSeekBar(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
