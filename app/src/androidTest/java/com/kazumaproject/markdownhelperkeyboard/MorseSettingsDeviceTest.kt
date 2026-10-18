package com.kazumaproject.markdownhelperkeyboard

import android.app.LocaleManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.LocaleList
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Spinner
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingMainBinding
import com.kazumaproject.markdownhelperkeyboard.morse.MorseMapEditorFragment
import com.kazumaproject.markdownhelperkeyboard.morse.MorsePreferences
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingMainFragment
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingTabRegistry
import com.kazumaproject.morse_keyboard.MorseControl
import com.kazumaproject.morse_keyboard.MorseControlLayout
import com.kazumaproject.morse_keyboard.MorseOutput
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 35)
class MorseSettingsDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val automation get() = ins.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    private fun nodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        automation.windows.forEach { it.root?.let(::visit) }
    }
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(80)
        }
        error(message)
    }
    private fun click(label: Int) {
        var node: AccessibilityNodeInfo? = null
        await("Missing ${context.getString(label)}") {
            node = nodes().firstOrNull { it.isVisibleToUser && it.text?.toString() == context.getString(label) }
            node != null
        }
        var target = node
        while (target != null && !target.isClickable) target = target.parent
        assertNotNull("Clickable row for ${context.getString(label)}", target)
        assertTrue(target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(100)
    }
    private fun screenshot(name: String) {
        val bitmap = automation.takeScreenshot()
        val folder = File(context.getExternalFilesDir(null), "morse-shots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun host(activity: MainActivity) = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
    private fun allViews(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { allViews(view.getChildAt(it)) } else emptyList()

    @Test fun newCategoryLegacyTabAndMapEditorAreUsableAndPersistCustomAssignments() {
        org.junit.Assume.assumeTrue("Use an isolated .morsetest applicationId", context.packageName.contains(".morsetest"))
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val oldIme = shell("settings get secure default_input_method").trim()
        val enabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        val locale = context.getSystemService(LocaleManager::class.java)
        val oldLocales = locale.applicationLocales
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            MorsePreferences(context).saveControlLayout(MorseControlLayout())
            MorsePreferences(context).saveReverse(false)
            shell("ime enable $target"); shell("ime set $target")
            locale.applicationLocales = LocaleList.forLanguageTags("ja")
            prefs.edit().remove("morse_custom_maps_v1").remove("morse_map_order").remove("morse_enabled_maps").remove("morse_selected_map")
                .putString("morse_style", "PADDLE_A").putInt("morse_wpm", 15)
                .putBoolean("morse_auto_boundary", true).putInt("morse_boundary_ms", 600).putBoolean("morse_sound", true).commit()
            for (newHome in listOf(true, false)) {
                prefs.edit().putBoolean("setting_use_new_home_screen_preference", newHome).commit()
                AppPreference.init(context)
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                    await("settings navigation") {
                        var ready = false
                        scenario.onActivity { ready = host(it).navController.currentDestination != null }
                        ready
                    }
                    if (newHome) {
                        scenario.onActivity { host(it).navController.navigate(R.id.navigation_setting) }
                        await("Morse category") { nodes().any { it.isVisibleToUser && it.text?.toString() == context.getString(R.string.morse_title) } }
                        var category: AccessibilityNodeInfo? = nodes().first { it.text?.toString() == context.getString(R.string.morse_title) }
                        category?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                        while(category != null && !category.isScrollable) category = category.parent
                        category?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        SystemClock.sleep(400)
                        screenshot("new-settings-category")
                        scenario.onActivity { host(it).navController.navigate(R.id.morsePreferenceFragment) }
                    } else {
                        scenario.onActivity { host(it).navController.navigate(R.id.settingMainFragment) }
                        await("legacy pager") {
                            var ready = false
                            scenario.onActivity { activity ->
                                val fragment = host(activity).childFragmentManager.primaryNavigationFragment
                                if (fragment is SettingMainFragment && fragment.view != null) {
                                    FragmentSettingMainBinding.bind(fragment.requireView()).settingViewPager.currentItem =
                                        SettingTabRegistry.createTabs().indexOfFirst { it.key == SettingTabRegistry.TAB_MORSE }
                                    ready = true
                                }
                            }
                            ready
                        }
                    }
                    await("Morse preferences") { nodes().any { it.isVisibleToUser && it.text?.toString() == context.getString(R.string.morse_style_title) } }
                    screenshot(if (newHome) "new-morse-settings" else "legacy-morse-settings")
                    if (newHome) {
                        click(R.string.morse_key_layout_title)
                        await("layout preview") { nodes().any { it.isVisibleToUser && it.contentDescription?.toString() == context.getString(com.kazumaproject.morse_keyboard.R.string.morse_delete) } }
                        val delete = nodes().single { it.isVisibleToUser && it.contentDescription?.toString() == context.getString(com.kazumaproject.morse_keyboard.R.string.morse_delete) }
                        assertTrue(delete.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        SystemClock.sleep(80)
                        click(R.string.morse_move_left)
                        await("reordered control") { MorsePreferences(context).controlLayout().rows.first().first() == MorseControl.DELETE }
                        click(R.string.morse_row_down)
                        await("two rows") { MorsePreferences(context).controlLayout().rows.size == 2 }
                        val placement = nodes().single { it.isVisibleToUser && it.text?.toString()?.startsWith(context.getString(R.string.morse_control_placement_value, "")) == true }
                        assertTrue(placement.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        val bothSides = context.resources.getStringArray(R.array.morse_control_placements)[4]
                        await("placement choices") { nodes().any { it.text?.toString() == bothSides } }
                        assertTrue(nodes().single { it.text?.toString() == bothSides }.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        await("controls around paddles") { MorsePreferences(context).controlLayout().placement == com.kazumaproject.morse_keyboard.MorseControlPlacement.BOTH_SIDES }
                        click(R.string.morse_add_key)
                        click(com.kazumaproject.morse_keyboard.R.string.morse_space)
                        await("space key added") { MorseControl.SPACE in MorsePreferences(context).controlLayout().rows.flatten() }
                        SystemClock.sleep(400)
                        screenshot("key-layout-editor")
                        click(R.string.morse_reset_layout)
                        await("default layout restored") { MorsePreferences(context).controlLayout() == MorseControlLayout() }
                        scenario.onActivity { host(it).navController.popBackStack() }
                        click(R.string.morse_maps_title)
                        await("map list") { nodes().any { it.text?.toString() == context.getString(R.string.morse_new_map) } }
                        screenshot("map-list")
                        click(R.string.morse_new_map)
                        click(R.string.morse_empty_map)
                        await("map editor") {
                            var ready = false
                            scenario.onActivity { ready = host(it).childFragmentManager.primaryNavigationFragment is MorseMapEditorFragment }
                            ready
                        }
                        scenario.onActivity { activity ->
                            val fragment = host(activity).childFragmentManager.primaryNavigationFragment!!
                            val fields = allViews(fragment.requireView()).filterIsInstance<EditText>()
                            fields.single { it.hint?.toString() == context.getString(R.string.morse_name) }.setText("チャット用")
                            fields.single { it.hint?.toString() == context.getString(R.string.morse_signal) }.setText(".-")
                            fields.single { it.hint?.toString() == context.getString(R.string.morse_value) }.setText("ありがとうございます😊")
                            allViews(fragment.requireView()).filterIsInstance<Spinner>().first().setSelection(2)
                            activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(fragment.requireView().windowToken, 0)
                        }
                        click(R.string.morse_add_entry)
                        await("assignment registered") {
                            var registered = false
                            scenario.onActivity { activity ->
                                val view = host(activity).childFragmentManager.primaryNavigationFragment!!.requireView()
                                registered = allViews(view).filterIsInstance<EditText>().single { it.hint?.toString() == context.getString(R.string.morse_signal) }.text.isEmpty()
                            }
                            registered
                        }
                        for(label in listOf(com.kazumaproject.morse_keyboard.R.string.morse_dot,
                            com.kazumaproject.morse_keyboard.R.string.morse_dash, com.kazumaproject.morse_keyboard.R.string.morse_boundary)) {
                            val key = nodes().single { it.isVisibleToUser && it.contentDescription?.toString() == context.getString(label) }
                            assertTrue(key.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                            SystemClock.sleep(60)
                        }
                        await("sandbox preview output") { nodes().any { it.text?.toString() == "ありがとうございます😊" } }
                        screenshot("map-editor")
                        click(R.string.morse_save)
                        await("saved map") { MorsePreferences(context).allMaps().any { it.name == "チャット用" } }
                        val saved = MorsePreferences(context).allMaps().single { it.name == "チャット用" }
                        assertEquals(MorseOutput.LiteralText("ありがとうございます😊"), saved.decode(".-"))
                        assertEquals(saved.id, MorsePreferences(context).selectedMapId)
                    }
                }
            }
        } finally {
            locale.applicationLocales = oldLocales
            if(oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if(!enabled) shell("ime disable $target")
        }
    }
}
