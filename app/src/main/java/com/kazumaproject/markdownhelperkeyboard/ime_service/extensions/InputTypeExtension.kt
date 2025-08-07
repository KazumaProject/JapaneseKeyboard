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
                 * */
                InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE, 180225, 147457 -> InputTypeForIME.TextImeMultiLine
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS -> InputTypeForIME.TextNoSuggestion
                InputType.TYPE_TEXT_VARIATION_URI -> InputTypeForIME.TextUri
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> InputTypeForIME.TextEmailAddress
                InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT -> InputTypeForIME.TextEmailSubject
                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> InputTypeForIME.TextShortMessage
                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE -> InputTypeForIME.TextLongMessage
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> InputTypeForIME.TextPersonName
                InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> InputTypeForIME.TextPostalAddress
                InputType.TYPE_TEXT_VARIATION_PASSWORD, 129, 225, 16545, 209 -> InputTypeForIME.TextPassword
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
