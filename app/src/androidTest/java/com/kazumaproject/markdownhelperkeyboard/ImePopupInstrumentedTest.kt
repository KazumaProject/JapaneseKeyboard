package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.database.AppDatabase
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordMatchMode
import com.kazumaproject.markdownhelperkeyboard.short_cut.data.ShortcutItem
import com.kazumaproject.markdownhelperkeyboard.text_macro.database.TextMacro
import com.kazumaproject.markdownhelperkeyboard.user_template.database.UserTemplate
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real IME windows and InputConnections; run with investigation/floating-panel.init.gradle. */
@RunWith(AndroidJUnit4::class)
class ImePopupInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val automation get() = ins.uiAutomation

    @Test fun nativeEmailAndPasswordKeepFocusWhileSelectingTemplatesDatesAndMacros() {
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            withHost(variation = variation) { host ->
                var losses = 0
                host.onActivity { losses = it.focusLosses }
                openShortcut("定型文")
                awaitNode(TEMPLATE)
                SystemClock.sleep(500)
                host.onActivity { assertEquals(losses, it.focusLosses); assertTrue(it.editor.hasWindowFocus()) }
                tap(awaitNode(TEMPLATE))
                awaitText(host, TEMPLATE)
                host.onActivity { it.editor.setText("") }
                openShortcut("日付")
                val list = awaitNodeId("popup_listview")
                val row = requireNotNull(list.getChild(0))
                val date = row.text?.toString() ?: requireNotNull(row.getChild(0)).text.toString()
                tap(row)
                awaitText(host, date)
                host.onActivity { it.editor.setText("") }
                openShortcut("動的定型文（マクロ）")
                tap(awaitNode(MACRO_NAME))
                awaitText(host, MACRO_TEXT)
                host.onActivity { assertEquals(losses, it.focusLosses) }
            }
        }
    }

    @Test fun outsideTapBackAndEscapeDismissOnlyTheList() = withHost { host ->
        openShortcut("定型文")
        awaitNode(TEMPLATE)
        touch(5f, 250f)
        await { findNodeId("popup_listview") == null }
        awaitText(host, "")
        openShortcut("定型文")
        awaitNode(TEMPLATE)
        key(KeyEvent.KEYCODE_ESCAPE)
        await { findNodeId("popup_listview") == null }
        openShortcut("定型文")
        awaitNode(TEMPLATE)
        shell("input keyevent 4")
        await { findNodeId("popup_listview") == null }
        awaitNode("定型文")
        awaitText(host, "")
    }

    @Test fun integratedAndFloatingToolbarsUseTheSameSelectionWindow() {
        for (integrated in listOf(false, true)) for (floating in listOf(false, true)) {
            withHost(integrated = integrated, floating = floating) { host ->
                openShortcut("定型文")
                tap(awaitNode(TEMPLATE))
                awaitText(host, TEMPLATE)
            }
        }
    }

    @Test fun ngWordFormEditsLocallyAndSavesExplicitMatchMode() = withHost { host ->
        typeReading()
        longPress(awaitNode(TEMPLATE))
        tap(awaitNode(ins.targetContext.getString(R.string.candidate_action_hide_word)))
        val reading = awaitNodeId("edit_text_ng_word_yomi_registration")
        var appBefore = ""
        host.onActivity { appBefore = it.editor.text.toString() }
        tap(reading)
        key(KeyEvent.KEYCODE_X)
        val word = awaitNodeId("edit_text_ng_word_tango_registration")
        tap(word)
        key(KeyEvent.KEYCODE_Y)
        val updatedReading = awaitNodeId("edit_text_ng_word_yomi_registration").text.toString()
        val updatedWord = awaitNodeId("edit_text_ng_word_tango_registration").text.toString()
        assertTrue(updatedReading.contains("x"))
        assertTrue(updatedWord.contains("y"))
        host.onActivity { assertEquals(appBefore, it.editor.text.toString()); assertTrue(it.editor.hasWindowFocus()) }
        tap(awaitNodeId("ng_word_match_exact"))
        tap(awaitNodeId("button_ng_word_registration_save"))
        await { findNodeId("button_ng_word_registration_save") == null }
        await { runBlocking { database.ngWordDao().find(updatedReading, updatedWord) != null } }
        assertEquals(NgWordMatchMode.EXACT,
            runBlocking { database.ngWordDao().find(updatedReading, updatedWord) }!!.matchMode)
        key(KeyEvent.KEYCODE_A)
        await { text(host) != appBefore }
        Unit
    }

    @Test fun ngWordCancelDoesNotRegisterDrafts() = withHost { host ->
        val before = runBlocking { database.ngWordDao().getAll().size }
        typeReading()
        longPress(awaitNode(TEMPLATE))
        tap(awaitNode(ins.targetContext.getString(R.string.candidate_action_hide_word)))
        tap(awaitNodeId("edit_text_ng_word_yomi_registration"))
        key(KeyEvent.KEYCODE_X)
        tap(awaitNodeId("button_ng_word_registration_cancel"))
        await { findNodeId("button_ng_word_registration_save") == null }
        assertEquals(before, runBlocking { database.ngWordDao().getAll().size })
        key(KeyEvent.KEYCODE_A)
        await { text(host).endsWith("あ") }
        Unit
    }

    @Test fun endingInputSessionDiscardsNgWordDraftAndRestoresAppTarget() {
        val before = runBlocking { database.ngWordDao().getAll().size }
        withHost { _ ->
            typeReading()
            longPress(awaitNode(TEMPLATE))
            tap(awaitNode(ins.targetContext.getString(R.string.candidate_action_hide_word)))
            tap(awaitNodeId("edit_text_ng_word_tango_registration"))
            key(KeyEvent.KEYCODE_X)
        }
        await { findNodeId("button_ng_word_registration_save") == null }
        assertEquals(before, runBlocking { database.ngWordDao().getAll().size })
        withHost { host ->
            key(KeyEvent.KEYCODE_A)
            await { text(host).isNotEmpty() }
            Unit
        }
    }

    @Test fun focusableControlPopupReproducesHostFocusLoss() = withHost { host ->
        openSoftwareKeyboard()
        var losses = 0
        var control: android.widget.PopupWindow? = null
        host.onActivity { activity ->
            losses = activity.focusLosses
            control = android.widget.PopupWindow(android.widget.TextView(activity).apply { text = "Focusable control" },
                400, 200, true).apply {
                showAtLocation(activity.window.decorView, android.view.Gravity.CENTER, 0, 0)
            }
        }
        try {
            await {
                var lost = false
                host.onActivity { lost = it.focusLosses > losses }
                lost
            }
        } finally { ins.runOnMainSync { control?.dismiss() } }
    }

    @Test fun webViewEmailAndPasswordKeepTheServedFieldWhenSelectingTemplates() {
        withHost(web = true) { host ->
            for (label in listOf("Email", "Password")) {
                val field = await {
                    allNodes().firstOrNull { it.className == "android.widget.EditText" &&
                        (it.hintText?.toString() == label || it.text?.toString() == label || it.contentDescription?.toString() == label) }
                        ?: allNodes().filter { it.className == "android.widget.EditText" }.getOrNull(if (label == "Email") 0 else 1)
                }
                tap(field)
                openShortcut("定型文")
                tap(awaitNode(TEMPLATE))
                val id = label.lowercase()
                await {
                    var result = ""
                    val latch = java.util.concurrent.CountDownLatch(1)
                    host.onActivity { it.webView!!.evaluateJavascript("document.getElementById('$id').value") {
                        result = it; latch.countDown()
                    } }
                    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                    result.takeIf { it.contains(TEMPLATE) }
                }
            }
        }
    }

    /** Optional network check against the reported site; never submits the login form. */
    @Test fun chromePayworksKeepsEmailAndPasswordFocusedWhileSelectingLists() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("payworks") == "true")
        prefs.edit().putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", false)
            .putBoolean("keyboard_floating_preference", false).commit()
        ins.targetContext.startActivity(Intent(Intent.ACTION_VIEW,
            android.net.Uri.parse("https://login.payworks.ca/login?ssoLoggedOut=true"))
            .setPackage("com.android.chrome").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(2500) // Wait for Chrome navigation to replace the previous tab's nodes.
        for (password in listOf(false, true)) {
            val field = await {
                allNodes().firstOrNull { it.packageName == "com.android.chrome" &&
                    it.className == "android.widget.EditText" && it.viewIdResourceName != "com.android.chrome:id/url_bar" &&
                    it.isPassword == password }
            }
            tap(field)
            openShortcut("定型文")
            awaitNode(TEMPLATE)
            SystemClock.sleep(500)
            assertNotNull(findNode(TEMPLATE))
            assertTrue("Chrome must retain application window focus", automation.windows.any {
                it.isFocused && it.root?.packageName == "com.android.chrome"
            })
            tap(awaitNode(TEMPLATE))
            openShortcut("日付")
            awaitNodeId("popup_listview")
            SystemClock.sleep(500)
            assertNotNull(findNodeId("popup_listview"))
            key(KeyEvent.KEYCODE_ESCAPE)
            openShortcut("動的定型文（マクロ）")
            awaitNode(MACRO_NAME)
            SystemClock.sleep(500)
            assertNotNull(findNode(MACRO_NAME))
            tap(awaitNode(MACRO_NAME))
            if (!password) {
                SystemClock.sleep(300)
                shell("input keyevent 4") // Reveal the password field below Chrome's resized viewport.
            }
        }
    }

    private fun withHost(
        variation: Int = InputType.TYPE_TEXT_VARIATION_NORMAL,
        integrated: Boolean = false,
        floating: Boolean = false,
        web: Boolean = false,
        block: (HostScenario) -> Unit,
    ) {
        prefs.edit().putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", integrated)
            .putBoolean("keyboard_floating_preference", floating).commit()
        // Each configuration gets a fresh IME instance so a previous test's physical/floating
        // keyboard state cannot hide the software keyboard in the next editor.
        if (originalIme.isNotEmpty() && originalIme != "null" && originalIme != targetIme) {
            shell("ime set $originalIme")
            SystemClock.sleep(200)
            shell("ime set $targetIme")
        }
        ins.targetContext.startActivity(Intent(ins.targetContext, ImePopupHostActivity::class.java)
            .putExtra("variation", variation).putExtra("web", web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity = await { ImePopupHostActivity.current }
        try { block(HostScenario(activity)) } finally {
            ins.runOnMainSync { activity.finish() }
            await { ImePopupHostActivity.current == null }
        }
    }

    // InputMethodService animations can keep the main queue busy. Synchronize callbacks
    // directly instead of ActivityScenario's unbounded waitForIdleSync during launch.
    private class HostScenario(private val activity: ImePopupHostActivity) {
        fun onActivity(action: (ImePopupHostActivity) -> Unit) =
            InstrumentationRegistry.getInstrumentation().runOnMainSync { action(activity) }
    }

    private fun text(host: HostScenario): String {
        var value = ""
        host.onActivity { value = it.editor.text.toString() }
        return value
    }
    private fun awaitText(host: HostScenario, expected: String) {
        await { (text(host) == expected).takeIf { it } }
    }
    private fun openShortcut(label: String) {
        await {
            findNodeId("dock_icon")?.let { tap(it) }
            findNode(label) ?: findNode(ins.targetContext.getString(R.string.shortcut_entry_content_description))?.let {
                tap(it); null
            }
        }.let(::tap)
        awaitNodeId("popup_listview")
    }
    private fun typeReading() {
        openSoftwareKeyboard()
        flick("key_4", 100f, 0f) // て
        flick("key_3", 0f, -100f) // す
        flick("key_4", 0f, 100f) // と
    }
    private fun openSoftwareKeyboard() {
        await {
            findNodeId("dock_icon")?.let { tap(it) }
            findNode("定型文")
        }
    }
    private fun flick(id: String, dx: Float, dy: Float) {
        val rect = Rect().also(awaitNodeId(id)::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
            val moved = action != MotionEvent.ACTION_DOWN
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                rect.exactCenterX() + if (moved) dx else 0f,
                rect.exactCenterY() + if (moved) dy else 0f, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true)); event.recycle()
            SystemClock.sleep(100)
        }
    }
    private fun key(code: Int) {
        val device = InputDevice.getDeviceIds().asIterable().mapNotNull(InputDevice::getDevice)
            .firstOrNull { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }?.id ?: 0
        val down = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            check(automation.injectInputEvent(KeyEvent(down, SystemClock.uptimeMillis(), action, code,
                0, 0, device, 0, 0, InputDevice.SOURCE_KEYBOARD), true))
        }
        SystemClock.sleep(100)
    }
    private fun tap(node: AccessibilityNodeInfo) {
        val rect = Rect().also(node::getBoundsInScreen)
        check(!rect.isEmpty) { "Node has no bounds" }
        touch(rect.exactCenterX(), rect.exactCenterY())
    }
    private fun longPress(node: AccessibilityNodeInfo) {
        val rect = Rect().also(node::getBoundsInScreen)
        touch(rect.exactCenterX(), rect.exactCenterY(), 700)
    }
    private fun touch(x: Float, y: Float, hold: Long = 50) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true)); event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(hold)
        }
        SystemClock.sleep(100)
    }
    private fun allNodes(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            nodes += node
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        automation.windows.forEach { visit(it.root) }
        return nodes.filter { it.isVisibleToUser }
    }
    private fun findNode(text: String) = allNodes().firstOrNull {
        it.text?.toString()?.trim() == text || it.contentDescription?.toString() == text
    }
    private fun findNodeId(id: String) = allNodes().firstOrNull { it.viewIdResourceName?.endsWith(":id/$id") == true }
    private fun awaitNode(text: String): AccessibilityNodeInfo {
        android.util.Log.i("ImePopupQA", "Await node: $text")
        return await { findNode(text) }
    }
    private fun awaitNodeId(id: String) = await { findNodeId(id) }
    private fun <T : Any> await(timeout: Long = 30000, value: () -> T?): T {
        val end = SystemClock.uptimeMillis() + timeout
        do {
            value()?.let { if (it != false) return it }
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < end)
        throw AssertionError("Timed out waiting for IME popup state; nodes=" + allNodes().map { "${it.text}/${it.contentDescription}/${it.viewIdResourceName}" })
    }

    companion object {
        private const val TEMPLATE = "popup-qa@example.invalid"
        private const val MACRO_NAME = "IME Popup QA Macro"
        private const val MACRO_TEXT = "popup-qa-macro"
        private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
        private val prefs get() = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        private lateinit var database: AppDatabase
        private var originalIme = ""
        private var originalHardware = ""
        private var wasEnabled = false
        private var targetIme = ""
        private lateinit var oldShortcuts: List<ShortcutItem>
        private var macroId = 0L
        private var templateId = 0
        private var previous: Map<String, Any?> = emptyMap()
        private val defaults = mapOf<String, Any>(
            "shortcut_toolbar_visibility_preference" to true,
            "shortcut_toolbar_integrated_in_suggestion_preference" to false,
            "keyboard_floating_preference" to false,
            "keyboard_order_preference" to "[\"TENKEY\"]",
            "save_last_used_keyboard" to false,
            "live_conversion_preference" to false,
            "learn_dictionary_preference" to false,
            "user_template_preference" to true,
            "ng_word_enable_preference" to true,
            "physical_keyboard_input_mode_preference" to "romaji",
        )
        private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }

        @JvmStatic @BeforeClass fun setup() {
            Assume.assumeTrue("Use the isolated floating-panel QA application",
                instrumentation.targetContext.packageName.startsWith("com.kazumaproject.floatingqa"))
            instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            originalIme = shell("settings get secure default_input_method")
            originalHardware = shell("settings get secure show_ime_with_hard_keyboard")
            previous = defaults.keys.associateWith { prefs.all[it] }
            prefs.edit().apply {
                defaults.forEach { (key, value) -> when (value) {
                    is Boolean -> putBoolean(key, value); is String -> putString(key, value)
                } }
            }.commit()
            database = EntryPointAccessors.fromApplication(instrumentation.targetContext.applicationContext,
                BunsetsuTestDatabaseEntryPoint::class.java).database()
            runBlocking {
                oldShortcuts = database.shortcutDao().getAllShortcuts()
                database.shortcutDao().replaceAll(listOf("template", "select_date", "text_macro", "keyboard_picker")
                    .mapIndexed { index, type -> ShortcutItem(typeId = type, sortOrder = index) })
                database.userTemplateDao().insert(UserTemplate(word = TEMPLATE, reading = "てすと", posIndex = 0, posScore = 0))
                templateId = database.userTemplateDao().searchByReadingExactSuspend("てすと", 100).single { it.word == TEMPLATE }.id
                macroId = database.textMacroDao().insert(TextMacro(name = MACRO_NAME, body = MACRO_TEXT))
            }
            targetIme = "${instrumentation.targetContext.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
            wasEnabled = shell("ime list -s").lines().any { it == targetIme }
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("ime enable $targetIme"); shell("ime set $targetIme")
        }

        @JvmStatic @AfterClass fun restore() {
            if (originalIme.isNotEmpty() && originalIme != "null") shell("ime set $originalIme")
            if (!wasEnabled && targetIme.isNotEmpty() && targetIme != originalIme) shell("ime disable $targetIme")
            if (originalHardware == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else if (originalHardware.isNotEmpty()) shell("settings put secure show_ime_with_hard_keyboard $originalHardware")
            if (::database.isInitialized) runBlocking {
                database.userTemplateDao().delete(templateId)
                database.textMacroDao().deleteById(macroId)
                if (::oldShortcuts.isInitialized) database.shortcutDao().replaceAll(oldShortcuts)
                database.ngWordDao().getAll().filter { it.tango.startsWith(TEMPLATE) }.forEach { database.ngWordDao().delete(it) }
            }
            prefs.edit().apply { previous.forEach { (key, value) -> when (value) {
                null -> remove(key); is Boolean -> putBoolean(key, value); is String -> putString(key, value)
            } } }.commit()
        }
    }
}
