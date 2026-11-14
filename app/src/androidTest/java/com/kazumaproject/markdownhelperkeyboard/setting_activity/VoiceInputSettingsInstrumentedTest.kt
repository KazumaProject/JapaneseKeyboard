package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.VoiceInputPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.VoiceInputRuleDialogFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceInputSettingsInstrumentedTest {
    @Test
    fun textRulesCanBeAddedRestoredEditedDisabledAndDeletedInBothSettingsHomes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        for (newHome in listOf(true, false)) {
            prefs.edit().clear().putBoolean("setting_use_new_home_screen_preference", newHome).commit()
            AppPreference.init(context)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    host.navController.navigate(R.id.voiceInputPreferenceFragment)
                }
                awaitVoicePreferences(scenario)
                scenario.onActivity { activity ->
                    val fragment = voiceFragment(activity)
                    assertFalse(AppPreference.voice_input_text_replacement_enabled)
                    fragment.findPreference<Preference>(AppPreference.VOICE_INPUT_TEXT_REPLACEMENT_ADD_KEY)!!.performClick()
                    fragment.childFragmentManager.executePendingTransactions()
                    val dialog = ruleDialog(fragment)
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
                    assertTrue("Empty source should keep the editor open", dialog.isShowing)
                    dialog.findViewById<android.widget.EditText>(R.id.source)!!.setText("僕")
                    dialog.findViewById<android.widget.EditText>(R.id.replacement)!!.setText("ぼく")
                }
                // Also retain an unsaved rule when Android recreates the activity.
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                awaitVoicePreferences(scenario)
                scenario.onActivity { activity ->
                    val fragment = voiceFragment(activity)
                    val dialog = ruleDialog(fragment)
                    assertEquals("僕", dialog.findViewById<android.widget.EditText>(R.id.source)!!.text.toString())
                    assertEquals("ぼく", dialog.findViewById<android.widget.EditText>(R.id.replacement)!!.text.toString())
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
                    fragment.childFragmentManager.executePendingTransactions()
                    val enabled = fragment.findPreference<SwitchPreferenceCompat>(AppPreference.VOICE_INPUT_TEXT_REPLACEMENT_ENABLED_KEY)!!
                    enabled.isChecked = true
                    assertEquals("ぼくはぼくです", AppPreference.voice_input_settings.transform("僕は僕です", "ja-JP"))
                    assertEquals("ぼく says hello", AppPreference.voice_input_settings.transform("僕 says hello", "en-CA"))
                }
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                awaitVoicePreferences(scenario)
                scenario.onActivity { activity ->
                    val fragment = voiceFragment(activity)
                    val enabled = fragment.findPreference<SwitchPreferenceCompat>(AppPreference.VOICE_INPUT_TEXT_REPLACEMENT_ENABLED_KEY)!!
                    assertTrue(enabled.isChecked)
                    enabled.isChecked = false
                    assertEquals("僕", AppPreference.voice_input_settings.transform("僕", "ja-JP"))
                    assertEquals("ぼく", AppPreference.voice_input_text_replacement_rules.single().replacement)
                    val category = fragment.findPreference<PreferenceCategory>(AppPreference.VOICE_INPUT_TEXT_REPLACEMENT_RULES_KEY)!!
                    category.getPreference(0).performClick()
                    fragment.childFragmentManager.executePendingTransactions()
                    ruleDialog(fragment).findViewById<android.widget.EditText>(R.id.replacement)!!.setText("ボク")
                    ruleDialog(fragment).getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
                    fragment.childFragmentManager.executePendingTransactions()
                    enabled.isChecked = true
                    assertEquals("ボク", AppPreference.voice_input_settings.transform("僕", "ja-JP"))
                    category.getPreference(0).performClick()
                    fragment.childFragmentManager.executePendingTransactions()
                    ruleDialog(fragment).getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).performClick()
                }
                scenario.onActivity { activity ->
                    val fragment = voiceFragment(activity)
                    fragment.childFragmentManager.executePendingTransactions()
                    assertTrue(AppPreference.voice_input_text_replacement_rules.isEmpty())
                    val category = fragment.findPreference<PreferenceCategory>(AppPreference.VOICE_INPUT_TEXT_REPLACEMENT_RULES_KEY)!!
                    assertEquals(1, category.preferenceCount)
                }
            }
        }
    }

    private fun ruleDialog(fragment: VoiceInputPreferenceFragment) =
        (fragment.childFragmentManager.findFragmentByTag(VoiceInputRuleDialogFragment.TAG) as VoiceInputRuleDialogFragment)
            .requireDialog() as androidx.appcompat.app.AlertDialog

    @Test
    fun newAndLegacySettingsLoadAndRetainReplacementAfterRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        for (newHome in listOf(true, false)) {
            prefs.edit().clear().putBoolean("setting_use_new_home_screen_preference", newHome).commit()
            AppPreference.init(context)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    host.navController.navigate(R.id.voiceInputPreferenceFragment)
                }
                awaitVoicePreferences(scenario)
                scenario.onActivity { activity ->
                    val fragment = voiceFragment(activity)
                    val enabled = fragment.findPreference<SwitchPreferenceCompat>(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_ENABLED_KEY)!!
                    val replacement = fragment.findPreference<ListPreference>(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_KEY)!!
                    val custom = fragment.findPreference<EditTextPreference>(AppPreference.VOICE_INPUT_SPACE_CUSTOM_TEXT_KEY)!!
                    assertFalse(enabled.isChecked)
                    assertEquals("remove", replacement.value)
                    assertFalse(replacement.isEnabled)
                    assertFalse(custom.isEnabled)
                    assertTrue(enabled.callChangeListener(true))
                    enabled.isChecked = true
                    assertTrue(replacement.isEnabled)
                    assertTrue(replacement.callChangeListener("custom"))
                    replacement.value = "custom"
                    assertTrue(custom.isEnabled)
                    custom.text = " ・ "
                    assertTrue(custom.summary.toString().contains(" ・ "))
                    assertTrue(enabled.callChangeListener(false))
                    enabled.isChecked = false
                }
                scenario.recreate()
                scenario.awaitSettingsContentReady()
                awaitVoicePreferences(scenario)
                scenario.onActivity { activity ->
                    val fragment = voiceFragment(activity)
                    val enabled = fragment.findPreference<SwitchPreferenceCompat>(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_ENABLED_KEY)!!
                    val replacement = fragment.findPreference<ListPreference>(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_KEY)!!
                    val custom = fragment.findPreference<EditTextPreference>(AppPreference.VOICE_INPUT_SPACE_CUSTOM_TEXT_KEY)!!
                    assertFalse(replacement.isEnabled)
                    assertFalse(custom.isEnabled)
                    assertEquals("custom", replacement.value)
                    assertEquals(" ・ ", custom.text)
                    assertTrue(enabled.callChangeListener(true))
                    enabled.isChecked = true
                    assertTrue(custom.isEnabled)
                    assertEquals("あ ・ い", AppPreference.voice_input_settings.transform("あ い", "ja-JP"))
                    assertEquals("hello world", AppPreference.voice_input_settings.transform("hello world", "en-CA"))
                }
            }
        }
    }

    private fun voiceFragment(activity: MainActivity): VoiceInputPreferenceFragment {
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        return host.childFragmentManager.primaryNavigationFragment as VoiceInputPreferenceFragment
    }

    private fun awaitVoicePreferences(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
                val fragment = host?.childFragmentManager?.primaryNavigationFragment as? VoiceInputPreferenceFragment
                ready = fragment?.findPreference<SwitchPreferenceCompat>(AppPreference.VOICE_INPUT_SPACE_REPLACEMENT_ENABLED_KEY) != null
            }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue("Voice input preferences did not finish loading", ready)
    }
}
