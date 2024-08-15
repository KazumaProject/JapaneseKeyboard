package com.kazumaproject.tenkey.state

import com.kazumaproject.tenkey.key.TenKeyInfo
import com.kazumaproject.tenkey.key.TenKeyMap

sealed class InputMode{
    object ModeJapanese: InputMode()
    object ModeEnglish: InputMode()
    object ModeNumber: InputMode()

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
