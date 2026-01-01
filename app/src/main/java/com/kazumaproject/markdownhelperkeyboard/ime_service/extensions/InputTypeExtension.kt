package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME

/**
 * Return InputTypeForIME
 * @param inputType: Int
 * @return InputTypeForIME
 *  180225 : Messages
 *  524305: Search View in Chrome, Edge and DuckDuckGo
 *  17: FireFox
 *  294917:
 */
fun getCurrentInputTypeForIME(editorInfo: EditorInfo): InputTypeForIME {
    return when (editorInfo.inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> {
            when (editorInfo.inputType) {
                InputType.TYPE_TEXT_VARIATION_NORMAL -> InputTypeForIME.Text
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS -> InputTypeForIME.TextCapCharacters
                InputType.TYPE_TEXT_FLAG_CAP_WORDS -> InputTypeForIME.TextCapWords
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES -> InputTypeForIME.TextCapSentences
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT -> InputTypeForIME.TextAutoCorrect
                InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE -> InputTypeForIME.TextAutoComplete
                /**
                 *  180385 : Gmail content
                 *  131073 : EditText in Android App
                 *  409601 : Line
                 * */
                InputType.TYPE_TEXT_FLAG_MULTI_LINE, 409601 -> InputTypeForIME.TextMultiLine

                /**
                 *  180225 : Twitter Tweet & Messenger
                 *  147457 : Facebook Messenger
                 *  131201 : TB Bank Password
                 * */
                InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE, 180225, 147457 -> InputTypeForIME.TextImeMultiLine
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 557217 -> InputTypeForIME.TextNoSuggestion
                InputType.TYPE_TEXT_VARIATION_URI -> InputTypeForIME.TextUri
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> InputTypeForIME.TextEmailAddress
                InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT -> InputTypeForIME.TextEmailSubject
                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> InputTypeForIME.TextShortMessage
                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE -> InputTypeForIME.TextLongMessage
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> InputTypeForIME.TextPersonName
                InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> InputTypeForIME.TextPostalAddress
                InputType.TYPE_TEXT_VARIATION_PASSWORD, 129, 225, 16545, 131201 -> InputTypeForIME.TextPassword
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> InputTypeForIME.TextVisiblePassword
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> InputTypeForIME.TextWebEditText
                InputType.TYPE_TEXT_VARIATION_FILTER -> InputTypeForIME.TextFilter
                InputType.TYPE_TEXT_VARIATION_PHONETIC -> InputTypeForIME.TextPhonetic
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> InputTypeForIME.TextWebEmailAddress
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> InputTypeForIME.TextWebPassword
                /**
                 * TD Bank Book First, Last Name and Phone Number
                 * EditTexts in WebView in Chrome
                 * **/
                49313 -> {
                    InputTypeForIME.TextEditTextInWebView
                }

                524305 -> InputTypeForIME.TextWebSearchView
                17 -> InputTypeForIME.TextWebSearchViewFireFox
                294913 -> InputTypeForIME.TextNotCursorUpdate
                /**
                 *  524465 : Twitter (X) SearchView
                 *  589825 : SearchView in Android App
                 *  1048577: Pixel Setting SearchView
                 *  524289 : GoogleMap SearchView
                 *  540833: YouTube SearchView in WebView
                 *  177: YouTube Android App SearchView
                 *  573601: Chrome SearchView under Youtube website in WebView
                 *  655521: Chrome SearchView on the top in WebView
                 *  524449: Brave SearchView on the top in WebView
                 *  1: SearchView in Nova Launcher, File Manager App
                 *  65537: SearchView in Default Message App
                 *  131073: Instagram Search
                 * **/
                524465, 589825, 1048577, 524289, 540833,
                177, 573601, 655521, 524449,
                65537 -> InputTypeForIME.TextSearchView

                1 -> {
                    editorInfo.imeOptions.inputTypeFromImeOptions(editorInfo)
                }
                /**
                 *  33 : Gmail To section
                 *  442417 : Gmail Subject
                 * */
                33, 442417 -> InputTypeForIME.TextNextLine
                else -> {
                    editorInfo.imeOptions.inputTypeFromImeOptions(editorInfo)
                }
            }
        }

        InputType.TYPE_CLASS_NUMBER -> {
            when (editorInfo.inputType) {
                InputType.TYPE_NUMBER_VARIATION_NORMAL -> InputTypeForIME.Number
                InputType.TYPE_NUMBER_FLAG_SIGNED -> InputTypeForIME.NumberSigned
                InputType.TYPE_NUMBER_FLAG_DECIMAL -> InputTypeForIME.NumberDecimal
                InputType.TYPE_NUMBER_VARIATION_PASSWORD -> InputTypeForIME.NumberPassword
                180225 -> InputTypeForIME.TextImeMultiLine
                else -> InputTypeForIME.Number
            }
        }

        InputType.TYPE_CLASS_PHONE -> {
            when (editorInfo.inputType) {
                180225 -> InputTypeForIME.TextImeMultiLine
                else -> InputTypeForIME.Phone
            }
        }

        InputType.TYPE_CLASS_DATETIME -> {
            when (editorInfo.inputType) {
                InputType.TYPE_DATETIME_VARIATION_NORMAL -> InputTypeForIME.Datetime
                InputType.TYPE_DATETIME_VARIATION_DATE -> InputTypeForIME.Date
                InputType.TYPE_DATETIME_VARIATION_TIME -> InputTypeForIME.Time
                180225 -> InputTypeForIME.TextImeMultiLine
                else -> InputTypeForIME.Datetime
            }
        }

        InputType.TYPE_NULL -> InputTypeForIME.None
        else -> InputTypeForIME.None
    }
}

fun Int.inputTypeFromImeOptions(
    editorInfo: EditorInfo
): InputTypeForIME {
    when (this) {
        EditorInfo.IME_ACTION_SEARCH -> {
            return InputTypeForIME.TextSearchView
        }

        EditorInfo.IME_ACTION_NEXT -> {
            return InputTypeForIME.TextNextLine
        }

        EditorInfo.IME_ACTION_DONE -> {
            return InputTypeForIME.TextDone
        }

        else -> {
            editorInfo.hintText?.toString()?.let {
                if (it.contains("検索")) return InputTypeForIME.TextSearchView
                if (it.contains("レビューを書き込んでください（任意）")) return InputTypeForIME.TextMultiLine
                if (it.contains("Describe your experience")) return InputTypeForIME.TextMultiLine
                if (it.contains("搜索")) return InputTypeForIME.TextSearchView
                if (it.contains("검색")) return InputTypeForIME.TextSearchView
                if (it.contains("Search")) return InputTypeForIME.TextSearchView
                if (it.contains("Password")) return InputTypeForIME.TextPassword
                if (it.contains("password")) return InputTypeForIME.TextPassword
            }
            return InputTypeForIME.Text
        }
    }
}

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
