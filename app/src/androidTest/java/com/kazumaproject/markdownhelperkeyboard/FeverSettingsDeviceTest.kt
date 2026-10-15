package com.kazumaproject.markdownhelperkeyboard

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.ime_service.fever.FeverSettings
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.FeverSettingsFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeverSettingsDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private fun descendants(fragment: Fragment): List<Fragment> = listOf(fragment) + fragment.childFragmentManager.fragments.flatMap(::descendants)
    private fun click(text: String) {
        val deadline = SystemClock.uptimeMillis() + 15000
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isVisibleToUser && node.text?.toString() == text) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { find(it)?.let { hit -> return hit } }
            return null
        }
        while (SystemClock.uptimeMillis() < deadline) {
            var target = ins.uiAutomation.rootInActiveWindow?.let(::find)
            while (target != null) {
                if (target.isClickable && target.isEnabled && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { ins.waitForIdleSync(); return }
                target = target.parent
            }
            SystemClock.sleep(100)
        }
        error("Missing settings entry: $text; UI: " + ins.uiAutomation.rootInActiveWindow?.let { root ->
            fun labels(node: AccessibilityNodeInfo): String = node.text.toString() + " " +
                (0 until node.childCount).joinToString { node.getChild(it)?.let(::labels).orEmpty() }
            labels(root)
        })
    }
    @Test(timeout = 180000) fun newAndLegacySettingsShareFeverControlsAndRetainThemAfterRecreation() {
        check(context.packageName.startsWith("com.kazumaproject.skinfidelity"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val originallyEnabled = target in shell("ime list -s").lines()
        val originalSkin = prefs.getString("keyboard_skin_preference", null)
        val original = prefs.all.filterKeys { it.startsWith(FeverSettings.PREFIX) }
        try {
            shell("ime enable $target")
            prefs.edit().putString("keyboard_skin_preference", "fever").putBoolean(FeverSettings.FULL_SCREEN, false).commit()
            for (legacy in listOf(false, true)) {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.awaitSettingsContentReady()
                    scenario.onActivity {
                        val host = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                        host.navController.navigate(if (legacy) R.id.settingMainFragment else R.id.keyboardThemeFragment)
                    }
                    if (legacy) {
                        var selected = false
                        val deadline = SystemClock.uptimeMillis() + 15000
                        while (!selected && SystemClock.uptimeMillis() < deadline) {
                            ins.runOnMainSync {
                                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                                activity?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.setting_view_pager)?.let {
                                    it.setCurrentItem(1, false)
                                    selected = activity.supportFragmentManager.fragments.flatMap(::descendants)
                                        .any { fragment -> fragment is com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment && fragment.isResumed }
                                }
                            }
                            SystemClock.sleep(100)
                        }
                        check(selected)
                    }
                    for (skin in listOf("default", "fever", "cupertino_dark", "fever")) {
                        scenario.onActivity { activity ->
                            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                            host.navController.navigate(R.id.keyboardSkinSelectionFragment)
                        }
                        ins.waitForIdleSync()
                        prefs.edit().putString("keyboard_skin_preference", skin).commit()
                        scenario.onActivity { activity ->
                            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                            check(host.navController.popBackStack())
                        }
                        val deadline = SystemClock.uptimeMillis() + 15000
                        var correct = false
                        while (!correct && SystemClock.uptimeMillis() < deadline) {
                            scenario.onActivity { activity ->
                                val theme = activity.supportFragmentManager.fragments.flatMap(::descendants)
                                    .filterIsInstance<com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment>()
                                    .firstOrNull { it.isResumed }
                                val entry = theme?.findPreference<androidx.preference.Preference>("fever_settings")
                                correct = entry != null && entry.isVisible == (skin == "fever")
                                if (skin == "fever" && entry != null) assertTrue(entry.isEnabled)
                            }
                            SystemClock.sleep(100)
                        }
                        assertTrue("FEVER visibility legacy=$legacy skin=$skin", correct)
                    }
                    click(context.getString(R.string.fever_settings_title))
                    click(context.getString(R.string.fever_full_screen))
                    assertEquals(!legacy, prefs.getBoolean(FeverSettings.FULL_SCREEN, false))
                    scenario.onActivity { activity ->
                        val fragment = activity.supportFragmentManager.fragments.flatMap(::descendants).filterIsInstance<FeverSettingsFragment>().first()
                        val expected = if (legacy) 27 else 73
                        fragment.findPreference<SeekBarPreference>(FeverSettings.HIT_RATE)!!.value = expected
                        assertEquals(expected, FeverSettings.read(prefs).hitRate)
                    }
                    scenario.recreate(); scenario.awaitSettingsContentReady()
                    assertEquals(!legacy, FeverSettings.read(prefs).fullScreen)
                    assertEquals(if (legacy) 27 else 73, FeverSettings.read(prefs).hitRate)
                    // Open the common screen through each real search scope as well.
                    scenario.onActivity { activity ->
                        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                        host.navController.navigate(R.id.settingSearchFragment, androidx.core.os.bundleOf(
                            com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingSearchFragment.ARG_SEARCH_SCOPE to
                                (if (legacy) "LEGACY_TABS" else "NEW_HOME")
                        ))
                    }
                    val deadline = SystemClock.uptimeMillis() + 15000
                    var searched = false
                    while (!searched && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { activity ->
                            activity.findViewById<android.widget.EditText>(R.id.setting_search_input)?.let {
                                it.setText("fever"); searched = true
                            }
                        }
                        SystemClock.sleep(100)
                    }
                    check(searched)
                    click(context.getString(R.string.fever_settings_title))
                    click(context.getString(R.string.fever_preview))
                    SystemClock.sleep(500)
                    val bitmap = requireNotNull(ins.uiAutomation.takeScreenshot())
                    val evidence = java.io.File(context.getExternalFilesDir(null), "fever-evidence").apply { mkdirs() }
                    java.io.File(evidence, "settings-preview-$legacy.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                    click(context.getString(android.R.string.ok))
                    fun editWords(value: String?) {
                        scenario.onActivity { activity ->
                            val fragment = activity.supportFragmentManager.fragments.flatMap(::descendants).filterIsInstance<FeverSettingsFragment>().first { it.isResumed }
                            fragment.findPreference<androidx.preference.Preference>(FeverSettings.KEYWORDS)!!.performClick()
                        }
                        if (value != null) ins.runOnMainSync {
                            fun find(view: android.view.View): android.widget.EditText? {
                                if (view is android.widget.EditText && view.isShown) return view
                                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                                return null
                            }
                            val editor = android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOf(::find)
                            editor.setText(value)
                        }
                        click(context.getString(if (value == null) R.string.fever_restore_words else android.R.string.ok))
                    }
                    editWords("ＦＥＶＥＲ\nテスト")
                    assertEquals(listOf("ＦＥＶＥＲ", "テスト"), FeverSettings.read(prefs).keywords)
                    editWords(""); assertTrue(FeverSettings.read(prefs).keywords.isEmpty())
                    editWords(null); assertEquals(FeverSettings.DEFAULT_KEYWORDS, FeverSettings.read(prefs).keywords)
                }
            }
        } finally {
            val edit = prefs.edit()
            prefs.all.keys.filter { it.startsWith(FeverSettings.PREFIX) }.forEach(edit::remove)
            original.forEach { (k,v) -> when(v) { is Boolean -> edit.putBoolean(k,v); is Int -> edit.putInt(k,v); is String -> edit.putString(k,v) } }
            if (originalSkin == null) edit.remove("keyboard_skin_preference") else edit.putString("keyboard_skin_preference", originalSkin)
            check(edit.commit())
            if (!originallyEnabled) shell("ime disable $target")
        }
    }
}
