package com.kazumaproject.markdownhelperkeyboard.keyboard_presentation

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.awaitSettingsContentReady
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Captures only running product screens; fixture settings are confined to an isolated app ID. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class KeyboardPreviewProductDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val runId = System.currentTimeMillis().toString()
    private val output by lazy { File(context.getExternalFilesDir(null), "keyboard-preview-product/$runId").apply { mkdirs() } }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 90_000
        while (SystemClock.uptimeMillis() < end) {
            var done = false
            ins.runOnMainSync { done = condition() }
            if (done) return
            SystemClock.sleep(100)
        }
        shot("failure")
        fail(message)
    }
    private fun shot(name: String) {
        SystemClock.sleep(250)
        val bitmap = requireNotNull(ins.uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun home(activity: MainActivity) = (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
        .childFragmentManager.primaryNavigationFragment!!
    private fun activePreview(activity: MainActivity, id: String): KeyboardPreviewView? {
        val pager = activity.findViewById<ViewPager2>(R.id.keyboard_preview_pager) ?: return null
        if (pager.scrollState != ViewPager2.SCROLL_STATE_IDLE) return null
        val recycler = pager.getChildAt(0) as androidx.recyclerview.widget.RecyclerView
        val holder = recycler.findViewHolderForAdapterPosition(pager.currentItem) ?: return null
        if (holder.bindingAdapter !== pager.adapter) return null
        return descendants(holder.itemView).filterIsInstance<KeyboardPreviewView>()
            .firstOrNull { it.target.id == id && it.ready && (it.nativeBody?.width ?: 0) > 0 }
    }
    private fun selectionState() = prefs.all.filterKeys {
        it.contains("last_used") || it.contains("last_input") || it.startsWith("split_keyboard_") || it == "keyboard_order_preference"
    }

    private fun gesture(view: View, horizontal: Boolean) {
        val rect = android.graphics.Rect()
        ins.runOnMainSync {
            assertTrue(view.getGlobalVisibleRect(rect))
            val origin = IntArray(2).also(view.rootView::getLocationOnScreen)
            rect.offset(origin[0], origin[1])
        }
        val start = SystemClock.uptimeMillis()
        val fromX = if (horizontal) rect.right - rect.width() / 5f else rect.exactCenterX()
        val toX = if (horizontal) rect.left + rect.width() / 5f else fromX
        val fromY = if (horizontal) rect.exactCenterY() else rect.bottom - rect.height() / 5f
        val toY = if (horizontal) fromY else rect.top + rect.height() / 5f
        for (step in 0..10) {
            val action = when (step) { 0 -> android.view.MotionEvent.ACTION_DOWN; 10 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
            val event = android.view.MotionEvent.obtain(start, start + step * 25, action,
                fromX + (toX - fromX) * step / 10f, fromY + (toY - fromY) * step / 10f, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(ins.uiAutomation.injectInputEvent(event, true)); event.recycle()
            SystemClock.sleep(25)
        }
    }
    private fun tap(view: View) {
        val rect = android.graphics.Rect()
        ins.runOnMainSync {
            assertTrue(view.getGlobalVisibleRect(rect))
            val origin = IntArray(2).also(view.rootView::getLocationOnScreen)
            rect.offset(origin[0], origin[1])
        }
        val time = SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            assertTrue(ins.uiAutomation.injectInputEvent(event, true)); event.recycle()
            SystemClock.sleep(50)
        }
    }

    private fun actualBody(preview: View): View? = WindowInspector.getGlobalWindowViews()
        .filter { (it.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_INPUT_METHOD && it.windowVisibility == View.VISIBLE }
        .flatMap { descendants(it).toList() }.firstOrNull { it.javaClass == preview.javaClass && it.isShown && it.width > 0 }

    private fun compareSplit(preview: KeyboardPreviewView, activity: MainActivity, name: String) {
        val frames = descendants(requireNotNull(preview.nativeSceneForTesting()))
            .filterIsInstance<com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingPanelFrame>().toList()
        val actual = WindowInspector.getGlobalWindowViews()
            .filterIsInstance<com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingPanelFrame>().filter { it.isShown && it.windowVisibility == View.VISIBLE }
        assertEquals("Preview split panes", 2, frames.size)
        assertEquals("Actual split panes", 2, actual.size)
        val area = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingWindowCoordinates(
            activity.getSystemService(WindowManager::class.java)).safeArea(activity.window.decorView)
        val top = actual.minOf { frame -> IntArray(2).also(frame::getLocationOnScreen)[1] }
        fun keyboard(root: View): View = descendants(root).first { it.isShown && (
            it is com.kazumaproject.tenkey.TenKey || it is com.kazumaproject.gojuon_keyboard.GojuonKeyboardView ||
            it is com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView || it is com.kazumaproject.custom_keyboard.view.FlickKeyboardView ||
            it is com.kazumaproject.morse_keyboard.MorseKeyboardView) }
        for (frame in frames) {
            val slotLabel = if (frame.contentDescription == frame.context.getString(R.string.split_keyboard_main))
                R.string.split_keyboard_main else R.string.split_keyboard_sub
            val real = actual.single { it.contentDescription == it.context.getString(slotLabel) }
            val location = IntArray(2).also(real::getLocationOnScreen)
            assertEquals("Pane width $name", real.width, frame.width)
            assertEquals("Pane height $name", real.height, frame.height)
            assertEquals("Pane horizontal position $name", (location[0] - area.left).toFloat(), frame.left.toFloat(), 1f)
            assertEquals("Pane vertical position $name", (location[1] - top).toFloat(), frame.top.toFloat(), 1f)
            compareBodies(keyboard(frame), keyboard(real), "$name-${frame.contentDescription}")
        }
    }

    private fun compareBodies(preview: View, ime: View, name: String) {
        try { compareBodiesInternal(preview, ime, name) }
        catch (error: AssertionError) {
            fun save(view: View, suffix: String) {
                File(output, "$name-$suffix-tree.txt").writeText(descendants(view).joinToString("\n") {
                    "${it.javaClass.simpleName} id=${it.id} bounds=${it.left},${it.top},${it.width},${it.height} " +
                        "padding=${it.paddingLeft},${it.paddingTop},${it.paddingRight},${it.paddingBottom} " +
                        "layoutRequested=${it.isLayoutRequested} density=${it.resources.displayMetrics.density} " +
                        "displayWidth=${it.resources.displayMetrics.widthPixels} configWidth=${it.resources.configuration.screenWidthDp} " +
                        "text=${(it as? TextView)?.text}"
                })
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(output, "$name-$suffix-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            save(ime, "ime"); save(preview, "preview")
            throw error
        }
    }

    private fun compareBodiesInternal(preview: View, ime: View, name: String) {
        assertEquals("Native keyboard width differs: $name", ime.width, preview.width)
        assertEquals("Native keyboard height differs: $name", ime.height, preview.height)
        fun render(view: View): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        val expected = render(ime)
        val actual = render(preview)
        fun leaves(root: View) = descendants(root).filter { it.isShown && it.width > 0 && (it is TextView || it is android.widget.ImageView) }.toList()
        val left = leaves(ime); val right = leaves(preview)
        assertEquals("Visible key count differs: $name", left.size, right.size)
        for ((a, b) in left.zip(right)) {
            assertEquals("Key class differs: $name", a.javaClass, b.javaClass)
            assertEquals("Key bounds differ: $name", listOf(a.left, a.top, a.width, a.height, a.paddingLeft, a.paddingTop, a.paddingRight, a.paddingBottom),
                listOf(b.left, b.top, b.width, b.height, b.paddingLeft, b.paddingTop, b.paddingRight, b.paddingBottom))
            if (a is TextView && b is TextView) {
                assertEquals("Key label differs: $name", a.text.toString(), b.text.toString())
                assertEquals("Key text size differs: $name", a.textSize, b.textSize, .01f)
                assertEquals("Key font weight differs: $name", a.typeface.weight, b.typeface.weight)
                assertEquals("Key text color differs: $name", a.currentTextColor, b.currentTextColor)
                fun styles(text: CharSequence): List<String> = if (text is android.text.Spanned) text.getSpans(0, text.length, android.text.style.StyleSpan::class.java)
                    .map { "${text.getSpanStart(it)}:${text.getSpanEnd(it)}:${it.style}" } else emptyList()
                assertEquals("Key text emphasis differs: $name", styles(a.text), styles(b.text))
            }
            if (a is android.widget.ImageView && b is android.widget.ImageView) {
                assertEquals("Icon transform differs: $name", a.imageMatrix, b.imageMatrix)
                val ad = a.drawable; val bd = b.drawable
                assertEquals("Icon renderer differs: $name", ad?.javaClass, bd?.javaClass)
                if (ad is com.kazumaproject.core.ui.font.KeyboardFontGlyphDrawable && bd is com.kazumaproject.core.ui.font.KeyboardFontGlyphDrawable) {
                    assertEquals("Icon resource differs: $name", ad.resourceId, bd.resourceId)
                    assertEquals("Icon font differs: $name", ad.usesCustomFont, bd.usesCustomFont)
                } else if (ad?.constantState != null && bd?.constantState != null) {
                    fun modeValue(view: View, field: String): Any? =
                        com.kazumaproject.core.ui.input_mode_witch.InputModeSwitch::class.java.getDeclaredField(field)
                            .apply { isAccessible = true }.get(view)
                    if (a is com.kazumaproject.core.ui.input_mode_witch.InputModeSwitch) {
                        for (field in listOf("currentInputMode", "isGojuonMode", "useThreeStateKeyboard", "numberReturnTarget"))
                            assertEquals("Mode icon state differs: $name $field", modeValue(a, field), modeValue(b, field))
                    }
                    fun icon(view: android.widget.ImageView): Bitmap {
                        val original = requireNotNull(view.drawable)
                        val clone = if (view is com.kazumaproject.core.ui.input_mode_witch.InputModeSwitch) {
                            val resource = com.kazumaproject.core.ui.input_mode_witch.resolveInputModeSwitchIconResId(
                                modeValue(view, "currentInputMode") as com.kazumaproject.core.domain.state.InputMode,
                                modeValue(view, "isGojuonMode") as Boolean, modeValue(view, "useThreeStateKeyboard") as Boolean,
                                modeValue(view, "numberReturnTarget") as com.kazumaproject.core.domain.state.TwoStateNumberReturnTarget)
                            view.resources.getXml(resource).use { android.graphics.drawable.Drawable.createFromXml(view.resources, it, view.context.theme) }
                        } else requireNotNull(original.constantState).newDrawable(view.resources, view.context.theme).mutate()
                        clone.state = original.state; clone.level = original.level; clone.alpha = original.alpha
                        clone.setTintList(view.imageTintList); clone.colorFilter = original.colorFilter
                        // A ConstantState clone can retain the source VectorDrawable's warm bitmap.
                        // Force a different cache size before comparing both icons at 96 px.
                        val primer = Bitmap.createBitmap(97, 97, Bitmap.Config.ARGB_8888)
                        clone.setBounds(0, 0, 97, 97); clone.draw(Canvas(primer)); primer.recycle()
                        return Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { bitmap -> clone.setBounds(0, 0, 96, 96); clone.draw(Canvas(bitmap)) }
                    }
                    val ai = icon(a); val bi = icon(b)
                    val identicalIcon = ai.sameAs(bi)
                    if (!identicalIcon) {
                        File(output, "$name-icon-${a.id}-ime.png").outputStream().use { ai.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        File(output, "$name-icon-${a.id}-preview.png").outputStream().use { bi.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    ai.recycle(); bi.recycle()
                    val iconName = runCatching { a.resources.getResourceEntryName(a.id) }.getOrDefault(a.contentDescription?.toString() ?: a.id.toString())
                    assertTrue("Canonical icon artwork differs: $name id=$iconName tint=${a.imageTintList}/${b.imageTintList} state=${ad.state.toList()}/${bd.state.toList()}", identicalIcon)
                }
            }
        }
        val pa = IntArray(expected.width * expected.height); val pb = IntArray(pa.size)
        expected.getPixels(pa, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(pb, 0, actual.width, 0, 0, actual.width, actual.height)
        var changed = 0; var error = 0L
        for (index in pa.indices) {
            if (pa[index] != pb[index]) changed++
            for (shift in listOf(0, 8, 16, 24)) error += kotlin.math.abs((pa[index] ushr shift and 255) - (pb[index] ushr shift and 255))
        }
        val changedFraction = changed.toDouble() / pa.size
        val meanError = error.toDouble() / (pa.size * 4.0 * 255.0)
        // Warm VectorDrawables rasterize at different cache densities in the scaled viewport.
        // Key geometry, typography and canonical icon artwork above must still match exactly.
        val equal = changedFraction <= .005 && meanError <= .001
        File(output, "$name-comparison.json").writeText(org.json.JSONObject().put("changedPixels", changed)
            .put("changedFraction", changedFraction).put("meanChannelError", meanError).put("keyGeometryAndArtworkMatch", true).toString())
        if (!equal) {
            fun metadata(view: View) = descendants(view).joinToString("\n") { item ->
                "${item.javaClass.simpleName} id=${item.id} bounds=${item.left},${item.top},${item.width},${item.height} " +
                    "padding=${item.paddingLeft},${item.paddingTop},${item.paddingRight},${item.paddingBottom} " +
                    (if (item is TextView) "text=${item.text} size=${item.textSize}" else "") +
                    (if (item is android.widget.ImageView) "matrix=${item.imageMatrix} tint=${item.imageTintList}" else "")
            }
            File(output, "$name-ime-metadata.txt").writeText(metadata(ime))
            File(output, "$name-preview-metadata.txt").writeText(metadata(preview))
            File(output, "$name-ime-body.png").outputStream().use { expected.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(output, "$name-preview-body.png").outputStream().use { actual.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        expected.recycle(); actual.recycle()
        assertTrue("Preview key rendering differs from the real IME: $name", equal)
    }

    @Test fun settingsPreviewsUseTheCurrentLayoutAndRealConversionCandidates() {
        assumeTrue(context.packageName.endsWith(".settingsreview"))
        val saved = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        try {
            AppPreference.init(context)
            AppPreference.setting_use_new_home_screen_preference = true
            prefs.edit().putString("keyboard_order_preference", "[\"TENKEY\",\"QWERTY\"]").commit()
            AppPreference.save_last_used_keyboard_enable_preference = true
            AppPreference.save_last_used_keyboard_position_preference = 1
            AppPreference.keyboardSkin = KeyboardSkinId.DEFAULT
            shell("ime enable $ime"); shell("ime set $ime")
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                var activity: MainActivity? = null
                await("Portrait settings did not load") {
                    activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                    activity?.resources?.configuration?.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT &&
                        activity?.let { activePreview(it, "TENKEY") } != null
                }
                fun nav() = (activity!!.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment).navController
                ins.runOnMainSync {
                    descendants(activity!!.window.decorView).filterIsInstance<com.google.android.material.button.MaterialButton>()
                        .first { it.text.toString() == activity!!.getString(R.string.keyboard_preview_try) }.performClick()
                }
                var keyboard: View? = null
                await("The trial editor did not receive focus") { WindowInspector.getGlobalWindowViews().any {
                    it.findViewById<View>(R.id.keyboard_trial_input)?.hasFocus() == true
                } }
                await("The real typing keyboard did not open") {
                    activePreview(activity!!, "TENKEY")?.nativeBody?.let { keyboard = actualBody(it) }
                    keyboard != null
                }
                SystemClock.sleep(400)
                var key: View? = null
                ins.runOnMainSync { key = descendants(requireNotNull(keyboard)).filterIsInstance<TextView>().first { it.text.toString() == "あ" } }
                tap(requireNotNull(key))
                await("Real conversion did not produce candidates") {
                    KeyboardTrialSession.candidates.value?.let { it.input == "あ" && it.candidates.isNotEmpty() } == true
                }
                val converted = requireNotNull(KeyboardTrialSession.candidates.value).candidates
                ins.runOnMainSync {
                    (home(activity!!).childFragmentManager.findFragmentByTag("keyboard-trial") as KeyboardTrialDialog).dismiss()
                    nav().navigate(R.id.candidateViewHeightSettingFragment)
                }
                await("Candidate settings did not display the actual trial result") {
                    val adapter = activity!!.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.candidate_height_setting_recyclerview)?.adapter
                        as? com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting.SuggestionAdapter2
                    adapter?.suggestions == converted && descendants(activity!!.window.decorView).filterIsInstance<KeyboardPreviewView>()
                        .any { it.ready && it.nativeBody is com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView }
                }
                shot("candidate-height-with-real-conversion")
                ins.runOnMainSync { assertTrue(nav().popBackStack()); nav().navigate(R.id.keyboardSkinSelectionFragment) }
                for (position in KeyboardSkinId.entries.indices) {
                    ins.runOnMainSync { activity!!.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.keyboard_theme_list).scrollToPosition(position) }
                    await("The theme picker did not render the selected QWERTY layout") {
                        val list = activity!!.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.keyboard_theme_list)
                        val holder = list?.findViewHolderForAdapterPosition(position)
                        holder != null && descendants(holder.itemView).filterIsInstance<KeyboardPreviewView>().any {
                            it.ready && (it.nativeBody?.width ?: 0) > 0 && it.target.type == KeyboardType.QWERTY &&
                                it.nativeBody is com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
                        }
                    }
                    ins.runOnMainSync {
                        val skin = KeyboardSkinId.entries[position]
                        val palette = com.kazumaproject.core.ui.skin.KeyboardSkinRegistry.find(skin)?.palette
                        if (palette != null) {
                            val holder = activity!!.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.keyboard_theme_list)
                                .findViewHolderForAdapterPosition(position)!!
                            val letter = descendants(holder.itemView).filterIsInstance<TextView>()
                                .first { it.text.toString().equals("q", ignoreCase = true) }
                            assertEquals("Theme picker must apply the offered skin palette: $skin", palette.text, letter.currentTextColor)
                        }
                    }
                    shot("theme-picker-${KeyboardSkinId.entries[position].name.lowercase()}")
                }
                ins.runOnMainSync {
                    assertEquals("Previewing themes changed the selected theme", KeyboardSkinId.DEFAULT, AppPreference.keyboardSkin)
                    assertEquals("Trial changed the remembered layout", 1, AppPreference.save_last_used_keyboard_position_preference)
                    assertTrue(nav().popBackStack())
                }
                File(output, "completed.json").writeText(org.json.JSONObject().put("surfaceScreens", true).put("runId", runId).toString())
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            prefs.edit().clear().apply {
                saved.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value); is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value); is Long -> putLong(key, value); is Float -> putFloat(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                } }
            }.commit()
        }
    }

    @Test fun selectedLayoutsRemainCompactAndTrialDoesNotChangeRememberedSelection() {
        assumeTrue(context.packageName.endsWith(".settingsreview"))
        val saved = prefs.all.toMap()
        val oldIme = shell("settings get secure default_input_method")
        val ime = "${context.packageName}/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        val created = mutableListOf<Long>()
        val repo = EntryPointAccessors.fromApplication(context, KeyboardPreviewDependencies::class.java).keyboards()
        try {
            runBlocking {
                for ((name, style) in listOf("かな・フリック" to "default", "かな・二段フリック" to "second-flick")) {
                    created += repo.saveLayout(KeyboardDefaultLayouts.createFinalLayout(KeyboardInputMode.HIRAGANA,
                        emptyMap(), "flick", style), name, null)
                }
            }
            prefs.edit().putBoolean("setting_use_new_home_screen_preference", true)
                .putString("keyboard_order_preference", "[\"TENKEY\",\"SUMIRE\",\"QWERTY\",\"CUSTOM\",\"GOJUON\",\"ROMAJI\",\"SPLIT\",\"MORSE\"]")
                .putBoolean("save_last_used_keyboard", true).putInt("save_last_used_keyboard_position_preference", 2)
                .putBoolean("clipboard_preview_enable_preference", false).putBoolean("zero_query_suggestion_preference", false).commit()
            AppPreference.init(context)
            AppPreference.save_last_used_keyboard_enable_preference = true
            AppPreference.save_last_used_keyboard_position_preference = 2
            AppPreference.remember_last_custom_keyboard_preference = true
            shell("ime enable $ime"); shell("ime set $ime")
            val layouts = runBlocking { repo.getLayoutsNotFlow() }
            val splitFixture = InstrumentationRegistry.getArguments().getString("split_fixture")
            if (splitFixture == "mixed") {
                val split = com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardSettings(prefs)
                val main = com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitSlot.MAIN
                val sub = com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitSlot.SUB
                split.saveSelection(main, com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardSelection(KeyboardType.QWERTY))
                split.saveSelection(sub, com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardSelection(KeyboardType.CUSTOM, layouts.last().stableId))
                for (landscape in listOf(false, true)) {
                    split.savePlacement(main, landscape, com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitPlacement(0f, 1f, if (landscape) 340f else 175f, 210f))
                    split.savePlacement(sub, landscape, com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitPlacement(1f, 1f, if (landscape) 280f else 180f, 240f))
                }
                prefs.edit().putString(com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard.SplitKeyboardSettings.CANDIDATES, "MAIN").commit()
            }
            val targets = keyboardPreviewTargets(AppPreference.keyboard_order, layouts)
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.awaitSettingsContentReady()
                var activity: MainActivity? = null
                scenario.onActivity { activity = it }
                await("Home carousel did not load") { activePreview(activity!!, targets.first().id) != null }
                val matrix = InstrumentationRegistry.getArguments().getString("matrix") == "true"
                val skins = if (matrix) KeyboardSkinId.entries.toList() else listOf(KeyboardSkinId.DEFAULT)
                val orientations = (if (matrix) listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    else listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)).filter { orientation ->
                    when (InstrumentationRegistry.getArguments().getString("orientation")) {
                        "landscape" -> orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        "portrait" -> orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else -> true
                    }
                }
                val baseline = selectionState()
                if (!matrix && InstrumentationRegistry.getArguments().getString("only_type") == null) {
                    val first = requireNotNull(activePreview(activity!!, targets.first().id))
                    gesture(first, true)
                    await("Horizontal swipe did not browse layouts") { activity!!.findViewById<ViewPager2>(R.id.keyboard_preview_pager).let {
                        it.currentItem == 1 && it.scrollState == ViewPager2.SCROLL_STATE_IDLE
                    } }
                    ins.runOnMainSync { activity!!.findViewById<ViewPager2>(R.id.keyboard_preview_pager).setCurrentItem(0, false) }
                    await("First layout was not restored") { activePreview(activity!!, targets.first().id) != null }
                    gesture(requireNotNull(activePreview(activity!!, targets.first().id)), false)
                    await("Vertical scroll was intercepted by preview") { descendants(activity!!.window.decorView).filterIsInstance<androidx.core.widget.NestedScrollView>().any { it.scrollY > 0 } }
                    ins.runOnMainSync { descendants(activity!!.window.decorView).filterIsInstance<androidx.core.widget.NestedScrollView>().forEach { it.scrollTo(0, 0) } }
                    assertEquals("Browsing changed selected keyboards", baseline, selectionState())
                }
                var lastVisitedId = targets.first().id
                val onlyType = InstrumentationRegistry.getArguments().getString("only_type")
                for (orientation in orientations) {
                    ins.runOnMainSync { AppPreference.keyboardSkin = KeyboardSkinId.DEFAULT }
                    scenario.onActivity { it.requestedOrientation = orientation }
                    await("Settings did not finish rotating") {
                        activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                        activity?.let { it.isSettingsContentReady &&
                            it.resources.configuration.orientation == (if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                                android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT) &&
                            it.findViewById<ViewPager2>(R.id.keyboard_preview_pager) != null } == true
                    }
                    for (skin in skins) {
                        ins.runOnMainSync {
                            AppPreference.keyboardSkin = skin
                            (activity!!.findViewById<ViewGroup>(R.id.setting_home_preview).getChildAt(0) as KeyboardPreviewCarousel).reload()
                        }
                        var cardHeight: Int? = null
                        val prefix = (if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) "landscape" else "portrait") + "-${skin.name.lowercase()}"
                for ((index, target) in targets.withIndex()) {
                    if (onlyType != null && target.type.name != onlyType) continue
                    lastVisitedId = target.id
                    ins.runOnMainSync { activity!!.findViewById<ViewPager2>(R.id.keyboard_preview_pager).setCurrentItem(index, false) }
                    await("Missing native preview ${target.id}") { activePreview(activity!!, target.id) != null }
                    ins.runOnMainSync { val a = activity!!
                        val height = a.findViewById<View>(R.id.setting_home_preview).height
                        if (cardHeight == null) cardHeight = height else assertEquals("Carousel height changed", cardHeight, height)
                        assertEquals(baseline, selectionState())
                        if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                            activePreview(a, target.id)?.let { it.requestRectangleOnScreen(android.graphics.Rect(0, 0, it.width, it.height), true) }
                        }
                    }
                    shot("${prefix}-home-${index + 1}-${target.type.name.lowercase()}")
                    if (!matrix && target.type == KeyboardType.SPLIT) {
                        ins.runOnMainSync { activePreview(activity!!, target.id)!!.performClick() }
                        await("Expanded preview did not load") { WindowInspector.getGlobalWindowViews().any {
                            it.findViewById<KeyboardPreviewView>(R.id.keyboard_preview_expanded)?.ready == true } }
                        shot("split-expanded")
                        ins.runOnMainSync { WindowInspector.getGlobalWindowViews().firstNotNullOf { it.findViewById<View>(R.id.keyboard_preview_expanded_close) }.performClick() }
                    }

                    if (index == 1 && !matrix) {
                        scenario.onActivity { a ->
                            val info = descendants(a.window.decorView).filterIsInstance<com.google.android.material.button.MaterialButton>()
                                .first { it.contentDescription?.toString()?.contains(target.name(a, layouts)) == true }
                            info.performClick()
                        }
                        SystemClock.sleep(600); shot("home-layout-details")
                        ins.runOnMainSync { WindowInspector.getGlobalWindowViews().filter { (it.layoutParams as? WindowManager.LayoutParams)?.type != WindowManager.LayoutParams.TYPE_INPUT_METHOD }
                            .filter { it !== activity!!.window.decorView }.forEach { root -> root.findViewById<View>(com.google.android.material.R.id.touch_outside)?.performClick() } }
                        SystemClock.sleep(300)
                    }
                    var previewBody: View? = null
                    await("Visible preview was replaced while loading: ${target.id}") {
                        previewBody = activePreview(activity!!, target.id)?.nativeBody
                        previewBody != null
                    }
                    ins.runOnMainSync {
                        val button = descendants(activity!!.window.decorView).filterIsInstance<com.google.android.material.button.MaterialButton>()
                            .first { it.text.toString() == activity!!.getString(R.string.keyboard_preview_try) }
                        button.performClick()
                    }
                    await("Trial editor did not open") { WindowInspector.getGlobalWindowViews().any { it.findViewById<View>(R.id.keyboard_trial_input)?.hasFocus() == true } }
                    await("Real IME did not open for ${target.id}") { WindowInspector.getGlobalWindowViews().any {
                        (it.layoutParams as? WindowManager.LayoutParams)?.type == WindowManager.LayoutParams.TYPE_INPUT_METHOD && it.isShown && it.windowVisibility == View.VISIBLE
                    } }
                    SystemClock.sleep(1200)
                    if (target.type == KeyboardType.SPLIT) {
                        await("Split windows did not open") { WindowInspector.getGlobalWindowViews()
                            .filterIsInstance<com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.FloatingPanelFrame>().count { it.isShown } == 2 }
                        SystemClock.sleep(800)
                        shot("${prefix}-trial-${index + 1}-${target.type.name.lowercase()}")
                        ins.runOnMainSync { compareSplit(requireNotNull(activePreview(activity!!, target.id)), activity!!, prefix) }
                    } else {
                        await("Requested IME surface did not appear: ${target.id}") {
                            previewBody = activePreview(activity!!, target.id)?.nativeBody
                            previewBody?.let { actualBody(it) != null } == true
                        }
                        shot("${prefix}-trial-${index + 1}-${target.type.name.lowercase()}")
                        ins.runOnMainSync { compareBodies(requireNotNull(previewBody), requireNotNull(actualBody(previewBody!!)), "${prefix}-${index + 1}-${target.type}") }
                    }
                    if (!matrix && onlyType == null && target.type == KeyboardType.TENKEY) {
                        var key: View? = null
                        ins.runOnMainSync { key = descendants(requireNotNull(actualBody(previewBody!!))).filterIsInstance<TextView>().first { it.text.toString() == "あ" } }
                        tap(requireNotNull(key))
                        await("Actual conversion candidates were not published") { KeyboardTrialSession.candidates.value?.let { it.input == "あ" && it.candidates.isNotEmpty() } == true }
                        shot("trial-with-real-conversion-candidates")
                    }
                    ins.runOnMainSync { (home(activity!!).childFragmentManager.findFragmentByTag("keyboard-trial") as KeyboardTrialDialog).dismiss() }
                    SystemClock.sleep(400)
                    assertEquals("Trial wrote remembered selection for ${target.id}", baseline, selectionState())
                }
                    }
                }
                ins.runOnMainSync { AppPreference.keyboardSkin = KeyboardSkinId.DEFAULT }
                scenario.recreate(); scenario.awaitSettingsContentReady()
                scenario.onActivity { activity = it }
                await("Restored page is missing") { activePreview(activity!!, lastVisitedId) != null }
                shot("home-restored-last-page")
                File(output, "completed.json").writeText(org.json.JSONObject().put("matrix", matrix).put("onlyType", onlyType).put("splitFixture", splitFixture).put("runId", runId).toString())
            }
        } finally {
            if (oldIme.isNotBlank() && oldIme != "null") shell("ime set $oldIme")
            runBlocking { created.forEach { repo.deleteLayout(it) } }
            prefs.edit().clear().apply {
                saved.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value); is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value); is Long -> putLong(key, value); is Float -> putFloat(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                } }
            }.commit()
        }
    }
}
