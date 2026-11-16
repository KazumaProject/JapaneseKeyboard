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
            ins.runOnMainSync {
                val id = if (floating) R.id.keyboard_view_floating else R.id.keyboard_view
                val view = WindowInspector.getGlobalWindowViews().filter {
                    (it is com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard.FloatingKeyboardPanel) == floating
                }.mapNotNull { it.findViewById<View>(id)?.takeIf(View::isShown) }.single()
                val point = IntArray(2).also(view::getLocationOnScreen)
                result = Rect(point[0], point[1], point[0] + view.width, point[1] + view.height)
            }
            stable = if (result == previous) stable + 1 else 0
            previous = result
            SystemClock.sleep(150)
            stable >= 3
        }
        return result
    }
    private fun nativeBounds(id: String): Rect? {
        var result: Rect? = null
        ins.runOnMainSync {
            val wanted = context.resources.getIdentifier(id, "id", context.packageName)
            fun find(view: View) {
                if (result != null || !view.isShown) return
                if (view.id == wanted && view.width > 0 && view.height > 0 &&
                    view.getGlobalVisibleRect(Rect())) {
                    val point = IntArray(2).also(view::getLocationOnScreen)
                    result = Rect(point[0], point[1], point[0] + view.width, point[1] + view.height)
                } else if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))
            }
            val floating = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("keyboard_floating_preference", false)
            WindowInspector.getGlobalWindowViews().filter {
                (it is com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard.FloatingKeyboardPanel) == floating
            }.forEach(::find)
        }
        return result
    }
    private fun tap(id: String) {
        var previous = Rect(); var stable = 0; var rect = Rect()
        await {
            val bounds = nativeBounds(id)
            if (bounds != null) {
                stable = if (bounds == previous && !bounds.isEmpty) stable + 1 else 0
                previous = bounds; rect = bounds
            } else stable = 0
            SystemClock.sleep(80)
            stable >= 2
        }
        val accessible = nodes().firstOrNull { it.isVisibleToUser &&
            it.viewIdResourceName == "${context.packageName}:id/$id" }?.let { Rect().also(it::getBoundsInScreen) }
        nativeBounds(id)?.let { rect = it }
        println("FEVER tap $id native=$rect accessibility=$accessible")
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                rect.centerX().toFloat(), rect.centerY().toFloat(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(ui.injectInputEvent(event, true)) } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(35)
        }
    }
    private fun keyLabelBitmap(): Bitmap {
        val rect = requireNotNull(nativeBounds("key_5"))
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
    private fun assertCapBindings(floating: Boolean) {
        ins.runOnMainSync {
            val ids = listOf("key_1", "key_5", "key_delete").map { context.resources.getIdentifier(it, "id", context.packageName) }
            val caps = mutableListOf<View>()
            fun visit(view: View) {
                if (view.isShown && view.id in ids) caps.add(view)
                if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
            WindowInspector.getGlobalWindowViews().filter {
                (it is com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard.FloatingKeyboardPanel) == floating
            }.forEach(::visit)
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

    @Test fun variedTypingMilestonesAndEditingKeepRealCandidatesUsable() {
        check(context.packageName.startsWith("com.kazumaproject.skinfidelity"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.all.toMap()
        val original = shell("settings get secure default_input_method")
        val scale = shell("settings get global animator_duration_scale")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabled = target in shell("ime list -s").lines()
        var activity: FastInputHostActivity? = null
        var recordingDone: java.util.concurrent.CountDownLatch? = null
        try {
            ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
            check(ui.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0))
            prefs.edit().putString("keyboard_skin_preference", "fever")
                .putString("keyboard_order_preference", "[\"TENKEY\"]").putBoolean("save_last_used_keyboard", false)
                .putBoolean("keyboard_floating_preference", false).putInt("keyboard_width_preference", 100)
                .putBoolean(FeverSettings.FULL_SCREEN, true).putInt(FeverSettings.INTENSITY, 2)
                .putInt(FeverSettings.HIT_RATE, 100).putInt(FeverSettings.COMBO_SECONDS, 10)
                .putBoolean("key_sound_preference", false).commit()
            shell("settings put global animator_duration_scale 0")
            shell("ime enable $target"); shell("ime set $target")
            activity = startEditor(false)
            key("key_1"); stableKeyboardBounds(false)
            val root = ime()
            val awards = mutableSetOf<FeverAward>()
            ins.runOnMainSync { root.controller.eventObserver = { it.award?.let(awards::add) } }
            if (InstrumentationRegistry.getArguments().getString("feverVideo") == "true") {
                val recording = ui.executeShellCommand("screenrecord --time-limit 45 /sdcard/Download/fever-variety.mp4")
                recordingDone = java.util.concurrent.CountDownLatch(1)
                val done = recordingDone
                Thread { try { ParcelFileDescriptor.AutoCloseInputStream(recording).use { it.readBytes() } } finally { done.countDown() } }.start()
                SystemClock.sleep(200)
            }
            fun touchKey(id: String, hold: Long = 25, flick: Boolean = false) {
                val cap = Rect().also(key(id)::getBoundsInScreen)
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, dx: Float = 0f) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                        cap.centerX() + dx, cap.centerY().toFloat(), 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    try { check(ui.injectInputEvent(event, true)) } finally { event.recycle() }
                }
                send(MotionEvent.ACTION_DOWN); SystemClock.sleep(hold)
                if (flick) { send(MotionEvent.ACTION_MOVE, cap.width() * .8f); SystemClock.sleep(180) }
                send(MotionEvent.ACTION_UP, if (flick) cap.width() * .8f else 0f)
            }
            for (count in 1..50) {
                touchKey(if (count % 2 == 0) "key_2" else "key_1")
                await { var combo = 0; ins.runOnMainSync { combo = root.controller.combo }; combo == count }
                if (count <= 5) { SystemClock.sleep(200); capture("variety-input-$count"); SystemClock.sleep(250) }
                else SystemClock.sleep(60)
                if (count in listOf(10, 25, 50)) { SystemClock.sleep(250); capture("variety-combo-$count"); SystemClock.sleep(1400) }
            }
            ins.runOnMainSync { assertTrue(awards.containsAll(listOf(FeverAward.COMBO, FeverAward.FEVER, FeverAward.HOT))) }
            touchKey("key_5", 650, true)
            touchKey("key_delete"); SystemClock.sleep(400)
            touchKey("key_soft_left"); SystemClock.sleep(400)
            touchKey("key_move_cursor_right"); SystemClock.sleep(400)
            touchKey("key_space"); SystemClock.sleep(400)
            touchKey("key_space"); SystemClock.sleep(400)
            assertTrue(nodes().any { it.isVisibleToUser && it.viewIdResourceName == "${context.packageName}:id/suggestion_item_text_view" && !it.text.isNullOrBlank() })
            touchKey("suggestion_item_text_view")
            ins.runOnMainSync { assertTrue(awards.contains(FeverAward.JACKPOT)); assertTrue(activity.editText.text.isNotEmpty()) }
            SystemClock.sleep(700); capture("variety-confirmed")
        } finally {
            recordingDone?.await(50, java.util.concurrent.TimeUnit.SECONDS)
            ins.runOnMainSync { activity?.finish() }
            restorePreferences(prefs, saved)
            if (scale == "null") shell("settings delete global animator_duration_scale") else shell("settings put global animator_duration_scale $scale")
            shell("ime set $original"); if (!enabled) shell("ime disable $target")
            ui.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        }
    }

    @Test(timeout = 180000) fun heldDeleteAnimatesEveryRepeatAndStopsOnReleaseAndCancel() {
        check(context.packageName.startsWith("com.kazumaproject.skinfidelity"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.all.toMap()
        val original = shell("settings get secure default_input_method")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabled = target in shell("ime list -s").lines()
        var activity: FastInputHostActivity? = null
        try {
            ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
            check(ui.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0))
            shell("ime enable $target"); shell("ime set $target")
            for (floating in listOf(false, true)) for (full in listOf(false, true)) {
                prefs.edit().putString("keyboard_skin_preference", "fever")
                    .putString("keyboard_order_preference", "[\"TENKEY\"]").putBoolean("save_last_used_keyboard", false)
                    .putBoolean("keyboard_floating_preference", floating)
                    .putInt("keyboard_width_preference", if (floating) 70 else 100)
                    .putInt("keyboard_floating_position_x", 24).putInt("keyboard_floating_position_y", 160)
                    .putInt("long_press_timeout_preference", 300)
                    .putBoolean(FeverSettings.FULL_SCREEN, full).putInt(FeverSettings.COMBO_SECONDS, 10)
                    .putBoolean("key_sound_preference", false).putBoolean(FeverSettings.VIBRATION, false).commit()
                activity = startEditor(false)
                key("key_1"); stableKeyboardBounds(floating)
                val host = activity
                val root = ime()
                val cues = java.util.concurrent.CopyOnWriteArrayList<FeverEvent>()
                ins.runOnMainSync { root.controller.eventObserver = { cues.add(it) } }
                // Real composing input, followed by a held key; no reflected service mutation.
                repeat(8) { tap(if (it % 2 == 0) "key_1" else "key_2") }
                var combo = 0
                ins.runOnMainSync { combo = root.controller.combo }
                assertEquals(8, combo)
                for (cancel in listOf(false, true)) {
                    if (cancel) {
                        ins.runOnMainSync {
                            host.editText.setText("あいうえお".repeat(20))
                            host.editText.setSelection(host.editText.length())
                            host.restartEditorInput(false)
                        }
                        SystemClock.sleep(700); stableKeyboardBounds(floating)
                    }
                    val cap = requireNotNull(nativeBounds("key_delete"))
                    val down = SystemClock.uptimeMillis()
                    fun send(action: Int) {
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                            cap.exactCenterX(), cap.exactCenterY(), 0)
                        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                        try { check(ui.injectInputEvent(event, true)) } finally { event.recycle() }
                    }
                    cues.clear()
                    var before = 0
                    ins.runOnMainSync { before = host.editText.length(); combo = root.controller.combo }
                    send(MotionEvent.ACTION_DOWN)
                    try {
                        SystemClock.sleep(750)
                        val first = requireNotNull(ui.takeScreenshot())
                        SystemClock.sleep(140)
                        val second = requireNotNull(ui.takeScreenshot())
                        val a = IntArray(first.width * first.height); val b = IntArray(a.size)
                        first.getPixels(a, 0, first.width, 0, 0, first.width, first.height)
                        second.getPixels(b, 0, second.width, 0, 0, second.width, second.height)
                        assertTrue("Repeat must animate actual frames", a.indices.count { a[it] != b[it] } > 50)
                        first.recycle(); second.recycle()
                        assertTrue("Repeated DELETE cues: floating=$floating full=$full cancel=$cancel $cues",
                            cues.count { it.cue == FeverCue.DELETE } >= 4)
                        ins.runOnMainSync {
                            assertTrue("Held delete must remove text", host.editText.length() < before)
                            assertEquals("Delete must not add combo", combo, root.controller.combo)
                        }
                    } finally { send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP) }
                    SystemClock.sleep(200)
                    val count = cues.count { it.cue == FeverCue.DELETE }
                    var remaining = 0
                    ins.runOnMainSync { remaining = host.editText.length() }
                    SystemClock.sleep(350)
                    assertEquals("Repeat cue leaked after release", count, cues.count { it.cue == FeverCue.DELETE })
                    ins.runOnMainSync { assertEquals("Deletion leaked after release", remaining, host.editText.length()) }
                    println("FEVER held delete PASS floating=$floating full=$full cancel=$cancel cues=$count")
                }
                ins.runOnMainSync { host.finish() }; activity = null
            }
        } finally {
            ins.runOnMainSync { activity?.finish() }
            restorePreferences(prefs, saved)
            shell("ime set $original"); if (!enabled) shell("ime disable $target")
            ui.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        }
    }

    @Test(timeout = 240000) fun sumirePopupsMovementAndCandidateButtonsStayVisible() {
        check(context.packageName.startsWith("com.kazumaproject.skinfidelity"))
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.all.toMap()
        val original = shell("settings get secure default_input_method")
        val target = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val enabled = target in shell("ime list -s").lines()
        var activity: FastInputHostActivity? = null
        fun field(value: Any, name: String): Any? = value.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(value)
        fun sumire(): com.kazumaproject.custom_keyboard.view.FlickKeyboardView? {
            var result: com.kazumaproject.custom_keyboard.view.FlickKeyboardView? = null
            fun visit(view: View) {
                if (!view.isShown) return
                if (view is com.kazumaproject.custom_keyboard.view.FlickKeyboardView && view.width > 0) result = view
                if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            val floating = prefs.getBoolean("keyboard_floating_preference", false)
            WindowInspector.getGlobalWindowViews().filter {
                (it is com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard.FloatingKeyboardPanel) == floating
            }.forEach(::visit)
            return result
        }
        try {
            ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
            check(ui.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0))
            shell("ime enable $target"); shell("ime set $target")
            for (floating in listOf(false, true)) for (full in listOf(false, true)) {
                prefs.edit().clear().putString("keyboard_skin_preference", "fever")
                    .putString("keyboard_order_preference", "[\"SUMIRE\"]").putBoolean("save_last_used_keyboard", false)
                    .putBoolean("gojuon_keyboard_type_migrated_v1", true)
                    .putString("sumire_input_method_preference", "flick").putString("sumire_keyboard_style_preference", "default")
                    .putBoolean("sumire_restore_input_mode_on_restart_preference", false)
                    .putBoolean("landscape_force_qwerty_preference", false)
                    .putBoolean("flick_editor_preview_preference", false).putBoolean("live_conversion_preference", false)
                    .putBoolean("keyboard_floating_preference", floating).putInt("keyboard_width_preference", if (floating) 70 else 100)
                    .putInt("keyboard_floating_position_x", 24).putInt("keyboard_floating_position_y", 160)
                    .putInt("long_press_timeout_preference", 300).putBoolean(FeverSettings.FULL_SCREEN, full)
                    .putBoolean(FeverSettings.SOUND, true).putInt(FeverSettings.VOLUME, 100).putBoolean("key_sound_preference", false)
                    .putBoolean(FeverSettings.VIBRATION, false).commit()
                activity = startEditor(false)
                await { var found = false; ins.runOnMainSync { found = sumire() != null }; found }
                SystemClock.sleep(700)
                val inputFrame = ime()
                for (label in listOf("あ", "わ")) for (hold in listOf(40L, 500L, 500L)) {
                    lateinit var keyView: android.widget.Button
                    lateinit var keyboard: com.kazumaproject.custom_keyboard.view.FlickKeyboardView
                    var x = 0f; var y = 0f; var keyWidth = 0
                    var previousBounds: Rect? = null
                    var stable = 0
                    await {
                        var bounds: Rect? = null
                        ins.runOnMainSync {
                        keyboard = requireNotNull(sumire())
                        val buttons = mutableListOf<android.widget.Button>()
                        fun visit(v: View) {
                            if (v is android.widget.Button && v.text.toString() == label) buttons.add(v)
                            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
                        }
                        visit(keyboard); keyView = buttons.first()
                        val position = IntArray(2).also(keyView::getLocationOnScreen)
                        keyWidth = keyView.width; x = position[0] + keyView.width / 2f; y = position[1] + keyView.height / 2f
                        bounds = Rect(position[0], position[1], position[0] + keyView.width, position[1] + keyView.height)
                        }
                        stable = if (bounds == previousBounds && bounds?.isEmpty == false) stable + 1 else 0
                        previousBounds = bounds
                        SystemClock.sleep(100)
                        stable >= 3
                    }
                    val down = SystemClock.uptimeMillis()
                    fun send(action: Int, dx: Float = 0f, dy: Float = 0f) {
                        val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x + dx, y + dy, 0)
                        e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                        try { assertTrue(ui.injectInputEvent(e, true)) } finally { e.recycle() }
                    }
                    send(MotionEvent.ACTION_DOWN)
                    lateinit var popup: View
                    try {
                        SystemClock.sleep(hold)
                        send(MotionEvent.ACTION_MOVE, -keyWidth * .8f)
                        SystemClock.sleep(25)
                        send(MotionEvent.ACTION_MOVE, 0f, -keyWidth * .8f)
                        ins.runOnMainSync {
                            val controllers = field(keyboard, "crossFlickControllers") as List<*>
                            val controller = controllers.filterNotNull().first { field(it, "anchorView") === keyView }
                            popup = if (hold < 100) requireNotNull(field(controller, "currentVisibleDirectionalPopup")) as View
                                else field(controller, "gridPopupView") as View
                            assertTrue("Popup must remain attached: $label $hold", popup.isAttachedToWindow)
                            val host = (field(controller, "popupOverlay") as Any).let { field(it, "host") as View }
                            assertTrue("Popup clipped: $label $hold ${popup.left},${popup.top}", popup.left >= 0 && popup.top >= 0 && popup.right <= host.width && popup.bottom <= host.height)
                            val trail = requireNotNull(field(popup, "fingerTrail"))
                            assertTrue("MOVE must reach the popup effect", (field(trail, "marks") as List<*>).isNotEmpty())
                        }
                        SystemClock.sleep(80)
                        val screen = requireNotNull(ui.takeScreenshot())
                        File(File(context.getExternalFilesDir(null), "fever-evidence"), "surface-$floating-$full-$label-$hold.png")
                            .outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        try { ins.runOnMainSync {
                            val rendered = Bitmap.createBitmap(popup.width, popup.height, Bitmap.Config.ARGB_8888)
                            try {
                                popup.draw(android.graphics.Canvas(rendered))
                                val location = IntArray(2).also(popup::getLocationOnScreen)
                                val cells = if (popup is ViewGroup) {
                                    val grid = (popup as ViewGroup).getChildAt(0) as ViewGroup
                                    (0 until grid.childCount).map(grid::getChildAt).filter { it.background != null }
                                } else listOf(popup)
                                assertTrue(cells.isNotEmpty())
                                cells.forEach { cell ->
                                    cell.background?.let { assertTrue("Overlay background must stay visible after MOVE", it.isVisible) }
                                    val cx = if (cell === popup) popup.width / 4 else cell.left + cell.width / 4
                                    val cy = if (cell === popup) popup.height / 4 else cell.top + cell.height / 4
                                    val expected = rendered.getPixel(cx, cy)
                                    assertEquals("Popup surface must be opaque", 255, android.graphics.Color.alpha(expected))
                                    val actual = screen.getPixel(location[0] + cx, location[1] + cy)
                                    for (shift in listOf(0, 8, 16)) assertTrue("Hardware popup surface differs: floating=$floating full=$full label=$label hold=$hold point=${location[0] + cx},${location[1] + cy} expected=${Integer.toHexString(expected)} actual=${Integer.toHexString(actual)}",
                                        kotlin.math.abs((expected shr shift and 255) - (actual shr shift and 255)) < 18)
                                }
                            } finally { rendered.recycle() }
                        } } finally { screen.recycle() }
                        capture("sumire-$floating-$full-$label-$hold")
                    } finally { send(if (hold == 500L) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP) }
                    ins.runOnMainSync {
                        assertFalse(popup.isAttachedToWindow); assertEquals(label, keyView.text.toString())
                        if (hold < 100) {
                            val feedback = requireNotNull(field(inputFrame.controller, "feedback"))
                            assertTrue("Dedicated sound must play in the real IME with ordinary key sounds OFF",
                                (field(feedback, "streams") as List<*>).any { it is Int && it > 0 })
                        }
                    }
                }
                // Commit a real Sumire tap to expose the expand/restore control.
                lateinit var kana: android.widget.Button
                ins.runOnMainSync {
                    fun find(v: View) {
                        if (v is android.widget.Button && v.text.toString() == "あ") kana = v
                        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))
                    }
                    find(requireNotNull(sumire()))
                    val now = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val e = MotionEvent.obtain(now, now + 30, action, kana.width / 2f, kana.height / 2f, 0)
                        try { kana.dispatchTouchEvent(e) } finally { e.recycle() }
                    }
                }
                await { nativeBounds("suggestion_visibility") != null }
                repeat(2) { expanded ->
                    ins.runOnMainSync {
                        val id = context.resources.getIdentifier("suggestion_visibility", "id", context.packageName)
                        val buttons = WindowInspector.getGlobalWindowViews().mapNotNull { it.findViewById<View>(id)?.takeIf(View::isShown) }
                        assertTrue(buttons.isNotEmpty())
                        buttons.forEach { button ->
                            assertEquals("Both candidate button states use the FEVER rim", "FeverKeyDrawable", button.background.javaClass.simpleName)
                            assertNull("Tint must not erase the neon", button.backgroundTintList)
                        }
                    }
                    capture("sumire-button-$floating-$full-$expanded")
                    tap("suggestion_visibility"); SystemClock.sleep(250)
                }
                println("FEVER Sumire PASS floating=$floating full=$full")
                ins.runOnMainSync { activity?.finish() }; activity = null
                SystemClock.sleep(300)
            }
        } finally {
            ins.runOnMainSync { activity?.finish() }
            restorePreferences(prefs, saved)
            shell("ime set $original"); if (!enabled) shell("ime disable $target")
            ui.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        }
    }

    private fun restorePreferences(prefs: android.content.SharedPreferences, saved: Map<String, *>) {
        val edit = prefs.edit().clear()
        saved.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value); is Float -> edit.putFloat(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
        } }
        check(edit.commit())
    }

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
            shell("settings put global animator_duration_scale 0")
            shell("ime enable $target"); shell("ime set $target")
            val baselineByOrientation = mutableMapOf<Boolean, Rect>()
            val orientations = if (InstrumentationRegistry.getArguments().getString("feverOrientation") == "landscape") listOf(true) else listOf(false, true)
            for (landscape in orientations) for (full in listOf(false, true)) {
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
                    assertCapBindings(floating)
                    var jackpotSeen = false
                    var lastCharacterAt = 0L
                    var recordedCombo = 0
                    val cuesSeen = mutableSetOf<FeverCue>()
                    val mode = "${if (floating) "floating" else "docked"}-${if (landscape) "landscape" else "portrait"}-${if (full) "screen" else "keyboard"}"
                    val captured = java.util.concurrent.CountDownLatch(1)
                    val captureFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
                    ins.runOnMainSync { root.controller.eventObserver = {
                        println("FEVER event combo=${it.combo} award=${it.award} cue=${it.cue} time=${SystemClock.uptimeMillis()}")
                        if (it.cue == null && it.combo > recordedCombo) {
                            recordedCombo = it.combo; lastCharacterAt = SystemClock.uptimeMillis()
                        }
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
                        if (i == 1) {
                            val cap = Rect().also(key("key_1")::getBoundsInScreen)
                            val down = SystemClock.uptimeMillis()
                            fun touch(action: Int, dx: Float = 0f) {
                                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                                    cap.centerX().toFloat() + dx, cap.centerY().toFloat(), 0)
                                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                                try { assertTrue(ui.injectInputEvent(event, true)) } finally { event.recycle() }
                            }
                            touch(MotionEvent.ACTION_DOWN); SystemClock.sleep(70)
                            ins.runOnMainSync { assertEquals("Down must not count a character", 0, root.controller.combo) }
                            touch(MotionEvent.ACTION_MOVE, 5f); SystemClock.sleep(40)
                            ins.runOnMainSync { assertEquals("Move must not count a character", 0, root.controller.combo) }
                            capture("$mode-move")
                            touch(MotionEvent.ACTION_UP, 5f)
                        } else tap(if (i % 2 == 0) "key_2" else "key_1")
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
                    fun assertEditingDoesNotCount() {
                        // Shell input can stall for seconds on an emulator under load. Editing must
                        // not extend the real combo deadline even when the test itself takes longer.
                        val expected = if (SystemClock.uptimeMillis() - lastCharacterAt >= root.controller.settings.comboTimeoutMillis) 0 else 12
                        assertEquals("Editing must not count or extend the deadline", expected, root.controller.combo)
                    }
                    tap("key_delete")
                    ins.runOnMainSync {
                        assertEditingDoesNotCount()
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
                        assertTrue("Showing candidates should have its own cue", cuesSeen.contains(FeverCue.CANDIDATES))
                        assertEditingDoesNotCount()
                    }
                    SystemClock.sleep(160); capture("$mode-candidate")
                    // Exercise both tapping the actual candidate and the enter/confirm key.
                    SystemClock.sleep(2100)
                    if (full) tap("suggestion_item_text_view") else tap("key_enter")
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
            restorePreferences(prefs, saved)
            if (originalAnimations == "null") shell("settings delete global animator_duration_scale")
            else shell("settings put global animator_duration_scale $originalAnimations")
            shell("ime set $original"); if (!enabled) shell("ime disable $target")
            ui.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        }
    }
}
