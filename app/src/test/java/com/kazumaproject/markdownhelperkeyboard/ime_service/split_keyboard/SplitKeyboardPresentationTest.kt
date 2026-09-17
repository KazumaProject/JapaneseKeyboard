package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SplitKeyboardPresentationTest {
    private val modes = listOf(TenKeyQWERTYMode.Default, TenKeyQWERTYMode.Gojuon,
        TenKeyQWERTYMode.TenKeyQWERTY, TenKeyQWERTYMode.TenKeyQWERTYRomaji,
        TenKeyQWERTYMode.Sumire, TenKeyQWERTYMode.Custom)
    private val colors = SplitKeyboardDrawables(ColorDrawable(1), ColorDrawable(2),
        ColorDrawable(3), ColorDrawable(4), ColorDrawable(5), ColorDrawable(6))
    private fun binding(): FloatingKeyboardLayoutBinding {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)
        return FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(context)).also {
            it.customLayoutFloating.setKeyboard(KeyboardDefaultLayouts.createFinalLayout(
                KeyboardInputMode.HIRAGANA, emptyMap(), "switch-mode-effective", "default"))
        }
    }
    private fun inputMode(mode: TenKeyQWERTYMode) =
        if (mode == TenKeyQWERTYMode.TenKeyQWERTY) InputMode.ModeEnglish else InputMode.ModeJapanese

    private fun render(binding: FloatingKeyboardLayoutBinding, mode: TenKeyQWERTYMode, text: String,
                       input: InputMode = inputMode(mode)) {
        val p = SplitKeyboardPresentation.resolve(mode, input, text, false, false, true, 3)
        renderSplitKeyboardPresentation(binding, mode, p, colors, if (p.japanese) "検索" else "search")
    }
    private fun key(view: FlickKeyboardView, id: String): KeyData {
        val field = FlickKeyboardView::class.java.getDeclaredField("dynamicKeyMap").apply { isAccessible = true }
        val info = (field.get(view) as Map<*, *>)[id]!!
        return info.javaClass.getDeclaredField("keyData").apply { isAccessible = true }.get(info) as KeyData
    }
    private fun assertPresentation(binding: FloatingKeyboardLayoutBinding, mode: TenKeyQWERTYMode, composing: Boolean) {
        val japanese = inputMode(mode) == InputMode.ModeJapanese
        when (mode) {
            TenKeyQWERTYMode.Default -> {
                val view = binding.keyboardViewFloating
                assertSame(if (composing) colors.convert else colors.space,
                    view.findViewById<ImageView>(com.kazumaproject.tenkey.R.id.key_space).drawable)
                assertSame(if (composing) colors.confirm else colors.enter,
                    view.findViewById<ImageView>(com.kazumaproject.tenkey.R.id.key_enter).drawable)
                if (composing) assertSame(colors.kana,
                    view.findViewById<ImageView>(com.kazumaproject.tenkey.R.id.key_small_letter).drawable)
            }
            TenKeyQWERTYMode.Gojuon -> {
                assertSame(if (composing) colors.convert else colors.space,
                    binding.gojuonViewFloating.findViewById<ImageView>(com.kazumaproject.gojuon_keyboard.R.id.key_space).drawable)
                assertSame(if (composing) colors.confirm else colors.enter,
                    binding.gojuonViewFloating.findViewById<ImageView>(com.kazumaproject.gojuon_keyboard.R.id.key_enter).drawable)
            }
            TenKeyQWERTYMode.TenKeyQWERTY, TenKeyQWERTYMode.TenKeyQWERTYRomaji -> {
                val state = binding.qwertyViewFloating.snapshotUiState()
                assertEquals(if (japanese) { if (composing) "変換" else "空白" } else "space", state.spaceKeyText)
                assertEquals(if (composing) { if (japanese) "確定" else "done" } else { if (japanese) "検索" else "search" }, state.enterKeyText)
            }
            else -> {
                assertEquals(if (composing) KeyAction.ToggleKatakana else KeyAction.SwitchToNumberLayout,
                    key(binding.customLayoutFloating, "katakana_toggle_key").action)
                assertEquals(if (composing) KeyAction.ToggleDakuten else KeyAction.InputText("^_^"),
                    key(binding.customLayoutFloating, "dakuten_toggle_key").action)
                assertEquals(if (composing) KeyAction.Convert else KeyAction.Space,
                    key(binding.customLayoutFloating, "space_convert_key").action)
                assertEquals(if (composing) "確定" else "検索", key(binding.customLayoutFloating, "enter_key").label)
            }
        }
    }

    @Test fun everyPairTracksInputDeletionAndConfirmationFromEitherSide() {
        val left = binding()
        val right = binding()
        for (a in modes) for (b in modes) for (source in SplitSlot.entries) {
            for (text in listOf("", "か", "は", "や", "")) {
                // Both panes receive the shared edit, irrespective of the last input source.
                render(left, a, text); render(right, b, text)
                assertPresentation(left, a, text.isNotEmpty())
                assertPresentation(right, b, text.isNotEmpty())
                // A delayed candidate refresh must be idempotent.
                render(if (source == SplitSlot.MAIN) left else right,
                    if (source == SplitSlot.MAIN) a else b, text)
                assertPresentation(left, a, text.isNotEmpty())
                assertPresentation(right, b, text.isNotEmpty())
            }
        }
    }

    @Test fun mixedInputModesDoNotShareTheirSmallKeyOrConversionPresentation() {
        for (mode in modes) {
            val japanese = SplitKeyboardPresentation.resolve(mode, InputMode.ModeJapanese, "は", false, false, true, 3)
            val english = SplitKeyboardPresentation.resolve(mode, InputMode.ModeEnglish, "は", false, false, true, 3)
            val number = SplitKeyboardPresentation.resolve(mode, InputMode.ModeNumber, "は", false, false, true, 3)
            assertEquals(1, japanese.dakuten)
            assertEquals(0, english.dakuten)
            assertEquals(0, english.space)
            assertEquals(SplitKeyboardPresentation.SmallKey.NUMBER, number.smallKey)
            assertEquals(5, number.enter)
        }
    }

    @Test fun replacingCustomOrNumberLayoutReappliesCurrentEditorEnterState() {
        val view = binding()
        render(view, TenKeyQWERTYMode.Custom, "は")
        view.customLayoutFloating.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
        render(view, TenKeyQWERTYMode.Number, "", InputMode.ModeNumber)
        assertEquals("検索", key(view.customLayoutFloating, "enter_key").label)
        view.customLayoutFloating.setKeyboard(KeyboardDefaultLayouts.createFinalLayout(
            KeyboardInputMode.HIRAGANA, emptyMap(), "switch-mode-effective", "default"))
        render(view, TenKeyQWERTYMode.Custom, "か")
        assertPresentation(view, TenKeyQWERTYMode.Custom, true)
    }
    @Test fun qwertyCompositionUpdatesPreserveIndependentShiftAndCapsLock() {
        val left = binding(); val right = binding()
        val shifted = com.kazumaproject.core.data.qwerty.CapsLockState(shiftOn = true)
        val locked = com.kazumaproject.core.data.qwerty.CapsLockState(capsLockOn = true)
        left.qwertyViewFloating.renderUiState(left.qwertyViewFloating.snapshotUiState().copy(capsLockState = shifted))
        right.qwertyViewFloating.renderUiState(right.qwertyViewFloating.snapshotUiState().copy(capsLockState = locked))
        for (text in listOf("か", "")) {
            render(left, TenKeyQWERTYMode.TenKeyQWERTYRomaji, text)
            render(right, TenKeyQWERTYMode.TenKeyQWERTY, text)
            assertEquals(shifted, left.qwertyViewFloating.snapshotUiState().capsLockState)
            assertEquals(locked, right.qwertyViewFloating.snapshotUiState().capsLockState)
        }
    }

    @Test fun customReloadRestoresEachPanesToggleIconsAndCharacterCase() {
        val left = binding().customLayoutFloating
        val right = binding().customLayoutFloating
        val actions = listOf(KeyAction.ShiftKey, KeyAction.CapLockKey,
            KeyAction.SwitchDirectMode, KeyAction.SwitchRomajiEnglish)
        val keys = actions.mapIndexed { i, action ->
            KeyData("toggle$i", 0, i, false, action, isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.shift_24px, keyId = "toggle$i")
        }
        val layout = com.kazumaproject.custom_keyboard.data.KeyboardLayout(keys, emptyMap(), 4, 1)
        for (view in listOf(left, right)) view.setKeyboard(layout)
        fun apply() {
            renderCustomKeyboardToggles(left,
                com.kazumaproject.markdownhelperkeyboard.ime_service.CustomKeyboardShiftState.LOCKED, true, false)
            renderCustomKeyboardToggles(right,
                com.kazumaproject.markdownhelperkeyboard.ime_service.CustomKeyboardShiftState.OFF, false, true)
        }
        fun icon(view: FlickKeyboardView, index: Int) = org.robolectric.Shadows.shadowOf(
            (view.getChildAt(index) as ImageView).drawable).createdFromResId
        apply()
        for (view in listOf(left, right)) {
            view.setKeyboard(KeyboardDefaultLayouts.createNumberLayout())
            view.setKeyboard(layout)
        }
        apply()
        assertEquals(com.kazumaproject.core.R.drawable.caps_lock, icon(left, 0))
        assertEquals(com.kazumaproject.core.R.drawable.shift_24px, icon(right, 0))
        assertEquals(com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px, icon(left, 2))
        assertEquals(com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px, icon(right, 2))
        assertEquals(com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px, icon(left, 3))
        assertEquals(com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px, icon(right, 3))
    }

}
