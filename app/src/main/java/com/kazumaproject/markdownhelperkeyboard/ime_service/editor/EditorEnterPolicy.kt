package com.kazumaproject.markdownhelperkeyboard.ime_service.editor

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.TypeNullInputBehaviorSetting

/** Only the editor-facing Enter. Composition/segment confirmation is owned by the caller. */
sealed interface EditorEnterAction {
    data object Enter : EditorEnterAction
    data object Newline : EditorEnterAction
    data class Action(val id: Int) : EditorEnterAction
}

object EditorEnterPolicy {
    fun resolve(editorInfo: EditorInfo?): EditorEnterAction {
        if (editorInfo == null) return EditorEnterAction.Enter
        val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        if (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 &&
            action in EditorInfo.IME_ACTION_GO..EditorInfo.IME_ACTION_PREVIOUS
        ) {
            return EditorEnterAction.Action(action)
        }
        // Gboard raw captures use commitText("\n") for SHORT_MESSAGE, independent of
        // multiline flags; other observed text variations send an Enter key pair.
        return if (editorInfo.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            editorInfo.inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
        ) EditorEnterAction.Newline else EditorEnterAction.Enter
    }

    fun usesEditorActionForDefaultTypeNull(
        inputType: Int?,
        setting: TypeNullInputBehaviorSetting,
        hasExplicitDirectOverride: Boolean,
    ): Boolean = inputType == InputType.TYPE_NULL &&
        setting == TypeNullInputBehaviorSetting.DEFAULT && !hasExplicitDirectOverride

    // Existing built-in key states 0..5 keep their meanings; 6..8 are editor actions.
    fun keyStateIndex(action: EditorEnterAction): Int = when (action) {
        EditorEnterAction.Enter, EditorEnterAction.Newline -> 0
        is EditorEnterAction.Action -> when (action.id) {
            EditorInfo.IME_ACTION_GO -> 7
            EditorInfo.IME_ACTION_SEARCH -> 3
            EditorInfo.IME_ACTION_SEND -> 8
            EditorInfo.IME_ACTION_NEXT -> 4
            EditorInfo.IME_ACTION_PREVIOUS -> 6
            else -> 5
        }
    }

    fun label(action: EditorEnterAction, japanese: Boolean): String = when (action) {
        EditorEnterAction.Enter, EditorEnterAction.Newline -> if (japanese) "改行" else "return"
        is EditorEnterAction.Action -> when (action.id) {
            EditorInfo.IME_ACTION_GO -> if (japanese) "実行" else "go"
            EditorInfo.IME_ACTION_SEARCH -> if (japanese) "検索" else "search"
            EditorInfo.IME_ACTION_SEND -> if (japanese) "送信" else "send"
            EditorInfo.IME_ACTION_NEXT -> if (japanese) "次へ" else "next"
            EditorInfo.IME_ACTION_PREVIOUS -> if (japanese) "前へ" else "previous"
            else -> if (japanese) "完了" else "done"
        }
    }

    /** Keep the service's existing dispatch wrappers (and mutation bookkeeping) in the path. */
    fun dispatch(action: EditorEnterAction, sendKey: (Int) -> Unit, performAction: (Int) -> Unit, commitNewline: () -> Unit) {
        when (action) {
            EditorEnterAction.Enter -> sendKey(KeyEvent.KEYCODE_ENTER)
            EditorEnterAction.Newline -> commitNewline()
            is EditorEnterAction.Action -> performAction(action.id)
        }
    }
}
