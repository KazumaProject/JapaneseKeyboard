package com.kazumaproject.markdownhelperkeyboard.ime_service.enter

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME

/** Non-composing Enter actions; reviewed Gboard fixtures cover editor-action precedence. */
internal sealed interface EnterAction {
    data object NewLine : EnterAction
    data object KeyEvent : EnterAction
    data class EditorAction(val id: Int) : EnterAction
}

internal object EnterActionResolver {
    fun resolve(inputType: InputTypeForIME, editorInfo: EditorInfo? = null): EnterAction {
        // Use the editor's original action, not the lossy keyboard/input-type classification.
        // Reviewed Gboard fixtures cover text flags and numeric/phone/date actions.
        if (editorInfo != null) {
            if (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
                return if (editorInfo.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT)
                    EnterAction.NewLine else EnterAction.KeyEvent
            }
            when (val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
                EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS, EditorInfo.IME_ACTION_DONE ->
                    return EnterAction.EditorAction(action)
            }
            val inputClass = editorInfo.inputType and InputType.TYPE_MASK_CLASS
            if (inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME
            ) {
                return EnterAction.KeyEvent
            }
        }
        return legacyAction(inputType)
    }

    private fun legacyAction(inputType: InputTypeForIME): EnterAction = when (inputType) {
        InputTypeForIME.TextMultiLine,
        InputTypeForIME.TextImeMultiLine,
        InputTypeForIME.TextShortMessage,
        InputTypeForIME.TextLongMessage,
            -> EnterAction.NewLine

        InputTypeForIME.None,
        InputTypeForIME.Text,
        InputTypeForIME.TextAutoComplete,
        InputTypeForIME.TextAutoCorrect,
        InputTypeForIME.TextCapCharacters,
        InputTypeForIME.TextCapSentences,
        InputTypeForIME.TextCapWords,
        InputTypeForIME.TextEmailSubject,
        InputTypeForIME.TextFilter,
        InputTypeForIME.TextNoSuggestion,
        InputTypeForIME.TextPersonName,
        InputTypeForIME.TextPhonetic,
        InputTypeForIME.TextWebEditText,
        InputTypeForIME.TextUri,
        InputTypeForIME.TextPostalAddress,
        InputTypeForIME.TextEmailAddress,
        InputTypeForIME.TextWebEmailAddress,
        InputTypeForIME.TextPassword,
        InputTypeForIME.TextVisiblePassword,
        InputTypeForIME.TextWebPassword,
        InputTypeForIME.TextNotCursorUpdate,
        InputTypeForIME.TextEditTextInWebView,
        InputTypeForIME.TypeNull,
        InputTypeForIME.TextSend
            -> EnterAction.KeyEvent

        InputTypeForIME.TextNextLine -> EnterAction.EditorAction(EditorInfo.IME_ACTION_NEXT)

        InputTypeForIME.TextDone -> EnterAction.EditorAction(EditorInfo.IME_ACTION_DONE)

        InputTypeForIME.Number,
        InputTypeForIME.NumberDecimal,
        InputTypeForIME.NumberPassword,
        InputTypeForIME.NumberSigned,
        InputTypeForIME.Phone,
        InputTypeForIME.Date,
        InputTypeForIME.Datetime,
        InputTypeForIME.Time,
            -> EnterAction.EditorAction(EditorInfo.IME_ACTION_DONE)

        InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> EnterAction.EditorAction(EditorInfo.IME_ACTION_SEARCH)

    }
}

/** Keep the service's existing commit and Android key-event helpers intact. */
internal object EnterActionExecutor {
    fun execute(
        action: EnterAction,
        commitText: (CharSequence, Int) -> Boolean,
        performAction: (Int) -> Boolean,
        sendKey: (Int) -> Unit,
    ) {
        when (action) {
            EnterAction.NewLine -> commitText("\n", 1)
            EnterAction.KeyEvent -> sendKey(KeyEvent.KEYCODE_ENTER)
            is EnterAction.EditorAction -> performAction(action.id)
        }
    }
}
