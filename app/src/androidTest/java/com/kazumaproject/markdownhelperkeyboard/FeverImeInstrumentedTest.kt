package com.kazumaproject.markdownhelperkeyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import androidx.preference.PreferenceManager
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.ime_service.fever.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual editor, IME, window insets and touchscreen; runs only in an isolated application ID. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class FeverImeInstrumentedTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ui get() = ins.uiAutomation
    private val context get() = ins.targetContext
    private fun shell(cmd: String) = ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(cmd)).bufferedReader().use { it.readText().trim() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun visit(n: AccessibilityNodeInfo) { nodes.add(n); for (i in 0 until n.childCount) n.getChild(i)?.let(::visit) }
        ui.windows.forEach { it.root?.let(::visit) }
        return nodes
    }
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 45000
        while (SystemClock.uptimeMillis() < end) { if (condition()) return; SystemClock.sleep(100) }
        error("Timed out; UI: " + nodes().joinToString { "${it.viewIdResourceName}: ${it.text}" })
    }
    private fun key(id: String): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await { found = nodes().firstOrNull { it.isVisibleToUser && it.viewIdResourceName == "${context.packageName}:id/$id" }; found != null }
        return found!!
    }
    private fun stableKeyboardBounds(floating: Boolean): Rect {
        var previous: Rect? = null
        var stable = 0
        var result = Rect()
        await {
            result = Rect().also(key(if (floating) "keyboard_view_floating" else "keyboard_view")::getBoundsInScreen)
            stable = if (result == previous) stable + 1 else 0
            previous = result
            SystemClock.sleep(150)
            stable >= 3
        }
        return result
    }
    private fun tap(id: String) {
        val rect = Rect().also(key(id)::getBoundsInScreen)
        shell("input tap ${rect.centerX()} ${rect.centerY()}")
    }
    private fun keyLabelBitmap(): Bitmap {
        val rect = Rect().also(key("key_5")::getBoundsInScreen)
        rect.inset(rect.width() / 4, rect.height() / 4)
        val screen = requireNotNull(ui.takeScreenshot())
        return Bitmap.createBitmap(screen, rect.left, rect.top, rect.width(), rect.height()).also { screen.recycle() }
    }
    private fun ime(): FeverInputFrameLayout {
        var result: FeverInputFrameLayout? = null
        ins.runOnMainSync {
            fun find(v: View) {
                if (v is FeverInputFrameLayout && v.isShown) result = v
                if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))
            }
            WindowInspector.getGlobalWindowViews().forEach(::find)
        }
        return requireNotNull(result)
    }
    private fun assertCapBindings() {
        ins.runOnMainSync {
            val ids = listOf("key_1", "key_5", "key_delete").map { context.resources.getIdentifier(it, "id", context.packageName) }
            val caps = mutableListOf<View>()
            fun visit(view: View) {
                if (view.isShown && view.id in ids) caps.add(view)
                if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            WindowInspector.getGlobalWindowViews().forEach(::visit)
            assertEquals(3, caps.size)
            caps.forEach { cap ->
                assertNotNull("Each key must retain its FEVER cap", cap.background)
                assertNotNull("Key caps must support the keyboard's drawable cloning", cap.background.constantState)
            }
        }
    }
    private fun capture(name: String) {
        val out = File(context.getExternalFilesDir(null), "fever-evidence").apply { mkdirs() }
        val bitmap = requireNotNull(ui.takeScreenshot())
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun assertLabelLegible(before: Bitmap, during: Bitmap, name: String) {
        assertEquals(before.width, during.width); assertEquals(before.height, during.height)
        val out = File(context.getExternalFilesDir(null), "fever-evidence").apply { mkdirs() }
        for ((suffix, bitmap) in listOf("label-before" to before, "label-during" to during))
            File(out, "$name-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var glyph = 0; var retained = 0; var dark = 0; var contrast = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            val a = before.getPixel(x, y); val b = during.getPixel(x, y)
            fun low(c: Int) = minOf(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
            fun high(c: Int) = maxOf(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
            if (low(a) >= 190) { glyph++; if (low(b) >= 150) retained++ }
            if (high(a) < 80) { dark++; if (high(b) < 170) contrast++ }
        }
        println("FEVER label $name glyph=$retained/$glyph dark=$contrast/$dark")
        assertTrue("The real label must remain visible", glyph > 10 && retained >= glyph * .98)
        assertTrue("The label must keep a dark contrasting surface", dark > 10 && contrast >= dark * .90)
    }
    private fun startEditor(landscape: Boolean): FastInputHostActivity {
        shell("am start -n ${context.packageName}/com.kazumaproject.markdownhelperkeyboard.FastInputHostActivity")
        var activity: FastInputHostActivity? = null
        val wanted = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        await {
            ins.runOnMainSync {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FastInputHostActivity>().firstOrNull()
                if (resumed != null) {
                    if (resumed.requestedOrientation != wanted) resumed.requestedOrientation = wanted
                    else if (resumed.window.decorView.width > 0 && (resumed.window.decorView.width > resumed.window.decorView.height) == landscape)
                        activity = resumed
                }
            }
            activity != null
        }
        return requireNotNull(activity)
    }
    private fun verifyNumericAndPasswordEditors() {
        val activity = startEditor(false)
        try {
            ins.runOnMainSync { activity.editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER; activity.restartEditorInput(true) }
            var numericKey: AccessibilityNodeInfo? = null
            await {
                numericKey = nodes().firstOrNull { it.isVisibleToUser && it.text?.toString()?.trim() == "1" && it.isClickable }
                numericKey != null
            }
            val root = ime()
            var numericAward = false
            ins.runOnMainSync { root.controller.eventObserver = { println("FEVER numeric event $it"); numericAward = numericAward || it.award == FeverAward.JACKPOT } }
            // The automatic numeric layout can appear before its window finishes moving after rotation.
            var numberBounds = Rect()
            var stable = 0
            await {
                val current = nodes().firstOrNull { it.isVisibleToUser && it.text?.toString()?.trim() == "1" && it.isClickable }
                val next = current?.let { Rect().also(it::getBoundsInScreen) }
                stable = if (next != null && next == numberBounds && !next.isEmpty) stable + 1 else 0
                if (next != null) numberBounds = next
                SystemClock.sleep(150)
                stable >= 4
            }
            println("FEVER numeric tap $numberBounds")
            shell("input tap ${numberBounds.centerX()} ${numberBounds.centerY()}")
            await { var applied = false; ins.runOnMainSync { applied = activity.editText.text.toString() == "1" }; applied }
            ins.runOnMainSync { assertEquals(1, root.controller.combo); assertTrue(numericAward) }
            ins.runOnMainSync {
                activity.editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                activity.restartEditorInput(true)
            }
            await { var disabled = false; ins.runOnMainSync { disabled = !root.controller.enabled }; disabled }
            val events = mutableListOf<FeverEvent>()
            ins.runOnMainSync { root.controller.eventObserver = { events.add(it) } }
            var passwordKey: AccessibilityNodeInfo? = null
            await {
                passwordKey = nodes().firstOrNull { it.isVisibleToUser &&
                    (it.viewIdResourceName == "${context.packageName}:id/key_a" || it.viewIdResourceName == "${context.packageName}:id/key_1") }
                passwordKey != null
            }
            val passwordBounds = Rect().also(requireNotNull(passwordKey)::getBoundsInScreen)
            shell("input tap ${passwordBounds.centerX()} ${passwordBounds.centerY()}")
            await { var applied = false; ins.runOnMainSync { applied = activity.editText.text.isNotEmpty() }; applied }
            ins.runOnMainSync { assertEquals(0, root.controller.combo); assertTrue(events.isEmpty()) }
        } finally { ins.runOnMainSync { activity.finish() } }
    }
    @Test fun keyboardAndFullScreenKeepEditorTouchesAndStopWhenHidden() = verifyMatrix(false)
    @Test fun floatingKeyboardAndFullScreenKeepEditorTouchesAndStopWhenHidden() = verifyMatrix(true)

    private fun verifyMatrix(floating: Boolean) {
        check(context.packageName.startsWith("com.kazumaproject.skinfidelity"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.all.toMap()
        val original = shell("settings get secure default_input_method")
        val originalAnimations = shell("settings get global animator_duration_scale")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabled = target in shell("ime list -s").lines()
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            shell("settings put global animator_duration_scale 1")
            shell("ime enable $target"); shell("ime set $target")
            val baselineByOrientation = mutableMapOf<Boolean, Rect>()
            for (landscape in listOf(false, true)) for (full in listOf(false, true)) {
                assertTrue(ui.setRotation(if (landscape) android.app.UiAutomation.ROTATION_FREEZE_90 else android.app.UiAutomation.ROTATION_FREEZE_0))
                prefs.edit().putString("keyboard_skin_preference", "fever")
                    .putString("keyboard_order_preference", "[\"TENKEY\"]").putBoolean("save_last_used_keyboard", false)
                    .putBoolean("keyboard_floating_preference", floating)
                    .putInt("keyboard_floating_position_x", 24).putInt("keyboard_floating_position_y", 160)
                    .putInt("keyboard_width_preference", if (floating) 70 else 100)
                    .putInt("keyboard_width_landscape_preference", if (floating) 70 else 100)
                    .putBoolean(FeverSettings.FULL_SCREEN, full).putInt(FeverSettings.HIT_RATE, 100)
                    .putInt(FeverSettings.COMBO_SECONDS, 10).putBoolean("key_sound_preference", false).commit()
                println("FEVER case floating=$floating landscape=$landscape full=$full")
                val activity = startEditor(landscape)
                var recordingDone: java.util.concurrent.CountDownLatch? = null
                try {
                    println("FEVER activity ready")
                    key("key_1"); SystemClock.sleep(1000)
                    val root = ime()
                    assertCapBindings()
                    var jackpotSeen = false
                    val cuesSeen = mutableSetOf<FeverCue>()
                    val mode = "${if (floating) "floating" else "docked"}-${if (landscape) "landscape" else "portrait"}-${if (full) "screen" else "keyboard"}"
                    val captured = java.util.concurrent.CountDownLatch(1)
                    val captureFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
                    ins.runOnMainSync { root.controller.eventObserver = {
                        println("FEVER event combo=${it.combo} award=${it.award} cue=${it.cue} time=${SystemClock.uptimeMillis()}")
                        it.cue?.let(cuesSeen::add)
                        if (it.award == FeverAward.JACKPOT) {
                            jackpotSeen = true
                            Thread {
                                try { SystemClock.sleep(500); capture(mode) }
                                catch (error: Throwable) { captureFailure.set(error) }
                                finally { captured.countDown() }
                            }.start()
                        }
                    } }
                    val before = stableKeyboardBounds(floating)
                    ins.runOnMainSync {
                        val location = IntArray(2).also(root.rootView::getLocationOnScreen)
                        println("FEVER geometry mode=$mode keyboard=$before host=${root.rootView.width}x${root.rootView.height} at=${location.toList()}")
                    }
                    // Compare both modes at the same saved panel position, independent of auto-placement.
                    if (!full) baselineByOrientation[landscape] = before
                    else assertEquals("Screen effects must retain the keyboard-only geometry", baselineByOrientation[landscape], before)
                    var labelBefore: Bitmap? = null
                    for (i in 1..12) {
                        if (i == 1 && !floating && !landscape && full &&
                            InstrumentationRegistry.getArguments().getString("feverVideo") == "true") {
                            val recording = ui.executeShellCommand("screenrecord --time-limit 12 /sdcard/Download/fever-demo.mp4")
                            val done = java.util.concurrent.CountDownLatch(1)
                            recordingDone = done
                            Thread { try { ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() } } finally { done.countDown() } }.start()
                        }
                        tap(if (i % 2 == 0) "key_2" else "key_1")
                        await { var count = 0; ins.runOnMainSync { count = root.controller.combo }; count == i }
                        if (i == 9) labelBefore = keyLabelBitmap()
                        if (i == 10) {
                            SystemClock.sleep(350)
                            val labelDuring = keyLabelBitmap()
                            assertLabelLegible(requireNotNull(labelBefore), labelDuring, mode)
                            labelBefore?.recycle(); labelDuring.recycle()
                            capture("$mode-combo10")
                            assertTrue("Candidates must remain accessible during a celebration", nodes().any {
                                it.isVisibleToUser && it.viewIdResourceName == "${context.packageName}:id/suggestion_item_text_view" && !it.text.isNullOrBlank()
                            })
                        }
                    }
                    ins.runOnMainSync { assertEquals("One combo per real tap", 12, root.controller.combo) }
                    tap("key_delete")
                    ins.runOnMainSync {
                        assertEquals("Delete must not count", 12, root.controller.combo)
                        assertTrue(cuesSeen.contains(FeverCue.DELETE))
                    }
                    tap("key_soft_left"); tap("key_move_cursor_right")
                    ins.runOnMainSync {
                        assertTrue(cuesSeen.contains(FeverCue.CURSOR_LEFT))
                        assertTrue(cuesSeen.contains(FeverCue.CURSOR_RIGHT))
                    }
                    tap("key_space"); tap("key_space")
                    ins.runOnMainSync {
                        assertTrue("Conversion should have its own cue", cuesSeen.contains(FeverCue.CONVERSION))
                        assertTrue("Changing a candidate should have its own cue", cuesSeen.contains(FeverCue.CANDIDATE))
                        assertEquals(12, root.controller.combo)
                    }
                    // Let the ten-combo award end, then force a deterministic jackpot by confirming.
                    SystemClock.sleep(2100)
                    tap("key_enter")
                    ins.runOnMainSync { assertTrue("Successful confirmation must produce a jackpot at 100%", jackpotSeen) }
                    assertTrue("Capture a frame while the award is visible", captured.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    captureFailure.get()?.let { throw it }
                    val after = stableKeyboardBounds(floating)
                    assertEquals("Celebrations must not change key geometry", before, after)
                    var touches = 0
                    lateinit var editorBounds: Rect
                    ins.runOnMainSync {
                        assertTrue(activity.editText.text.isNotEmpty())
                        activity.editText.setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_DOWN) touches++; false }
                        val location = IntArray(2).also(activity.editText::getLocationOnScreen)
                        editorBounds = Rect(location[0], location[1], location[0] + activity.editText.width, location[1] + activity.editText.height)
                    }
                    var obstruction = Rect()
                    ins.runOnMainSync {
                        val point = IntArray(2).also(root::getLocationOnScreen)
                        obstruction = Rect(point[0], point[1], point[0] + root.width, point[1] + root.height)
                    }
                    if (floating) ins.runOnMainSync {
                        val panel = WindowInspector.getGlobalWindowViews().first {
                            it is com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard.FloatingKeyboardPanel
                        }
                        val point = IntArray(2).also(panel::getLocationOnScreen)
                        obstruction = Rect(point[0], point[1], point[0] + panel.width, point[1] + panel.height)
                    }
                    // The floating panel includes a drag handle and candidates above the key bounds.
                    // Choose an exposed part of the editor, outside that entire input window.
                    var safe = Rect()
                    ins.runOnMainSync {
                        val decor = activity.window.decorView
                        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor)?.getInsetsIgnoringVisibility(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
                        safe = Rect(insets?.left ?: 0, insets?.top ?: 0,
                            decor.width - (insets?.right ?: 0), decor.height - (insets?.bottom ?: 0))
                    }
                    val safeTop = maxOf(editorBounds.top, safe.top + 1)
                    val safeBottom = minOf(editorBounds.bottom, safe.bottom - 1)
                    val safeLeft = maxOf(editorBounds.left, safe.left + 1)
                    val safeRight = minOf(editorBounds.right, safe.right - 1)
                    val gaps = listOf(
                        Rect(safeLeft, safeTop, safeRight, minOf(safeBottom, obstruction.top)),
                        Rect(safeLeft, maxOf(safeTop, obstruction.bottom), safeRight, safeBottom),
                        Rect(safeLeft, safeTop, minOf(safeRight, obstruction.left), safeBottom),
                        Rect(maxOf(safeLeft, obstruction.right), safeTop, safeRight, safeBottom)
                    ).filter { !it.isEmpty }
                    val gap = gaps.maxByOrNull { it.width() * it.height() }
                        ?: error("No exposed editor region: editor=$editorBounds obstruction=$obstruction")
                    println("FEVER editor touch ($mode): $gap outside $obstruction")
                    shell("input tap ${gap.centerX()} ${gap.centerY()}")
                    ins.runOnMainSync { assertEquals("The app behind the effect must receive touch", 1, touches) }
                    ins.runOnMainSync { activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(activity.editText.windowToken, 0) }
                    await { var combo = -1; ins.runOnMainSync { combo = root.controller.combo }; combo == 0 }
                } finally {
                    recordingDone?.await(16, java.util.concurrent.TimeUnit.SECONDS)
                    ins.runOnMainSync { activity.finish() }
                }
            }
            if (!floating) {
                check(ui.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0))
                prefs.edit().putBoolean(FeverSettings.FULL_SCREEN, false).commit()
                verifyNumericAndPasswordEditors()
            }
        } finally {
            val edit = prefs.edit().clear()
            saved.forEach { (k,v) -> when(v) {
                is String -> edit.putString(k,v); is Boolean -> edit.putBoolean(k,v); is Int -> edit.putInt(k,v)
                is Long -> edit.putLong(k,v); is Float -> edit.putFloat(k,v)
                is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(k,v as Set<String>)
            } }; check(edit.commit())
            if (originalAnimations == "null") shell("settings delete global animator_duration_scale")
            else shell("settings put global animator_duration_scale $originalAnimations")
            shell("ime set $original"); if (!enabled) shell("ime disable $target")
            ui.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        }
    }
}
