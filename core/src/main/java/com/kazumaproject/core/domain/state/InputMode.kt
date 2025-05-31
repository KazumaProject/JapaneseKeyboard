package com.kazumaproject.core.domain.state

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyMap

sealed class InputMode {
    data object ModeJapanese : InputMode()
    data object ModeEnglish : InputMode()
    data object ModeNumber : InputMode()

    fun InputMode.next(keyMap: KeyMap, key: Key, isTablet: Boolean): KeyInfo {
        return when (this) {
            ModeJapanese -> {
                keyMap.getKeyInfoJapanese(key, isTablet)
            }

            ModeEnglish -> {
                keyMap.getKeyInfoEnglish(key, isTablet)
            }

            ModeNumber -> {
                keyMap.getKeyInfoNumber(key, isTablet)
            }
        }
    }
}