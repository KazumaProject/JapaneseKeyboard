package com.kazumaproject.core.state

import com.kazumaproject.core.key.Key
import com.kazumaproject.core.key.KeyInfo
import com.kazumaproject.core.key.KeyMap

sealed class InputMode {
    data object ModeJapanese : InputMode()
    data object ModeEnglish : InputMode()
    data object ModeNumber : InputMode()

    fun InputMode.next(keyMap: KeyMap, key: Key): KeyInfo {
        return when (this) {
            ModeJapanese -> {
                keyMap.getKeyInfoJapanese(key)
            }

            ModeEnglish -> {
                keyMap.getKeyInfoEnglish(key)
            }

            ModeNumber -> {
                keyMap.getKeyInfoNumber(key)
            }
        }
    }
}