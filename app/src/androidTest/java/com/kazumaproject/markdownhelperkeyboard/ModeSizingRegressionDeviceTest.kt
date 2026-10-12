package com.kazumaproject.markdownhelperkeyboard

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ModeSizingRegressionDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(ctx)
    private var previous: Map<String, *> = emptyMap<String, Any>()
    private var oldIme = ""
    private var oldHardware = ""
    private val output get() = File(ctx.filesDir, "mode-sizing-regressions").apply { mkdirs() }
    private fun shell(cmd: String) = ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand(cmd)).bufferedReader().use { it.readText().trim() }
    private fun <T> main(action: () -> T): T { var result: Result<T>? = null; ins.runOnMainSync { result = runCatching(action) }; return result!!.getOrThrow() }
    private fun waitFor(label: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 25000
        do { if (main(condition)) return; SystemClock.sleep(100) } while (SystemClock.uptimeMillis() < end)
        capture("timeout-$label")
        record("timeout-diagnostics", main { roots().flatMap(::descendants).filter { it is com.kazumaproject.tenkey.TenKey || it is FlickKeyboardView }.joinToString("\n") { view -> val xy=IntArray(2); view.getLocationOnScreen(xy); val r=Rect(); view.getGlobalVisibleRect(r); "view=${view.javaClass.simpleName} id=${view.resources.getResourceEntryName(view.id)} shown=${view.isShown} attached=${view.isAttachedToWindow} root=${view.rootView.width}x${view.rootView.height} body=${view.width}x${view.height} screen=${xy.toList()} rect=$r" } })
        error("Timed out: $label")
    }
    private fun roots() = WindowInspector.getGlobalWindowViews()
    private fun descendants(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) } else emptyList()
    private fun shown(view: View): Boolean = view.isShown && view.getGlobalVisibleRect(Rect())
    private fun keyboardShown(view: View): Boolean = view.isShown && Rect().let { view.getGlobalVisibleRect(it) && it.height() > 100 && it.width() > 100 }
    private fun liveFlicks() = roots().flatMap(::descendants).filterIsInstance<FlickKeyboardView>().filter(::keyboardShown).distinct()
    private fun liveTenkeys() = roots().flatMap(::descendants).filterIsInstance<com.kazumaproject.tenkey.TenKey>().filter(::keyboardShown).distinct()
    private fun numberField(view: Any, name: String): Float = view.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(view).let { (it as Number).toFloat() }
    private fun metrics(key: TextView, character: String): Float {
        val text = key.text
        val index = text.toString().indexOf(character)
        check(index >= 0) { "No $character in $text" }
        val paint = TextPaint(key.paint)
        if (text is Spanned) text.getSpans(index, index + character.length, MetricAffectingSpan::class.java).forEach { it.updateMeasureState(paint) }
        return paint.textSize / key.resources.displayMetrics.scaledDensity
    }
    private fun record(name: String, text: String) { File(output, "$name.txt").writeText("API=${android.os.Build.VERSION.SDK_INT}\n$text\n"); android.util.Log.i("ModeSizingRegression", "$name $text") }
    private fun capture(name: String) {
        SystemClock.sleep(1200)
        ins.uiAutomation.takeScreenshot()?.let { bitmap -> File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
    private fun tap(view: View) {
        val rect = main { val xy = IntArray(2); view.getLocationOnScreen(xy); Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height) }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(ins.uiAutomation.injectInputEvent(event, true)); event.recycle(); SystemClock.sleep(60)
        }
        SystemClock.sleep(700)
    }
    @Before fun setup() {
        previous = prefs.all.toMap()
        oldIme = shell("settings get secure default_input_method")
        oldHardware = shell("settings get secure show_ime_with_hard_keyboard")
        shell("input keyevent WAKEUP"); shell("wm dismiss-keyguard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        prefs.edit().clear().putString("keyboard_order_preference", "[\"TENKEY\"]")
            .putBoolean("save_last_used_keyboard", false).putBoolean("keyboard_floating_preference", false)
            .putBoolean("live_conversion_preference", false).putBoolean("learn_dictionary_preference", false)
            .putBoolean("flick_input_only_preference", true).putBoolean("landscape_force_qwerty_preference", false)
            .putBoolean("landscape_force_qwerty_romaji_preference", false)
            .putBoolean("tenkey_kana_english_qwerty_preference", false).putBoolean("sumire_english_qwerty_preference", false)
            .putBoolean("tenkey_restore_input_mode_on_restart_preference", false).putBoolean("sumire_restore_input_mode_on_restart_preference", false)
            .putBoolean("tenkey_keymap_guide", false).putBoolean("tenkey_keymap_guide_number", false)
            .putString("sumire_input_method_preference", "toggle").putString("sumire_keyboard_style_preference", "default")
            .putInt("keyboard_height_preference", 280).putInt("keyboard_width_preference", 100)
            .putInt("keyboard_height_landscape_preference", 180).putInt("keyboard_width_landscape_preference", 100)
            .putFloat("key_letter_size_preference", 0f).putInt("flick_guide_text_size_sp_preference", 9).commit()
    }
    @After fun cleanup() {
        main { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() } }
        prefs.edit().clear().apply { previous.forEach { (key, value) -> when (value) {
            is Boolean -> putBoolean(key, value); is Int -> putInt(key, value); is Long -> putLong(key, value)
            is Float -> putFloat(key, value); is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        } } }.commit()
        if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
        if (oldHardware == "null") shell("settings delete secure show_ime_with_hard_keyboard") else shell("settings put secure show_ime_with_hard_keyboard $oldHardware")
    }
    private fun openSettings(tenkey: Boolean = true, custom: Boolean = false): MainActivity {
        val activity = ins.startActivitySync(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        waitFor("nav") { activity.findViewById<View>(R.id.nav_host_fragment_activity_main) != null }
        main { (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController.navigate(
            if (tenkey) R.id.tenKeyCandidateLetterSizeFragment else R.id.flickKeyboardSizeSettingsFragment,
            android.os.Bundle().apply { if (custom) putString("sizingSurface", "custom") }
        ) }
        waitFor("settings") { activity.findViewById<View>(R.id.sizingGuide) != null }
        return activity
    }
    private fun slider(activity: MainActivity, id: Int, value: Float) = main {
        val slider = activity.findViewById<Slider>(id)
        val direction = if (value == slider.valueFrom) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        slider.value = if (value == slider.valueFrom) value + 1 else value - 1
        slider.requestFocus()
        slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, direction)); slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, direction))
        assertEquals(value, slider.value, .01f)
    }
    private fun previewKey(activity: MainActivity, id: Int) = activity.findViewById<TextView>(id)
    private fun previewTop(activity: MainActivity) = main { val nav = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment; (nav.childFragmentManager.primaryNavigationFragment?.view as? androidx.core.widget.NestedScrollView)?.scrollTo(0, 0) }
    private fun openEditor(): FastInputHostActivity {
        main { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().forEach { it.finish() } }
        val ime = "${ctx.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        shell("ime enable $ime"); shell("ime set $ime")
        ctx.startActivity(Intent(ctx, FastInputHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var host: FastInputHostActivity? = null
        waitFor("editor") { host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<FastInputHostActivity>().firstOrNull(); host != null }
        waitFor("keyboard") { liveFlicks().isNotEmpty() || liveTenkeys().isNotEmpty() }
        SystemClock.sleep(1200)
        return host!!
    }
    @Test fun sumireGuideSurvivesInputViewStart() {
        prefs.edit().putString("keyboard_order_preference", "[\"SUMIRE\"]").putBoolean("sumire_keymap_guide_japanese", true).commit()
        val activity = openSettings(false)
        main { activity.findViewById<MaterialSwitch>(R.id.sizingGuidePreview).isChecked = true }
        slider(activity, R.id.sizingCenter, 24f); slider(activity, R.id.sizingGuide, 16f)
        val preview = main { numberField(activity.findViewById<FlickKeyboardView>(R.id.previewFlickKeyboardView), "flickGuideTextSizeSp") }
        previewTop(activity); capture("sumire-preview-guide16")
        val host = openEditor()
        waitFor("sumire-start") { liveFlicks().isNotEmpty() }
        var actual = main { numberField(liveFlicks().first(), "flickGuideTextSizeSp") }
        capture("sumire-live-guide16")
        main { host.restartEditorInput(true) }; SystemClock.sleep(1500)
        val restarted = main { numberField(liveFlicks().first(), "flickGuideTextSizeSp") }
        record("sumire-start-guide", "saved=${prefs.getFloat("sumire_hiragana_guide_text_size_sp", -1f)} preview=$preview actual=$actual after_restart=$restarted")
        assertEquals("The per-mode guide size must survive input-view initialization", 16f, actual, .01f)
        assertEquals(16f, restarted, .01f)
    }
    @Test fun guideOnlyEditPreviewsTheInheritedPunctuationCenter() {
        val activity = openSettings()
        slider(activity, R.id.sizingGuide, 16f)
        val preview = main { metrics(previewKey(activity, com.kazumaproject.tenkey.R.id.key_12), ",") }
        assertFalse(prefs.contains("tenkey_hiragana_key_text_size_sp"))
        previewTop(activity); capture("guide-only-preview")
        openEditor()
        val actual = main { metrics(liveTenkeys().first().findViewById(com.kazumaproject.tenkey.R.id.key_12), ",") }
        capture("guide-only-live")
        record("guide-only-preview-mismatch", "preview_comma_sp=$preview live_comma_sp=$actual guide_saved=${prefs.getFloat("tenkey_hiragana_guide_text_size_sp", -1f)} center_present=${prefs.contains("tenkey_hiragana_key_text_size_sp")}")
        assertEquals("Guide-only edit must preview the inherited center used by the IME", actual, preview, .1f)
    }
    @Test fun centerOnlyEditKeepsTheInheritedEllipsisGuide() {
        val host = openEditor()
        val before = main { metrics(liveTenkeys().first().findViewById(com.kazumaproject.tenkey.R.id.key_12), "…") }
        capture("ellipsis-before")
        main { host.finish() }
        val activity = openSettings()
        slider(activity, R.id.sizingCenter, 24f)
        assertFalse(prefs.contains("tenkey_hiragana_guide_text_size_sp"))
        openEditor()
        val after = main { metrics(liveTenkeys().first().findViewById(com.kazumaproject.tenkey.R.id.key_12), "…") }
        capture("ellipsis-after")
        record("ellipsis-center-only", "before=$before after=$after center_saved=${prefs.getFloat("tenkey_hiragana_key_text_size_sp", -1f)} guide_present=${prefs.contains("tenkey_hiragana_guide_text_size_sp")}")
        assertEquals("Changing only the center must retain the inherited ellipsis guide size", before, after, .1f)
    }
    @Test fun liveNumberBracketGuidesResizeWithTheGuideSlider() {
        prefs.edit().putBoolean("tenkey_keymap_guide_number", true).putBoolean("tenkey_use_three_state_keyboard_preference", true).commit()
        val observations = mutableListOf<String>()
        val bitmaps = mutableListOf<Bitmap>()
        for (size in listOf(6f, 16f)) {
            val activity = openSettings()
            main { activity.findViewById<MaterialButtonToggleGroup>(R.id.sizingModes).check(R.id.sizingNumbers) }
            slider(activity, R.id.sizingGuide, size)
            val host = openEditor()
            repeat(2) { tap(main { liveTenkeys().first().findViewById<View>(com.kazumaproject.tenkey.R.id.key_switch_key_mode) }); SystemClock.sleep(700) }
            waitFor("live-number") { liveTenkeys().first().currentInputMode.value == com.kazumaproject.core.domain.state.InputMode.ModeNumber }
            val keySize = main { metrics(liveTenkeys().first().findViewById(com.kazumaproject.tenkey.R.id.key_1), "☆") }
            observations.add("guide_saved=$size ordinary_star_guide=$keySize")
            val bitmap = main { Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { bitmap -> val drawable = liveTenkeys().first().findViewById<ImageView>(com.kazumaproject.tenkey.R.id.key_small_letter).drawable; drawable.setBounds(0, 0, 200, 200); drawable.draw(Canvas(bitmap)) } }
            File(output, "live-bracket-guide${size.toInt()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmaps.add(bitmap)
            capture("live-number-guide${size.toInt()}")
            main { host.finish() }; SystemClock.sleep(300)
        }
        val a = IntArray(40000); val b = IntArray(40000)
        bitmaps[0].getPixels(a, 0, 200, 0, 0, 200, 200); bitmaps[1].getPixels(b, 0, 200, 0, 0, 200, 200)
        val changed = a.indices.count { a[it] != b[it] }
        record("live-bracket", observations.joinToString("\n") + "\nbracket_changed_pixels=$changed")
        bitmaps.forEach { it.recycle() }
        assertTrue("Live bracket directional guides must resize alongside the other number guides", changed > 0)
    }
    @Test fun tenkeyPreviewRespectsConfiguredKeyboardHeight() {
        prefs.edit().putInt("keyboard_height_preference", 180).putBoolean("tenkey_keymap_guide", true).commit()
        val activity = openSettings()
        main { activity.findViewById<MaterialSwitch>(R.id.sizingGuidePreview).isChecked = true }
        slider(activity, R.id.sizingCenter, 20f); slider(activity, R.id.sizingGuide, 12f)
        val preview = main { activity.findViewById<View>(R.id.tenkeyLetterSizePreview).height / activity.resources.displayMetrics.density }
        val previewLines = main { val key = previewKey(activity, com.kazumaproject.tenkey.R.id.key_1); "layout=${key.layout.height} available=${key.height - key.compoundPaddingTop - key.compoundPaddingBottom}" }
        previewTop(activity); capture("height-preview")
        openEditor()
        val actual = main { liveTenkeys().first().height / ctx.resources.displayMetrics.density }
        val actualLines = main { val key = liveTenkeys().first().findViewById<TextView>(com.kazumaproject.tenkey.R.id.key_1); "layout=${key.layout.height} available=${key.height - key.compoundPaddingTop - key.compoundPaddingBottom}" }
        capture("height-live")
        record("tenkey-preview-height", "configured=180 preview_height_dp=$preview actual_height_dp=$actual preview_lines=[$previewLines] actual_lines=[$actualLines]")
        assertEquals("The size-settings preview should use the current keyboard body height", actual, preview, 1f)
    }
    @Test fun tappingUnchangedInheritedCenterDoesNotSilentlyChangeIt() {
        prefs.edit().putBoolean("tenkey_keymap_guide", true).commit()
        val activity = openSettings()
        main { activity.findViewById<MaterialSwitch>(R.id.sizingGuidePreview).isChecked = true }
        val slider = main { activity.findViewById<Slider>(R.id.sizingCenter) }
        main {
            val nav = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            val scroll = nav.childFragmentManager.primaryNavigationFragment?.view as androidx.core.widget.NestedScrollView
            val xy = IntArray(2); val sy=IntArray(2); slider.getLocationOnScreen(xy); scroll.getLocationOnScreen(sy)
            scroll.scrollTo(0, scroll.scrollY + xy[1] - sy[1] - scroll.height / 2)
        }
        SystemClock.sleep(400)
        val xy = main { IntArray(2).also(slider::getLocationOnScreen) }
        val x=xy[0]+main { slider.trackSidePadding + (slider.value - slider.valueFrom) / (slider.valueTo - slider.valueFrom) * (slider.width - 2 * slider.trackSidePadding) }; val y=xy[1]+slider.height/2f
        capture("inherited-thumb-before")
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,x,y,0); event.source=InputDevice.SOURCE_TOUCHSCREEN
            check(ins.uiAutomation.injectInputEvent(event,true)); event.recycle(); SystemClock.sleep(100)
        }
        SystemClock.sleep(500)
        val displayed=main { activity.findViewById<TextView>(R.id.sizingCenterValue).text.toString() }
        val preview=main { activity.findViewById<com.kazumaproject.tenkey.TenKey>(R.id.tenkeyLetterSizePreview).findViewById<TextView>(com.kazumaproject.tenkey.R.id.key_1).textSize/ctx.resources.displayMetrics.scaledDensity }
        val saved=prefs.all["tenkey_hiragana_key_text_size_sp"]
        capture("inherited-thumb-after")
        openEditor()
        val actual=main { liveTenkeys().first().findViewById<TextView>(com.kazumaproject.tenkey.R.id.key_1).textSize/ctx.resources.displayMetrics.scaledDensity }
        capture("inherited-thumb-live")
        record("inherited-thumb-save", "displayed=$displayed preview=$preview saved=$saved live=$actual")
        assertFalse("Tapping the unchanged inherited thumb must not silently store a different size", prefs.contains("tenkey_hiragana_key_text_size_sp"))
        assertEquals(11f, actual, .01f)
    }
    @Test fun draggingCenterSavesAndPreviewsTheTouchedSize() {
        val activity = openSettings()
        val slider = main { activity.findViewById<Slider>(R.id.sizingCenter) }
        main {
            val nav = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            val scroll = nav.childFragmentManager.primaryNavigationFragment?.view as androidx.core.widget.NestedScrollView
            val xy=IntArray(2); val sy=IntArray(2); slider.getLocationOnScreen(xy); scroll.getLocationOnScreen(sy)
            scroll.scrollTo(0, scroll.scrollY+xy[1]-sy[1]-scroll.height/2)
        }
        SystemClock.sleep(400)
        val xy=main { IntArray(2).also(slider::getLocationOnScreen) }
        fun x(value: Float) = main { xy[0]+slider.trackSidePadding+(value-slider.valueFrom)/(slider.valueTo-slider.valueFrom)*(slider.width-2*slider.trackSidePadding) }
        val start=x(main { slider.value }); val end=x(24f); val y=xy[1]+slider.height/2f
        val down=SystemClock.uptimeMillis()
        for (step in 0..8) {
            val action=when(step) {0->MotionEvent.ACTION_DOWN;8->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE}
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,start+(end-start)*step/8f,y,0)
            event.source=InputDevice.SOURCE_TOUCHSCREEN; check(ins.uiAutomation.injectInputEvent(event,true)); event.recycle(); SystemClock.sleep(60)
        }
        SystemClock.sleep(400)
        capture("center-drag-after")
        record("center-drag-touch", "slider=${main { slider.value }} saved=${prefs.all["tenkey_hiragana_key_text_size_sp"]}")
        val saved=prefs.getFloat("tenkey_hiragana_key_text_size_sp",-1f)
        val preview=main { previewKey(activity,com.kazumaproject.tenkey.R.id.key_1).textSize/ctx.resources.displayMetrics.scaledDensity }
        assertEquals(24f,saved,.1f); assertEquals(saved,preview,.01f)
        openEditor()
        val actual=main { liveTenkeys().first().findViewById<TextView>(com.kazumaproject.tenkey.R.id.key_1).textSize/ctx.resources.displayMetrics.scaledDensity }
        assertEquals(saved,actual,.01f)
        record("center-drag","saved=$saved preview=$preview actual=$actual")
    }

    @Test fun cancelledSliderTouchDoesNotSaveAnUnchangedCenter() {
        val activity = openSettings()
        main { activity.findViewById<MaterialSwitch>(R.id.sizingGuidePreview).isChecked = true }
        val slider = main { activity.findViewById<Slider>(R.id.sizingCenter) }
        main {
            val nav = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            val scroll = nav.childFragmentManager.primaryNavigationFragment?.view as androidx.core.widget.NestedScrollView
            val sliderLocation = IntArray(2); val scrollLocation = IntArray(2)
            slider.getLocationOnScreen(sliderLocation); scroll.getLocationOnScreen(scrollLocation)
            scroll.scrollTo(0, scroll.scrollY + sliderLocation[1] - scrollLocation[1] - scroll.height / 2)
        }
        SystemClock.sleep(400)
        val rect = main { Rect().also { check(slider.getGlobalVisibleRect(it)) } }
        val sidePadding = main { slider.trackSidePadding }
        val x = rect.left + sidePadding.toFloat()
        val y = rect.exactCenterY()
        val down = SystemClock.uptimeMillis()
        for (step in 0..10) {
            val action = when(step) { 0 -> MotionEvent.ACTION_DOWN; 10 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y - step * 25f, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(ins.uiAutomation.injectInputEvent(event, true)); event.recycle(); SystemClock.sleep(40)
        }
        SystemClock.sleep(400)
        capture("vertical-scroll")
        record("slider-cancel", "center_override=${prefs.all["tenkey_hiragana_key_text_size_sp"]}")
        assertFalse("Cancelling a slider touch should not create a center override", prefs.contains("tenkey_hiragana_key_text_size_sp"))
    }
    @Test(timeout = 600000) fun modeSizingAcrossSurfacesOrientationsAndBothSplitPanes() {
        val observations = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val modeNames = listOf("HIRAGANA", "ENGLISH", "SYMBOLS")
        val centers = listOf(18f, 22f, 26f)
        val guides = listOf(6f, 10f, 16f)
        for (keyboard in listOf("TENKEY", "SUMIRE")) for (surface in listOf("normal", "floating", "split")) for (landscape in listOf(false, true)) {
            main { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finish() } }
            SystemClock.sleep(300)
            prefs.edit()
                .putString("keyboard_order_preference", "[\"${if (surface == "split") "SPLIT" else keyboard}\"]")
                .putBoolean("keyboard_floating_preference", surface == "floating")
                .putString("split_keyboard_main_type", keyboard).putString("split_keyboard_sub_type", keyboard)
                .putBoolean("tenkey_keymap_guide", true).putBoolean("tenkey_keymap_guide_english", true).putBoolean("tenkey_keymap_guide_number", true)
                .putBoolean("sumire_keymap_guide_japanese", true).putBoolean("sumire_keymap_guide_english", true).putBoolean("sumire_keymap_guide_number", true)
                .putBoolean("tenkey_use_three_state_keyboard_preference", true)
                .putBoolean("tenkey_switch_number_to_qwerty_number_preference", false)
                .apply {
                    for (slot in listOf("main", "sub")) for (orientation in listOf("portrait", "landscape")) {
                        putFloat("split_keyboard_${slot}_${orientation}_width", if (orientation == "portrait") 170f else 320f)
                        putFloat("split_keyboard_${slot}_${orientation}_height", if (orientation == "portrait") 280f else 180f)
                        putFloat("split_keyboard_${slot}_${orientation}_x", if (slot == "main") 0f else 1f)
                        putFloat("split_keyboard_${slot}_${orientation}_y", 1f)
                    }
                    modeNames.forEachIndexed { index, mode ->
                        val prefix = "${keyboard.lowercase(java.util.Locale.ROOT)}_${mode.lowercase(java.util.Locale.ROOT)}"
                        putFloat("${prefix}_key_text_size_sp", centers[index]); putFloat("${prefix}_guide_text_size_sp", guides[index])
                    }
                }.commit()
            val host = openEditor()
            main { host.requestedOrientation = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            waitFor("orientation") { ctx.resources.configuration.orientation == if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT }
            fun candidates(): List<View> = (if (keyboard == "TENKEY") liveTenkeys() else liveFlicks()).filter { view -> (view.id == R.id.keyboard_view_floating || view.id == R.id.custom_layout_floating) == (surface != "normal") }
            fun targets(): List<View> = main { candidates().sortedBy { view -> IntArray(2).also(view::getLocationOnScreen)[0] } }
            val count = if (surface == "split") 2 else 1
            waitFor("$keyboard-$surface-mounted") { candidates().size == count }
            SystemClock.sleep(700)
            val expectedModes = IntArray(count)
            fun centerLabel(view: View): String {
                if (view is com.kazumaproject.tenkey.TenKey) {
                    val label = view.findViewById<TextView>(com.kazumaproject.tenkey.R.id.key_1).text as Spanned
                    val annotation = label.getSpans(0, label.length, android.text.Annotation::class.java).first { it.key == "tenkey" && it.value == "center" }
                    return label.subSequence(label.getSpanStart(annotation), label.getSpanEnd(annotation)).toString()
                }
                val texts = descendants(view).filterIsInstance<TextView>().map { it.text.toString() }
                return when { "あ" in texts -> "あ"; "1" in texts -> "1"; "ABC" in texts || "a" in texts -> "@"; else -> texts.joinToString("|") }
            }
            fun observe(stage: String) {
                targets().forEachIndexed { pane, view ->
                    val mode = expectedModes[pane]
                    val values = main {
                        if (view is com.kazumaproject.tenkey.TenKey) {
                            val key = view.findViewById<TextView>(com.kazumaproject.tenkey.R.id.key_1)
                            val text = key.text as Spanned
                            val span = text.getSpans(0, text.length, android.text.style.AbsoluteSizeSpan::class.java).firstOrNull()
                            key.textSize / view.resources.displayMetrics.scaledDensity to (span?.size?.div(view.resources.displayMetrics.scaledDensity) ?: -1f)
                        } else numberField(view, "defaultTextSize") to numberField(view, "flickGuideTextSizeSp")
                    }
                    val line = "$keyboard $surface ${if (landscape) "landscape" else "portrait"} $stage pane=$pane mode=${modeNames[mode]} center=${values.first}/${centers[mode]} guide=${values.second}/${guides[mode]}"
                    observations.add(line)
                    if (kotlin.math.abs(values.first - centers[mode]) > .5f || kotlin.math.abs(values.second - guides[mode]) > .5f) failures.add(line)
                }
            }
            for (pane in 0 until count) for (mode in 0..2) {
                if (mode > 0) {
                    val view = targets()[pane]
                    val modeButton = main {
                        if (view is com.kazumaproject.tenkey.TenKey) view.findViewById<View>(com.kazumaproject.tenkey.R.id.key_switch_key_mode)
                        else descendants(view).first { it.contentDescription?.toString() in listOf("モード", if (mode == 1) "ABC" else "123") }
                    }
                    tap(modeButton)
                    val expected = if (mode == 1) "@" else "1"
                    waitFor("$keyboard-$surface-pane$pane-mode$mode") {
                        val ordered = candidates().sortedBy { IntArray(2).also(it::getLocationOnScreen)[0] }
                        ordered.size == count && centerLabel(ordered[pane]) == expected
                    }
                    expectedModes[pane] = mode
                }
                observe("change-pane$pane-mode$mode")
            }
            record("matrix-progress", observations.joinToString("\n"))
            capture("matrix-$keyboard-$surface-${if (landscape) "landscape" else "portrait"}")
        }
        record("matrix-all", observations.joinToString("\n") + "\nFAILURES=${failures.size}\n" + failures.joinToString("\n"))
        assertTrue("Per-mode sizes or independent split-pane sizes failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test fun resetConfirmationScopeAndCustomNavigationRemainIndependent() {
        prefs.edit().putFloat("flick_key_text_size_sp_preference", 28f).putInt("flick_guide_text_size_sp_preference", 7)
            .putInt("flick_key_width_scale_percent_preference", 190).commit()
        for ((index, mode) in com.kazumaproject.custom_keyboard.data.KeyboardInputMode.entries.withIndex()) {
            com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference.setModeKeySize("sumire", mode, false, 20f + index * 2)
            com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference.setModeKeySize("sumire", mode, true, 10f + index)
        }
        val activity = openSettings(false)
        main { activity.findViewById<View>(R.id.sizingResetMode).performClick() }
        assertFalse(prefs.contains("sumire_hiragana_key_text_size_sp"))
        assertFalse(prefs.contains("sumire_hiragana_guide_text_size_sp"))
        assertEquals(22f, prefs.getFloat("sumire_english_key_text_size_sp", -1f), .01f)
        assertEquals(24f, prefs.getFloat("sumire_symbols_key_text_size_sp", -1f), .01f)
        assertEquals(190, prefs.getInt("flick_key_width_scale_percent_preference", -1))
        main { activity.findViewById<View>(R.id.sizingResetAll).performClick() }
        waitFor("reset-dialog") { roots().any { it.findViewById<View>(android.R.id.button2)?.let { button -> shown(button) && button.hasWindowFocus() } == true } }
        SystemClock.sleep(400)
        tap(main { roots().mapNotNull { it.findViewById<View>(android.R.id.button2) }.first { shown(it) && it.hasWindowFocus() } })
        waitFor("reset-dialog-dismissed") { roots().none { it.findViewById<View>(android.R.id.button2)?.let(::shown) == true } }
        assertEquals(190, prefs.getInt("flick_key_width_scale_percent_preference", -1))
        assertEquals(22f, prefs.getFloat("sumire_english_key_text_size_sp", -1f), .01f)
        main { activity.findViewById<View>(R.id.sizingResetAll).performClick() }
        waitFor("reset-dialog") { roots().any { it.findViewById<View>(android.R.id.button1)?.let { button -> shown(button) && button.hasWindowFocus() } == true } }
        SystemClock.sleep(400)
        tap(main { roots().mapNotNull { it.findViewById<View>(android.R.id.button1) }.first { shown(it) && it.hasWindowFocus() } })
        waitFor("reset-saved") { prefs.contains("sumire_hiragana_key_text_size_sp") }
        for (mode in listOf("hiragana", "english", "symbols")) {
            assertEquals(16f, prefs.getFloat("sumire_${mode}_key_text_size_sp", -1f), .01f)
            assertEquals(9f, prefs.getFloat("sumire_${mode}_guide_text_size_sp", -1f), .01f)
        }
        assertEquals(28f, prefs.getFloat("flick_key_text_size_sp_preference", -1f), .01f)
        assertEquals(7, prefs.getInt("flick_guide_text_size_sp_preference", -1))
        main { activity.finish() }
        val custom = openSettings(false, custom = true)
        assertEquals(View.GONE, main { custom.findViewById<View>(R.id.sizingModes).visibility })
        assertEquals(28f, main { custom.findViewById<Slider>(R.id.sizingCenter).value }, .01f)
        assertEquals(7f, main { custom.findViewById<Slider>(R.id.sizingGuide).value }, .01f)
        slider(custom, R.id.sizingCenter, 27f)
        assertEquals(27f, prefs.getFloat("flick_key_text_size_sp_preference", -1f), .01f)
        for (mode in listOf("hiragana", "english", "symbols")) assertEquals(16f, prefs.getFloat("sumire_${mode}_key_text_size_sp", -1f), .01f)
        record("reset-custom-scope", "per-mode-reset=passed reset-cancel=passed reset-all=passed shared-legacy-preserved=passed custom-route=passed custom-change-isolated=passed")
    }

    @Test fun viewingAndModeChangesNeverWriteModeSizeOverrides() {
        for (tenkey in listOf(true, false)) {
            val activity = openSettings(tenkey)
            for (id in listOf(R.id.sizingEnglish, R.id.sizingNumbers, R.id.sizingJapanese)) main {
                activity.findViewById<MaterialButtonToggleGroup>(R.id.sizingModes).check(id)
                val guides = activity.findViewById<MaterialSwitch>(R.id.sizingGuidePreview); guides.isChecked = !guides.isChecked
            }
            assertTrue(prefs.all.keys.none { it.matches(Regex("(tenkey|sumire)_(hiragana|english|symbols)_(key|guide)_text_size_sp")) })
            main { activity.finish() }
        }
        record("viewing-no-writes", "both-settings=passed mode-changes=passed guide-preview-toggle=passed")
    }

}
