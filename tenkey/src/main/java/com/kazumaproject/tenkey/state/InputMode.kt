package com.kazumaproject.tenkey.state

import com.kazumaproject.core.key.Key
import com.kazumaproject.tenkey.key.TenKeyInfo
import com.kazumaproject.tenkey.key.TenKeyMap

sealed class InputMode{
    data object ModeJapanese: InputMode()
    data object ModeEnglish: InputMode()
    data object ModeNumber: InputMode()

    fun InputMode.next(tenKeyMap: TenKeyMap,key: Key): TenKeyInfo {
        return when(this){
            ModeJapanese -> {
                tenKeyMap.getTenKeyInfoJapanese(key)
            }

            ModeEnglish -> {
                tenKeyMap.getTenKeyInfoEnglish(key)
            }

            ModeNumber -> {
                tenKeyMap.getTenKeyInfoNumber(key)
            }
        }
    }
}
