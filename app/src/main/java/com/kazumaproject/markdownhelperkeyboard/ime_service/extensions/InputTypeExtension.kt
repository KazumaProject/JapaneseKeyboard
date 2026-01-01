package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME

fun InputTypeForIME.isPassword(): Boolean = when (this) {
    InputTypeForIME.TextPassword,
    InputTypeForIME.TextWebPassword,
    InputTypeForIME.TextVisiblePassword,
    InputTypeForIME.NumberPassword -> true

    else -> false
}

/**
 * Returns the InputTypeForIME by correctly parsing the EditorInfo's inputType and imeOptions.
 * This function uses bitwise masks for reliable detection instead of matching specific integer values.
 */
fun getCurrentInputTypeForIME2(editorInfo: EditorInfo?): InputTypeForIME {
    if (editorInfo == null || editorInfo.inputType == InputType.TYPE_NULL) {
        return InputTypeForIME.None
    }

    val inputType = editorInfo.inputType
    val inputClass = inputType and InputType.TYPE_MASK_CLASS

    return when (inputClass) {
        InputType.TYPE_CLASS_TEXT -> getTextRelatedInputType(editorInfo)
        InputType.TYPE_CLASS_NUMBER -> getNumberRelatedInputType(inputType)
        InputType.TYPE_CLASS_PHONE -> InputTypeForIME.Phone
        InputType.TYPE_CLASS_DATETIME -> getDateTimeRelatedInputType(inputType)
        else -> InputTypeForIME.None
    }
}

/**
 * Handles all text-based input types with a refined priority logic.
 * It correctly balances strong actions, structural flags, and weak actions.
 */
private fun getTextRelatedInputType(editorInfo: EditorInfo): InputTypeForIME {
    val inputType = editorInfo.inputType
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    val imeAction = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION

    // 優先度1: 最も具体的なテキスト「種類」を先にチェック (パスワード、メールアドレスなど)
    when (variation) {
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> return InputTypeForIME.TextPassword

        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> return InputTypeForIME.TextVisiblePassword
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> return InputTypeForIME.TextEmailAddress

        InputType.TYPE_TEXT_VARIATION_URI -> return InputTypeForIME.TextUri
        InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> return InputTypeForIME.TextPersonName
    }

    // 優先度2: 強い目的を持つアクションを先に評価
    when (imeAction) {
        EditorInfo.IME_ACTION_SEARCH -> return InputTypeForIME.TextSearchView
        EditorInfo.IME_ACTION_GO -> return InputTypeForIME.TextUri // ブラウザのURLバーなど
        EditorInfo.IME_ACTION_SEND -> return InputTypeForIME.TextSend
    }

    // 優先度3: 複数行の判定（最重要）
    val isMultiLine = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
    val isImeMultiLine = (inputType and InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0

    if (isMultiLine && !isImeMultiLine) {
        // 「真の」複数行フィールド。
        // 開発者が「Enterでアクション実行」を意図していないため、改行を優先する。
        return InputTypeForIME.TextMultiLine
    }

    // ここに到達するのは以下のいずれかの場合:
    // 1. single-line のフィールド
    // 2. multi-line だが、Enterキーでアクションを実行すべきフィールド (isImeMultiLine == true)

    // 優先度4: 残りのIMEアクションを評価
    when (imeAction) {
        EditorInfo.IME_ACTION_NEXT -> return InputTypeForIME.TextNextLine
        EditorInfo.IME_ACTION_DONE -> return InputTypeForIME.TextDone
    }

    // 優先度5: フォールバック
    if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
        return InputTypeForIME.TextNoSuggestion
    }
    editorInfo.hintText?.toString()?.let {
        val hint = it.lowercase()
        if (hint.contains("search") || hint.contains("検索")) return InputTypeForIME.TextSearchView
        if (hint.contains("password")) return InputTypeForIME.TextPassword
    }

    // 優先度6: デフォルト
    // isMultiLineがtrueでもここに到達することがある（isImeMultiLineがtrueの場合）
    // その場合、適切なアクションが指定されていなければ、デフォルトのTextとして扱う
    if (isMultiLine) {
        return InputTypeForIME.TextMultiLine
    }

    return InputTypeForIME.Text
}

/**
 * Handles number-based input types.
 */
private fun getNumberRelatedInputType(inputType: Int): InputTypeForIME {
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
        return InputTypeForIME.NumberPassword
    }

    if ((inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0) {
        return InputTypeForIME.NumberDecimal
    }
    if ((inputType and InputType.TYPE_NUMBER_FLAG_SIGNED) != 0) {
        return InputTypeForIME.NumberSigned
    }

    return InputTypeForIME.Number
}

/**
 * Handles date/time-based input types.
 */
private fun getDateTimeRelatedInputType(inputType: Int): InputTypeForIME {
    return when (inputType and InputType.TYPE_MASK_VARIATION) {
        InputType.TYPE_DATETIME_VARIATION_DATE -> InputTypeForIME.Date
        InputType.TYPE_DATETIME_VARIATION_TIME -> InputTypeForIME.Time
        else -> InputTypeForIME.Datetime
    }
}
