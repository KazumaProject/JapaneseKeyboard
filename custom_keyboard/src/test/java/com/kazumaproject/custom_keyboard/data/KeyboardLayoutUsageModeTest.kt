package com.kazumaproject.custom_keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLayoutUsageModeTest {

    @Test
    fun keyboardLayout_defaultUsageMode_isNormal() {
        val layout = KeyboardLayout(
            keys = emptyList(),
            flickKeyMaps = emptyMap(),
            columnCount = 5,
            rowCount = 4
        )

        assertEquals(KeyboardLayoutUsageMode.Normal, layout.usageMode)
    }
}
