package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Intent
import android.os.Bundle
import android.graphics.Bitmap
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.engine.*
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NumberCandidateSettingsInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var uiContext: android.content.Context? = null
    private fun label(id: Int) = (uiContext ?: context).getString(id)
    private fun fill(id: Int, value: String) {
        onView(withHint(label(id))).perform(scrollTo(), replaceText(value), closeSoftKeyboard())
    }
    private fun click(id: Int) { onView(allOf(withText(label(id)), isAssignableFrom(android.widget.Button::class.java))).perform(scrollTo(), click()) }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    fun editorFieldsExposeLabelsWithTalkBackEnabled() {
        val automation = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val oldServices = shell("settings get secure enabled_accessibility_services")
        val oldEnabled = shell("settings get secure accessibility_enabled")
        val service = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        val services = oldServices.takeUnless { it == "null" || it.isBlank() }?.let { "$it:$service" } ?: service
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            shell("settings put secure enabled_accessibility_services $services")
            shell("settings put secure accessibility_enabled 1")
            val manager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            val deadline = android.os.SystemClock.uptimeMillis() + 10000
            while (!manager.isTouchExplorationEnabled && android.os.SystemClock.uptimeMillis() < deadline) android.os.SystemClock.sleep(100)
            assertTrue("TalkBack touch exploration must be active", manager.isTouchExplorationEnabled)
            scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
            scenario.onActivity {
                val nav = (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController
                nav.navigate(R.id.numberUnitEditorFragment)
            }
            instrumentation.waitForIdleSync()
            fun descendants(node: android.view.accessibility.AccessibilityNodeInfo): List<android.view.accessibility.AccessibilityNodeInfo> =
                listOf(node) + (0 until node.childCount).flatMap { index -> node.getChild(index)?.let(::descendants).orEmpty() }
            var fields = emptyList<android.view.accessibility.AccessibilityNodeInfo>()
            val fieldDeadline = android.os.SystemClock.uptimeMillis() + 10000
            while ((fields.none { it.hintText?.toString() == label(R.string.number_unit_output) } || fields.none { it.hintText?.toString() == label(R.string.number_unit_reading) }) && android.os.SystemClock.uptimeMillis() < fieldDeadline) {
                val root = automation.rootInActiveWindow
                // TalkBack may show its own notification prompt on first activation. Dismiss it
                // without granting a permission before inspecting this app's accessibility tree.
                if (root?.packageName?.toString() == "com.google.android.permissioncontroller") {
                    automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                }
                fields = root?.let(::descendants).orEmpty().filter { it.isEditable && it.isVisibleToUser }
                android.os.SystemClock.sleep(100)
            }
            automation.takeScreenshot()?.let { bitmap ->
                File(context.getExternalFilesDir(null), "number-talkback.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            assertTrue("root=${automation.rootInActiveWindow?.packageName} fields=${fields.map { it.text to it.hintText }}", fields.any { it.hintText?.toString() == label(R.string.number_unit_output) })
            assertTrue(fields.any { it.hintText?.toString() == label(R.string.number_unit_reading) })
            val field = fields.first { it.hintText?.toString() == label(R.string.number_unit_output) }
            assertTrue(field.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        } finally {
            scenario?.close()
            if (oldServices == "null") shell("settings delete secure enabled_accessibility_services") else shell("settings put secure enabled_accessibility_services '$oldServices'")
            if (oldEnabled == "null") shell("settings delete secure accessibility_enabled") else shell("settings put secure accessibility_enabled $oldEnabled")
        }
    }

    @Test fun registerPreviewRotateEditAndDeleteAnOfflineUnit() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val original = preferences.getString("number_candidate_config_v1", null)
        preferences.edit().remove("number_candidate_config_v1").commit()
        AppPreference.init(context)
        val oldLocales = if (android.os.Build.VERSION.SDK_INT >= 33) {
            androidx.core.os.LocaleListCompat.wrap(context.getSystemService(android.app.LocaleManager::class.java).applicationLocales)
        } else androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        fun setLocales(locales: androidx.core.os.LocaleListCompat) = instrumentation.runOnMainSync {
            if (android.os.Build.VERSION.SDK_INT >= 33) context.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags(locales.toLanguageTags())
            else androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
        }
        val japanese = InstrumentationRegistry.getArguments().getString("number_ui_japanese") == "true"
        if (japanese) setLocales(androidx.core.os.LocaleListCompat.forLanguageTags("ja"))
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        var retainForRestart = false
        try {
            scenario.onActivity {
                uiContext = it
                val nav = (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController
                nav.navigate(R.id.numberCandidateSettingsFragment)
            }
            onView(withText(startsWith(label(R.string.number_kind_time) + "\n"))).perform(click())
            assertTrue(NumberCandidateKind.TIME in AppPreference.number_candidate_config.disabledKinds)
            onView(withText(startsWith(label(R.string.number_kind_time) + "\n"))).perform(click())
            click(R.string.number_add_unit)
            capture("number-unit-empty")
            fill(R.string.number_unit_output, "個")
            fill(R.string.number_unit_reading, "こ")
            fill(R.string.number_try_reading, "にこ")
            click(R.string.number_special_add)
            onView(withHint(startsWith(label(R.string.number_special_value)))).perform(scrollTo(), replaceText("1"), closeSoftKeyboard())
            fill(R.string.number_special_reading, "いっこ")
            onView(withText("いっこ → 1個・１個・一個")).perform(scrollTo()).check(matches(isDisplayed()))
            capture("number-special-preview")
            onView(withHint(label(R.string.number_special_reading))).perform(scrollTo(), click())
            scenario.onActivity { activity ->
                (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .showSoftInput(activity.currentFocus, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            var keyboardVisible = false
            val keyboardDeadline = android.os.SystemClock.uptimeMillis() + 5000
            while (!keyboardVisible && android.os.SystemClock.uptimeMillis() < keyboardDeadline) {
                scenario.onActivity { keyboardVisible = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
                android.os.SystemClock.sleep(100)
            }
            assertTrue("The editor layout must be tested with the software keyboard visible", keyboardVisible)
            onView(withText("いっこ → 1個・１個・一個")).perform(scrollTo()).check(matches(isDisplayed()))
            capture("number-special-keyboard")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            scenario.recreate()
            onView(withText("いっこ → 1個・１個・一個")).perform(scrollTo()).check(matches(isDisplayed()))
            click(R.string.number_apply_reading)
            for (whole in listOf("はっこ", "はちこ")) {
                click(R.string.number_special_add)
                onView(withHint(startsWith(label(R.string.number_special_value)))).perform(scrollTo(), replaceText("8"), closeSoftKeyboard())
                fill(R.string.number_special_reading, whole)
                click(R.string.number_apply_reading)
            }
            click(R.string.number_special_add)
            onView(withHint(startsWith(label(R.string.number_special_value)))).perform(scrollTo(), replaceText("7"), closeSoftKeyboard())
            fill(R.string.number_special_reading, "いっこ")
            click(R.string.number_apply_reading)
            onView(withText(label(R.string.number_duplicate_reading))).perform(scrollTo()).check(matches(isDisplayed()))
            click(R.string.number_cancel)
            for (whole in listOf("はっこ", "はちこ")) {
                onView(withText(label(R.string.number_delete) + "：" + whole)).perform(scrollTo(), click())
                onView(withId(android.R.id.button1)).perform(click())
            }
            fill(R.string.number_try_reading, "いっこ")
            onView(withText("いっこ → 1個・１個・一個")).perform(scrollTo()).check(matches(isDisplayed()))
            fill(R.string.number_try_reading, "じゅういっこ")
            onView(withText(label(R.string.number_try_no_match))).perform(scrollTo()).check(matches(isDisplayed()))
            click(R.string.number_save)
            val unit = AppPreference.number_candidate_config.units.single()
            assertEquals(listOf(SpecialNumberReading(1, "いっこ")), unit.specialReadings)
            if (InstrumentationRegistry.getArguments().getString("number_restart_prepare") == "true") {
                File(context.filesDir, "number-restart-original.json").writeText(original ?: "null")
                // Commit before the host stops the process; the next instrumentation verifies cold loading.
                assertTrue(preferences.edit().putString("number_candidate_config_v1", AppPreference.number_candidate_config.encode()).commit())
                retainForRestart = true
                return
            }
            scenario.recreate()
            onView(withText(label(R.string.number_edit) + "：個")).perform(scrollTo(), click())
            fill(R.string.number_unit_output, "セット")
            scenario.onActivity { it.onSupportNavigateUp() }
            capture("number-discard-dialog")
            onView(withText(label(R.string.number_discard))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()))
            onView(withId(android.R.id.button2)).perform(click())
            click(R.string.number_save)
            assertEquals("セット", AppPreference.number_candidate_config.units.single().output)
            onView(withText(label(R.string.number_edit) + "：セット")).perform(scrollTo(), click())
            click(R.string.number_delete)
            onView(withText(label(R.string.number_delete_unit))).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()))
            onView(withId(android.R.id.button1)).perform(click())
            assertTrue(AppPreference.number_candidate_config.units.isEmpty())
        } finally {
            scenario.close()
            if (!retainForRestart) preferences.edit().apply { if (original == null) remove("number_candidate_config_v1") else putString("number_candidate_config_v1", original) }.commit()
            AppPreference.init(context)
            if (japanese) setLocales(oldLocales)
        }
    }
    @Test fun registeredUnitLoadsAfterAColdProcessRestart() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("number_restart_verify") == "true")
        val backup = File(context.filesDir, "number-restart-original.json")
        assertTrue("The host must run the registration phase before stopping the process", backup.exists())
        val original = backup.readText().takeUnless { it == "null" }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            AppPreference.init(context)
            val unit = AppPreference.number_candidate_config.units.single()
            assertEquals("個", unit.output)
            assertEquals(listOf(SpecialNumberReading(1, "いっこ")), unit.specialReadings)
            val results = KanaKanjiEngine().getCandidatesEnglishKana("いっこ", PredictionConfig(
                japaneseNumberCandidatesEnabled = true, numberCandidateConfig = AppPreference.number_candidate_config))
            assertTrue(results.any { it.string == "1個" && it.number?.customUnit?.id == unit.id })
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity {
                    uiContext = it
                    (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.numberCandidateSettingsFragment)
                }
                onView(withText(label(R.string.number_edit) + "：個")).perform(scrollTo()).check(matches(isDisplayed()))
            }
        } finally {
            preferences.edit().apply { if (original == null) remove("number_candidate_config_v1") else putString("number_candidate_config_v1", original) }.commit()
            backup.delete()
            AppPreference.init(context)
        }
    }

}
