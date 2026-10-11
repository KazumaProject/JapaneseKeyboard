package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui

import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.KeyEvent
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardSizingControlsTest {
    private fun root(): View = LayoutInflater.from(ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard,
    )).inflate(R.layout.fragment_flick_keyboard_size_settings, null)

    @Test fun modeGuideAndProgrammaticChangesNeverSavePreferences() {
        val root = root()
        val writes = mutableListOf<Triple<KeyboardInputMode, SizingField, Float>>()
        var renderedMode = KeyboardInputMode.HIRAGANA
        var guides = false
        val controls = KeyboardSizingControls(root, null, false,
            read = { mode, _ -> KeyboardSizingValues(if (mode == KeyboardInputMode.ENGLISH) 24f else 16f,
                9f, 160f, 160f) },
            save = { mode, field, value -> writes.add(Triple(mode, field, value)) },
            render = { mode, show, _ -> renderedMode = mode; guides = show },
            reset = {}, inherited = { true },
        )
        root.findViewById<MaterialButtonToggleGroup>(R.id.sizingModes).check(R.id.sizingEnglish)
        root.findViewById<MaterialSwitch>(R.id.sizingGuidePreview).isChecked = true
        val slider = root.findViewById<Slider>(R.id.sizingCenter)
        assertEquals(24f, slider.value, 0f)
        slider.value = 18f
        assertTrue(writes.isEmpty())
        assertEquals(KeyboardInputMode.ENGLISH, renderedMode)
        assertTrue(guides)
        val state = Bundle()
        controls.saveState(state)
        assertEquals("ENGLISH", state.getString("sizingMode"))
        assertTrue(state.getBoolean("sizingGuides"))
        val restored = root()
        KeyboardSizingControls(restored, state, false,
            read = { _, _ -> KeyboardSizingValues(24f, 9f, 160f, 160f) },
            save = { mode, field, value -> writes.add(Triple(mode, field, value)) },
            render = { mode, show, _ -> renderedMode = mode; guides = show },
            reset = {}, inherited = { true },
        )
        assertEquals(R.id.sizingEnglish,
            restored.findViewById<MaterialButtonToggleGroup>(R.id.sizingModes).checkedButtonId)
        assertTrue(restored.findViewById<MaterialSwitch>(R.id.sizingGuidePreview).isChecked)
        assertTrue(writes.isEmpty())
    }

    @Test fun layoutsSupportLocalesNightModeLargeFontsAndLandscape() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        for (locale in listOf("ja", "en")) for (night in listOf(false, true)) {
            val configuration = android.content.res.Configuration(application.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(locale))
                fontScale = 1.4f
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES
                    else android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            val context = ContextThemeWrapper(application.createConfigurationContext(configuration),
                R.style.Theme_MarkdownKeyboard)
            for (layout in listOf(R.layout.fragment_tenkey_candidate_letter_size,
                R.layout.fragment_flick_keyboard_size_settings)) {
                val root = LayoutInflater.from(context).inflate(layout, null) as androidx.core.widget.NestedScrollView
                KeyboardSizingControls(root, null, layout == R.layout.fragment_tenkey_candidate_letter_size,
                    read = { _, _ -> KeyboardSizingValues(16f, 9f, 100f, 100f) },
                    save = { _, _, _ -> fail("Inflation must not save") }, render = { _, _, _ -> },
                    reset = {}, inherited = { true },
                )
                val density = context.resources.displayMetrics.density
                root.measure(View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertTrue(root.getChildAt(0).height > root.height)
                assertTrue(root.findViewById<Slider>(R.id.sizingGuide).width > 0)
                assertEquals(context.getString(R.string.sizing_hiragana),
                    root.findViewById<android.widget.TextView>(R.id.sizingJapanese).text.toString())
            }
        }
    }

    @Test fun resetScopeIsExplicitAndFullResetRequiresConfirmation() {
        val root = root()
        val resets = mutableListOf<KeyboardInputMode?>()
        KeyboardSizingControls(root, null, false,
            read = { _, _ -> KeyboardSizingValues(16f, 9f, 160f, 160f) },
            save = { _, _, _ -> fail("Reload must not save") }, render = { _, _, _ -> },
            reset = { resets.add(it) }, inherited = { false },
        )
        root.findViewById<View>(R.id.sizingResetMode).performClick()
        assertEquals(listOf(KeyboardInputMode.HIRAGANA), resets)
        root.findViewById<View>(R.id.sizingResetAll).performClick()
        assertEquals(1, resets.size)
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(listOf(KeyboardInputMode.HIRAGANA, null), resets)
    }

    @Test fun keyboardAdjustmentSavesOnlyTheSelectedField() {
        val root = root()
        val writes = mutableListOf<SizingField>()
        KeyboardSizingControls(root, null, false,
            read = { _, _ -> KeyboardSizingValues(16f, 9f, 160f, 160f) },
            save = { _, field, _ -> writes.add(field) }, render = { _, _, _ -> },
            reset = {}, inherited = { false },
        )
        val slider = root.findViewById<Slider>(R.id.sizingGuide)
        slider.requestFocus()
        slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        slider.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(writes.isNotEmpty())
        assertTrue(writes.all { it == SizingField.GUIDE })
    }
}
