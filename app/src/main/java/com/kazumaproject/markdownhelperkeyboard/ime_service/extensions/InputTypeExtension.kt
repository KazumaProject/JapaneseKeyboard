package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import timber.log.Timber

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
                InputType.TYPE_TEXT_VARIATION_PASSWORD, 129, 225, 16545 -> InputTypeForIME.TextPassword
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

fun analyzeInputType(inputType: Int) {
    // 1. クラスの解析
    val inputClass = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> "TYPE_CLASS_TEXT"
        InputType.TYPE_CLASS_NUMBER -> "TYPE_CLASS_NUMBER"
        InputType.TYPE_CLASS_PHONE -> "TYPE_CLASS_PHONE"
        InputType.TYPE_CLASS_DATETIME -> "TYPE_CLASS_DATETIME"
        else -> "UNKNOWN_CLASS"
    }
    Timber.d("InputTypeAnalyzer: Class: $inputClass")

    // 2. バリエーションの解析
    val variation = when (inputType and InputType.TYPE_MASK_VARIATION) {
        InputType.TYPE_TEXT_VARIATION_NORMAL -> "NORMAL"
        InputType.TYPE_TEXT_VARIATION_URI -> "URI"
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> "EMAIL_ADDRESS"
        InputType.TYPE_TEXT_VARIATION_PASSWORD -> "PASSWORD"
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> "VISIBLE_PASSWORD"
        InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> "WEB_EDIT_TEXT"
        InputType.TYPE_NUMBER_VARIATION_PASSWORD -> "NUMBER_PASSWORD"
        else -> "OTHER_VARIATION"
    }
    Timber.d("InputTypeAnalyzer: Variation: $variation")

    // 3. フラグの解析
    val flags = mutableListOf<String>()
    if ((inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) flags.add("FLAG_MULTI_LINE")
    if ((inputType and InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0) flags.add("FLAG_IME_MULTI_LINE")
    if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) flags.add("FLAG_NO_SUGGESTIONS")
    if ((inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0) flags.add("FLAG_AUTO_CORRECT")
    if ((inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) flags.add("FLAG_AUTO_COMPLETE")
    if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) flags.add("FLAG_CAP_SENTENCES")

    Timber.d("InputTypeAnalyzer: Flags: ${flags.joinToString(", ")}")
}

/**
 * EditorInfoから現在の入力フィールドのタイプをInputTypeForIMEとして返します。
 *
 * @param editorInfo IMEから提供される現在のエディタの状態。nullの場合もあります。
 * @return 対応するInputTypeForIME。不明な場合はNoneを返します。
 */
fun getInputTypeForIME(editorInfo: EditorInfo?): InputTypeForIME {
    if (editorInfo == null) {
        return InputTypeForIME.None
    }

    val inputType = editorInfo.inputType
    val variation = inputType and InputType.TYPE_MASK_VARIATION

    // --- 最も具体的な条件から順にチェック ---

    // 1. IMEアクションを最優先でチェック (フィールドの目的を判定)
    val imeAction = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
    if (imeAction == EditorInfo.IME_ACTION_SEARCH) {
        // WebView内かどうかに応じて、より詳細な型を返すことも可能
        return if (variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
            InputTypeForIME.TextWebSearchView
        } else {
            InputTypeForIME.TextSearchView
        }
    }
    if (imeAction == EditorInfo.IME_ACTION_NEXT) {
        return InputTypeForIME.TextNextLine
    }
    if (imeAction == EditorInfo.IME_ACTION_DONE) {
        return InputTypeForIME.TextDone
    }

    // 2. パスワード関連をチェック (非常に特殊なケース)
    when (variation) {
        InputType.TYPE_TEXT_VARIATION_PASSWORD -> return InputTypeForIME.TextPassword
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> return InputTypeForIME.TextVisiblePassword
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> return InputTypeForIME.TextWebPassword
    }

    // 3. 構造を定義するフラグをチェック
    if ((inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
        return InputTypeForIME.TextMultiLine
    }
    if ((inputType and InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0) {
        return InputTypeForIME.TextImeMultiLine
    }
    if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
        return InputTypeForIME.TextNoSuggestion
    }

    // 4. その他のバリエーションをチェック
    when (variation) {
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> return InputTypeForIME.TextEmailAddress
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> return InputTypeForIME.TextWebEmailAddress
        InputType.TYPE_TEXT_VARIATION_URI -> return InputTypeForIME.TextUri
        InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> return InputTypeForIME.TextPersonName
        InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> return InputTypeForIME.TextPostalAddress
        InputType.TYPE_TEXT_VARIATION_FILTER -> return InputTypeForIME.TextFilter
        InputType.TYPE_TEXT_VARIATION_PHONETIC -> return InputTypeForIME.TextPhonetic
        // TextWebEditTextは他の条件に当てはまらない場合のフォールバックとして扱う
        InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> return InputTypeForIME.TextWebEditText
    }

    // どの条件にも当てはまらない場合は、汎用的なテキストとして扱う
    return InputTypeForIME.Text
}

/**
 * テキストクラス関連のInputTypeForIMEを返します。
 */
private fun getTextRelatedInputType(editorInfo: EditorInfo): InputTypeForIME {
    val inputType = editorInfo.inputType
    // inputTypeからバリエーション部分（パスワード、URIなど）を抽出
    val variation = inputType and InputType.TYPE_MASK_VARIATION

    // まずは特殊なバリエーションから判定
    when (variation) {
        InputType.TYPE_TEXT_VARIATION_PASSWORD -> return InputTypeForIME.TextPassword
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> return InputTypeForIME.TextVisiblePassword
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> return InputTypeForIME.TextWebPassword
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> return InputTypeForIME.TextEmailAddress
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> return InputTypeForIME.TextWebEmailAddress
        InputType.TYPE_TEXT_VARIATION_URI -> return InputTypeForIME.TextUri
        InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> return InputTypeForIME.TextPersonName
        InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> return InputTypeForIME.TextPostalAddress
        InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> return InputTypeForIME.TextWebEditText
        InputType.TYPE_TEXT_VARIATION_FILTER -> return InputTypeForIME.TextFilter
        InputType.TYPE_TEXT_VARIATION_PHONETIC -> return InputTypeForIME.TextPhonetic
    }

    // 次に、IMEアクション（Enterキーの挙動）をチェック
    val imeAction = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
    if (imeAction == EditorInfo.IME_ACTION_SEARCH) {
        return InputTypeForIME.TextSearchView
    }
    if (imeAction == EditorInfo.IME_ACTION_NEXT) {
        return InputTypeForIME.TextNextLine
    }
    if (imeAction == EditorInfo.IME_ACTION_DONE) {
        return InputTypeForIME.TextDone
    }

    // 次に、フラグをチェック
    if ((inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
        return InputTypeForIME.TextMultiLine
    }
    if ((inputType and InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0) {
        return InputTypeForIME.TextImeMultiLine
    }
    if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
        return InputTypeForIME.TextNoSuggestion
    }

    // どの条件にも当てはまらない場合は、汎用的なテキストとして扱う
    return InputTypeForIME.Text
}

/**
 * 数値クラス関連のInputTypeForIMEを返します。
 */
private fun getNumberRelatedInputType(inputType: Int): InputTypeForIME {
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
        return InputTypeForIME.NumberPassword
    }

    // フラグをチェック
    if ((inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0) {
        return InputTypeForIME.NumberDecimal
    }
    if ((inputType and InputType.TYPE_NUMBER_FLAG_SIGNED) != 0) {
        return InputTypeForIME.NumberSigned
    }

    return InputTypeForIME.Number
}

/**
 * 日時クラス関連のInputTypeForIMEを返します。
 */
private fun getDateTimeRelatedInputType(inputType: Int): InputTypeForIME {
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (variation) {
        InputType.TYPE_DATETIME_VARIATION_DATE -> InputTypeForIME.Date
        InputType.TYPE_DATETIME_VARIATION_TIME -> InputTypeForIME.Time
        else -> InputTypeForIME.Datetime
    }
}
