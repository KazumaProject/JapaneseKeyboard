package com.kazumaproject.markdownhelperkeyboard

import android.content.res.Configuration
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.Rect
import android.util.TypedValue
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.AutoSizeButton
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SumireModeKeyTextSizeDeviceTest {
    @Test
    fun settingsSliderUpdatesVisibleModeLabels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Use investigation/custom-toggle.init.gradle", context.packageName.startsWith("com.kazumaproject.customtoggletest"))
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val preferenceKey = "flick_special_key_text_size_sp_preference"
        val previousSize = preferences.all[preferenceKey] as? Float
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            var navigated = false
            val deadline = SystemClock.uptimeMillis() + 15000
            while (!navigated && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
                    if (host?.navController?.currentDestination != null) {
                        host.navController.navigate(R.id.flickKeyboardSizeSettingsFragment)
                        navigated = true
                    }
                }
                if (!navigated) SystemClock.sleep(50)
            }
            assertTrue("Settings navigation must initialize", navigated)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val slider = activity.findViewById<SeekBar>(R.id.seekBarSpecialTextSize)
                val keyboard = activity.findViewById<FlickKeyboardView>(R.id.previewFlickKeyboardView)
                assertNotNull(slider)
                assertNotNull(keyboard)
                for (size in listOf(8f, 16f, 32f)) {
                    slider.progress = (size - 8f).toInt()
                    measure(keyboard, (1600 * keyboard.resources.displayMetrics.density).toInt(), 800)
                    val buttons = (0 until keyboard.childCount).mapNotNull { keyboard.getChildAt(it) as? AutoSizeButton }
                        .filter { it.text.toString() in labels.values }
                    assertEquals("Preview includes all three mode labels", 3, buttons.size)
                    buttons.forEach { assertEquals(sp(keyboard, size), it.textSize, 0.1f) }
                    assertEquals(size, preferences.getFloat(preferenceKey, -1f), 0f)
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            val editor = preferences.edit()
            if (previousSize == null) editor.remove(preferenceKey) else editor.putFloat(preferenceKey, previousSize)
            check(editor.commit())
        }
    }

    @Test
    fun previewAndGeneratedLayoutsUseSpecialTextSizingOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (night in listOf(false, true)) {
                val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(config),
                    com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
                val density = context.resources.displayMetrics.density
                val wideWidth = (1600 * density).toInt()
                val previewRoot = LayoutInflater.from(context).inflate(R.layout.fragment_flick_keyboard_size_settings, null)
                val keyboard = previewRoot.findViewById<FlickKeyboardView>(R.id.previewFlickKeyboardView)
                val layouts = listOf(KeyboardDefaultLayouts.defaultLayout()) +
                    listOf("toggle", "flick").flatMap { layoutType ->
                        KeyboardInputMode.entries.map { mode ->
                            KeyboardDefaultLayouts.createFinalLayout(mode, emptyMap(), layoutType, "default")
                        }
                    }
                for (customFont in listOf(false, true)) {
                    keyboard.setKeyboardFont(KeyboardFontSnapshot(
                        if (customFont) Typeface.MONOSPACE else null, if (customFont) 1 else 2))
                    for (size in listOf(8f, 16f, 32f)) {
                        keyboard.applyKeySizing(160, 160, 80, 16f, size)
                        layouts.forEachIndexed { index, layout ->
                            keyboard.setKeyboard(layout)
                            measure(keyboard, wideWidth, (280 * density).toInt())
                            for (item in layout.items) {
                                val key = (item as? com.kazumaproject.custom_keyboard.data.KeyItem)?.keyData ?: continue
                                val label = labels[key.drawableResId] ?: continue
                                if (!key.isSpecialKey) continue
                                val button = keyboard.getChildAt(layout.items.indexOf(item)) as AutoSizeButton
                                assertEquals(label, button.text.toString())
                                assertEquals(sp(keyboard, size), button.textSize, 0.1f)
                                assertEquals(label, button.contentDescription)
                                if (customFont) assertEquals(Typeface.create(Typeface.MONOSPACE, button.typeface.style), button.typeface)
                            }
                            if (size == 16f && index == 0) {
                                measure(keyboard, context.resources.displayMetrics.widthPixels, (280 * density).toInt())
                                saveRendering(keyboard, "preview-night-$night-font-$customFont")
                            }
                        }
                    }

                    keyboard.setKeyboard(KeyboardLayout(
                        keys = labels.entries.mapIndexed { index, entry ->
                            KeyData("mode", 0, index, false, KeyAction.ChangeInputMode,
                                drawableResId = entry.key, isSpecialKey = true)
                        } + KeyData("改行", 0, 3, false, KeyAction.NewLine, isSpecialKey = true),
                        flickKeyMaps = emptyMap(), columnCount = 4, rowCount = 1))
                    keyboard.applyKeySizing(160, 160, 80, 16f, 32f)
                    measure(keyboard, wideWidth, (80 * density).toInt())
                    val reference = (keyboard.getChildAt(3) as AutoSizeButton).textSize
                    repeat(3) { assertEquals(reference, (keyboard.getChildAt(it) as AutoSizeButton).textSize, 0.1f) }
                    measure(keyboard, (260 * density).toInt(), (80 * density).toInt())
                    var shrunk = false
                    repeat(3) {
                        val button = keyboard.getChildAt(it) as AutoSizeButton
                        assertEquals("Mode names must stay on one line", 1, button.lineCount)
                        assertTrue("Fitting must not enlarge text", button.textSize <= reference)
                        shrunk = shrunk || button.textSize < reference
                        val bounds = Rect()
                        button.paint.getTextBounds(button.text.toString(), 0, button.text.length, bounds)
                        assertTrue("$bounds must fit key ${button.width} with padding ${button.paddingLeft}/${button.paddingRight}",
                            bounds.width() <= button.width - button.paddingLeft - button.paddingRight)
                    }
                    assertTrue("At least one long mode label must shrink in the narrow layout", shrunk)
                    saveRendering(keyboard, "narrow-night-$night-font-$customFont")
                }
            }
        }
    }

    private val labels = linkedMapOf(
        com.kazumaproject.core.R.drawable.input_mode_english_custom to "ABC",
        com.kazumaproject.core.R.drawable.input_mode_number_select_custom to "123",
        com.kazumaproject.core.R.drawable.input_mode_japanese_select_custom to "あいう")

    private fun measure(view: View, width: Int, height: Int) {
        // Text fitting in onSizeChanged requests another layout before the next frame.
        fun forceLayout(target: View) {
            if (target is ViewGroup) repeat(target.childCount) { forceLayout(target.getChildAt(it)) }
            target.forceLayout()
        }
        repeat(2) {
            forceLayout(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
    }

    private fun sp(view: View, size: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, view.resources.displayMetrics)

    private fun saveRendering(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val keyboard = view as FlickKeyboardView
        repeat(keyboard.childCount) { index ->
            val button = keyboard.getChildAt(index) as? AutoSizeButton ?: return@repeat
            if (button.text.toString() !in labels.values) return@repeat
            var inkPixels = 0
            for (y in button.top until button.bottom) for (x in button.left until button.right) {
                if (bitmap.getPixel(x, y) == button.currentTextColor) inkPixels++
            }
            assertTrue("Mode label ${button.text} must have visible text pixels", inkPixels > 0)
        }
        val directory = File(view.context.getExternalFilesDir(null), "sumire-mode-key-size").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
