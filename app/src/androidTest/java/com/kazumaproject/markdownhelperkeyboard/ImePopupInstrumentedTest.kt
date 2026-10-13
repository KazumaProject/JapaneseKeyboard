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

    @Test fun fortyTemplatesScrollBothWaysAndCommitTheLastEntry() {
        val entries = (0..40).map { index -> UserTemplate(word = "Scroll QA %02d".format(index),
            reading = "scroll-qa-%02d".format(index), posIndex = 0, posScore = 0) }
        val ids = runBlocking {
            database.userTemplateDao().insertAll(entries)
            database.userTemplateDao().getAllSuspend().filter { it.reading.startsWith("scroll-qa-") }.map { it.id }
        }
        try {
            for (floating in listOf(false, true)) withHost(floating = floating, integrated = floating) { host ->
                openShortcut("定型文")
                awaitNode("Scroll QA 00")
                assertTrue(awaitNodeId("popup_listview").childCount <= 5)
                scrollToTemplate("Scroll QA 40", up = true)
                scrollToTemplate("Scroll QA 00", up = false)
                scrollToTemplate("Scroll QA 40", up = true)
                tap(awaitNode("Scroll QA 40"))
                awaitText(host, "Scroll QA 40")
                host.onActivity { assertTrue(it.editor.hasWindowFocus()) }
            }
        } finally { runBlocking { ids.forEach { database.userTemplateDao().delete(it) } } }
    }

    private fun scrollToTemplate(label: String, up: Boolean) {
        repeat(20) {
            findNode(label)?.let { node ->
                val row = Rect().also(node::getBoundsInScreen)
                val viewport = Rect().also(awaitNodeId("popup_listview")::getBoundsInScreen)
                if (viewport.contains(row)) return
            }
            swipeList(up)
        }
        awaitNode(label)
    }

    private fun swipeList(up: Boolean) {
        val rect = Rect().also(awaitNodeId("popup_listview")::getBoundsInScreen)
        val start = if (up) rect.bottom - 12f else rect.top + 12f
        val end = if (up) rect.top + 12f else rect.bottom - 12f
        val down = SystemClock.uptimeMillis()
        for (step in 0..12) {
            val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                rect.exactCenterX(), start + (end - start) * step / 12f, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true)); event.recycle()
            SystemClock.sleep(30)
        }
        SystemClock.sleep(100)
    }

    @Test fun toolbarPopupPlacementFitsPortraitAndLandscapeScreens() {
        try {
            for (rotation in listOf(0, 1)) {
                check(automation.setRotation(rotation))
                for (floating in listOf(false, true)) withHost(floating = floating, integrated = floating) { host ->
                    // The launcher can force portrait until the unconstrained editor is shown.
                    host.onActivity { it.requestedOrientation = if (rotation == 0) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    await { automation.takeScreenshot()?.let { screenshot ->
                        val matches = (screenshot.width > screenshot.height) == (rotation == 1)
                        screenshot.recycle(); matches.takeIf { it }
                    } }
                    SystemClock.sleep(300)
                    openShortcut("定型文")
                    val listBounds = Rect().also(awaitNodeId("popup_listview")::getBoundsInScreen)
                    val screenshot = checkNotNull(automation.takeScreenshot())
                    assertTrue(listBounds.left >= 0 && listBounds.top >= 0)
                    assertTrue(listBounds.right <= screenshot.width && listBounds.bottom <= screenshot.height)
                    screenshot.recycle()
                    tap(awaitNode(TEMPLATE))
                    awaitText(host, TEMPLATE)
                    host.onActivity { assertTrue(it.editor.hasWindowFocus()) }
                }
            }
        } finally { automation.setRotation(-2) }
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
        repeat(3) {
            openShortcut("定型文")
            shell("input keyevent 4")
            await { findNodeId("popup_listview") == null }
            awaitNode("定型文")
            awaitText(host, "")
        }
        shell("input keyevent 4")
        await { findNode("定型文") == null }
        Unit
    }

    @Test fun independentAndIntegratedToolbarsUseTheSameSelectionWindow() {
        for (integrated in listOf(false, true)) {
            withHost(integrated = integrated) { host ->
                openShortcut("定型文")
                tap(awaitNode(TEMPLATE))
                awaitText(host, TEMPLATE)
            }
        }
    }

    @Test fun dictionarySearchKeepsItsLocalInputTargetWhileSelectingAList() = withHost { host ->
        await {
            findNodeId("dock_icon")?.let { tap(it) }
            findNode("ユーザー辞書フロート") ?: findNode(ins.targetContext.getString(R.string.shortcut_entry_content_description))?.let {
                tap(it); null
            }
        }.let(::tap)
        val searchLabel = ins.targetContext.getString(R.string.floating_dictionary_search)
        tap(awaitNode(searchLabel))
        longPress(awaitNodeId("key_small_letter"))
        awaitNodeId("popup_listview")
        SystemClock.sleep(500)
        assertNotNull(findNode(searchLabel))
        tap(requireNotNull(awaitNodeId("popup_listview").getChild(0)))
        await { findNodeId("popup_listview") == null }
        tap(awaitNode(searchLabel))
        openShortcut("定型文")
        tap(awaitNode(TEMPLATE))
        await { allNodes().firstOrNull { it.contentDescription == searchLabel && it.text?.toString() == TEMPLATE } }
        awaitText(host, "")
        host.onActivity { assertTrue(it.editor.hasWindowFocus()) }
    }

    @Test fun keyboardPickerSelectsInternalKeyboardAndAnEnabledExternalIme() = withHost { host ->
        val manager = ins.targetContext.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        val external = manager.enabledInputMethodList.first { it.packageName != ins.targetContext.packageName }
        longPress(awaitNodeId("key_small_letter"))
        tap(requireNotNull(awaitNodeId("popup_listview").getChild(0)))
        await { findNodeId("popup_listview") == null }
        host.onActivity { assertTrue(it.editor.hasWindowFocus()) }
        longPress(awaitNodeId("key_small_letter"))
        val label = external.loadLabel(ins.targetContext.packageManager).toString()
        try {
            repeat(10) { if (findNode(label) == null) swipeList(up = true) }
            tap(awaitNode(label))
            if (android.os.Build.VERSION.SDK_INT < 28) {
                awaitNodeId("select_dialog_listview")
                tap(awaitNode(label))
            }
            await { (shell("settings get secure default_input_method") == external.id).takeIf { it } }
        } finally { shell("ime set $targetIme") }
    }

    @Test fun floatingKeyboardSwitchListPreservesFocusAndSelection() = withHost(floating = true) { host ->
        awaitNodeId("keyboard_view_floating")
        SystemClock.sleep(500) // Let the freshly attached floating window finish positioning.
        longPress(awaitNodeId("key_small_letter"))
        val list = awaitNodeId("popup_listview")
        host.onActivity { assertTrue(it.editor.hasWindowFocus()) }
        tap(requireNotNull(list.getChild(0)))
        await { findNodeId("popup_listview") == null }
        awaitText(host, "")
        awaitNodeId("keyboard_view_floating")
        longPress(awaitNodeId("key_small_letter"))
        awaitNodeId("popup_listview")
        key(KeyEvent.KEYCODE_ESCAPE)
        await { findNodeId("popup_listview") == null }
        host.onActivity { assertTrue(it.editor.hasWindowFocus()) }
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
        flick("key_4", 0f, 100f) // Compose と through the IME's own keyboard into the local form.
        key(KeyEvent.KEYCODE_Y)
        // Make each registration new even when a previous device run was interrupted.
        SystemClock.uptimeMillis().toString().forEach { key(KeyEvent.KEYCODE_0 + (it - '0')) }
        val updatedReading = awaitNodeId("edit_text_ng_word_yomi_registration").text.toString()
        val updatedWord = awaitNodeId("edit_text_ng_word_tango_registration").text.toString()
        assertTrue(updatedReading.contains("x"))
        assertTrue(updatedWord.contains("y"))
        assertTrue(updatedWord.contains("と"))
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
                        ((android.os.Build.VERSION.SDK_INT >= 26 && it.hintText?.toString() == label) || it.text?.toString() == label || it.contentDescription?.toString() == label) }
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
        restartImeForFixture {
            prefs.edit().putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", false)
                .putBoolean("keyboard_floating_preference", false).commit()
        }
        shell("am force-stop com.android.chrome")
        ins.targetContext.startActivity(Intent(Intent.ACTION_VIEW,
            android.net.Uri.parse("https://login.payworks.ca/login?ssoLoggedOut=true"))
            .setPackage("com.android.chrome").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(2500) // Wait for Chrome navigation to replace the previous tab's nodes.
        for (password in listOf(false, true)) {
            val field = await(timeout = 90000) {
                allNodes().firstOrNull { it.packageName == "com.android.chrome" &&
                    it.className == "android.widget.EditText" && it.viewIdResourceName != "com.android.chrome:id/url_bar" &&
                    it.isPassword == password }
            }
            field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            SystemClock.sleep(500)
            val currentField = await { allNodes().firstOrNull { it.viewIdResourceName == field.viewIdResourceName && it.className == "android.widget.EditText" } }
            tap(currentField)
            awaitNodeId("keyboard_view")
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
            tap(requireNotNull(awaitNodeId("popup_listview").getChild(0)))
            await { findNodeId("popup_listview") == null }
            openShortcut("動的定型文（マクロ）")
            awaitNode(MACRO_NAME)
            SystemClock.sleep(500)
            assertNotNull(findNode(MACRO_NAME))
            tap(awaitNode(MACRO_NAME))
            if (!password) {
                SystemClock.sleep(300)
                shell("input keyevent 4") // Reveal the password field below Chrome's resized viewport.
                SystemClock.sleep(700) // Retrieve password bounds after Chrome completes its viewport resize.
            }
        }
    }

    private fun restartImeForFixture(configure: () -> Unit = {}) {
        check(originalIme.isNotEmpty() && originalIme != "null" && originalIme != targetIme) {
            "The isolated fixture requires another IME to restore between tests"
        }
        shell("ime set $originalIme")
        // Switching is asynchronous. Wait for the previous service to finish destroying
        // before writing preferences that its teardown can otherwise overwrite.
        await {
            (!shell("dumpsys activity services ${ins.targetContext.packageName}")
                .contains(".ime_service.IMEService")).takeIf { it }
        }
        configure()
        shell("ime set $targetIme")
    }

    private fun withHost(
        variation: Int = InputType.TYPE_TEXT_VARIATION_NORMAL,
        integrated: Boolean = false,
        floating: Boolean = false,
        web: Boolean = false,
        block: (HostScenario) -> Unit,
    ) {
        // Each configuration gets a fresh IME instance so a previous test's physical/floating
        // keyboard state cannot hide the software keyboard in the next editor.
        restartImeForFixture {
            prefs.edit().putBoolean("shortcut_toolbar_integrated_in_suggestion_preference", integrated)
                .putBoolean("keyboard_floating_preference", floating).commit()
        }
        ins.targetContext.startActivity(Intent(ins.targetContext, ImePopupHostActivity::class.java)
            .putExtra("variation", variation).putExtra("web", web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity = await { ImePopupHostActivity.current }
        try { block(HostScenario(activity)) } finally {
            ins.runOnMainSync { (ImePopupHostActivity.current ?: activity).finish() }
            await { ImePopupHostActivity.current == null }
        }
    }

    // InputMethodService animations can keep the main queue busy. Synchronize callbacks
    // directly instead of ActivityScenario's unbounded waitForIdleSync during launch.
    private class HostScenario(private val activity: ImePopupHostActivity) {
        fun onActivity(action: (ImePopupHostActivity) -> Unit) =
            InstrumentationRegistry.getInstrumentation().runOnMainSync { action(ImePopupHostActivity.current ?: activity) }
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
        }.let { node ->
            if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) tap(node)
        }
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
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true)); event.recycle()
        }
        inject(MotionEvent.ACTION_DOWN)
        try {
            // Wait for the actual long-press result before releasing, including on a busy emulator.
            awaitNodeId("popup_listview")
        } finally { inject(MotionEvent.ACTION_UP) }
        SystemClock.sleep(500)
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
        private lateinit var originalNgWordIds: Set<Int>
        private var macroId = 0L
        private var originalAutoRotation = ""
        private var originalRotation = ""
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
            originalAutoRotation = shell("settings get system accelerometer_rotation")
            originalRotation = shell("settings get system user_rotation")
            previous = defaults.keys.associateWith { prefs.all[it] }
            prefs.edit().apply {
                defaults.forEach { (key, value) -> when (value) {
                    is Boolean -> putBoolean(key, value); is String -> putString(key, value)
                } }
            }.commit()
            database = EntryPointAccessors.fromApplication(instrumentation.targetContext.applicationContext,
                BunsetsuTestDatabaseEntryPoint::class.java).database()
            runBlocking {
                originalNgWordIds = database.ngWordDao().getAll().map { it.id }.toSet()
                oldShortcuts = database.shortcutDao().getAllShortcuts()
                database.shortcutDao().replaceAll(listOf("template", "select_date", "text_macro", "keyboard_picker", "floating_user_dictionary")
                    .mapIndexed { index, type -> ShortcutItem(typeId = type, sortOrder = index) })
                database.userTemplateDao().insert(UserTemplate(word = TEMPLATE, reading = "てすと", posIndex = 0, posScore = 0))
                templateId = database.userTemplateDao().searchByReadingExactSuspend("てすと", 100).single { it.word == TEMPLATE }.id
                // Recover our reserved record after an interrupted instrumentation process.
                val existingMacro = database.textMacroDao().getByName(MACRO_NAME)
                check(existingMacro == null || existingMacro.body == MACRO_TEXT)
                macroId = existingMacro?.id
                    ?: database.textMacroDao().insert(TextMacro(name = MACRO_NAME, body = MACRO_TEXT))
            }
            targetIme = "${instrumentation.targetContext.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
            wasEnabled = shell("ime list -s").lines().any { it == targetIme }
            shell("settings put secure show_ime_with_hard_keyboard 1")
            shell("ime enable $targetIme"); shell("ime set $targetIme")
        }

        @JvmStatic @AfterClass fun restore() {
            for ((key, value) in listOf("accelerometer_rotation" to originalAutoRotation, "user_rotation" to originalRotation)) {
                if (value == "null") shell("settings delete system $key")
                else if (value.isNotEmpty()) shell("settings put system $key $value")
            }
            if (originalIme.isNotEmpty() && originalIme != "null") shell("ime set $originalIme")
            if (!wasEnabled && targetIme.isNotEmpty() && targetIme != originalIme) shell("ime disable $targetIme")
            if (originalHardware == "null") shell("settings delete secure show_ime_with_hard_keyboard")
            else if (originalHardware.isNotEmpty()) shell("settings put secure show_ime_with_hard_keyboard $originalHardware")
            if (::database.isInitialized) runBlocking {
                database.userTemplateDao().delete(templateId)
                database.textMacroDao().deleteById(macroId)
                if (::oldShortcuts.isInitialized) database.shortcutDao().replaceAll(oldShortcuts)
                if (::originalNgWordIds.isInitialized) {
                    // Editing can insert text anywhere in the fixture word, so a prefix check
                    // cannot identify saved drafts. Preserve pre-existing IDs and remove new ones.
                    database.ngWordDao().getAll().filter { it.id !in originalNgWordIds }
                        .forEach { database.ngWordDao().delete(it) }
                    assertEquals(originalNgWordIds, database.ngWordDao().getAll().map { it.id }.toSet())
                }
            }
            prefs.edit().apply { previous.forEach { (key, value) -> when (value) {
                null -> remove(key); is Boolean -> putBoolean(key, value); is String -> putString(key, value)
            } } }.commit()
        }
    }
}
