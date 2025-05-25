package com.kazumaproject.tenkey.state

import com.kazumaproject.core.key.Key
import com.kazumaproject.core.key.KeyInfo
import com.kazumaproject.core.key.KeyMap

sealed class InputMode {
    data object ModeJapanese : InputMode()
    data object ModeEnglish : InputMode()
    data object ModeNumber : InputMode()

    fun InputMode.next(tenKeyMap: KeyMap, key: Key): KeyInfo {
        return when (this) {
            ModeJapanese -> {
                tenKeyMap.getKeyInfoJapanese(key)
            }

            ModeEnglish -> {
                tenKeyMap.getKeyInfoEnglish(key)
            }

            ModeNumber -> {
                tenKeyMap.getKeyInfoNumber(key)
            }
        }
    }
}
