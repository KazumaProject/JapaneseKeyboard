package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.skin.KeyboardSkinId
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardSkinLayoutTest {
    @Test fun defaultRetainsPreSkinHeightAcrossInsetsAndCandidateSizes() {
        for (height in listOf(340,390,430)) for (inset in listOf(0,24,48,126)) {
            assertEquals(height, resolveSkinWindowHeight(height,inset,KeyboardSkinId.DEFAULT))
        }
    }
    @Test fun cupertinoReservesInsetsAndReturningToDefaultRemovesOnlyTheOverride() {
        for (skin in listOf(KeyboardSkinId.CUPERTINO_LIGHT,KeyboardSkinId.CUPERTINO_DARK)) {
            assertEquals(516,resolveSkinWindowHeight(390,126,skin))
            assertEquals(390,resolveSkinWindowHeight(390,0,skin))
            assertEquals(390,resolveSkinWindowHeight(390,126,KeyboardSkinId.DEFAULT))
        }
    }
}
