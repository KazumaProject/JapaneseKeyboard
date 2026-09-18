package com.kazumaproject.core.domain.key

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyTextSizeDefaultsTest {

    @Test
    fun japaneseTenKeyAndSumireDefaultsAreReadable() {
        assertEquals(20f, KeyTextSizeDefaults.tenKeySizeSp(InputMode.ModeJapanese))
        assertEquals(20f, KeyTextSizeDefaults.SumireKeySp)
    }

    @Test
    fun tenKeyKeepsModeSpecificDefaults() {
        assertEquals(12f, KeyTextSizeDefaults.tenKeySizeSp(InputMode.ModeEnglish))
        assertEquals(16f, KeyTextSizeDefaults.tenKeySizeSp(InputMode.ModeNumber))
    }
}
