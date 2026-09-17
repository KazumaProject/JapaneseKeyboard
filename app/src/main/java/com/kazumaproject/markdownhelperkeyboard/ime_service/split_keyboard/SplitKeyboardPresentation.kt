package com.kazumaproject.markdownhelperkeyboard.ime_service.split_keyboard

import android.graphics.drawable.Drawable
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.ime_service.CustomKeyboardShiftState
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.tenkey.extensions.getDakutenSmallChar
import com.kazumaproject.tenkey.extensions.isHiragana
import com.kazumaproject.tenkey.extensions.isLatinAlphabet

/** Shared composition is projected onto each pane without changing the service's input source. */
internal data class SplitKeyboardPresentation(
    val composing: Boolean,
    val japanese: Boolean,
    val smallKey: SmallKey,
    val dakuten: Int,
    val katakana: Int,
    val space: Int,
    val enter: Int,
) {
    enum class SmallKey { KANA, ENGLISH, IME, NUMBER }

    companion object {
        fun resolve(mode: TenKeyQWERTYMode, inputMode: InputMode, text: String,
                    hasTail: Boolean, converting: Boolean, showIme: Boolean,
                    editorEnter: Int): SplitKeyboardPresentation {
            val composing = text.isNotEmpty() || hasTail || converting
            val japanese = inputMode == InputMode.ModeJapanese
            val last = text.lastOrNull()
            return SplitKeyboardPresentation(
                composing, japanese,
                when {
                    inputMode == InputMode.ModeNumber -> SmallKey.NUMBER
                    last?.isLatinAlphabet() == true -> SmallKey.ENGLISH
                    japanese && last?.isHiragana() == true -> SmallKey.KANA
                    showIme -> SmallKey.IME
                    japanese -> SmallKey.KANA
                    else -> SmallKey.ENGLISH
                },
                if (mode != TenKeyQWERTYMode.Number && japanese && last?.getDakutenSmallChar() != null) 1 else 0,
                if (composing && japanese) 1 else 0,
                if (composing && japanese) 1 else 0,
                if (composing) 5 else editorEnter,
            )
        }
    }
}

internal data class SplitKeyboardDrawables(
    val kana: Drawable?, val english: Drawable?, val space: Drawable?,
    val convert: Drawable?, val confirm: Drawable?, val enter: Drawable?,
)

internal fun renderSplitKeyboardPresentation(
    binding: FloatingKeyboardLayoutBinding,
    mode: TenKeyQWERTYMode,
    presentation: SplitKeyboardPresentation,
    drawables: SplitKeyboardDrawables,
    enterLabel: String,
) {
    val p = presentation
    val enter = if (p.composing) drawables.confirm else drawables.enter
    val space = if (p.composing && p.japanese) drawables.convert else drawables.space
    when (mode) {
        TenKeyQWERTYMode.Default -> binding.keyboardViewFloating.apply {
            setSideKeyEnterDrawable(enter)
            setSideKeySpaceDrawable(space)
            setSideKeyPreviousState(p.japanese || p.smallKey == SplitKeyboardPresentation.SmallKey.NUMBER)
            when (p.smallKey) {
                SplitKeyboardPresentation.SmallKey.KANA -> setBackgroundSmallLetterKey(drawables.kana)
                SplitKeyboardPresentation.SmallKey.ENGLISH -> setBackgroundSmallLetterKey(drawables.english)
                SplitKeyboardPresentation.SmallKey.IME -> setBackgroundSmallLetterKey(isLanguageEnable = true, isEnglish = !p.japanese)
                SplitKeyboardPresentation.SmallKey.NUMBER -> setNumberSmallKeyPresentation()
            }
        }
        TenKeyQWERTYMode.Gojuon -> binding.gojuonViewFloating.apply {
            setSideKeyEnterDrawable(enter)
            setSideKeySpaceDrawable(space)
            setSideKeyPreviousState(p.japanese || p.smallKey == SplitKeyboardPresentation.SmallKey.NUMBER)
        }
        TenKeyQWERTYMode.TenKeyQWERTY, TenKeyQWERTYMode.TenKeyQWERTYRomaji -> binding.qwertyViewFloating.apply {
            setSpaceKeyText(if (p.japanese) { if (p.composing) "変換" else "空白" } else "space")
            setReturnKeyText(if (p.composing) { if (p.japanese) "確定" else "done" } else enterLabel)
        }
        else -> binding.customLayoutFloating.apply {
            updateDynamicKey("enter_key", p.enter)
            updateDynamicKey("space_convert_key", p.space)
            updateDynamicKey("dakuten_toggle_key", p.dakuten)
            updateDynamicKey("katakana_toggle_key", p.katakana)
        }
    }
}

internal fun renderCustomKeyboardToggles(
    flickView: FlickKeyboardView,
    shift: CustomKeyboardShiftState,
    direct: Boolean,
    romaji: Boolean,
) {
    flickView.setKeyCharacterCase(shift.keyCharacterCase)
    flickView.updateKeyIconByAction(
        KeyAction.SwitchDirectMode,
        if (direct) {
            com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px
        } else {
            com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px
        }
    )
    flickView.updateKeyIconByAction(
        KeyAction.SwitchRomajiEnglish,
        if (romaji) {
            com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px
        } else {
            com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px
        }
    )
    flickView.updateKeyIconByAction(
        KeyAction.ShiftKey,
        when (shift) {
            CustomKeyboardShiftState.OFF ->
                com.kazumaproject.core.R.drawable.shift_24px
            CustomKeyboardShiftState.ONE_SHOT ->
                com.kazumaproject.core.R.drawable.shift_fill_24px
            CustomKeyboardShiftState.LOCKED ->
                com.kazumaproject.core.R.drawable.caps_lock
        }
    )
    flickView.updateKeyIconByAction(
        KeyAction.CapLockKey,
        if (shift == CustomKeyboardShiftState.LOCKED) {
            com.kazumaproject.core.R.drawable.caps_lock
        } else {
            com.kazumaproject.core.R.drawable.caps_lock_outline
        }
    )
}
