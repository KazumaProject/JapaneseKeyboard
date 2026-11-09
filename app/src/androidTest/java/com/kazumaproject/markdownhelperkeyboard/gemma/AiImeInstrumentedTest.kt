package com.kazumaproject.markdownhelperkeyboard.gemma

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ProgressBar
import androidx.preference.PreferenceManager
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.GemmaPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.gemma.floating.GemmaFloatingSettings
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking

/** Touch input -> production IME -> selected AI backend -> floating result -> editor insertion. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class AiImeInstrumentedTest {
    @Test(timeout = 360_000)
    fun selectedModelReplacesComposingTextThroughTheIme() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Use the isolated QA application ID", context.packageName == "com.kazumaproject.universalaiqa")
        val arguments = InstrumentationRegistry.getArguments()
        val filename = arguments.getString("aiModel")
        assumeTrue("Supply aiModel", filename != null)
        val model = File(context.getExternalFilesDir(null), "gemma/$filename")
        assertTrue(model.isFile)
        val ui = instrumentation.uiAutomation
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        fun node(id: String): AccessibilityNodeInfo? {
            if (android.os.Build.VERSION.SDK_INT >= 33) ui.clearCache()
            return ui.windows.asSequence().mapNotNull { it.root }
                .flatMap { it.findAccessibilityNodeInfosByViewId("${context.packageName}:id/$id").asSequence() }
                .firstOrNull { it.isVisibleToUser }
        }
        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 120_000
            while (!condition()) {
                check(SystemClock.uptimeMillis() < deadline) { message }
                SystemClock.sleep(50)
            }
        }
        fun tap(bounds: Rect) {
            val down = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    bounds.exactCenterX(), bounds.exactCenterY(), 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                    check(ui.injectInputEvent(this, true))
                    recycle()
                }
            }
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = preferences.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        try {
            preferences.edit()
                .putBoolean("gemma_translation_enable_preference", true)
                .putString("gemma_translation_model_path_preference", model.absolutePath)
                .putString("gemma_translation_backend_preference", arguments.getString("aiBackend") ?: "cpu")
                .putBoolean(GemmaFloatingSettings.ENABLED, true)
                .putBoolean("save_last_used_keyboard", false)
                .putBoolean("allow_fullscreen_mode_preference", false)
                .putBoolean("learn_dictionary_preference", false)
                .putBoolean("live_conversion_preference", false)
                .putBoolean("qwerty_english_direct_input_preference", false)
                .putBoolean("landscape_force_qwerty_preference", false)
                .putString("keyboard_order_preference", "[\"QWERTY\"]").commit()
            val settings = GemmaFloatingSettings(preferences)
            settings.save(null, "Uppercase", "Convert every Latin letter to uppercase. Return only the converted text.")
            settings.select(settings.rules.last().id)
            AppPreference.init(context)
            ui.serviceInfo = ui.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            ui.setRotation(UiAutomation.ROTATION_FREEZE_0)
            shell("input keyevent WAKEUP")
            shell("wm dismiss-keyguard")
            val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
            shell("ime enable $ime")
            shell("ime set $ime")
            ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { scenario ->
                scenario.onActivity { it.editText.setText("PREFIX "); it.restartEditorInput(false) }
                await("Keyboard did not appear") { node("key_h") != null }
                for (letter in "hello") {
                    tap(Rect().also { requireNotNull(node("key_$letter")).getBoundsInScreen(it) })
                    SystemClock.sleep(60)
                }
                await("AI uppercase result did not appear") { node("gemma_floating_result")?.text?.toString()?.contains("HELLO") == true }
                tap(Rect().also { requireNotNull(node("gemma_floating_result")).getBoundsInScreen(it) })
                await("AI result did not replace composing text") {
                    var text = ""
                    scenario.onActivity { text = it.editText.text.toString() }
                    text == "PREFIX HELLO"
                }
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            restorePreferences(preferences, saved)
            AppPreference.init(context)
            ui.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    @Test(timeout = 60_000)
    fun aiSettingsShowsImportedFormatsAndGgufProjectorControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Use the isolated QA application ID", context.packageName == "com.kazumaproject.universalaiqa")
        val filename = InstrumentationRegistry.getArguments().getString("aiModel")
        assumeTrue("Supply aiModel", filename != null)
        val model = File(context.getExternalFilesDir(null), "gemma/$filename")
        assertTrue(model.isFile)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = preferences.all.toMap()
        try {
            preferences.edit().putBoolean("gemma_translation_enable_preference", false)
                .putString("gemma_translation_model_path_preference", model.absolutePath).commit()
            AppPreference.init(context)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity {
                    (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.gemmaPreferenceFragment)
                }
                var ready = false
                val deadline = SystemClock.uptimeMillis() + 15_000
                while (!ready && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity {
                        val host = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                        val fragment = host.childFragmentManager.primaryNavigationFragment as? GemmaPreferenceFragment
                        val models = fragment?.findPreference<ListPreference>("gemma_model_selection_preference")
                        ready = models?.entryValues?.isNotEmpty() == true
                    }
                    if (!ready) SystemClock.sleep(50)
                }
                assertTrue("AI preferences did not load", ready)
                scenario.onActivity {
                    val host = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                    assertEquals("AI", host.navController.currentDestination?.label.toString())
                    val fragment = host.childFragmentManager.primaryNavigationFragment as GemmaPreferenceFragment
                    val models = requireNotNull(fragment.findPreference<ListPreference>("gemma_model_selection_preference"))
                    assertTrue(models.entryValues.any { value -> value.toString().endsWith(".gguf") })
                    assertTrue(models.entryValues.any { value -> value.toString().endsWith(".litertlm") })
                    assertEquals(model.extension == "gguf", fragment.findPreference<Preference>("ai_projector_preference")?.isEnabled)
                    val backend = requireNotNull(fragment.findPreference<ListPreference>("gemma_translation_backend_preference"))
                    assertTrue("Select a backend before enabling AI", backend.isEnabled)
                    assertEquals(listOf("cpu", "gpu_if_available", "npu_if_available"), backend.entryValues.map { it.toString() })
                    assertFalse(requireNotNull(fragment.findPreference<Preference>("ai_model_loading_preference")).isVisible)
                }
            }
        } finally {
            restorePreferences(preferences, saved)
            AppPreference.init(context)
        }
    }

    @Test(timeout = 240_000)
    fun modelLoadingShowsProgressAndRestoresControlsOnSuccessAndFailure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Use the isolated QA application ID", context.packageName == "com.kazumaproject.universalaiqa")
        val arguments = InstrumentationRegistry.getArguments()
        val filename = arguments.getString("aiModel")
        assumeTrue("Supply aiModel", filename != null)
        val model = File(context.getExternalFilesDir(null), "gemma/$filename")
        assertTrue(model.isFile)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = preferences.all.toMap()
        val invalid = File(context.cacheDir, "invalid-loading-model.gguf")
        var manager: GemmaTranslationManager? = null
        try {
            preferences.edit().putBoolean("gemma_translation_enable_preference", false)
                .putString("gemma_translation_model_path_preference", model.absolutePath).commit()
            AppPreference.init(context)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.awaitSettingsContentReady()
                scenario.onActivity {
                    (it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .navController.navigate(R.id.gemmaPreferenceFragment)
                }
                fun fragment(activity: MainActivity): GemmaPreferenceFragment? =
                    (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                        .childFragmentManager.primaryNavigationFragment as? GemmaPreferenceFragment
                fun await(message: String, condition: (MainActivity) -> Boolean) {
                    val deadline = SystemClock.uptimeMillis() + 120_000
                    while (true) {
                        var ready = false
                        scenario.onActivity { ready = condition(it) }
                        if (ready) return
                        check(SystemClock.uptimeMillis() < deadline) { message }
                        SystemClock.sleep(20)
                    }
                }
                await("AI preferences did not load") {
                    fragment(it)?.findPreference<ListPreference>("gemma_model_selection_preference")?.entryValues?.isNotEmpty() == true
                }
                scenario.onActivity {
                    val current = requireNotNull(fragment(it))
                    manager = current.gemmaTranslationManager
                    assertTrue(manager!!.selectModel(model.absolutePath))
                    val backend = requireNotNull(current.findPreference<ListPreference>("gemma_translation_backend_preference"))
                    val requested = arguments.getString("aiBackend") ?: "cpu"
                    assertTrue(backend.isEnabled)
                    assertTrue(backend.callChangeListener(requested))
                    backend.value = requested
                    val enabled = requireNotNull(current.findPreference<SwitchPreferenceCompat>("gemma_translation_enable_preference"))
                    assertTrue(enabled.callChangeListener(true))
                    enabled.isChecked = true
                }
                await("Visible indeterminate model progress did not appear") {
                    val progress = it.findViewById<ProgressBar>(R.id.ai_model_loading_progress)
                    progress?.isShown == true && progress.isIndeterminate &&
                        fragment(it)?.findPreference<ListPreference>("gemma_translation_backend_preference")?.isEnabled == false
                }
                await("Model did not finish loading") {
                    manager!!.loadState.value is GemmaLoadState.Ready &&
                        fragment(it)?.findPreference<Preference>("ai_model_loading_preference")?.isVisible == false &&
                        fragment(it)?.findPreference<ListPreference>("gemma_translation_backend_preference")?.isEnabled == true
                }
                arguments.getString("aiExpectedBackend")?.let { expected ->
                    assertEquals(expected, (manager!!.loadState.value as GemmaLoadState.Ready).backend)
                }

                invalid.outputStream().use { it.write("GGUF".toByteArray()); it.write(ByteArray(1_048_576)) }
                scenario.onActivity {
                    assertTrue(manager!!.selectModel(invalid.absolutePath))
                    val backend = requireNotNull(fragment(it)?.findPreference<ListPreference>("gemma_translation_backend_preference"))
                    assertTrue(backend.callChangeListener("cpu"))
                    backend.value = "cpu"
                }
                await("A failed load left progress or disabled controls") {
                    manager!!.loadState.value is GemmaLoadState.Failed &&
                        fragment(it)?.findPreference<Preference>("ai_model_loading_preference")?.isVisible == false &&
                        fragment(it)?.findPreference<ListPreference>("gemma_translation_backend_preference")?.isEnabled == true
                }
            }
        } finally {
            runBlocking { manager?.disable() }
            invalid.delete()
            restorePreferences(preferences, saved)
            AppPreference.init(context)
        }
    }

    private fun restorePreferences(preferences: SharedPreferences, saved: Map<String, *>) {
        preferences.edit().clear().apply {
            saved.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                }
            }
        }.commit()
    }
}
