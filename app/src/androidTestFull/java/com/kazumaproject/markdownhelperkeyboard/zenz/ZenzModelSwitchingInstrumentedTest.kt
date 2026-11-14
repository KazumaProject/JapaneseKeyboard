package com.kazumaproject.markdownhelperkeyboard.zenz

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.ZenzPreferenceFragment
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.ZenzRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.ZenzRuntimeConfig
import com.kazumaproject.zenz.ZenzEngine
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Full-only: real settings dialog, DocumentsUI picker, persisted URI and running IME client. */
@RunWith(AndroidJUnit4::class)
class ZenzModelSwitchingInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private val ui get() = ins.uiAutomation
    private val preferenceKeys = listOf("zenz_model_uri_preference", "zenz_model_selection_version",
        "keyboard_order_preference", "save_last_used_keyboard", "keyboard_floating_preference",
        "live_conversion_preference", "enable_ai_conversion_zenz_preference",
        "enable_ai_conversion_zenzai_preference", "enable_zenz_rerank_preference")
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }

    @Test(timeout = 1_800_000)
    fun selectingModelsPersistsReloadsAndReturnsToBundled() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(AppVariantConfig.hasZenz && args.containsKey("zenzModelSwitchTestDir"))
        val directory = File(args.getString("zenzModelSwitchTestDir")!!)
        val restartPhase = args.getString("zenzSettingsRestartPhase")
        if (restartPhase == "verify") {
            verifyAfterProcessRestart(directory)
            return@runBlocking
        }
        val models = JSONArray(File(directory, "models.json").readText())
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val original = prefs.all.filterKeys(preferenceKeys::contains).toMutableMap()
        // Recover cleanly from an interrupted prior run that selected this test's own folder.
        if (original["zenz_model_uri_preference"].toString().contains("zenz-model-switch-tests")) {
            original["zenz_model_uri_preference"] = ""
            original.remove("zenz_model_selection_version")
        }
        val permissions = context.contentResolver.persistedUriPermissions.map { it.uri.toString() }
            .filterNot { it.contains("zenz-model-switch-tests") }.toSet()
        val originalIme = shell("settings get secure default_input_method")
        prefs.edit().putString("keyboard_order_preference", "[\"TENKEY\"]")
            .putBoolean("save_last_used_keyboard", false).putBoolean("keyboard_floating_preference", false)
            .putBoolean("live_conversion_preference", false).putBoolean("enable_ai_conversion_zenz_preference", false)
            .putBoolean("enable_ai_conversion_zenzai_preference", false).putBoolean("enable_zenz_rerank_preference", false).commit()
        AppPreference.init(context)
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        shell("ime enable ${context.packageName}/.ime_service.IMEService")
        shell("ime set ${context.packageName}/.ime_service.IMEService")
        val settings = ActivityScenario.launch(MainActivity::class.java)
        var preserveForRestart = false
        try {
            settings.awaitSettingsContentReady()
            navigate(settings)
            var previousPath: String? = null
            for (index in 0 until models.length()) {
                val model = models.getJSONObject(index)
                val filename = model.getString("filename")
                android.util.Log.i("ZenzModelSwitchQA", "Selecting $filename")
                choose(settings, filename)
                val selected = AppPreference.zenz_model_uri_preference
                assertTrue("Picker URI not persisted", selected.startsWith("content://"))
                assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri.toString() == selected && it.isReadPermission })
                settings.recreate()
                awaitPreferences(settings)
                assertEquals("Selection lost after settings recreation", selected, AppPreference.zenz_model_uri_preference)
                val path = checkImeModel(File(directory, filename))
                if (previousPath != null) assertNotEquals("Different models share one runtime identity", previousPath, path)
                previousPath = path
                if (restartPhase == "prepare") {
                    File(context.filesDir, "zenz-settings-restart.json").writeText(JSONObject()
                        .put("uri", selected).put("model", model)
                        .put("preferences", JSONObject(original)).put("permissions", JSONArray(permissions.toList()))
                        .put("ime", originalIme).toString())
                    preserveForRestart = true
                    return@runBlocking
                }
            }

            // Reselect the same document after replacing its bytes, as a downloaded model update.
            val first = models.getJSONObject(0).getString("filename")
            val second = models.getJSONObject(1).getString("filename")
            val shared = "/sdcard/Download/zenz-model-switch-tests"
            shell("cp $shared/$first $shared/zenz-reselect.gguf")
            choose(settings, "zenz-reselect.gguf")
            val sameUri = AppPreference.zenz_model_uri_preference
            val before = checkImeModel(File(directory, first))
            shell("cp $shared/$second $shared/zenz-reselect.gguf")
            choose(settings, "zenz-reselect.gguf")
            assertEquals(sameUri, AppPreference.zenz_model_uri_preference)
            assertNotEquals("Reselecting changed bytes kept the old model", before, checkImeModel(File(directory, second)))

            openModelDialog(settings)
            clickText("デフォルト（Assets）")
            await("Default selection not stored") { AppPreference.zenz_model_uri_preference.isBlank() }
            val bundled = File(context.filesDir, "ggml-model-Q5_K_M.gguf")
            assertEquals(bundled.absolutePath, checkImeModel(bundled))
        } finally {
            settings.close()
            ZenzEngine.closeModel()
            if (!preserveForRestart) {
                restorePreferences(original)
                releaseNewPermissions(permissions)
                if (originalIme.isNotBlank() && originalIme != "null") shell("ime set $originalIme")
            }
        }
    }

    private suspend fun verifyAfterProcessRestart(directory: File) {
        val record = File(context.filesDir, "zenz-settings-restart.json")
        val state = JSONObject(record.readText())
        val saved = state.getJSONObject("preferences")
        val original = saved.keys().asSequence().associateWith(saved::get)
        val permissions = state.getJSONArray("permissions").let { a -> (0 until a.length()).map(a::getString).toSet() }
        try {
            AppPreference.init(context)
            val uri = state.getString("uri")
            assertEquals("Selected URI did not survive process restart", uri, AppPreference.zenz_model_uri_preference)
            assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri.toString() == uri && it.isReadPermission })
            val model = state.getJSONObject("model")
            // Force the restarted app to reopen SAF, rather than reuse the internal copy.
            File(context.cacheDir, "zenz-models/zenz_custom_model_${model.getString("sha256")}.gguf").delete()
            shell("ime enable ${context.packageName}/.ime_service.IMEService")
            shell("ime set ${context.packageName}/.ime_service.IMEService")
            checkImeModel(File(directory, model.getString("filename")))
        } finally {
            restorePreferences(original)
            releaseNewPermissions(permissions)
            val originalIme = state.getString("ime")
            if (originalIme.isNotBlank() && originalIme != "null") shell("ime set $originalIme")
            record.delete()
        }
    }

    private fun restorePreferences(original: Map<String, *>) {
        val edit = PreferenceManager.getDefaultSharedPreferences(context).edit()
        preferenceKeys.forEach(edit::remove)
        original.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
        } }
        edit.commit()
    }

    private fun releaseNewPermissions(original: Set<String>) {
        context.contentResolver.persistedUriPermissions.filter { it.uri.toString() !in original }.forEach {
            context.contentResolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private suspend fun checkImeModel(expectedFile: File): String {
        var editor: FastInputHostActivity? = null
        ins.runOnMainSync {
            context.startActivity(Intent(context, FastInputHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        await("Input host did not resume") {
            ins.runOnMainSync {
                editor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().singleOrNull()
            }
            editor != null
        }
        try {
            ins.runOnMainSync {
                editor!!.editText.requestFocus()
                editor!!.getSystemService(InputMethodManager::class.java).showSoftInput(editor!!.editText, InputMethodManager.SHOW_IMPLICIT)
            }
            var service: IMEService? = null
            await("IME window not available") {
                ins.runOnMainSync {
                    val keyId = context.resources.getIdentifier("key_1", "id", context.packageName)
                    service = android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { view ->
                        var current: android.content.Context? = view.findViewById<android.view.View>(keyId)?.context
                        val seen = mutableSetOf<android.content.Context>()
                        while (current is android.content.ContextWrapper && current !is IMEService && seen.add(current)) current = current.baseContext
                        current as? IMEService
                    }
                }
                service != null
            }
            android.util.Log.i("ZenzModelSwitchQA", "Resolving ${expectedFile.name}")
            val method = IMEService::class.java.getDeclaredMethod("resolveZenzRuntimeConfig", Continuation::class.java).apply { isAccessible = true }
            val config = suspendCoroutineUninterceptedOrReturn<ZenzRuntimeConfig?> { method.invoke(service, it) }!!
            android.util.Log.i("ZenzModelSwitchQA", "Resolved ${config.modelPath}")
            val client = IMEService::class.java.getDeclaredField("zenzRuntimeClient").apply { isAccessible = true }.get(service) as ZenzRuntimeClient
            assertTrue("Reference model failed to load", ZenzEngine.initModel(expectedFile.absolutePath))
            ZenzEngine.setRuntimeConfig(config.nCtx, config.nThreads)
            for (reading in listOf("コンニチハ", "マツ", "アリガトウ")) {
                val expected = ZenzEngine.generateWithContextAndConditionsV32("", "", "", "", "", "", reading, 64)
                val actual = client.generate(config, "", "", "", "", "", "", reading, 64)
                assertEquals("IME still uses another model: ${expectedFile.name}/$reading", expected, actual)
                assertTrue(actual.isNotBlank())
                val candidates = arrayOf("こんにちは", "今日は", "こんにちは。")
                val expectedScores = ZenzEngine.scoreCandidatesV32("", "", "", "", "", "", reading, candidates)
                val actualScores = client.score(config, "", "", "", "", "", "", reading, candidates)
                assertArrayEquals("IME score uses another model: ${expectedFile.name}/$reading", expectedScores, actualScores, 1e-4f)
            }
            ZenzEngine.closeModel()
            return config.modelPath
        } finally {
            ins.runOnMainSync { editor?.finish() }
        }
    }

    private fun navigate(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            host.navController.navigate(R.id.zenzPreferenceFragment)
        }
        awaitPreferences(scenario)
    }

    private fun awaitPreferences(scenario: ActivityScenario<MainActivity>) = await("Zenz settings not ready") {
        var ready = false
        scenario.onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
            ready = (host?.childFragmentManager?.primaryNavigationFragment as? ZenzPreferenceFragment)
                ?.findPreference<Preference>("zenz_model_select_preference") != null
        }
        ready
    }

    private fun openModelDialog(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            (host.childFragmentManager.primaryNavigationFragment as ZenzPreferenceFragment)
                .findPreference<Preference>("zenz_model_select_preference")!!.performClick()
        }
    }

    private fun choose(scenario: ActivityScenario<MainActivity>, filename: String) {
        openModelDialog(scenario)
        clickText("モデルを選択する")
        SystemClock.sleep(400)
        if (nodes().none { it.text?.toString() == filename }) {
            nodes().firstOrNull { it.contentDescription?.toString() in listOf("Show roots", "Open navigation drawer", "ルートを表示する") }?.let(::click)
            clickText(android.os.Build.MODEL)
            clickText("Download")
            clickText("zenz-model-switch-tests")
        }
        var clicked = false
        for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            repeat(20) {
                if (!clicked) {
                    val node = nodes().firstOrNull { it.text?.toString() == filename }
                    if (node != null) {
                        click(node)
                        clicked = true
                    } else {
                        nodes().lastOrNull { it.isScrollable }?.performAction(direction)
                        SystemClock.sleep(100)
                    }
                }
            }
            if (clicked) break
        }
        assertTrue("Document not found: $filename", clicked)
        await("Picker did not return to settings") { nodes().any { it.packageName?.toString() == context.packageName } }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        ui.rootInActiveWindow?.let(::visit)
        return result
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null && !current.isClickable) current = current.parent
        return current
    }

    private fun click(node: AccessibilityNodeInfo) {
        assertTrue("Node is not clickable: ${node.text}", clickableAncestor(node)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        SystemClock.sleep(300)
    }

    private fun clickText(text: String) {
        await("Missing UI text $text; ${nodes().joinToString { "${it.text}/${it.contentDescription}" }}") {
            // Drawer rows can repeat the current breadcrumb's text behind the drawer.
            val node = nodes().lastOrNull {
                it.isVisibleToUser && it.text?.toString() == text && clickableAncestor(it) != null
            } ?: return@await false
            click(node)
            true
        }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 20_000
        do {
            if (condition()) return
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < end)
        error(message)
    }
}
