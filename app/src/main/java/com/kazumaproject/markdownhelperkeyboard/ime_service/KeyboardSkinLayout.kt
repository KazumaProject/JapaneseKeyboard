package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.skin.KeyboardSkinId

/** Default's content budget is deliberately identical to the pre-skin implementation. */
internal fun resolveSkinWindowHeight(contentHeight: Int, bottomInset: Int, skin: KeyboardSkinId): Int =
    when (skin) {
        KeyboardSkinId.DEFAULT -> contentHeight
        KeyboardSkinId.CUPERTINO_LIGHT, KeyboardSkinId.CUPERTINO_DARK -> contentHeight + bottomInset
    }
