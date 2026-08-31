package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardDisplayNameTest {
    @Test
    fun gojuonHasEnglishDisplayName() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("Japanese – Gojūon", context.getKeyboardDisplayName(KeyboardType.GOJUON))
    }

    @Test
    @Config(qualifiers = "ja")
    fun gojuonHasJapaneseDisplayName() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("日本語 - 50音", context.getKeyboardDisplayName(KeyboardType.GOJUON))
    }
}
