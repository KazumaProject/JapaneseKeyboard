package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_browser

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.textfield.TextInputEditText
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.DictionaryPreferenceFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DictionaryBrowserUiInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun main(block: () -> Unit) = ins.runOnMainSync(block)
    private fun shell(command: String) = ins.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 60000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            main { done = condition() }
            if (done) return
            SystemClock.sleep(50)
        }
        error(message)
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    @Test fun newAndLegacyEntryPointsSearchPaginationAndRestorationWorkInTheApp() {
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabled = shell("ime list -s").lines().contains(ime)
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("ime enable $ime")
        try {
            shell("am start -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity")
            lateinit var activity: MainActivity
            fun activityReady(): Boolean {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull()
                if (resumed == null || !resumed.isSettingsContentReady || !resumed.window.decorView.hasWindowFocus()) return false
                activity = resumed
                return true
            }
            await("Settings not ready", ::activityReady)
            fun host() = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            main { host().navController.navigate(R.id.navigation_setting) }
            lateinit var entry: View
            await("New management entry missing") {
                val group = activity.findViewById<ViewGroup>(R.id.setting_home_management_list)
                val text = group?.let { descendants(it).filterIsInstance<TextView>().firstOrNull { label ->
                    label.text.toString() == context.getString(R.string.dictionary_browser_title)
                } } ?: return@await false
                var clickable: View = text
                while (!clickable.isClickable && clickable.parent is View) clickable = clickable.parent as View
                entry = clickable
                true
            }
            main { assertTrue(entry.performClick()) }
            lateinit var fragment: DictionaryBrowserFragment
            lateinit var model: DictionaryBrowserViewModel
            fun browserReady(): Boolean {
                val current = host().childFragmentManager.fragments.filterIsInstance<DictionaryBrowserFragment>().firstOrNull { it.isResumed }
                    ?: return false
                fragment = current
                model = ViewModelProvider(current)[DictionaryBrowserViewModel::class.java]
                val list = current.view?.findViewById<RecyclerView>(R.id.dictionary_words) ?: return false
                return !model.state.value.loading && !model.state.value.failed && model.state.value.rows.isNotEmpty() &&
                    list.adapter?.itemCount == model.state.value.rows.size &&
                    descendants(list).filterIsInstance<TextView>().any { it.id == R.id.word && it.text.isNotEmpty() && it.isShown }
            }
            await("Browser did not open from new settings", ::browserReady)
            main {
                assertEquals(39, activity.findViewById<Spinner>(R.id.dictionary_selector).adapter.count)
                assertEquals(DictionaryCategory.SYSTEM, model.state.value.category)
                activity.findViewById<RecyclerView>(R.id.dictionary_words).scrollToPosition(85)
            }
            await("Infinite scroll did not append the next page") { model.state.value.rows.size >= 200 && !model.state.value.loading }
            main {
                val layout = activity.findViewById<RecyclerView>(R.id.dictionary_words).layoutManager as LinearLayoutManager
                layout.scrollToPositionWithOffset(140, -10)
            }
            await("Scroll position not reached") {
                (activity.findViewById<RecyclerView>(R.id.dictionary_words).layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() == 140
            }
            main { activity.recreate() }
            await("Activity not recreated", ::activityReady)
            await("Browser not restored after recreation", ::browserReady)
            await("Scroll position not restored after recreation") {
                (activity.findViewById<RecyclerView>(R.id.dictionary_words).layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() == 140
            }
            main {
                val spinner = activity.findViewById<Spinner>(R.id.dictionary_selector)
                spinner.setSelection(DictionaryBrowserCatalog.all.indexOfFirst { it.category == DictionaryCategory.ENGLISH_READING })
                val search = activity.findViewById<TextInputEditText>(R.id.dictionary_search)
                search.setText("no_such_early_query")
                search.setText("ＦＥＴＣＨ")
            }
            await("Latest dictionary/query did not replace old results") {
                model.state.value.category == DictionaryCategory.ENGLISH_READING &&
                    !model.state.value.loading && model.state.value.rows == listOf(DictionaryBrowserRow("ふぇっち", "fetch"))
            }
            main { host().navController.navigate(R.id.dictionaryPreferenceFragment) }
            await("Dictionary settings not loaded") {
                host().childFragmentManager.fragments.filterIsInstance<DictionaryPreferenceFragment>()
                    .any { it.findPreference<Preference>("dictionary_browser_preference")?.onPreferenceClickListener != null }
            }
            main { host().navController.popBackStack() }
            await("Browser not restored after back navigation", ::browserReady)
            main {
                assertEquals("ＦＥＴＣＨ", activity.findViewById<TextInputEditText>(R.id.dictionary_search).text.toString())
                assertEquals(DictionaryCategory.ENGLISH_READING, model.state.value.category)
                activity.findViewById<TextInputEditText>(R.id.dictionary_search).setText("zz_no_word_4905")
            }
            await("Empty search state not shown") { !model.state.value.loading && model.state.value.rows.isEmpty() }
            main { assertEquals(context.getString(R.string.dictionary_browser_empty), activity.findViewById<TextView>(R.id.dictionary_status).text.toString()) }
            main { activity.findViewById<TextInputEditText>(R.id.dictionary_search).setText("") }
            await("Clearing search did not restore the list", ::browserReady)
            main { host().navController.navigate(R.id.dictionaryPreferenceFragment) }
            lateinit var preference: Preference
            await("Legacy dictionary entry not ready") {
                val settings = host().childFragmentManager.fragments.filterIsInstance<DictionaryPreferenceFragment>().firstOrNull { it.isResumed }
                settings?.findPreference<Preference>("dictionary_browser_preference")?.let {
                    if (it.onPreferenceClickListener != null) { preference = it; true } else false
                } ?: false
            }
            main { assertTrue(preference.onPreferenceClickListener!!.onPreferenceClick(preference)) }
            await("Browser did not open from legacy dictionary settings", ::browserReady)
            ins.waitForIdleSync()
            SystemClock.sleep(200) // Let the verified bound rows reach the display before capturing pixels.
            ins.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.cacheDir, "dictionary-browser-ui.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            if (!enabled) shell("ime disable $ime")
        }
    }
}
