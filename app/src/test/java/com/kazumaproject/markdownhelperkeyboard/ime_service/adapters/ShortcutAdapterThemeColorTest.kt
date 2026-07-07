package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutAdapterThemeColorTest {

    @Test
    fun iconColorSetterKeepsRequestedColor() {
        val state = ShortcutIconColorState()

        state.setIconColor(0x10203040)

        assertEquals(0x10203040, state.iconColor)
    }
}
