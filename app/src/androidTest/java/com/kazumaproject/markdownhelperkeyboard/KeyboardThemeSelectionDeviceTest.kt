package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardSkinSelectionFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeCatalog
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class KeyboardThemeSelectionDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val ctx get() = ins.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val all = mutableListOf<AccessibilityNodeInfo>()
        fun visit(n: AccessibilityNodeInfo) { all.add(n); for (i in 0 until n.childCount) n.getChild(i)?.let(::visit) }
        ui.windows.forEach { it.root?.let(::visit) }
        return all
    }
    private fun click(match: (AccessibilityNodeInfo) -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < end) {
            var n = nodes().firstOrNull { it.isVisibleToUser && match(it) }
            while (n != null) {
                if (n.isClickable && n.isEnabled) {
                    check(n.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    ins.waitForIdleSync(); SystemClock.sleep(400); return
                }
                n = n.parent
            }
            SystemClock.sleep(100)
        }
        error("Missing clickable UI: " + nodes().joinToString { "${it.text}/${it.contentDescription}" })
    }
    private fun descendants(fragment: Fragment): List<Fragment> =
        listOf(fragment) + fragment.childFragmentManager.fragments.flatMap(::descendants)
    private fun capture(name: String) {
        val out = File(ctx.getExternalFilesDir(null), "theme-selection").apply { mkdirs() }
        val bitmap = requireNotNull(ui.takeScreenshot())
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }

    @Test fun selectThemesFromLegacyAndNewSettingsAndUseTheIme() {
        check(ctx.packageName.startsWith("com.kazumaproject.skinfidelity"))
        check(!android.os.Build.FINGERPRINT.contains("generic"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val saved = prefs.all.toMap()
        val originalIme = shell("settings get secure default_input_method")
        val target = "${ctx.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabled = shell("ime list -s").lines().contains(target)
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            prefs.edit().putString("keyboard_skin_preference", "default")
                .putString("keyboard_order_preference", "[\"TENKEY\"]")
                .putBoolean("save_last_used_keyboard", false).commit()
            shell("ime enable $target"); shell("ime set $target")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    host.navController.navigate(R.id.settingMainFragment)
                }
                click { it.text?.toString() == ctx.getString(R.string.keyboardthemefragment) }
                click { it.text?.toString() == ctx.getString(R.string.keyboard_skin_choose) }
                for ((index, option) in KeyboardThemeCatalog.options.withIndex()) {
                    scenario.onActivity { activity ->
                        val picker = activity.supportFragmentManager.fragments.flatMap(::descendants)
                            .filterIsInstance<KeyboardSkinSelectionFragment>().first()
                        picker.requireView().findViewById<RecyclerView>(R.id.keyboard_theme_list).scrollToPosition(index)
                    }
                    SystemClock.sleep(500)
                    val preserved = prefs.all.filterKeys { it != "keyboard_skin_preference" }
                    val name = ctx.getString(option.titleRes)
                    click { it.contentDescription?.toString()?.startsWith("$name。") == true }
                    check(prefs.getString("keyboard_skin_preference", null) == option.id.preferenceValue)
                    check(prefs.all.filterKeys { it != "keyboard_skin_preference" } == preserved) { "Theme selection changed unrelated settings" }
                    capture("${option.id.preferenceValue}-selected")
                    scenario.onActivity { it.startActivity(android.content.Intent(it, FastInputHostActivity::class.java)) }
                    SystemClock.sleep(1500)
                    val deadline = SystemClock.uptimeMillis() + 45000
                    while (nodes().none { it.isVisibleToUser && it.viewIdResourceName == "${ctx.packageName}:id/key_1" } && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
                    val key = nodes().first { it.isVisibleToUser && it.viewIdResourceName == "${ctx.packageName}:id/key_1" }
                    val bounds = android.graphics.Rect().also(key::getBoundsInScreen)
                    shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
                    SystemClock.sleep(500)
                    lateinit var editor: FastInputHostActivity
                    ins.runOnMainSync {
                        editor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                            .filterIsInstance<FastInputHostActivity>().first()
                        check(editor.editText.text.isNotEmpty())
                    }
                    capture("${option.id.preferenceValue}-ime")
                    ins.runOnMainSync { editor.finish() }
                    ins.waitForIdleSync()

                }
                scenario.recreate()
                SystemClock.sleep(500)
                check(prefs.getString("keyboard_skin_preference", null) == "cupertino_dark")
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    check(host.navController.popBackStack())
                }
                SystemClock.sleep(500)
                scenario.onActivity { activity ->
                    val theme = activity.supportFragmentManager.fragments.flatMap(::descendants)
                        .filterIsInstance<KeyboardThemeFragment>().first { it.isResumed }
                    check(theme.findPreference<Preference>("keyboard_skin_preference")!!.summary == ctx.getString(R.string.keyboard_skin_cupertino_dark))
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    host.navController.navigate(R.id.keyboardThemeFragment)
                }
                click { it.text?.toString() == ctx.getString(R.string.keyboard_skin_choose) }
                click { it.contentDescription?.toString()?.startsWith(ctx.getString(R.string.keyboard_skin_default) + "。") == true }
                check(prefs.getString("keyboard_skin_preference", null) == "default")
                capture("new-settings-default")
                check(ui.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90))
                SystemClock.sleep(1000)
                check(nodes().any { it.isVisibleToUser && it.isSelected && it.contentDescription?.toString()?.startsWith(ctx.getString(R.string.keyboard_skin_default) + "。") == true })
                capture("landscape-default")
            }
        } finally {
            val edit = prefs.edit().clear()
            saved.forEach { (k,v) -> when(v) {
                is String -> edit.putString(k,v); is Boolean -> edit.putBoolean(k,v)
                is Int -> edit.putInt(k,v); is Long -> edit.putLong(k,v); is Float -> edit.putFloat(k,v)
                is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(k,v as Set<String>)
            } }
            check(edit.commit())
            shell("ime set $originalIme")
            if (!enabled) shell("ime disable $target")
            ui.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        }
    }
}
