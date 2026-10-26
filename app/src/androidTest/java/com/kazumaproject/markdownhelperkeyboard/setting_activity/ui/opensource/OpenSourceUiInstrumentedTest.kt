package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.opensource

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import android.widget.ExpandableListView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OpenSourceUiInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun main(block: () -> Unit) = ins.runOnMainSync(block)
    private fun shell(command: String) = ins.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 60000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            main { done = condition() }
            if (done) return
            SystemClock.sleep(100)
        }
        error(message)
    }
    private fun activity() = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureScreenshots") != "true") return
        SystemClock.sleep(500)
        val directory = File(context.getExternalFilesDir(null), "license-review").apply { mkdirs() }
        val bitmap = ins.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun localizedOfflineNoticesWorkInBothThemes() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("am start -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity")
        try {
            for (language in listOf("ja", "en")) for (dark in listOf(false, true)) {
                main {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
                    AppCompatDelegate.setDefaultNightMode(if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
                }
                ins.waitForIdleSync()
                await("Localized settings not ready") {
                    val current = activity()
                    current?.isSettingsContentReady == true && current.window.decorView.hasWindowFocus() &&
                        current.resources.configuration.locales[0].language == language &&
                        (current.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        (if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO)
                }
                lateinit var list: ExpandableListView
                main {
                    val host = activity()!!.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    host.navController.navigate(R.id.openSourceFragment)
                }
                await("License screen missing") {
                    val found = activity()?.findViewById<ExpandableListView>(R.id.open_source_license_list)
                    if (found != null) list = found
                    found != null && found.isShown
                }
                val prefix = "$language-${if (dark) "dark" else "light"}"
                main {
                    assertEquals(3, list.expandableListAdapter.groupCount)
                    assertEquals(if (language == "ja") "ライセンスと出典" else "Licenses and sources", activity()!!.supportActionBar!!.title.toString())
                    val night = activity()!!.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    assertEquals(if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO, night)
                }
                capture("$prefix-overview")
                fun open(group: Int, child: Int): WebView {
                    main {
                        for (index in 0 until list.expandableListAdapter.groupCount) list.collapseGroup(index)
                        list.expandGroup(group)
                        val flat = list.getFlatListPosition(ExpandableListView.getPackedPositionForChild(group, child))
                        list.setSelection(flat)
                        list.performItemClick(list, flat, list.expandableListAdapter.getChildId(group, child))
                    }
                    lateinit var web: WebView
                    await("Offline notice did not render") {
                        val found = WindowInspector.getGlobalWindowViews().asSequence().flatMap(::descendants)
                            .filterIsInstance<WebView>().firstOrNull()
                        if (found != null) web = found
                        found != null && found.progress == 100 && found.contentHeight > 0
                    }
                    main {
                        assertTrue(web.settings.blockNetworkLoads)
                        assertFalse(web.settings.javaScriptEnabled)
                        assertFalse(web.settings.allowFileAccess)
                    }
                    return web
                }
                val dictionary = open(0, 0)
                capture("$prefix-dictionary")
                main { dictionary.scrollTo(0, dictionary.contentHeight * 4) }
                await("Long notice cannot scroll") { dictionary.scrollY > 0 }
                shell("input keyevent KEYCODE_BACK")
                await("Back did not dismiss notice") { WindowInspector.getGlobalWindowViews().none { view -> descendants(view).any { it is WebView } } }
                open(1, 0)
                capture("$prefix-library")
                shell("input keyevent KEYCODE_BACK")
                await("Library dialog did not close") { WindowInspector.getGlobalWindowViews().none { view -> descendants(view).any { it is WebView } } }
                open(2, 0)
                capture("$prefix-model")
                shell("input keyevent KEYCODE_BACK")
                await("Model dialog did not close") { WindowInspector.getGlobalWindowViews().none { view -> descendants(view).any { it is WebView } } }
                main { (activity()!!.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController.popBackStack() }
            }
        } finally {
            main {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }
}
