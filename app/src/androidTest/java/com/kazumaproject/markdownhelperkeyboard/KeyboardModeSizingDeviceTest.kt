package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.tenkey.TenKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class KeyboardModeSizingDeviceTest {
    @Test fun tenkeySizesSurviveModeSwitchesAndGuideRedraws() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_MarkdownKeyboard)
            val keyboard = android.view.LayoutInflater.from(context)
                .inflate(R.layout.fragment_tenkey_candidate_letter_size, null)
                .findViewById<TenKey>(R.id.tenkeyLetterSizePreview)
            val modes = listOf(InputMode.ModeJapanese, InputMode.ModeEnglish, InputMode.ModeNumber)
            val centers = modes.zip(listOf(18f, 22f, 26f)).toMap()
            val guides = modes.zip(listOf(6f, 10f, 16f)).toMap()
            repeat(2) {
                modes.forEach { mode ->
                    keyboard.setCurrentMode(mode)
                    keyboard.setModeLetterSizes(centers, guides)
                    keyboard.setFlickGuideEnabled(true, true, true)
                    val key = keyboard.findViewById<TextView>(com.kazumaproject.tenkey.R.id.key_1)
                    assertEquals(sp(context, centers.getValue(mode)), key.textSize, .1f)
                    val text = key.text as Spanned
                    val sizes = text.getSpans(0, text.length, AbsoluteSizeSpan::class.java)
                    assertTrue("Guide size spans must exist", sizes.isNotEmpty())
                    sizes.forEach { assertEquals(sp(context, guides.getValue(mode)).toInt(), it.size) }
                    keyboard.setFlickGuideEnabled(false)
                    assertEquals(sp(context, centers.getValue(mode)), key.textSize, .1f)
                }
            }
        }
    }

    @Test fun existingScreensSwitchModesSaveAndRestorePreviewState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val previous = preferences.all.filterKeys { it.matches(Regex("(tenkey|sumire)_(hiragana|english|symbols)_(key|guide)_text_size_sp")) }
        var activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            waitForView(activity, R.id.nav_host_fragment_activity_main)
            for ((surface, destination) in listOf("tenkey" to R.id.tenKeyCandidateLetterSizeFragment,
                "sumire" to R.id.flickKeyboardSizeSettingsFragment)) {
                instrumentation.runOnMainSync {
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    host.navController.navigate(destination)
                }
                waitForView(activity, R.id.sizingGuidePreview)
                instrumentation.runOnMainSync {
                    val modes = activity.findViewById<MaterialButtonToggleGroup>(R.id.sizingModes)
                    val guideSwitch = activity.findViewById<MaterialSwitch>(R.id.sizingGuidePreview)
                    guideSwitch.isChecked = true
                    for ((id, mode) in listOf(R.id.sizingJapanese to KeyboardInputMode.HIRAGANA,
                        R.id.sizingEnglish to KeyboardInputMode.ENGLISH, R.id.sizingNumbers to KeyboardInputMode.SYMBOLS)) {
                        modes.check(id)
                        val slider = activity.findViewById<Slider>(R.id.sizingGuide)
                        slider.value = 10f
                        slider.requestFocus()
                        slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                        slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                        assertEquals(11f, AppPreference.modeKeySize(surface, mode, true)!!, .01f)
                    }
                    modes.check(R.id.sizingJapanese)
                    assertEquals(11f, activity.findViewById<Slider>(R.id.sizingGuide).value, .01f)
                }
                val previousActivity = activity
                instrumentation.runOnMainSync { previousActivity.recreate() }
                val deadline = SystemClock.uptimeMillis() + 20000
                var recreated: MainActivity? = null
                while (recreated == null && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        recreated = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                            .filterIsInstance<MainActivity>().firstOrNull { it !== previousActivity }
                    }
                    if (recreated == null) SystemClock.sleep(50)
                }
                activity = requireNotNull(recreated) { "Activity must recreate" }
                waitForView(activity, R.id.sizingGuidePreview)
                instrumentation.runOnMainSync {
                    assertTrue(activity.findViewById<MaterialSwitch>(R.id.sizingGuidePreview).isChecked)
                    assertEquals(11f, activity.findViewById<Slider>(R.id.sizingGuide).value, .01f)
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    (host.childFragmentManager.primaryNavigationFragment?.view as? androidx.core.widget.NestedScrollView)?.scrollTo(0, 0)
                }
                SystemClock.sleep(200)
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                File(context.cacheDir, "sizing-$surface.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                AppPreference.resetModeKeySizes("tenkey")
                AppPreference.resetModeKeySizes("sumire")
                preferences.edit().apply {
                    previous.forEach { (key, value) -> putFloat(key, value as Float) }
                }.commit()
            }
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun waitForView(activity: MainActivity, id: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var ready = false
        val deadline = SystemClock.uptimeMillis() + 20000
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { ready = activity.findViewById<android.view.View>(id) != null }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue("Settings content must finish loading", ready)
    }

    private fun sp(context: android.content.Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics)
}
