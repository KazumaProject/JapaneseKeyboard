package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** API 24-compatible end-to-end checks against real IME windows and touch dispatch. */
@RunWith(AndroidJUnit4::class)
class FloatingPanelDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val ctx get() = ins.targetContext
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) { node.refresh(); result.add(node); for (i in 0 until node.childCount) node.getChild(i)?.let(::visit) }
        ui.windows.forEach { it.root?.let(::visit) }
        return result.filter { it.isVisibleToUser }
    }
    private fun awaitNodes(predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val until = SystemClock.uptimeMillis() + 60000
        do {
            val found = nodes().filter(predicate)
            if (found.isNotEmpty()) return found
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < until)
        error("Missing node; " + nodes().joinToString { "${it.viewIdResourceName}/${it.contentDescription}" })
    }
    private fun id(value: String) = awaitNodes { it.viewIdResourceName == "${ctx.packageName}:id/$value" }
    private fun description(value: String) = awaitNodes { it.contentDescription?.toString() == value }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun gesture(rect: Rect, dx: Float = 0f, dy: Float = 0f) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(ui.injectInputEvent(event, true)); event.recycle()
        }
        val x = rect.exactCenterX(); val y = rect.exactCenterY()
        send(MotionEvent.ACTION_DOWN, x, y); SystemClock.sleep(50)
        if (dx != 0f || dy != 0f) { send(MotionEvent.ACTION_MOVE, x + dx, y + dy); SystemClock.sleep(150) }
        send(MotionEvent.ACTION_UP, x + dx, y + dy); SystemClock.sleep(400)
    }
    private fun text(scenario: ActivityScenario<FastInputHostActivity>): String {
        var value = ""; scenario.onActivity { value = it.editText.text.toString() }; return value
    }
    private fun capture(name: String) {
        val bitmap = requireNotNull(ui.takeScreenshot())
        val directory = File(ctx.filesDir, "floating-panel").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        File(directory, "$name-nodes.txt").writeText(nodes().joinToString("\n") { "${it.viewIdResourceName} ${it.contentDescription} ${bounds(it)}" })
        File(directory, "$name-windows.txt").writeText(shell("dumpsys window windows"))
    }

    @Test fun inputMoveResizeAndRestore() {
        check(ctx.packageName.startsWith("com.kazumaproject.floatingqa")) { "Use investigation/floating-panel.init.gradle" }
        val args = InstrumentationRegistry.getArguments()
        val split = args.getString("surface") == "split"
        val keyboard = args.getString("keyboard") ?: "TENKEY"
        val landscape = args.getString("rotation") == "landscape"
        val skin = args.getString("skin") ?: "default"
        val media = args.getString("media") ?: "none"
        val label = "${if (split) "split" else "floating"}-$keyboard-${if (landscape) "landscape" else "portrait"}-$skin-$media"
        val oldIme = shell("settings get secure default_input_method")
        val oldRotation = shell("settings get system user_rotation")
        val oldAuto = shell("settings get system accelerometer_rotation")
        val target = "${ctx.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val wasEnabled = shell("settings get secure enabled_input_methods").split(':').any { it.substringBefore(';') == target }
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            shell("input keyevent WAKEUP")
            shell("wm dismiss-keyguard")
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation ${if (landscape) 1 else 0}")
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            check(prefs.edit().clear()
                .putBoolean("save_last_used_keyboard", false)
                .putString("keyboard_order_preference", "[\"${if (split) "SPLIT" else keyboard}\"]")
                .putString("split_keyboard_main_type", keyboard).putString("split_keyboard_sub_type", "TENKEY")
                .putBoolean("keyboard_floating_preference", !split)
                .putString("keyboard_skin_preference", skin)
                .putInt("keyboard_width_preference", 75).putInt("qwerty_keyboard_width_preference", 75)
                .putInt("keyboard_height_preference", 220).putInt("qwerty_keyboard_height_preference", 220)
                .putInt("keyboard_width_landscape_preference", 65).putInt("qwerty_keyboard_width_landscape_preference", 65)
                .putInt("keyboard_height_landscape_preference", 140).putInt("qwerty_keyboard_height_landscape_preference", 140)
                .putBoolean("landscape_force_qwerty_preference", false).putBoolean("landscape_force_qwerty_romaji_preference", false)
                .putBoolean("qwerty_show_emoji_button_preference", true)
                .putBoolean("clipboard_preview_enable_preference", false)
                .putBoolean("live_conversion_preference", false).putBoolean("learn_dictionary_preference", false)
                .putBoolean("tenkey_kana_english_qwerty_preference", false)
                .putBoolean("flick_input_only_preference", true).commit())
            if (keyboard == "CUSTOM") kotlinx.coroutines.runBlocking {
                val db = com.kazumaproject.markdownhelperkeyboard.ime_service.di.AppModule.providesLearnDatabase(ctx)
                val repository = com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository(db.keyboardLayoutDao())
                val editor = com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardEditorViewModel(repository)
                editor.applyTemplate(com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts.createToggleKanaTemplateLayout())
                repository.saveLayout(editor.uiState.value.layout.copy(isDirectMode = false), "Floating panel test", repository.getLayoutsNotFlow().firstOrNull()?.layoutId)
            }
            if (media != "none") {
                val file = File(ctx.filesDir, if (media == "video") "panel-background.mp4" else "panel-background.png")
                if (media == "video") {
                    ins.context.assets.open("floating-panel-background.mp4").use { input -> file.outputStream().use(input::copyTo) }
                } else {
                    val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(0xff00cc99.toInt())
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                prefs.edit().putString("keyboard_background_${if (media == "video") "video" else "image"}_uri_preference", android.net.Uri.fromFile(file).toString()).commit()
            }
            ActivityScenario.launch<FastInputHostActivity>(Intent(ctx, FastInputHostActivity::class.java)).use { scenario ->
                SystemClock.sleep(700)
                shell("ime enable $target"); shell("ime set $target")
                scenario.onActivity { it.restartEditorInput(true) }
                val rootId = when (keyboard) { "QWERTY", "ROMAJI" -> "qwerty_view_floating"; "GOJUON" -> "gojuon_view_floating"; "SUMIRE", "CUSTOM" -> "custom_layout_floating"; else -> "keyboard_view_floating" }
                id(rootId); SystemClock.sleep(1200)
                capture("$label-initial")
                val navigation = nodes().firstOrNull { it.viewIdResourceName == "com.android.systemui:id/navigation_bar_frame" }?.let(::bounds)
                if (navigation != null && !navigation.isEmpty) {
                    val panels = if (split) nodes().filter { it.contentDescription?.toString() in listOf(ctx.getString(R.string.split_keyboard_main), ctx.getString(R.string.split_keyboard_sub)) }
                        else description(ctx.getString(R.string.composing_guide_move))
                    panels.forEach { check(!Rect.intersects(bounds(it), navigation)) { "Panel overlaps navigation bar: ${bounds(it)} / $navigation" } }
                }
                if (media != "none") {
                    SystemClock.sleep(1500)
                    val bitmap = requireNotNull(ui.takeScreenshot())
                    var mediaPixels = 0
                    for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) {
                        val color = bitmap.getPixel(x, y)
                        if (android.graphics.Color.green(color) > 130 && android.graphics.Color.blue(color) > 80 && android.graphics.Color.red(color) < 80) mediaPixels++
                    }
                    bitmap.recycle()
                    check(mediaPixels > 50) { "Background $media was not rendered" }
                }
                val moveText = ctx.getString(R.string.composing_guide_move)
                val grips = description(moveText)
                check(grips.size == if (split) 2 else 1) { "Unexpected panel count ${grips.size}" }
                val keyId = if (keyboard == "QWERTY" || keyboard == "ROMAJI") "key_a" else "key_1"
                if (keyboard in listOf("TENKEY", "QWERTY", "ROMAJI", "GOJUON", "SUMIRE", "CUSTOM")) {
                    val keys = if (keyboard == "SUMIRE" || keyboard == "CUSTOM") description("あ") else id(keyId)
                    keys.forEach { gesture(bounds(it)) }
                    if (split && skin.startsWith("cupertino_")) {
                        // Exercise every direction. Skin popups must remain in the same
                        // token-routed window as the split-pane key while the Cupertino
                        // surface changes in place.
                        listOf(
                            -90f to 0f,
                            0f to -90f,
                            90f to 0f,
                            0f to 90f,
                        ).forEach { (dx, dy) ->
                            keys.forEach { gesture(bounds(it), dx = dx, dy = dy) }
                        }
                    }
                    check(text(scenario).isNotEmpty()) { "Key touches did not reach editor" }
                    if (!split && keyboard == "TENKEY") check(text(scenario) == "あ") { "Wrong key hit: ${text(scenario)}" }
                    capture("$label-input")
                    if (keyboard != "QWERTY") {
                        val candidate = id("suggestion_item_text_view").first()
                        gesture(bounds(candidate))
                        scenario.onActivity {
                            check(android.view.inputmethod.BaseInputConnection.getComposingSpanStart(it.editText.text) == -1) { "Candidate was not committed" }
                        }
                        capture("$label-committed")
                    }
                }
                val grip = bounds(description(moveText).first())
                val screenHeight = requireNotNull(ui.takeScreenshot()).let { val height = it.height; it.recycle(); height }
                val delta = if (grip.centerY() < screenHeight * .65f) 70f else -70f
                gesture(grip, dy = delta)
                capture("$label-moved")
                val moved = bounds(description(moveText).first())
                check(kotlin.math.abs(moved.top - grip.top) > 10) { "Move handle did not move window: $grip -> $moved" }
                val edit = ctx.getString(R.string.composing_guide_edit)
                gesture(bounds(description(edit).first()))
                val right = ctx.getString(R.string.composing_guide_resize_right)
                val before = bounds(description(moveText).first())
                gesture(bounds(description(right).first()), dx = -60f)
                val after = bounds(description(moveText).first())
                check(after.width() < before.width()) { "Resize did not change body width: $before -> $after" }
                capture("$label-editing")
                gesture(bounds(description(ctx.getString(R.string.composing_guide_done)).first()))
                SystemClock.sleep(800)
                val savedGrip = bounds(description(moveText).first())
                val restoredWidth = savedGrip.width()
                shell("input keyevent BACK")
                scenario.onActivity { it.restartEditorInput(false) }
                id(rootId); SystemClock.sleep(700)
                capture("$label-reopened")
                check(kotlin.math.abs(bounds(description(moveText).first()).width() - restoredWidth) <= 3) { "Width not restored: $restoredWidth -> ${bounds(description(moveText).first()).width()}" }
                val reopenedGrip = bounds(description(moveText).first())
                check(kotlin.math.abs(reopenedGrip.left - savedGrip.left) <= 3 && kotlin.math.abs(reopenedGrip.top - savedGrip.top) <= 3) {
                    "Position not restored: $savedGrip -> $reopenedGrip"
                }
                capture("$label-restored")
                if (!split && keyboard in listOf("TENKEY", "QWERTY")) {
                    scenario.onActivity { it.restartEditorInput(true) }
                    SystemClock.sleep(700)
                    id(rootId)
                    if (keyboard == "QWERTY") gesture(bounds(id("key_emoji").first()))
                    else gesture(bounds(description("symbol").first()))
                    id("return_jp_keyboard_button")
                    capture("$label-symbols")
                    gesture(bounds(id("symbol_text").first()))
                    check(text(scenario).isNotEmpty()) { "Symbol was not inserted" }
                    gesture(bounds(id("return_jp_keyboard_button").first()))
                    id(rootId)
                }
                ins.sendStatus(0, Bundle().apply { putString("stream", "PASS $label input/move/resize/restore\n") })
            }
        } finally {
            capture("$label-final")
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $target")
            if (oldRotation != "null") shell("settings put system user_rotation $oldRotation")
            if (oldAuto != "null") shell("settings put system accelerometer_rotation $oldAuto")
        }
    }
}
