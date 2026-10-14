package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.widget.ListView
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.external_dictionary.ExternalDictionarySettingsFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.*
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.qwerty_keyboard.ui.QWERTYButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ClassifiedDictionaryUiInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun main(block: () -> Unit) = ins.runOnMainSync(block)
    private fun shell(command: String): String = ins.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun views() = WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        error(message)
    }
    private fun screenshot(name: String) {
        ins.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun everyCategoryLicenseDialogRendersOffline() {
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lines().contains(target)
        shell("ime enable $target")
        try {
        shell("am start -f 0x10008000 -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity")
        lateinit var activity: MainActivity
        await("Settings not ready") {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
            val host = resumed?.supportFragmentManager?.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
            val ready = resumed != null && resumed.window.decorView.hasWindowFocus() && MainActivity::class.java.getDeclaredField("isSettingsContentReady")
                .apply { isAccessible = true }.getBoolean(resumed)
            if (!ready || host?.navController?.currentDestination == null) false else { activity = resumed!!; true }
        }
        main { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        main { (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController.navigate(R.id.openSourceFragment) }
        lateinit var list: ListView
        try { await("License list not shown") { activity.findViewById<ListView>(R.id.open_source_license_list)?.let { list = it; true } ?: false } } catch (failure: Throwable) {
            screenshot("classified-license-failure.png")
            main { println("CLASSIFIED_SETTINGS_DEST " + (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController.currentDestination); println("CLASSIFIED_SETTINGS_TEXT " + descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()) }
            throw failure
        }
        for (spec in ClassifiedDictionaries.all) {
            main {
                val name = activity.getString(spec.nameRes)
                val index = (0 until list.adapter.count).single { list.adapter.getItem(it) == name }
                list.performItemClick(list, index, index.toLong())
            }
            await("${spec.id} notice did not render") { views().filterIsInstance<WebView>().any { it.isShown && it.height >= 200 * context.resources.displayMetrics.density && it.contentHeight > 0 && it.progress == 100 && it.title?.contains(spec.id) == true } }
            if (spec.id == "person") { SystemClock.sleep(150); screenshot("classified-license.png") }
            main { views().filterIsInstance<TextView>().first { it.isShown && it.id == android.R.id.button1 }.performClick() }
            await("License dialog did not close") { views().filterIsInstance<WebView>().none { it.isShown } }
        }
        } finally {
            if (!wasEnabled) shell("ime disable $target")
        }
    }

    @Test fun settingsExposeAndPersistAll28IndependentSwitches() {
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lines().contains(target)
        shell("ime enable $target")
        try {
        shell("am start -f 0x10008000 -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity")
        lateinit var activity: MainActivity
        await("Settings not ready") {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
            if (resumed == null || !resumed.isSettingsContentReady || !resumed.window.decorView.hasWindowFocus()) false else { activity = resumed; true }
        }
        lateinit var host: NavHostFragment
        main {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            host.navController.navigate(R.id.externalDictionarySettingsFragment)
        }
        lateinit var fragment: ExternalDictionarySettingsFragment
        try { await("Dictionary settings not ready") {
            val current = host.childFragmentManager.fragments.filterIsInstance<ExternalDictionarySettingsFragment>().firstOrNull()
            if (current?.preferenceScreen == null) false else { fragment = current; true }
        } } catch (failure: Throwable) {
            screenshot("classified-settings-failure.png")
            main { println("CLASSIFIED_SETTINGS_DEST " + host.navController.currentDestination); println("CLASSIFIED_SETTINGS_FRAGMENTS " + host.childFragmentManager.fragments.map { it.javaClass.name }); println("CLASSIFIED_SETTINGS_TEXT " + descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()) }
            throw failure
        }
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val previous = ClassifiedDictionaries.all.associate { it.category to store.isOptionalBundledEnabled(it.category) }
        fun findSwitch(spec: ClassifiedDictionarySpec): SwitchPreferenceCompat? {
            // Loading publishes the hierarchy before its RecyclerView layout completes.
            val loading = com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.AsyncPreferenceFragment::class.java
                .getDeclaredField("loadJob").apply { isAccessible = true }.get(fragment) as? kotlinx.coroutines.Job
            if (loading?.isActive == true) return null
            val screen = fragment.preferenceScreen ?: return null
            val group = (0 until screen.preferenceCount).map { screen.getPreference(it) }
                .filterIsInstance<PreferenceCategory>().firstOrNull { it.title == activity.getString(spec.nameRes) } ?: return null
            return (0 until group.preferenceCount).map { group.getPreference(it) }.filterIsInstance<SwitchPreferenceCompat>().singleOrNull()
        }
        try {
            for (spec in ClassifiedDictionaries.all) {
                val original = previous.getValue(spec.category)
                await("${spec.id} switch not ready") { findSwitch(spec)?.let { it.onPreferenceChangeListener != null && it.isChecked == original } == true }
                main {
                    val switch = findSwitch(spec)!!
                    assertTrue(switch.onPreferenceChangeListener!!.onPreferenceChange(switch, !original))
                    assertEquals(spec.id, !original, store.isOptionalBundledEnabled(spec.category))
                }
                await("${spec.id} enabled state not displayed") { findSwitch(spec)?.let { it.onPreferenceChangeListener != null && it.isChecked == !original } == true }
                main {
                    val switch = findSwitch(spec)!!
                    assertTrue(switch.onPreferenceChangeListener!!.onPreferenceChange(switch, original))
                    assertEquals(spec.id, original, store.isOptionalBundledEnabled(spec.category))
                }
                await("${spec.id} restored state not displayed") { findSwitch(spec)?.let { it.onPreferenceChangeListener != null && it.isChecked == original } == true }
            }
        } finally {
            previous.forEach { (category, value) -> store.setOptionalBundledEnabled(category, value) }
        }
        } finally {
            if (!wasEnabled) shell("ime disable $target")
        }
    }

    @Test fun actualRomajiImeShowsAwardLabelAndCommitsCandidate() {
        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        check(context.packageName.contains(".classifiedprobe")) { "Use the isolated classifiedprobe APK" }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val previous = store.isOptionalBundledEnabled(DictionaryCategory.CLASSIFIED_AWARD)
        val oldIme = shell("settings get secure default_input_method")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("ime list -s").lines().contains(target)
        try {
            prefs.edit().putString("keyboard_order_preference", "[\"ROMAJI\"]")
                .putBoolean("save_last_used_keyboard", false).putBoolean("live_conversion_preference", false)
                .putBoolean("show_dictionary_candidate_labels_preference", false)
                .putBoolean("clipboard_preview_enable_preference", false).putBoolean("clipboard_history_preference", false).commit()
            store.setOptionalBundledEnabled(DictionaryCategory.CLASSIFIED_AWARD, true)
            shell("ime enable $target"); shell("ime set $target")
            shell("am start -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity")
            lateinit var activity: FastInputHostActivity
            await("Romaji keyboard not shown") {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FastInputHostActivity>().firstOrNull()
                if (resumed == null) false else { activity = resumed; views().filterIsInstance<QWERTYButton>().any { it.isShown && it.text.toString().equals("a", true) } }
            }
            // The keyboard view appears before the editor restart/IME entrance animation completes.
            await("Editor input connection not active") { activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).isActive(activity.editText) }
            SystemClock.sleep(750)
            for (letter in "aiehusyou") {
                val point = IntArray(2)
                var x = 0f
                var y = 0f
                main {
                    val key = views().filterIsInstance<QWERTYButton>().first { it.isShown && it.text.toString().equals(letter.toString(), true) }
                    key.getLocationOnScreen(point)
                    x = point[0] + key.width / 2f
                    y = point[1] + key.height / 2f
                }
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    ins.uiAutomation.injectInputEvent(event, true)
                    event.recycle()
                    SystemClock.sleep(40)
                }
            }
            lateinit var label: TextView
            try { await("Award category label not shown") { views().filterIsInstance<TextView>().firstOrNull { it.isShown && it.id == R.id.suggestion_item_type_text_view && it.text.toString().contains("[賞]") }?.let { label = it; true } ?: false } } catch (failure: Throwable) {
                screenshot("classified-ime-failure.png")
                main { println("CLASSIFIED_IME_EDITOR ${activity.editText.text}"); println("CLASSIFIED_IME_LABELS " + views().filterIsInstance<TextView>().filter { it.id == R.id.suggestion_item_text_view || it.id == R.id.suggestion_item_type_text_view }.map { it.text.toString() }.toList()) }
                throw failure
            }
            screenshot("classified-ime.png")
            var committed = ""
            main {
                val item = label.parent as View
                committed = item.findViewById<TextView>(R.id.suggestion_item_text_view).text.toString().trim()
                item.performClick()
            }
            await("Candidate was not committed") { activity.editText.text.toString() == committed }
            assertTrue(committed.isNotBlank())
            println("CLASSIFIED_IME_COMMIT $committed")
        } finally {
            store.setOptionalBundledEnabled(DictionaryCategory.CLASSIFIED_AWARD, previous)
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $target")
        }
    }
}
