package com.kazumaproject.core.domain.state

enum class TwoStateNumberReturnTarget {
    Japanese,
    English
}

fun InputMode.toTwoStateNumberReturnTargetOrNull(): TwoStateNumberReturnTarget? {
    return when (this) {
        InputMode.ModeJapanese -> TwoStateNumberReturnTarget.Japanese
        InputMode.ModeEnglish -> TwoStateNumberReturnTarget.English
        InputMode.ModeNumber -> null
    }
}

fun TwoStateNumberReturnTarget.toInputMode(): InputMode {
    return when (this) {
        TwoStateNumberReturnTarget.Japanese -> InputMode.ModeJapanese
        TwoStateNumberReturnTarget.English -> InputMode.ModeEnglish
    }
}
