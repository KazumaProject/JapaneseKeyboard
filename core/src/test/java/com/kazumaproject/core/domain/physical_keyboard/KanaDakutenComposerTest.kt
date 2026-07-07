package com.kazumaproject.core.domain.physical_keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KanaDakutenComposerTest {
    @Test
    fun append_composesDakutenAndHandakuten() {
        assertEquals("が", KanaDakutenComposer.append("か", "゛"))
        assertEquals("ぱ", KanaDakutenComposer.append("は", "゜"))
        assertEquals("ゔ", KanaDakutenComposer.append("う", "゛"))
        assertEquals("あ゛", KanaDakutenComposer.append("あ", "゛"))
    }
}
