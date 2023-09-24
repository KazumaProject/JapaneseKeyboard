package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import android.text.InputType
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
fun getCurrentInputTypeForIME(
    inputType: Int
): InputTypeForIME{
    return when(inputType and InputType.TYPE_MASK_CLASS){
        InputType.TYPE_CLASS_TEXT ->{
            when(inputType){
                InputType.TYPE_TEXT_VARIATION_NORMAL -> InputTypeForIME.Text
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS -> InputTypeForIME.TextCapCharacters
                InputType.TYPE_TEXT_FLAG_CAP_WORDS -> InputTypeForIME.TextCapWords
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES -> InputTypeForIME.TextCapSentences
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT -> InputTypeForIME.TextAutoCorrect
                InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE -> InputTypeForIME.TextAutoComplete
                InputType.TYPE_TEXT_FLAG_MULTI_LINE -> InputTypeForIME.TextMultiLine
                /**
                 *  180225 : Twitter Tweet & Messenger
                 *  147457 : Facebook Messenger
                 * */
                InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE,180225, 147457 -> InputTypeForIME.TextImeMultiLine
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS -> InputTypeForIME.TextNoSuggestion
                InputType.TYPE_TEXT_VARIATION_URI -> InputTypeForIME.TextUri
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> InputTypeForIME.TextEmailAddress
                InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT -> InputTypeForIME.TextEmailSubject
                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> InputTypeForIME.TextShortMessage
                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE -> InputTypeForIME.TextLongMessage
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> InputTypeForIME.TextPersonName
                InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS, 573601 -> InputTypeForIME.TextPostalAddress
                InputType.TYPE_TEXT_VARIATION_PASSWORD, 129, 225,16545, 209 -> InputTypeForIME.TextPassword
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> InputTypeForIME.TextVisiblePassword
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> InputTypeForIME.TextWebEditText
                InputType.TYPE_TEXT_VARIATION_FILTER -> InputTypeForIME.TextFilter
                InputType.TYPE_TEXT_VARIATION_PHONETIC -> InputTypeForIME.TextPhonetic
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> InputTypeForIME.TextWebEmailAddress
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> InputTypeForIME.TextWebPassword
                /** TD Bank Book First, Last Name and Phone Number **/
                49313 -> InputTypeForIME.TextEditTextInBookingTDBank
                524305-> InputTypeForIME.TextWebSearchView
                17 -> InputTypeForIME.TextWebSearchViewFireFox
                294913 -> InputTypeForIME.TextNotCursorUpdate
                /**
                 *  524465 : Twitter (X) Search View
                 * **/
                524465 -> InputTypeForIME.TextSearchView
                else -> InputTypeForIME.Text
            }
        }
        InputType.TYPE_CLASS_NUMBER ->{
            when(inputType){
                InputType.TYPE_NUMBER_VARIATION_NORMAL -> InputTypeForIME.Number
                InputType.TYPE_NUMBER_FLAG_SIGNED -> InputTypeForIME.NumberSigned
                InputType.TYPE_NUMBER_FLAG_DECIMAL -> InputTypeForIME.NumberDecimal
                InputType.TYPE_NUMBER_VARIATION_PASSWORD -> InputTypeForIME.NumberPassword
                180225 -> InputTypeForIME.TextImeMultiLine
                else -> InputTypeForIME.Number
            }
        }
        InputType.TYPE_CLASS_PHONE -> {
            when(inputType){
                180225 -> InputTypeForIME.TextImeMultiLine
                else -> InputTypeForIME.Phone
            }
        }
        InputType.TYPE_CLASS_DATETIME ->{
            when(inputType){
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