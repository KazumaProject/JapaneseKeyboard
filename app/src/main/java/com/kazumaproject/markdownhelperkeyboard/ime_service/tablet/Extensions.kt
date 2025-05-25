package com.kazumaproject.markdownhelperkeyboard.ime_service.tablet

import com.kazumaproject.tenkey.state.InputMode

fun InputMode.nextInTablet(tabletKeyMap: TabletKeyMap, tabletKey: TabletKey): TabletKeyInfo {
    return when (this) {
        InputMode.ModeJapanese -> {
            tabletKeyMap.getTenKeyInfoJapanese(tabletKey)
        }

        InputMode.ModeEnglish -> {
            //tabletKeyMap.getTenKeyInfoEnglish(tabletKey)
            tabletKeyMap.getTenKeyInfoJapanese(tabletKey)
        }

        InputMode.ModeNumber -> {
            //tabletKeyMap.getTenKeyInfoNumber(tabletKey)
            tabletKeyMap.getTenKeyInfoJapanese(tabletKey)
        }
    }
}