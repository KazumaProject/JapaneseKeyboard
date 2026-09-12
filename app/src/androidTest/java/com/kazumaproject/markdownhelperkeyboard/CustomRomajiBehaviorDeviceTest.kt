package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real settings UI and software key taps. Requires investigation/custom-toggle.init.gradle. */
@RunWith(AndroidJUnit4::class)
class CustomRomajiBehaviorDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        automation.windows.forEach { it.root?.let(::visit) }
        return result
    }
    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            nodes().firstOrNull { it.isVisibleToUser && predicate(it) }?.let { return it }
            SystemClock.sleep(100)
        }
        error("Node missing: " + nodes().joinToString { "${it.viewIdResourceName}:${it.text}:${it.contentDescription}" })
    }
    private fun tap(node: AccessibilityNodeInfo) {
        val rect = Rect().also(node::getBoundsInScreen)
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true)); event.recycle()
            SystemClock.sleep(35)
        }
        SystemClock.sleep(120)
    }
    private fun key(label: String) = awaitNode {
        it.packageName?.toString() == ins.targetContext.packageName && it.isClickable &&
            (it.text ?: it.contentDescription)?.toString()?.lineSequence()?.firstOrNull()?.trim()?.equals(label, true) == true
    }
    private fun type(text: String) { text.forEach { tap(key(it.toString())) } }
    private fun text(host: ActivityScenario<FastInputHostActivity>): String {
        var text = ""; host.onActivity { text = it.editText.text.toString() }; return text
    }
    private fun setSettings(sokuon: Boolean, n: Boolean) {
        ActivityScenario.launch<MainActivity>(Intent(ins.targetContext, MainActivity::class.java)).use { activity ->
            activity.onActivity {
                val nav = it.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                nav.navController.navigate(R.id.romajiMapDetailFragment, Bundle().apply { putLong("mapId", 991L) })
            }
            awaitNode { it.text?.toString() == "Romaji behavior test" }
            ins.waitForIdleSync()
            for ((id, enabled) in listOf("auto_sokuon_switch" to sokuon, "auto_n_switch" to n)) {
                val node = awaitNode { it.viewIdResourceName?.endsWith("/$id") == true }
                if (node.isChecked != enabled) tap(node)
                awaitNode { it.viewIdResourceName?.endsWith("/$id") == true && it.isChecked == enabled }
            }
            SystemClock.sleep(200)
        }
    }

    @Test fun settingsAndBothSoftwareInputPathsAcrossAllCombinations() = runBlocking {
        val context = ins.targetContext
        check(context.packageName.startsWith("com.kazumaproject.customtoggletest"))
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val previousIme = shell("settings get secure default_input_method").trim()
        val wasEnabled = shell("ime list -s").lineSequence().any { it.trim() == target }
        val db = AppModule.providesLearnDatabase(context)
        val repo = RomajiMapRepository(db.romajiMapDao())
        repo.createDefaultMapIfNotExist()
        repo.insert(RomajiMapEntity(id = 991, name = "Romaji behavior test", mapData = repo.getDefaultMapData()))
        repo.setActiveMap(991)
        val keyboards = KeyboardRepository(db.keyboardLayoutDao())
        val template = KeyboardDefaultLayouts.createQwertyTemplateLayout().copy(isRomaji = true)
        val labeled = template.copyWithKeys(template.keys.map {
            when (it.action) {
                KeyAction.Enter -> it.copy(label = "ENTER", drawableResId = null, icon = null)
                KeyAction.Delete -> it.copy(label = "DELETE", drawableResId = null, icon = null)
                KeyAction.Space -> it.copy(label = "SPACE", drawableResId = null, icon = null)
                else -> it
            }
        })
        keyboards.saveLayout(labeled, "Romaji screen test", keyboards.getLayoutsNotFlow().firstOrNull()?.layoutId)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        try {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            shell("ime enable $target"); shell("ime set $target")
            for (mode in listOf("ROMAJI", "CUSTOM")) for (sokuon in listOf(false, true)) for (n in listOf(false, true)) {
                check(prefs.edit().putString("keyboard_order_preference", "[\"$mode\"]")
                    .putBoolean("save_last_used_keyboard", false)
                    .putBoolean("keyboard_floating_preference", false)
                    .putBoolean("live_conversion_preference", false)
                    .putBoolean("conversion_bunsetsu_separation_preference", false)
                    .putBoolean("remember_custom_keyboard_input_mode_preference", false).commit())
                android.util.Log.i("RomajiDeviceTest", "mode=$mode sokuon=$sokuon n=$n")
                setSettings(sokuon, n)
                ActivityScenario.launch<FastInputHostActivity>(Intent(context, FastInputHostActivity::class.java)).use { host ->
                    key("q"); SystemClock.sleep(500)
                    type("yappari")
                    assertEquals("$mode s=$sokuon n=$n", if (sokuon) "やっぱり" else "やっあり", text(host))
                    host.onActivity { it.restartEditorInput(true) }; key("q"); SystemClock.sleep(300)
                    type("kanta")
                    assertEquals("$mode s=$sokuon n=$n", if (n) "かんた" else "かnた", text(host))
                    host.onActivity { it.restartEditorInput(true) }; key("q"); SystemClock.sleep(300)
                    type("pp")
                    tap(if (mode == "CUSTOM") key("DELETE") else awaitNode { it.viewIdResourceName?.endsWith("/key_delete") == true })
                    type("pa")
                    assertEquals("$mode delete s=$sokuon", if (sokuon) "っぱ" else "ぱ", text(host))
                    host.onActivity { it.restartEditorInput(true) }; key("q"); SystemClock.sleep(300)
                    type("kan")
                    val enter = if (mode == "CUSTOM") key("ENTER") else awaitNode { it.viewIdResourceName?.endsWith("/key_return") == true }
                    tap(enter)
                    assertEquals("$mode final n=$n", if (n) "かん" else "かn", text(host))
                    host.onActivity { assertEquals(-1, BaseInputConnection.getComposingSpanStart(it.editText.text)) }
                    host.onActivity { it.restartEditorInput(true) }; key("q"); SystemClock.sleep(300)
                    type("kan")
                    tap(if (mode == "CUSTOM") key("SPACE") else awaitNode { it.viewIdResourceName?.endsWith("/key_space") == true })
                    SystemClock.sleep(500)
                    assertTrue(text(host).isNotEmpty())
                    assertEquals("$mode space n=$n: ${text(host)}", !n, text(host).contains('n'))
                    val firstCandidate = text(host)
                    tap(if (mode == "CUSTOM") key("SPACE") else awaitNode { it.viewIdResourceName?.endsWith("/key_space") == true })
                    SystemClock.sleep(300)
                    assertNotEquals("$mode second Space must advance the candidate", firstCandidate, text(host))
                }
            }
        } finally {
            if (previousIme.isNotEmpty() && previousIme != "null") shell("ime set $previousIme")
            if (!wasEnabled) shell("ime disable $target")
            db.close()
        }
    }
}
