package com.kazumaproject.markdownhelperkeyboard.ime_service.extensions

import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME

fun InputTypeForIME.getQWERTYReturnTextInJp(): String {
    return when (this) {
        InputTypeForIME.Text,
        InputTypeForIME.TextAutoComplete,
        InputTypeForIME.TextAutoCorrect,
        InputTypeForIME.TextCapCharacters,
        InputTypeForIME.TextCapSentences,
        InputTypeForIME.TextCapWords,
        InputTypeForIME.TextFilter,
        InputTypeForIME.TextNoSuggestion,
        InputTypeForIME.TextPersonName,
        InputTypeForIME.TextPhonetic,
        InputTypeForIME.TextWebEditText,
            -> {
            "確定"
        }

        InputTypeForIME.TextMultiLine,
        InputTypeForIME.TextImeMultiLine,
        InputTypeForIME.TextShortMessage,
        InputTypeForIME.TextLongMessage,
            -> {
            "改行"
        }

        InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
            "確定"
        }

        InputTypeForIME.TextDone -> {
            "確定"
        }

        InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
            "検索"
        }

        InputTypeForIME.TextEditTextInWebView,
        InputTypeForIME.TextUri,
        InputTypeForIME.TextPostalAddress,
        InputTypeForIME.TextWebEmailAddress,
        InputTypeForIME.TextPassword,
        InputTypeForIME.TextVisiblePassword,
        InputTypeForIME.TextWebPassword,
            -> {
            "確定"
        }

        InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
            "確定"
        }

        InputTypeForIME.Number,
        InputTypeForIME.NumberDecimal,
        InputTypeForIME.NumberPassword,
        InputTypeForIME.NumberSigned,
        InputTypeForIME.Phone,
        InputTypeForIME.Date,
        InputTypeForIME.Datetime,
        InputTypeForIME.Time,
            -> {
            "確定"
        }
    }
}

fun InputTypeForIME.getQWERTYReturnTextInEn(): String {
    return when (this) {
        InputTypeForIME.Text,
        InputTypeForIME.TextAutoComplete,
        InputTypeForIME.TextAutoCorrect,
        InputTypeForIME.TextCapCharacters,
        InputTypeForIME.TextCapSentences,
        InputTypeForIME.TextCapWords,
        InputTypeForIME.TextFilter,
        InputTypeForIME.TextNoSuggestion,
        InputTypeForIME.TextPersonName,
        InputTypeForIME.TextPhonetic,
        InputTypeForIME.TextWebEditText,
            -> {
            "return"
        }

        InputTypeForIME.TextMultiLine,
        InputTypeForIME.TextImeMultiLine,
        InputTypeForIME.TextShortMessage,
        InputTypeForIME.TextLongMessage,
            -> {
            "return"
        }

        InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
            "return"
        }

        InputTypeForIME.TextDone -> {
            "return"
        }

        InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
            "search"
        }

        InputTypeForIME.TextEditTextInWebView,
        InputTypeForIME.TextUri,
        InputTypeForIME.TextPostalAddress,
        InputTypeForIME.TextWebEmailAddress,
        InputTypeForIME.TextPassword,
        InputTypeForIME.TextVisiblePassword,
        InputTypeForIME.TextWebPassword,
            -> {
            "return"
        }

        InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
            "return"
        }

        InputTypeForIME.Number,
        InputTypeForIME.NumberDecimal,
        InputTypeForIME.NumberPassword,
        InputTypeForIME.NumberSigned,
        InputTypeForIME.Phone,
        InputTypeForIME.Date,
        InputTypeForIME.Datetime,
        InputTypeForIME.Time,
            -> {
            "return"
        }
    }
}

fun InputTypeForIME.getEnterKeyIndexSumire(): Int {
    return when (this) {
        InputTypeForIME.Text,
        InputTypeForIME.TextAutoComplete,
        InputTypeForIME.TextAutoCorrect,
        InputTypeForIME.TextCapCharacters,
        InputTypeForIME.TextCapSentences,
        InputTypeForIME.TextCapWords,
        InputTypeForIME.TextFilter,
        InputTypeForIME.TextNoSuggestion,
        InputTypeForIME.TextPersonName,
        InputTypeForIME.TextPhonetic,
        InputTypeForIME.TextWebEditText,
            -> {
            1
        }

        InputTypeForIME.TextMultiLine,
        InputTypeForIME.TextImeMultiLine,
        InputTypeForIME.TextShortMessage,
        InputTypeForIME.TextLongMessage,
            -> {
            0
        }

        InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
            4
        }

        InputTypeForIME.TextDone -> {
            1
        }

        InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
            3
        }

        InputTypeForIME.TextEditTextInWebView,
        InputTypeForIME.TextUri,
        InputTypeForIME.TextPostalAddress,
        InputTypeForIME.TextWebEmailAddress,
        InputTypeForIME.TextPassword,
        InputTypeForIME.TextVisiblePassword,
        InputTypeForIME.TextWebPassword,
            -> {
            1
        }

        InputTypeForIME.None, InputTypeForIME.TextNotCursorUpdate -> {
            1
        }

        InputTypeForIME.Number,
        InputTypeForIME.NumberDecimal,
        InputTypeForIME.NumberPassword,
        InputTypeForIME.NumberSigned,
        InputTypeForIME.Phone,
        InputTypeForIME.Date,
        InputTypeForIME.Datetime,
        InputTypeForIME.Time,
            -> {
            1
        }

    }
}
